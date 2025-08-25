package com.concerto.omnichannel.service;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
public class ExternalSwitchConnector {

    @Value("${iso8583.tpdu.enabled:true}")
    private boolean tpduEnabled;

    private static final Logger logger = LoggerFactory.getLogger(ExternalSwitchConnector.class);

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
     * Synchronous method to send message to switch - Updated to match Android implementation
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

        // Step 1: Create 2-byte length header (big-endian) - exactly like Android
        ByteBuffer lengthBuffer = ByteBuffer.allocate(2);
        lengthBuffer.putShort((short) isoMessageBytes.length);
        byte[] lengthHeader = lengthBuffer.array();

        logger.debug("Length header created: {} bytes", lengthHeader.length);
        logger.debug("Length header bytes: [0x{}, 0x{}]",
                String.format("%02X", lengthHeader[0] & 0xFF),
                String.format("%02X", lengthHeader[1] & 0xFF));

        // Step 2: Calculate total message size
        int totalSize = lengthHeader.length + TPDU_HEADER.length + isoMessageBytes.length;
        byte[] finalMessage = new byte[totalSize];

        // Step 3: Copy components exactly like Android code
        // Step 1: Copy lengthHeader into the finalMessage array
        System.arraycopy(lengthHeader, 0, finalMessage, 0, lengthHeader.length);

        // Step 2: Copy tpduHeader into the finalMessage array
        System.arraycopy(TPDU_HEADER, 0, finalMessage, lengthHeader.length, TPDU_HEADER.length);

        // Step 3: Copy actualBytes into the finalMessage array
        System.arraycopy(isoMessageBytes, 0, finalMessage,
                lengthHeader.length + TPDU_HEADER.length, isoMessageBytes.length);

        logger.debug("Final message built successfully: {} bytes total", finalMessage.length);
        logger.debug("Structure: Length({}) + TPDU({}) + ISO({})",
                lengthHeader.length, TPDU_HEADER.length, isoMessageBytes.length);

        return finalMessage;
    }

    /**
     * Parse response message - handle both with and without TPDU
     */
    private ISOMsg parseResponseMessage(byte[] responseBytes, String channelId) throws Exception {
        try {
            // First, try to parse assuming response has TPDU (like request)
            if (responseBytes.length > 7 || tpduEnabled) {
                // Check if first 5 bytes look like TPDU
                //  if (responseBytes[0] == (byte) 0x60) {
                logger.debug("Response appears to have TPDU header, extracting ISO message");
                // Skip TPDU header (first 5 bytes) and parse the rest
                byte[] isoBytes = new byte[responseBytes.length - 7];
                System.arraycopy(responseBytes, 7, isoBytes, 0, isoBytes.length);
                logger.debug("Message after removing header {}", ISOUtil.byte2hex(isoBytes));
                return messageParser.unpackMessageWithBinaryPackager(isoBytes);
                //  }
            }

            // If no TPDU, parse entire response as ISO message
            logger.debug("Parsing response as direct ISO message (no TPDU)");
            return messageParser.unpackMessage(responseBytes);

        } catch (Exception e) {
            logger.error("Failed to parse response message", e);
            throw e;
        }
    }

    /**
     * Utility method to convert hex string to bytes (kept from your original code)
     */
    public byte[] hexToBytes(String hexString) {
        try {
            // Remove any spaces, newlines, or other whitespace
            String cleanHex = hexString.replaceAll("\\s+", "");

            // Check if hex string has even length
            if (cleanHex.length() % 2 != 0) {
                throw new IllegalArgumentException("Hex string must have even length");
            }

            // Convert hex string to bytes
            int len = cleanHex.length();
            byte[] data = new byte[len / 2];

            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(cleanHex.charAt(i), 16) << 4)
                        + Character.digit(cleanHex.charAt(i + 1), 16));
            }

            return data;

        } catch (Exception e) {
            logger.error("Error converting hex to bytes: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Test connection to switch
     */
    public boolean testSwitchConnection(String channelId) {
        String switchHost = configurationService.getConfigValue(channelId, "switchHost", defaultSwitchHost);
        int switchPort = configurationService.getConfigValue(channelId, "switchPort", Integer.class, defaultSwitchPort);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(switchHost, switchPort), connectionTimeout);
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

    // ========== REMOVED OLD METHODS ==========
    // Removed: sendMessageWithLength() - now using buildFinalMessage()
    // Removed: receiveMessageWithLength() - now using simple buffer read like Android
    // The old methods were causing the connection issues because they didn't match
    // the Android implementation that works with your switch
}