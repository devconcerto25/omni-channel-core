package com.concerto.omnichannel.service;

import com.concerto.omnichannel.controller.ConnectionPoolMonitoringController;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// Add these imports at the top of AsyncExternalSwitchConnector.java
import java.nio.channels.CompletionHandler;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AsyncExternalSwitchConnector {

    @Value("${iso8583.tpdu.enabled:true}")
    private boolean tpduEnabled;

    private static final Logger logger = LoggerFactory.getLogger(AsyncExternalSwitchConnector.class);

    // TPDU Header constants (matching Android code)
    private static final byte[] TPDU_HEADER = {
            (byte) 0x60,  // Identifier
            (byte) 0x05,  // Source (first byte)
            (byte) 0x26,  // Source (second byte)
            (byte) 0x00,  // Destination (first byte)
            (byte) 0xF1   // Destination (second byte)
    };

    @Autowired
    private ISO8583MessageParser messageParser;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${switch.default.host:localhost}")
    private String defaultSwitchHost;

    @Value("${switch.default.port:8000}")
    private int defaultSwitchPort;

    @Value("${switch.connection.timeout:5000}")
    private int connectionTimeout;

    @Value("${switch.read.timeout:30000}")
    private int readTimeout;

    private final int maxPoolSize = 50; // Increased from 10
    private final int corePoolSize = 20; // New: minimum connections to maintain
    private volatile int currentPoolSize = 0;

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Enhanced connection pool with better error handling
    private final BlockingQueue<AsynchronousSocketChannel> connectionPool = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, ConnectionPool> channelPools = new ConcurrentHashMap<>();

    // Connection health tracking
    private final ConcurrentHashMap<AsynchronousSocketChannel, Long> connectionLastUsed = new ConcurrentHashMap<>();
    private final long connectionMaxIdleTime = TimeUnit.MINUTES.toMillis(5); // 5 minutes idle timeout
    private final ConcurrentHashMap<String, AsynchronousSocketChannel> persistentConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<AsynchronousSocketChannel>> connectionFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> connectionLocks = new ConcurrentHashMap<>();


    // Per-channel connection pools for better isolation
    private static class ConnectionPool {
        private final BlockingQueue<AsynchronousSocketChannel> connections = new LinkedBlockingQueue<>();
        private volatile int currentSize = 0;
        private final int maxSize;
        private final String channelId;

        ConnectionPool(String channelId, int maxSize) {
            this.channelId = channelId;
            this.maxSize = maxSize;
        }
    }

    public CompletableFuture<ISOMsg> sendToSwitchAsync(ISOMsg requestMsg, String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendToSwitchSync(requestMsg, channelId);
            } catch (Exception e) {
                logger.error("Failed to send message to switch for channel: {}", channelId, e);
                throw new RuntimeException("Switch communication failed", e);
            }
        }, executorService).orTimeout(readTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * APPROACH 2: True NIO Asynchronous implementation with AsynchronousSocketChannel
     */
    public CompletableFuture<ISOMsg> sendToSwitchNIO(ISOMsg requestMsg, String channelId) {
        CompletableFuture<ISOMsg> future = new CompletableFuture<>();

        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        logger.info("Sending ISO8583 message to switch {}:{} for channel: {} (NIO)", switchHost, switchPort, channelId);

        try {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

            // Step 1: Connect asynchronously
            channel.connect(new java.net.InetSocketAddress(switchHost, switchPort), null,
                    new CompletionHandler<Void, Void>() {
                        @Override
                        public void completed(Void result, Void attachment) {
                            try {
                                // Step 2: Send message asynchronously
                                byte[] isoMessageBytes = messageParser.packMessage(requestMsg);
                                byte[] finalMessage = buildFinalMessage(isoMessageBytes);

                                ByteBuffer writeBuffer = ByteBuffer.wrap(finalMessage);

                                channel.write(writeBuffer, null, new CompletionHandler<Integer, Void>() {
                                    @Override
                                    public void completed(Integer result, Void attachment) {
                                        logger.info("Message sent to switch: {} bytes", result);

                                        // Step 3: Read response asynchronously
                                        ByteBuffer readBuffer = ByteBuffer.allocate(4096);
                                        channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
                                            @Override
                                            public void completed(Integer result, Void attachment) {
                                                try {
                                                    if (result > 0) {
                                                        readBuffer.flip();
                                                        byte[] responseBytes = new byte[result];
                                                        readBuffer.get(responseBytes);

                                                        logger.info("Received response from switch: {} bytes", result);
                                                        ISOMsg responseMsg = parseResponseMessage(responseBytes, channelId);
                                                        future.complete(responseMsg);
                                                    } else {
                                                        future.completeExceptionally(new IOException("No response received"));
                                                    }
                                                } catch (Exception e) {
                                                    future.completeExceptionally(e);
                                                } finally {
                                                    closeChannel(channel);
                                                }
                                            }

                                            @Override
                                            public void failed(Throwable exc, Void attachment) {
                                                future.completeExceptionally(exc);
                                                closeChannel(channel);
                                            }
                                        });
                                    }

                                    @Override
                                    public void failed(Throwable exc, Void attachment) {
                                        future.completeExceptionally(exc);
                                        closeChannel(channel);
                                    }
                                });

                            } catch (Exception e) {
                                future.completeExceptionally(e);
                                closeChannel(channel);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            future.completeExceptionally(exc);
                            closeChannel(channel);
                        }
                    });

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        // Set timeout
        return future.orTimeout(readTimeout, TimeUnit.MILLISECONDS);
    }


    /**
     * APPROACH 3: Connection pooling with async processing
     */
    public CompletableFuture<ISOMsg> sendToSwitchWithPool(ISOMsg requestMsg, String channelId) {
        return getOrCreateConnection(channelId)
                .thenCompose(channel -> {
                    if (channel == null) {
                        incrementRequestStats(channelId, false);
                        return CompletableFuture.failedFuture(
                                new RuntimeException("Unable to obtain connection for channel: " + channelId));
                    }

                    return sendMessageThroughChannelAsync(channel, requestMsg, channelId)
                            .whenComplete((result, throwable) -> {
                                // Track statistics
                                incrementRequestStats(channelId, throwable == null);

                                // Always return connection to pool (even on error)
                                returnConnectionToPool(channelId, channel, throwable == null);
                            });
                })
                .orTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    // Handle timeout and other failures
                    incrementRequestStats(channelId, false);
                    throw new RuntimeException("Request failed for channel: " + channelId, throwable);
                });
    }
    private AsynchronousSocketChannel borrowConnection(String channelId) throws Exception {
        AsynchronousSocketChannel channel = connectionPool.poll();

        if (channel == null || !channel.isOpen()) {
            if (currentPoolSize < maxPoolSize) {
                channel = createNewConnection(channelId);
                currentPoolSize++;
            } else {
                // Wait for available connection with timeout
                channel = connectionPool.poll(connectionTimeout, TimeUnit.MILLISECONDS);
                if (channel == null) {
                    throw new RuntimeException("Connection pool exhausted");
                }
            }
        }

        return channel;
    }

   /* private AsynchronousSocketChannel createNewConnection(String channelId) throws Exception {
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        Future<Void> connectFuture = channel.connect(new java.net.InetSocketAddress(switchHost, switchPort));
        connectFuture.get(connectionTimeout, TimeUnit.MILLISECONDS);

        return channel;
    }*/

    private void returnConnection(AsynchronousSocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            connectionPool.offer(channel);
        } else {
            currentPoolSize--;
        }
    }

    /**
     * APPROACH 5: Callback-based asynchronous method
     */
    public void sendToSwitchWithCallback(ISOMsg requestMsg, String channelId,
                                         SwitchResponseCallback callback) {
        executorService.submit(() -> {
            try {
                ISOMsg response = sendToSwitchSync(requestMsg, channelId);
                callback.onSuccess(response);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
    }

    /**
     * Interface for callback-based approach
     */
    public interface SwitchResponseCallback {
        void onSuccess(ISOMsg response);
        void onFailure(Exception error);
    }

    /**
     * Utility method for sending message through existing channel
     */
    private ISOMsg sendMessageThroughChannel(AsynchronousSocketChannel channel, ISOMsg requestMsg, String channelId)
            throws Exception {

        // Pack the message
        byte[] isoMessageBytes = messageParser.packMessage(requestMsg);
        byte[] finalMessage = buildFinalMessage(isoMessageBytes);

        // Send message
        ByteBuffer writeBuffer = ByteBuffer.wrap(finalMessage);
        Future<Integer> writeFuture = channel.write(writeBuffer);
        writeFuture.get(readTimeout, TimeUnit.MILLISECONDS);

        // Read response
        ByteBuffer readBuffer = ByteBuffer.allocate(4096);
        Future<Integer> readFuture = channel.read(readBuffer);
        Integer bytesRead = readFuture.get(readTimeout, TimeUnit.MILLISECONDS);

        if (bytesRead <= 0) {
            throw new IOException("No response received from switch");
        }

        readBuffer.flip();
        byte[] responseBytes = new byte[bytesRead];
        readBuffer.get(responseBytes);

        return parseResponseMessage(responseBytes, channelId);
    }

    /**
     * Bulk message processing with async
     */
    public CompletableFuture<java.util.List<ISOMsg>> sendBulkMessages(
            java.util.List<ISOMsg> messages, String channelId) {

        java.util.List<CompletableFuture<ISOMsg>> futures = messages.stream()
                .map(msg -> sendToSwitchAsync(msg, channelId))
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList()));
    }


    /**
     * Original synchronous method (kept for compatibility)
     */
    public ISOMsg sendToSwitchSync(ISOMsg requestMsg, String channelId) throws Exception {
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        logger.info("Sending ISO8583 message to switch {}:{} for channel: {}", switchHost, switchPort, channelId);

        Socket socket = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;

        try {
            // Create socket connection (matching Android code)
            socket = new Socket();
            InetSocketAddress address = new InetSocketAddress(switchHost, switchPort);
            socket.connect(address, connectionTimeout);
            socket.setSoTimeout(readTimeout);

            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            // Pack the ISO8583 message first
            byte[] isoMessageBytes = messageParser.packMessage(requestMsg);
            logger.debug("ISO message packed: {} bytes", isoMessageBytes.length);

            // Build final message with length header and TPDU (exactly like Android)
//            byte[] isoMessageBytes1 = hexToBytes("02003020058020c012000010000000000001000005020051011100274013472305069708d270922628005331415e53504f53303030303031303030303030303335ea68cbdc350d02b101489f26089dcb521ae491c0f79f2701409f100706010a0360bc009f3704e73a5e489f36020ae4950580800480009a032508229c01009f02060000000001005f2a02035682021c009f1a0203569f03060000000000009f3303e0f8c89f34034203009f3501229f1e0830573334333936339f0902008c8407a00000000310109f410400000500500a564953412044454249549b026800");
            byte[] finalMessage = buildFinalMessage(isoMessageBytes);

            logger.info("Sending message to switch: {} bytes (length: {}, TPDU: {}, ISO: {})",
                    finalMessage.length, 2, TPDU_HEADER.length, isoMessageBytes.length);
            logger.debug("Final message hex: {}", ISOUtil.byte2hex(finalMessage));

            // Send the message (matching Android sendToHost method)
            outputStream.write(finalMessage);
            outputStream.flush();

            logger.info("Message sent to switch, waiting for response...");

            // Receive response (simple buffer read like Android)
            byte[] responseBuffer = new byte[4096];
            int responseLength = inputStream.read(responseBuffer);

            if (responseLength <= 0) {
                throw new IOException("No response received from switch");
            }

            logger.info("Received response from switch: {} bytes", responseLength);

            // Extract actual response data (trim buffer to actual length)
            byte[] responseBytes = new byte[responseLength];
            System.arraycopy(responseBuffer, 0, responseBytes, 0, responseLength);

            logger.debug("Response hex: {}", ISOUtil.byte2hex(responseBytes));

            // Parse response - check if it has TPDU header first
            ISOMsg responseMsg = parseResponseMessage(responseBytes, channelId);

            logger.info("Received response from switch with MTI: {} Response Code: {}",
                    responseMsg.getMTI(), responseMsg.getString(39));

            return responseMsg;

        } catch (SocketTimeoutException e) {
            logger.error("Socket timeout while communicating with switch", e);
            throw new IOException("Switch communication timeout", e);
        } catch (ConnectException e) {
            logger.error("Connection failed to switch at {}:{}", switchHost, switchPort, e);
            throw new IOException("Failed to connect to switch", e);
        } catch (SocketException e) {
            logger.error("Socket error during switch communication", e);
            throw new IOException("Socket error during switch communication", e);
        } finally {
            // Clean up resources (matching Android code)
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) { /* ignore */ }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) { /* ignore */ }
            }
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) { /* ignore */ }
            }
        }
    }


    /**
     * Build final message exactly like Android getFinalISOMessage1 method
     */
    private byte[] buildFinalMessage(byte[] isoMessageBytes) {
        logger.debug("Building final message - ISO message length: {}", isoMessageBytes.length);

        ByteBuffer lengthBuffer = ByteBuffer.allocate(2);
        lengthBuffer.putShort((short) isoMessageBytes.length);
        byte[] lengthHeader = lengthBuffer.array();

        int totalSize = lengthHeader.length + TPDU_HEADER.length + isoMessageBytes.length;
        byte[] finalMessage = new byte[totalSize];

        System.arraycopy(lengthHeader, 0, finalMessage, 0, lengthHeader.length);
        System.arraycopy(TPDU_HEADER, 0, finalMessage, lengthHeader.length, TPDU_HEADER.length);
        System.arraycopy(isoMessageBytes, 0, finalMessage,
                lengthHeader.length + TPDU_HEADER.length, isoMessageBytes.length);

        return finalMessage;
    }

    /**
     * Parse response message - handle both with and without TPDU
     */
    private ISOMsg parseResponseMessage(byte[] responseBytes, String channelId) throws Exception {
        try {
            if (responseBytes.length > 7 || tpduEnabled) {
                logger.debug("Response appears to have TPDU header, extracting ISO message");
                byte[] isoBytes = new byte[responseBytes.length - 7];
                System.arraycopy(responseBytes, 7, isoBytes, 0, isoBytes.length);
                return messageParser.unpackMessageWithBinaryPackager(isoBytes);
            }

            logger.debug("Parsing response as direct ISO message (no TPDU)");
            return messageParser.unpackMessage(responseBytes);
        } catch (Exception e) {
            logger.error("Failed to parse response message", e);
            throw e;
        }
    }

    /**
     * Health check for async connections
     */
    public CompletableFuture<Boolean> testSwitchConnectionAsync(String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
            int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

            try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
                Future<Void> connectFuture = channel.connect(
                        new java.net.InetSocketAddress(switchHost, switchPort));
                connectFuture.get(connectionTimeout, TimeUnit.MILLISECONDS);

                logger.info("Successfully connected to switch {}:{} for channel: {}",
                        switchHost, switchPort, channelId);
                return true;
            } catch (Exception e) {
                logger.error("Failed to connect to switch {}:{} for channel: {}",
                        switchHost, switchPort, channelId, e);
                return false;
            }
        }, executorService);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AsyncExternalSwitchConnector");

        // Close all pooled connections
        for (ConnectionPool pool : channelPools.values()) {
            while (!pool.connections.isEmpty()) {
                AsynchronousSocketChannel channel = pool.connections.poll();
                if (channel != null) {
                    closeChannel(channel);
                }
            }
        }
        channelPools.clear();
        connectionLastUsed.clear();

        // Close persistent connections
        persistentConnections.values().forEach(this::closeChannel);
        persistentConnections.clear();

        // Cancel pending futures
        connectionFutures.values().forEach(future -> future.cancel(true));
        connectionFutures.clear();

        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    public CompletableFuture<ISOMsg> sendToSwitchAsyncWithReuse(ISOMsg requestMsg, String channelId) {
        String connectionKey = getConnectionKey(channelId);

        return getOrCreatePersistentConnectionAsync(channelId)
                .thenCompose(channel -> sendMessageThroughChannelAsync(channel, requestMsg, channelId))
                .exceptionally(throwable -> {
                    // If connection fails, remove it and retry once
                    logger.warn("Connection failed for {}, removing from pool: {}", channelId, throwable.getMessage());
                    persistentConnections.remove(connectionKey);
                    connectionFutures.remove(connectionKey);
                    throw new RuntimeException("Switch communication failed", throwable);
                });
    }

    /**
     * Get or create persistent connection asynchronously
     */
    private CompletableFuture<AsynchronousSocketChannel> getOrCreatePersistentConnectionAsync(String channelId) {
        String connectionKey = getConnectionKey(channelId);

        // Check if we have a working connection
        AsynchronousSocketChannel existingChannel = persistentConnections.get(connectionKey);
        if (existingChannel != null && existingChannel.isOpen()) {
            return CompletableFuture.completedFuture(existingChannel);
        }

        // Check if connection is being created
        CompletableFuture<AsynchronousSocketChannel> existingFuture = connectionFutures.get(connectionKey);
        if (existingFuture != null && !existingFuture.isDone()) {
            return existingFuture;
        }

        // Create new connection
        CompletableFuture<AsynchronousSocketChannel> connectionFuture = createNewConnectionAsync(channelId);
        connectionFutures.put(connectionKey, connectionFuture);

        return connectionFuture.thenApply(channel -> {
            persistentConnections.put(connectionKey, channel);
            connectionFutures.remove(connectionKey);
            logger.info("Created persistent connection for channel: {} to {}:{}",
                    channelId, getHost(channelId), getPort(channelId));
            return channel;
        });
    }

    private CompletableFuture<AsynchronousSocketChannel> createNewConnectionAsync(String channelId) {
        CompletableFuture<AsynchronousSocketChannel> future = new CompletableFuture<>();

        try {
            String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
            int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

            // Set socket options for performance
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

            InetSocketAddress address = new InetSocketAddress(switchHost, switchPort);

            channel.connect(address, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    logger.debug("Successfully connected to {}:{}", switchHost, switchPort);
                    future.complete(channel);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    logger.error("Failed to connect to {}:{}", switchHost, switchPort, exc);
                    closeChannel(channel);
                    future.completeExceptionally(exc);
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }
    /**
     * Send message through existing channel asynchronously*/

    private CompletableFuture<ISOMsg> sendMessageThroughChannelAsync(AsynchronousSocketChannel channel,
                                                                     ISOMsg requestMsg, String channelId) {
        CompletableFuture<ISOMsg> future = new CompletableFuture<>();

        try {
            // Pack the message
            byte[] isoMessageBytes = messageParser.packMessage(requestMsg);
            byte[] finalMessage = buildFinalMessage(isoMessageBytes);
            ByteBuffer writeBuffer = ByteBuffer.wrap(finalMessage);

            logger.debug("Sending {} bytes to switch for channel {}", finalMessage.length, channelId);

            // Write message asynchronously with timeout
            channel.write(writeBuffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer bytesWritten, Void attachment) {
                    logger.debug("Successfully wrote {} bytes to switch", bytesWritten);

                    // Read response asynchronously
                    ByteBuffer readBuffer = ByteBuffer.allocate(4096);
                    long readStartTime = System.currentTimeMillis();

                    channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer bytesRead, Void attachment) {
                            try {
                                long readTime = System.currentTimeMillis() - readStartTime;
                                logger.debug("Received {} bytes from switch in {}ms", bytesRead, readTime);

                                if (bytesRead > 0) {
                                    readBuffer.flip();
                                    byte[] responseBytes = new byte[bytesRead];
                                    readBuffer.get(responseBytes);

                                    ISOMsg responseMsg = parseResponseMessage(responseBytes, channelId);
                                    future.complete(responseMsg);
                                } else {
                                    future.completeExceptionally(new IOException("No response received from switch"));
                                }
                            } catch (Exception e) {
                                logger.error("Error processing response", e);
                                future.completeExceptionally(e);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            logger.error("Failed to read response from switch", exc);
                            future.completeExceptionally(exc);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    logger.error("Failed to write to switch", exc);
                    future.completeExceptionally(exc);
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }


     /* Periodic cleanup of stale connections
     */
     @Scheduled(fixedRate = 300000) // Every 5 minutes
     public void cleanupStaleConnections() {
         logger.debug("Starting cleanup of stale connections");

         long currentTime = System.currentTimeMillis();
         int cleaned = 0;

         for (ConnectionPool pool : channelPools.values()) {
             List<AsynchronousSocketChannel> staleConnections = new ArrayList<>();

             // Identify stale connections
             for (AsynchronousSocketChannel channel : pool.connections) {
                 Long lastUsed = connectionLastUsed.get(channel);
                 if (lastUsed != null && (currentTime - lastUsed) > connectionMaxIdleTime) {
                     staleConnections.add(channel);
                 }
             }

             // Remove and close stale connections
             for (AsynchronousSocketChannel staleChannel : staleConnections) {
                 closeChannel(staleChannel);
                 connectionLastUsed.remove(staleChannel);
                 pool.currentSize--;
                 cleaned++;
             }
         }
     }

    private void closeChannel(AsynchronousSocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                logger.warn("Error closing channel", e);
            }
        }
    }

    /**
     * Enhanced shutdown with better cleanup
     */



    private String getConnectionKey(String channelId) {
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);
        return channelId + ":" + switchHost + ":" + switchPort;
    }

    private String getHost(String channelId) {
        return configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
    }

    private int getPort(String channelId) {
        return configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);
    }



    private AsynchronousSocketChannel getOrCreatePersistentConnection(String channelId) throws Exception {
        String connectionKey = getConnectionKey(channelId);
        AsynchronousSocketChannel channel = persistentConnections.get(connectionKey);

        if (channel == null || !channel.isOpen()) {
            // Create new persistent connection
            String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
            int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

            channel = AsynchronousSocketChannel.open();
            Future<Void> connectFuture = channel.connect(new InetSocketAddress(switchHost, switchPort));
            connectFuture.get(connectionTimeout, TimeUnit.MILLISECONDS);

            persistentConnections.put(connectionKey, channel);
            logger.info("Created persistent connection for channel: {} to {}:{}", channelId, switchHost, switchPort);
        }

        return channel;
    }

    private void sendMessageAsync(AsynchronousSocketChannel channel, ISOMsg requestMsg,
                                  String channelId, CompletableFuture<ISOMsg> future) {
        try {
            byte[] isoMessageBytes = messageParser.packMessage(requestMsg);
            byte[] finalMessage = buildFinalMessage(isoMessageBytes);
            ByteBuffer writeBuffer = ByteBuffer.wrap(finalMessage);

            channel.write(writeBuffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    logger.debug("Sent {} bytes to switch", result);

                    // Read response asynchronously
                    ByteBuffer readBuffer = ByteBuffer.allocate(4096);
                    channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer bytesRead, Void attachment) {
                            try {
                                if (bytesRead > 0) {
                                    readBuffer.flip();
                                    byte[] responseBytes = new byte[bytesRead];
                                    readBuffer.get(responseBytes);

                                    ISOMsg responseMsg = parseResponseMessage(responseBytes, channelId);
                                    future.complete(responseMsg);
                                } else {
                                    future.completeExceptionally(new IOException("No response received"));
                                }
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            // Connection failed, remove from pool and retry once
                            String connectionKey = getConnectionKey(channelId);
                            persistentConnections.remove(connectionKey);
                            future.completeExceptionally(exc);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    String connectionKey = getConnectionKey(channelId);
                    persistentConnections.remove(connectionKey);
                    future.completeExceptionally(exc);
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }



    // Add cleanup method for persistent connections
    public void closePersistentConnections() {
        persistentConnections.values().forEach(channel -> {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                logger.warn("Error closing persistent connection", e);
            }
        });
        persistentConnections.clear();
    }


    private CompletableFuture<AsynchronousSocketChannel> getOrCreateConnection(String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConnectionPool pool = getOrCreateChannelPool(channelId);

                // Try to get existing connection first
                AsynchronousSocketChannel channel = pool.connections.poll();

                if (channel != null && isConnectionHealthy(channel)) {
                    connectionLastUsed.put(channel, System.currentTimeMillis());
                    logger.debug("Reused existing connection for channel: {}", channelId);
                    return channel;
                }

                // Close unhealthy connection if any
                if (channel != null) {
                    closeChannel(channel);
                    pool.currentSize--;
                }

                // Create new connection if pool not at capacity
                if (pool.currentSize < pool.maxSize) {
                    channel = createNewConnection(channelId);
                    if (channel != null) {
                        pool.currentSize++;
                        connectionLastUsed.put(channel, System.currentTimeMillis());
                        logger.debug("Created new connection for channel: {}, pool size: {}",
                                channelId, pool.currentSize);
                        return channel;
                    }
                }

                // Wait for available connection with timeout (non-blocking)
                channel = pool.connections.poll(connectionTimeout, TimeUnit.MILLISECONDS);
                if (channel != null && isConnectionHealthy(channel)) {
                    connectionLastUsed.put(channel, System.currentTimeMillis());
                    return channel;
                }

                // If we get here, pool is exhausted
                logger.error("Connection pool exhausted for channel: {}, current size: {}, max size: {}",
                        channelId, pool.currentSize, pool.maxSize);
                return null;

            } catch (Exception e) {
                logger.error("Error obtaining connection for channel: {}", channelId, e);
                return null;
            }
        }, executorService);
    }

    /**
     * Create per-channel connection pool
     */
    private ConnectionPool getOrCreateChannelPool(String channelId) {
        return channelPools.computeIfAbsent(channelId,
                id -> new ConnectionPool(id, maxPoolSize / Math.max(channelPools.size(), 1) + 10)); // Dynamic sizing
    }

    private AsynchronousSocketChannel createNewConnection(String channelId) {
        try {
            String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
            int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

            // Set optimized socket options
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

            // Connect with timeout
            Future<Void> connectFuture = channel.connect(new InetSocketAddress(switchHost, switchPort));
            connectFuture.get(connectionTimeout, TimeUnit.MILLISECONDS);

            logger.debug("Successfully created connection to {}:{} for channel: {}",
                    switchHost, switchPort, channelId);
            return channel;

        } catch (Exception e) {
            logger.error("Failed to create connection for channel: {}", channelId, e);
            return null;
        }
    }


    /**
     * Check if connection is healthy
     */
    private boolean isConnectionHealthy(AsynchronousSocketChannel channel) {
        if (channel == null || !channel.isOpen()) {
            return false;
        }

        // Check if connection has been idle too long
        Long lastUsed = connectionLastUsed.get(channel);
        if (lastUsed != null && (System.currentTimeMillis() - lastUsed) > connectionMaxIdleTime) {
            logger.debug("Connection is stale, marking as unhealthy");
            connectionLastUsed.remove(channel);
            return false;
        }

        return true;
    }


    /**
     * Return connection to appropriate pool
     */
    private void returnConnectionToPool(String channelId, AsynchronousSocketChannel channel, boolean healthy) {
        if (channel == null) return;

        ConnectionPool pool = channelPools.get(channelId);
        if (pool == null) {
            closeChannel(channel);
            return;
        }

        if (healthy && channel.isOpen() && isConnectionHealthy(channel)) {
            // Return healthy connection to pool
            if (pool.connections.offer(channel)) {
                logger.debug("Returned connection to pool for channel: {}", channelId);
            } else {
                // Pool is full, close the connection
                closeChannel(channel);
                pool.currentSize--;
                connectionLastUsed.remove(channel);
            }
        } else {
            // Close unhealthy connection
            closeChannel(channel);
            pool.currentSize--;
            connectionLastUsed.remove(channel);
            logger.debug("Closed unhealthy connection for channel: {}", channelId);
        }
    }
    // Statistics tracking
    private final ConcurrentHashMap<String, AtomicLong> totalRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> failedRequests = new ConcurrentHashMap<>();


    private void incrementRequestStats(String channelId, boolean success) {
        totalRequests.computeIfAbsent(channelId, k -> new AtomicLong(0)).incrementAndGet();
        if (!success) {
            failedRequests.computeIfAbsent(channelId, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public Map<String, ConnectionPoolMonitoringController.PoolStatistics> getPoolStatistics() {
        Map<String, ConnectionPoolMonitoringController.PoolStatistics> stats = new HashMap<>();

        for (Map.Entry<String, ConnectionPool> entry : channelPools.entrySet()) {
            String channelId = entry.getKey();
            ConnectionPool pool = entry.getValue();

            long totalReqs = totalRequests.getOrDefault(channelId, new AtomicLong(0)).get();
            long failedReqs = failedRequests.getOrDefault(channelId, new AtomicLong(0)).get();

            ConnectionPoolMonitoringController.PoolStatistics poolStats = new ConnectionPoolMonitoringController.PoolStatistics(
                    channelId,
                    pool.maxSize,
                    pool.currentSize,
                    pool.connections.size(),
                    totalReqs,
                    failedReqs
            );

            stats.put(channelId, poolStats);
        }

        return stats;
    }
}