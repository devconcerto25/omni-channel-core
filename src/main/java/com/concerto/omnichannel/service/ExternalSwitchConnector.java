// External Switch Connector Service
package com.concerto.omnichannel.service;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ExternalSwitchConnector {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSwitchConnector.class);

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

    /**
     * Send ISO8583 message to external switch/interchange
     */
    public CompletableFuture<ISOMsg> sendToSwitch(ISOMsg requestMsg, String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendToSwitchSync(requestMsg, channelId);
            } catch (Exception e) {
                logger.error("Failed to send message to switch for channel: {}", channelId, e);
                throw new RuntimeException("Switch communication failed", e);
            }
        });
    }

    /**
     * Synchronous method to send message to switch
     */
    public ISOMsg sendToSwitchSync(ISOMsg requestMsg, String channelId) throws Exception {
        // Get channel-specific switch configuration
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        logger.info("Sending ISO8583 message to switch {}:{} for channel: {}", switchHost, switchPort, channelId);

        Socket socket = null;
        try {
            // Establish connection to switch
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(switchHost, switchPort), connectionTimeout);
            socket.setSoTimeout(readTimeout);

            // Pack and send the message
            byte[] requestBytes = messageParser.packMessage(requestMsg);
            sendMessageWithLength(socket, requestBytes);

            logger.debug("ISO8583 message sent to switch, waiting for response...");

            // Receive and unpack response
            byte[] responseBytes = receiveMessageWithLength(socket);
            ISOMsg responseMsg = messageParser.unpackMessage(responseBytes);

            logger.info("Received response from switch with MTI: {} Response Code: {}",
                    responseMsg.getMTI(), responseMsg.getString(39));

            return responseMsg;

        } catch (SocketTimeoutException e) {
            logger.error("Timeout waiting for response from switch {}:{}", switchHost, switchPort);
            throw new RuntimeException("Switch response timeout", e);
        } catch (IOException e) {
            logger.error("IO error communicating with switch {}:{}", switchHost, switchPort, e);
            throw new RuntimeException("Switch communication error", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Error closing socket connection", e);
                }
            }
        }
    }

    /**
     * Send message with 2-byte length header (standard for ISO8583)
     */
    private void sendMessageWithLength(Socket socket, byte[] messageBytes) throws IOException {
        // Create length header (2 bytes, big-endian)
        byte[] lengthHeader = new byte[2];
        int messageLength = messageBytes.length;
        lengthHeader[0] = (byte) ((messageLength >> 8) & 0xFF);
        lengthHeader[1] = (byte) (messageLength & 0xFF);

        // Send length header followed by message
        socket.getOutputStream().write(lengthHeader);
        socket.getOutputStream().write(messageBytes);
        socket.getOutputStream().flush();

        logger.debug("Sent message to switch, length: {} bytes", messageLength);
    }

    /**
     * Receive message with 2-byte length header
     */
    private byte[] receiveMessageWithLength(Socket socket) throws IOException {
        // Read 2-byte length header
        byte[] lengthHeader = new byte[2];
        int bytesRead = 0;
        while (bytesRead < 2) {
            int read = socket.getInputStream().read(lengthHeader, bytesRead, 2 - bytesRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading length header");
            }
            bytesRead += read;
        }

        // Parse message length
        int messageLength = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

        if (messageLength <= 0 || messageLength > 8192) { // Sanity check
            throw new IOException("Invalid message length: " + messageLength);
        }

        // Read message data
        byte[] messageBytes = new byte[messageLength];
        bytesRead = 0;
        while (bytesRead < messageLength) {
            int read = socket.getInputStream().read(messageBytes, bytesRead, messageLength - bytesRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading message data");
            }
            bytesRead += read;
        }

        logger.debug("Received message from switch, length: {} bytes", messageLength);
        return messageBytes;
    }

    /**
     * Test connection to switch
     */
    public boolean testSwitchConnection(String channelId) {
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(switchHost, switchPort), connectionTimeout);
            logger.info("Successfully connected to switch {}:{} for channel: {}", switchHost, switchPort, channelId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to switch {}:{} for channel: {}", switchHost, switchPort, channelId, e);
            return false;
        }
    }

    /**
     * Create a reversal message for failed transactions
     */
    public ISOMsg createReversalMessage(ISOMsg originalMsg) throws Exception {
        ISOMsg reversalMsg = (ISOMsg) originalMsg.clone();

        // Change MTI to reversal (0400)
        reversalMsg.setMTI("0400");

        // Set reversal reason code
        reversalMsg.set(90, "05"); // System malfunction

        // Update STAN if needed
        reversalMsg.set(11, messageParser.generateSTAN());

        // Update timestamp
        reversalMsg.set(7, getCurrentTimestamp());

        logger.info("Created reversal message for original STAN: {}", originalMsg.getString(11));
        return reversalMsg;
    }

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("MMddHHmmss").format(new java.util.Date());
    }
}