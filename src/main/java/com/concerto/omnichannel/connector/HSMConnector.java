package com.concerto.omnichannel.connector;


import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.entity.TerminalPINKey;
import com.concerto.omnichannel.repository.TerminalPINKeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.*;

@Component("HSM")
public class HSMConnector implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(HSMConnector.class);

    @Autowired
    private ConnectorTimeoutConfig timeoutConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${hsm.host:localhost}")
    private String hsmHost;

    @Value("${hsm.port:1500}")
    private int hsmPort;

    @Value("${hsm.connection.timeout:5000}")
    private int connectionTimeout;

    @Autowired
    private TerminalPINKeyRepository pinKeyRepository;


    // HSM Command Constants
    private static final String PIN_VERIFY = "PIN_VERIFY";
    private static final String KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String ENCRYPT_DATA = "ENCRYPT_DATA";
    private static final String DECRYPT_DATA = "DECRYPT_DATA";
    private static final String GENERATE_MAC = "GENERATE_MAC";
    private static final String VERIFY_MAC = "VERIFY_MAC";
    private static final String GENERATE_KEY = "GENERATE_KEY";
    private static final String TRANSLATE_PIN = "TRANSLATE_PIN";
    private static final String GENERATE_CVV = "GENERATE_CVV";
    private static final String VERIFY_CVV = "VERIFY_CVV";

    @Override
    public String process(TransactionRequest request) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<String> task = () -> processHSMRequest(request);
        Future<String> future = executor.submit(task);

        try {
            int timeout = timeoutConfig.getTimeoutFor("HSM");
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("HSM Connector timed out after " +
                    timeoutConfig.getTimeoutFor("HSM") + "ms");
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public CompletableFuture<String> processAsync(TransactionRequest request) throws Exception {
        return null;
    }

    @Override
    public boolean supports(String channel) {
        return "HSM".equalsIgnoreCase(channel);
    }

    @Override
    public String getConnectorType() {
        return "HSM_SOCKET";
    }

    private String processHSMRequest(TransactionRequest request) throws Exception {
        logger.info("Processing HSM request");
        String payload = objectMapper.writeValueAsString(request);
        JsonNode jsonNode = objectMapper.readTree(payload);
        String channel = jsonNode.get("channel").asText();
      //  Map<String, Object> request = objectMapper.readValue(payload, Map.class);
        String operation = jsonNode.get("operation").asText();
        //Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        String merchantId = jsonNode.get("merchantId").toString();
        String terminalId = jsonNode.get("terminalId").toString();
        String pan = jsonNode.get("cardNumber").toString();
        String encryptedPIN = String.valueOf(jsonNode.get("additionalFields").get("pinBlock"));

        // Get PIN key for terminal
        String pinKey = getPINKey(merchantId, terminalId);

        // Create HSM verification request
        Map<String, Object> hsmRequest = Map.of(
                "channel", "HSM",
                "operation", "PIN_VERIFY",
                "payload", Map.of(
                        "transactionType", "pin_verification",
                        "additionalFields", Map.of(
                                "encryptedPIN", encryptedPIN,
                                "panBlock", extractPAN(pan),
                                "pinKey", pinKey
                        )
                )
        );

        return switch (operation.toUpperCase()) {
            case PIN_VERIFY -> pinVerification(hsmRequest);
            case KEY_EXCHANGE -> keyExchange(hsmRequest);
            case ENCRYPT_DATA -> encryptData(hsmRequest);
            case DECRYPT_DATA -> decryptData(hsmRequest);
            case GENERATE_MAC -> generateMAC(hsmRequest);
            case VERIFY_MAC -> verifyMAC(hsmRequest);
            case GENERATE_KEY -> generateKey(hsmRequest);
            case TRANSLATE_PIN -> translatePIN(hsmRequest);
            case GENERATE_CVV -> generateCVV(hsmRequest);
            case VERIFY_CVV -> verifyCVV(hsmRequest);
            default -> throw new IllegalArgumentException("Unsupported HSM operation: " + operation);
        };
    }

    private String extractPAN(String pan) {
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("Invalid PAN: must be at least 13 digits");
        }

        // Remove any non-digit characters
        String cleanPan = pan.replaceAll("\\D", "");

        if (cleanPan.length() < 13 || cleanPan.length() > 19) {
            throw new IllegalArgumentException("Invalid PAN length: must be between 13-19 digits");
        }

        // Extract rightmost 12 digits excluding the check digit (last digit)
        // For a 16-digit PAN: positions 4-15 (0-indexed: 3-14)
        String panBlock;
        if (cleanPan.length() >= 13) {
            // Get rightmost 12 digits excluding check digit
            String rightmostDigits = cleanPan.substring(cleanPan.length() - 13, cleanPan.length() - 1);
            panBlock = "0000" + rightmostDigits;
        } else {
            throw new IllegalArgumentException("PAN too short for block extraction");
        }

        logger.debug("PAN block extracted for HSM verification");
        return panBlock;
    }

    private String getPINKey(String merchantId, String terminalId) {
        TerminalPINKey pinKeyEntity = pinKeyRepository
                .findByMerchantIdAndTerminalId(merchantId, terminalId)
                .orElseThrow(() -> new RuntimeException("PIN key not found for terminal"));

        return decryptPINKey(pinKeyEntity.getPinKey()); // Decrypt stored PIN key
    }

    private String decryptPINKey(String pinKey) {
        return  pinKey;
    }

    // ===== PIN VERIFICATION =====
    private String pinVerification(Map<String, Object> payload) throws Exception {
        logger.debug("Processing PIN verification request");

        String encryptedPIN = (String) payload.get("encryptedPIN");
        String panBlock = (String) payload.get("cardNumber");
        String pinKey = (String) payload.get("pinKey");

        // Build HSM command for PIN verification
        HSMCommand command = HSMCommand.builder()
                .commandCode("CA")  // PIN Verify command
                .parameter("PIN_BLOCK", encryptedPIN)
                .parameter("PAN_BLOCK", panBlock)
                .parameter("PIN_KEY", pinKey)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "message", response.getResponseCode().equals("00") ? "PIN verification successful" : "PIN verification failed",
                "hsmResponse", response.getRawResponse()
        ));
    }

    // ===== KEY EXCHANGE =====
    private String keyExchange(Map<String, Object> payload) throws Exception {
        logger.debug("Processing key exchange request");

        String keyType = (String) payload.get("keyType");
        String keyLength = (String) payload.get("keyLength");
        String keyUsage = (String) payload.get("keyUsage");

        HSMCommand command = HSMCommand.builder()
                .commandCode("A0")  // Generate Key command
                .parameter("KEY_TYPE", keyType)
                .parameter("KEY_LENGTH", keyLength)
                .parameter("KEY_USAGE", keyUsage)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "keyValue", response.getParameter("KEY_VALUE"),
                "keyCheckValue", response.getParameter("KEY_CHECK_VALUE"),
                "message", "Key exchange completed"
        ));
    }

    // ===== DATA ENCRYPTION =====
    private String encryptData(Map<String, Object> payload) throws Exception {
        logger.debug("Processing data encryption request");

        String plainData = (String) payload.get("plainData");
        String encryptionKey = (String) payload.get("encryptionKey");
        String algorithm = (String) payload.get("algorithm");

        HSMCommand command = HSMCommand.builder()
                .commandCode("M0")  // Encrypt Data command
                .parameter("PLAIN_DATA", plainData)
                .parameter("ENCRYPTION_KEY", encryptionKey)
                .parameter("ALGORITHM", algorithm)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "encryptedData", response.getParameter("ENCRYPTED_DATA"),
                "message", "Data encryption completed"
        ));
    }

    // ===== DATA DECRYPTION =====
    private String decryptData(Map<String, Object> payload) throws Exception {
        logger.debug("Processing data decryption request");

        String encryptedData = (String) payload.get("encryptedData");
        String decryptionKey = (String) payload.get("decryptionKey");
        String algorithm = (String) payload.get("algorithm");

        HSMCommand command = HSMCommand.builder()
                .commandCode("M2")  // Decrypt Data command
                .parameter("ENCRYPTED_DATA", encryptedData)
                .parameter("DECRYPTION_KEY", decryptionKey)
                .parameter("ALGORITHM", algorithm)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "plainData", response.getParameter("PLAIN_DATA"),
                "message", "Data decryption completed"
        ));
    }

    // ===== MAC GENERATION =====
    private String generateMAC(Map<String, Object> payload) throws Exception {
        logger.debug("Processing MAC generation request");

        String messageData = (String) payload.get("messageData");
        String macKey = (String) payload.get("macKey");
        String macAlgorithm = (String) payload.get("macAlgorithm");

        HSMCommand command = HSMCommand.builder()
                .commandCode("MS")  // Generate MAC command
                .parameter("MESSAGE_DATA", messageData)
                .parameter("MAC_KEY", macKey)
                .parameter("MAC_ALGORITHM", macAlgorithm)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "macValue", response.getParameter("MAC_VALUE"),
                "message", "MAC generation completed"
        ));
    }

    // ===== MAC VERIFICATION =====
    private String verifyMAC(Map<String, Object> payload) throws Exception {
        logger.debug("Processing MAC verification request");

        String messageData = (String) payload.get("messageData");
        String macKey = (String) payload.get("macKey");
        String macValue = (String) payload.get("macValue");
        String macAlgorithm = (String) payload.get("macAlgorithm");

        HSMCommand command = HSMCommand.builder()
                .commandCode("MV")  // Verify MAC command
                .parameter("MESSAGE_DATA", messageData)
                .parameter("MAC_KEY", macKey)
                .parameter("MAC_VALUE", macValue)
                .parameter("MAC_ALGORITHM", macAlgorithm)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "verified", "00".equals(response.getResponseCode()),
                "message", "00".equals(response.getResponseCode()) ? "MAC verification successful" : "MAC verification failed"
        ));
    }

    // ===== KEY GENERATION =====
    private String generateKey(Map<String, Object> payload) throws Exception {
        logger.debug("Processing key generation request");

        String keyType = (String) payload.get("keyType");
        String keyLength = (String) payload.get("keyLength");
        String keyUsage = (String) payload.get("keyUsage");

        HSMCommand command = HSMCommand.builder()
                .commandCode("A0")  // Generate Key command
                .parameter("KEY_TYPE", keyType)
                .parameter("KEY_LENGTH", keyLength)
                .parameter("KEY_USAGE", keyUsage)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "keyValue", response.getParameter("KEY_VALUE"),
                "keyCheckValue", response.getParameter("KEY_CHECK_VALUE"),
                "message", "Key generation completed"
        ));
    }

    // ===== PIN TRANSLATION =====
    private String translatePIN(Map<String, Object> payload) throws Exception {
        logger.debug("Processing PIN translation request");

        String sourcePINBlock = (String) payload.get("sourcePINBlock");
        String sourceKey = (String) payload.get("sourceKey");
        String destinationKey = (String) payload.get("destinationKey");
        String panBlock = (String) payload.get("panBlock");

        HSMCommand command = HSMCommand.builder()
                .commandCode("CC")  // Translate PIN command
                .parameter("SOURCE_PIN_BLOCK", sourcePINBlock)
                .parameter("SOURCE_KEY", sourceKey)
                .parameter("DESTINATION_KEY", destinationKey)
                .parameter("PAN_BLOCK", panBlock)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "translatedPINBlock", response.getParameter("TRANSLATED_PIN_BLOCK"),
                "message", "PIN translation completed"
        ));
    }

    // ===== CVV GENERATION =====
    private String generateCVV(Map<String, Object> payload) throws Exception {
        logger.debug("Processing CVV generation request");

        String pan = (String) payload.get("pan");
        String expiryDate = (String) payload.get("expiryDate");
        String serviceCode = (String) payload.get("serviceCode");
        String cvvKey = (String) payload.get("cvvKey");

        HSMCommand command = HSMCommand.builder()
                .commandCode("CW")  // Generate CVV command
                .parameter("PAN", pan)
                .parameter("EXPIRY_DATE", expiryDate)
                .parameter("SERVICE_CODE", serviceCode)
                .parameter("CVV_KEY", cvvKey)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "cvvValue", response.getParameter("CVV_VALUE"),
                "message", "CVV generation completed"
        ));
    }

    // ===== CVV VERIFICATION =====
    private String verifyCVV(Map<String, Object> payload) throws Exception {
        logger.debug("Processing CVV verification request");

        String pan = (String) payload.get("pan");
        String expiryDate = (String) payload.get("expiryDate");
        String serviceCode = (String) payload.get("serviceCode");
        String cvvKey = (String) payload.get("cvvKey");
        String cvvValue = (String) payload.get("cvvValue");

        HSMCommand command = HSMCommand.builder()
                .commandCode("CY")  // Verify CVV command
                .parameter("PAN", pan)
                .parameter("EXPIRY_DATE", expiryDate)
                .parameter("SERVICE_CODE", serviceCode)
                .parameter("CVV_KEY", cvvKey)
                .parameter("CVV_VALUE", cvvValue)
                .build();

        HSMResponse response = sendToHSM(command);

        return objectMapper.writeValueAsString(Map.of(
                "success", "00".equals(response.getResponseCode()),
                "responseCode", response.getResponseCode(),
                "verified", "00".equals(response.getResponseCode()),
                "message", "00".equals(response.getResponseCode()) ? "CVV verification successful" : "CVV verification failed"
        ));
    }

    // ===== HSM COMMUNICATION =====
    private HSMResponse sendToHSM(HSMCommand command) throws Exception {
        Socket socket = null;
        try {
            socket = new Socket(hsmHost, hsmPort);
            socket.setSoTimeout(connectionTimeout);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send command to HSM
            byte[] commandBytes = command.toBytes();
            out.write(commandBytes);
            out.flush();

            logger.debug("Sent HSM command: {}", command.getCommandCode());

            // Read response from HSM
            byte[] responseBuffer = new byte[4096];
            int responseLength = in.read(responseBuffer);

            if (responseLength <= 0) {
                throw new Exception("No response received from HSM");
            }

            byte[] responseBytes = new byte[responseLength];
            System.arraycopy(responseBuffer, 0, responseBytes, 0, responseLength);

            HSMResponse response = HSMResponse.fromBytes(responseBytes);
            logger.debug("Received HSM response: {}", response.getResponseCode());

            return response;

        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    // ===== HSM COMMAND BUILDER =====
    public static class HSMCommand {
        private String commandCode;
        private Map<String, String> parameters;

        private HSMCommand(String commandCode, Map<String, String> parameters) {
            this.commandCode = commandCode;
            this.parameters = parameters;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String commandCode;
            private Map<String, String> parameters = new java.util.HashMap<>();

            public Builder commandCode(String commandCode) {
                this.commandCode = commandCode;
                return this;
            }

            public Builder parameter(String key, String value) {
                this.parameters.put(key, value);
                return this;
            }

            public HSMCommand build() {
                return new HSMCommand(commandCode, parameters);
            }
        }

        public String getCommandCode() {
            return commandCode;
        }

        public byte[] toBytes() {
            // Convert command to HSM-specific byte format
            StringBuilder command = new StringBuilder();
            command.append(commandCode);

            parameters.forEach((key, value) -> {
                command.append(value != null ? value : "");
            });

            String commandStr = command.toString();
            byte[] lengthPrefix = String.format("%04d", commandStr.length()).getBytes();

            byte[] result = new byte[lengthPrefix.length + commandStr.length()];
            System.arraycopy(lengthPrefix, 0, result, 0, lengthPrefix.length);
            System.arraycopy(commandStr.getBytes(), 0, result, lengthPrefix.length, commandStr.length());

            return result;
        }
    }

    // ===== HSM RESPONSE PARSER =====
    public static class HSMResponse {
        private String responseCode;
        private String rawResponse;
        private Map<String, String> parameters;

        private HSMResponse(String responseCode, String rawResponse, Map<String, String> parameters) {
            this.responseCode = responseCode;
            this.rawResponse = rawResponse;
            this.parameters = parameters;
        }

        public static HSMResponse fromBytes(byte[] responseBytes) {
            String response = new String(responseBytes);

            // Parse response format: first 2 chars are response code
            String responseCode = response.length() >= 2 ? response.substring(0, 2) : "99";

            // Parse parameters based on HSM response format
            Map<String, String> parameters = parseResponseParameters(response);

            return new HSMResponse(responseCode, response, parameters);
        }

        private static Map<String, String> parseResponseParameters(String response) {
            Map<String, String> parameters = new java.util.HashMap<>();

            // HSM-specific response parsing logic
            // This would depend on your specific HSM vendor's response format
            if (response.length() > 2) {
                String data = response.substring(2);
                // Parse based on your HSM's response structure
                parameters.put("KEY_VALUE", data.length() > 16 ? data.substring(0, 16) : "");
                parameters.put("KEY_CHECK_VALUE", data.length() > 16 ? data.substring(16, Math.min(22, data.length())) : "");
            }

            return parameters;
        }

        public String getResponseCode() {
            return responseCode;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getParameter(String key) {
            return parameters.get(key);
        }
    }
}
