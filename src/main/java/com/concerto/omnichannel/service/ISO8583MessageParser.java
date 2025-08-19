// Enhanced ISO8583 Message Parser with ASCII/Binary support
package com.concerto.omnichannel.service;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ISO8583MessageParser {

    private static final Logger logger = LoggerFactory.getLogger(ISO8583MessageParser.class);

    private GenericPackager asciiPackager;
    private GenericPackager binaryPackager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${iso8583.packager.ascii.config:iso87ascii.xml}")
    private String asciiPackagerConfig;

    @Value("${iso8583.packager.binary.config:iso87binary.xml}")
    private String binaryPackagerConfig;

    @Value("${iso8583.packager.type:ascii}")
    private String defaultPackagerType;

    @PostConstruct
    public void initialize() {
        try {
            // Initialize ASCII packager
            initializeAsciiPackager();

            // Initialize Binary packager
            initializeBinaryPackager();

            logger.info("ISO8583 packagers initialized successfully - ASCII: {}, Binary: {}",
                    asciiPackager != null, binaryPackager != null);
        } catch (Exception e) {
            logger.error("Failed to initialize ISO8583 packagers", e);
            throw new RuntimeException("Failed to initialize ISO8583 packagers", e);
        }
    }

    private void initializeAsciiPackager() {
        try {
            InputStream configStream = getClass().getClassLoader().getResourceAsStream(asciiPackagerConfig);
            if (configStream == null) {
                logger.warn("ASCII packager config file not found, using default");
                asciiPackager = createDefaultAsciiPackager();
            } else {
                asciiPackager = new GenericPackager(configStream);
            }
            logger.info("ASCII packager initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize ASCII packager", e);
            asciiPackager = createDefaultAsciiPackager();
        }
    }

    /*private void initializeBinaryPackager() {
        try {
            InputStream configStream = getClass().getClassLoader().getResourceAsStream(binaryPackagerConfig);
            if (configStream == null) {
                logger.warn("Binary packager config file not found, using default");
                binaryPackager = createDefaultBinaryPackager();
            } else {
                binaryPackager = new GenericPackager(configStream);
            }
            logger.info("Binary packager initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize binary packager", e);
            binaryPackager = createDefaultBinaryPackager();
        }
    }*/

    private void initializeBinaryPackager() {
        try {
            InputStream configStream = getClass().getClassLoader().getResourceAsStream(binaryPackagerConfig);
            if (configStream == null) {
                logger.warn("Binary packager config file not found, using ASCII as fallback");
                binaryPackager = asciiPackager; // Use ASCII packager as fallback
            } else {
                binaryPackager = new GenericPackager(configStream);
            }
            logger.info("Binary packager initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize binary packager, using ASCII fallback", e);
            binaryPackager = asciiPackager; // Fallback to ASCII
        }
    }

    /**
     * Convert JSON string to ISO8583 message
     */
    public ISOMsg jsonToISO8583FromJson(String jsonPayload) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(jsonPayload);

        // Determine packager type from payload or use default
        String packagerType = getPackagerType(jsonNode);
        GenericPackager packager = getPackager(packagerType);

        logger.debug("Converting JSON to ISO8583 using {} packager", packagerType);

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        // Extract operation and set MTI
        String operation = jsonNode.get("operation").asText();
        String mti = getMTIForOperation(operation);
        isoMsg.setMTI(mti);

        // Map JSON fields to ISO8583 fields
        mapJsonToISO8583Fields(isoMsg, jsonNode);

        logger.debug("ISO8583 message created with MTI: {} using {} packager", mti, packagerType);
        return isoMsg;
    }

    /**
     * Convert TransactionRequest to ISO8583 message (backward compatibility)
     */
    public ISOMsg jsonToISO8583(TransactionRequest request) throws ISOException {
        return jsonToISO8583(request, defaultPackagerType);
    }

    /**
     * Convert TransactionRequest to ISO8583 message with specific packager type
     */
    public ISOMsg jsonToISO8583(TransactionRequest request, String packagerType) throws ISOException {
        logger.debug("Converting TransactionRequest to ISO8583 using {} packager", packagerType);

        GenericPackager packager = getPackager(packagerType);

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        // Set message type based on operation
        String mti = getMTIForOperation(request.getOperation());
        isoMsg.setMTI(mti);

        // Map JSON fields to ISO8583 fields
        mapJsonFieldsToISO(isoMsg, request);

        logger.debug("ISO8583 message created with MTI: {} using {} packager", mti, packagerType);
        return isoMsg;
    }

    /**
     * Convert ISO8583 response message to JSON
     */
    public Map<String, Object> iso8583ToJson(ISOMsg isoMsg) throws ISOException {
        logger.debug("Converting ISO8583 to JSON, MTI: {}", isoMsg.getMTI());

        Map<String, Object> jsonResponse = new HashMap<>();

        // Add basic message information
        jsonResponse.put("mti", isoMsg.getMTI());
        jsonResponse.put("responseCode", isoMsg.getString(39));
        jsonResponse.put("responseDescription", getResponseDescription(isoMsg.getString(39)));
        jsonResponse.put("packagerType", determinePackagerType(isoMsg));

        // Map ISO8583 fields to JSON
        mapISOFieldsToJson(isoMsg, jsonResponse);

        return jsonResponse;
    }

    /**
     * Pack ISO8583 message to byte array for transmission
     */
    public byte[] packMessage(ISOMsg isoMsg) throws ISOException {
        return isoMsg.pack();
    }

    /**
     * Unpack byte array to ISO8583 message using specified packager type
     */
    public ISOMsg unpackMessage(byte[] messageBytes, String packagerType) throws ISOException {
        GenericPackager packager = getPackager(packagerType);
        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);
        isoMsg.unpack(messageBytes);
        return isoMsg;
    }

    /**
     * Unpack byte array to ISO8583 message using default packager
     */
    public ISOMsg unpackMessage(byte[] messageBytes) throws ISOException {
        return unpackMessage(messageBytes, defaultPackagerType);
    }

    /**
     * Auto-detect packager type from message bytes
     */
    public ISOMsg unpackMessageAutoDetect(byte[] messageBytes) throws ISOException {
        // Try ASCII first
        try {
            ISOMsg isoMsg = new ISOMsg();
            isoMsg.setPackager(asciiPackager);
            isoMsg.unpack(messageBytes);
            logger.debug("Successfully unpacked using ASCII packager");
            return isoMsg;
        } catch (Exception e) {
            logger.debug("ASCII unpacking failed, trying binary packager");
        }

        // Try Binary
        try {
            ISOMsg isoMsg = new ISOMsg();
            isoMsg.setPackager(binaryPackager);
            isoMsg.unpack(messageBytes);
            logger.debug("Successfully unpacked using binary packager");
            return isoMsg;
        } catch (Exception e) {
            logger.error("Failed to unpack message with both ASCII and binary packagers");
            throw new ISOException("Unable to unpack message with available packagers");
        }
    }

    // Private helper methods
    private GenericPackager getPackager(String packagerType) {
        switch (packagerType.toLowerCase()) {
            case "binary":
            case "iso87b":
                return binaryPackager != null ? binaryPackager : asciiPackager;
            case "ascii":
            case "iso87a":
            default:
                return asciiPackager;
        }
    }

    private String getPackagerType(JsonNode jsonNode) {
        // Check if packager type is specified in the JSON
        if (jsonNode.has("packagerType")) {
            return jsonNode.get("packagerType").asText();
        }

        // Check metadata for packager hints
        if (jsonNode.has("metadata")) {
            JsonNode metadata = jsonNode.get("metadata");
            if (metadata.has("packagerType")) {
                return metadata.get("packagerType").asText();
            }
        }

        // Use default
        return defaultPackagerType;
    }

    private String determinePackagerType(ISOMsg isoMsg) {
        // Simple logic to determine packager type based on packager instance
        if (isoMsg.getPackager() == binaryPackager) {
            return "binary";
        } else {
            return "ascii";
        }
    }

    private void mapJsonToISO8583Fields(ISOMsg isoMsg, JsonNode jsonNode) throws Exception {
        JsonNode payload = jsonNode.get("payload");
        if (payload == null) {
            throw new IllegalArgumentException("Payload is required in JSON");
        }

        // Field 2: Primary Account Number (PAN)
        if (payload.has("cardNumber")) {
            isoMsg.set(2, payload.get("cardNumber").asText());
        }

        // Field 3: Processing Code
        String operation = jsonNode.get("operation").asText();
        isoMsg.set(3, getProcessingCode(operation));

        // Field 4: Amount, Transaction
        if (payload.has("amount")) {
            BigDecimal amount = new BigDecimal(payload.get("amount").asText());
            isoMsg.set(4, formatAmount(amount));
        }

        // Field 7: Transmission Date and Time
        isoMsg.set(7, getCurrentTimestamp());

        // Field 11: System Trace Audit Number (STAN)
        isoMsg.set(11, generateSTAN());

        // Field 12: Time, Local Transaction
        isoMsg.set(12, getCurrentTime());

        // Field 13: Date, Local Transaction
        isoMsg.set(13, getCurrentDate());

        // Field 15: Date, Settlement
        isoMsg.set(15, getCurrentDate());

        // Field 18: Merchant Category Code
        if (payload.has("merchantCategoryCode")) {
            isoMsg.set(18, payload.get("merchantCategoryCode").asText());
        } else {
            isoMsg.set(18, "5999"); // Default MCC
        }

        // Field 22: Point of Service Entry Mode
        String channel = jsonNode.get("channel").asText();
        isoMsg.set(22, getPOSEntryMode(channel));

        // Field 25: Point of Service Condition Code
        isoMsg.set(25, "00");

        // Field 32: Acquiring Institution ID Code
        if (payload.has("merchantId")) {
            String merchantId = payload.get("merchantId").asText();
            isoMsg.set(32, merchantId.substring(0, Math.min(11, merchantId.length())));
        }

        // Field 37: Retrieval Reference Number
        isoMsg.set(37, generateRRN());

        // Field 41: Card Acceptor Terminal ID
        if (payload.has("terminalId")) {
            isoMsg.set(41, payload.get("terminalId").asText());
        }

        // Field 42: Card Acceptor ID Code
        if (payload.has("merchantId")) {
            isoMsg.set(42, payload.get("merchantId").asText());
        }

        // Field 49: Currency Code, Transaction
        if (payload.has("currency")) {
            isoMsg.set(49, getCurrencyCode(payload.get("currency").asText()));
        }

        // Handle additional fields from payload
        if (payload.has("additionalFields")) {
            mapAdditionalFieldsFromJson(isoMsg, payload.get("additionalFields"));
        }
    }

    private void mapAdditionalFieldsFromJson(ISOMsg isoMsg, JsonNode additionalFields) throws Exception {
        additionalFields.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = entry.getValue().asText();

            try {
                switch (key.toLowerCase()) {
                    case "track2":
                        isoMsg.set(35, value);
                        break;
                    case "track1":
                        isoMsg.set(45, value);
                        break;
                    case "acquiringinstitutioncode":
                        isoMsg.set(32, value);
                        break;
                    case "merchantcategorycode":
                        isoMsg.set(18, value);
                        break;
                    // Add more field mappings as needed
                }
            } catch (Exception e) {
                logger.warn("Failed to set ISO field for key: {}", key, e);
            }
        });
    }

    // ... (Keep all the existing helper methods from previous implementation)
    private String getMTIForOperation(String operation) {
        switch (operation.toLowerCase()) {
            case "purchase":
            case "sale":
                return "0200"; // Authorization Request
            case "refund":
                return "0220"; // Advice Request
            case "balance":
            case "balance_inquiry":
                return "0100"; // Authorization Request (Balance Inquiry)
            case "withdrawal":
                return "0200"; // Authorization Request (Cash Withdrawal)
            case "reversal":
                return "0400"; // Reversal Request
            default:
                return "0200"; // Default to Authorization Request
        }
    }

    private void mapJsonFieldsToISO(ISOMsg isoMsg, TransactionRequest request) throws ISOException {
        // Field 2: Primary Account Number (PAN)
        if (request.getPayload().getCardNumber() != null) {
            isoMsg.set(2, request.getPayload().getCardNumber());
        }

        // Field 3: Processing Code
        isoMsg.set(3, getProcessingCode(request.getOperation()));

        // Field 4: Amount, Transaction
        if (request.getPayload().getAmount() != null) {
            String amount = formatAmount(request.getPayload().getAmount());
            isoMsg.set(4, amount);
        }

        // Field 7: Transmission Date and Time
        isoMsg.set(7, getCurrentTimestamp());

        // Field 11: System Trace Audit Number (STAN)
        isoMsg.set(11, generateSTAN());

        // Field 12: Time, Local Transaction
        isoMsg.set(12, getCurrentTime());

        // Field 13: Date, Local Transaction
        isoMsg.set(13, getCurrentDate());

        // Field 15: Date, Settlement
        isoMsg.set(15, getCurrentDate());

        // Field 18: Merchant Category Code
        isoMsg.set(18, "5999"); // Default MCC

        // Field 22: Point of Service Entry Mode
        isoMsg.set(22, getPOSEntryMode(request.getChannel()));

        // Field 25: Point of Service Condition Code
        isoMsg.set(25, "00");

        // Field 32: Acquiring Institution ID Code
        if (request.getPayload().getMerchantId() != null) {
            isoMsg.set(32, request.getPayload().getMerchantId().substring(0, Math.min(11, request.getPayload().getMerchantId().length())));
        }

        // Field 37: Retrieval Reference Number
        isoMsg.set(37, generateRRN());

        // Field 41: Card Acceptor Terminal ID
        if (request.getPayload().getTerminalId() != null) {
            isoMsg.set(41, request.getPayload().getTerminalId());
        }

        // Field 42: Card Acceptor ID Code
        if (request.getPayload().getMerchantId() != null) {
            isoMsg.set(42, request.getPayload().getMerchantId());
        }

        // Field 49: Currency Code, Transaction
        if (request.getPayload().getCurrency() != null) {
            isoMsg.set(49, getCurrencyCode(request.getPayload().getCurrency()));
        }

        // Add additional fields from metadata
        mapAdditionalFields(isoMsg, request);
    }

    private void mapISOFieldsToJson(ISOMsg isoMsg, Map<String, Object> jsonResponse) throws ISOException {
        // Map important response fields
        if (isoMsg.hasField(2)) {
            jsonResponse.put("cardNumber", maskCardNumber(isoMsg.getString(2)));
        }

        if (isoMsg.hasField(4)) {
            jsonResponse.put("amount", parseAmount(isoMsg.getString(4)));
        }

        if (isoMsg.hasField(6)) {
            jsonResponse.put("cardholderBillingAmount", parseAmount(isoMsg.getString(6)));
        }

        if (isoMsg.hasField(11)) {
            jsonResponse.put("stan", isoMsg.getString(11));
        }

        if (isoMsg.hasField(37)) {
            jsonResponse.put("rrn", isoMsg.getString(37));
        }

        if (isoMsg.hasField(38)) {
            jsonResponse.put("authorizationCode", isoMsg.getString(38));
        }

        if (isoMsg.hasField(54)) {
            jsonResponse.put("additionalAmounts", isoMsg.getString(54));
        }

        // Add all present fields for debugging (optional)
        Map<String, String> allFields = new HashMap<>();
        for (int i = 1; i <= 128; i++) {
            if (isoMsg.hasField(i)) {
                allFields.put("field_" + i, isoMsg.getString(i));
            }
        }
        jsonResponse.put("isoFields", allFields);
    }

    private String getProcessingCode(String operation) {
        switch (operation.toLowerCase()) {
            case "purchase":
            case "sale":
                return "000000"; // Purchase
            case "withdrawal":
                return "010000"; // Cash Withdrawal
            case "balance":
            case "balance_inquiry":
                return "310000"; // Balance Inquiry
            case "refund":
                return "200000"; // Refund
            default:
                return "000000";
        }
    }

    private String formatAmount(BigDecimal amount) {
        // Convert to cents/paise and pad to 12 digits
        long amountInCents = amount.multiply(new BigDecimal("100")).longValue();
        return String.format("%012d", amountInCents);
    }

    private BigDecimal parseAmount(String isoAmount) {
        if (isoAmount == null || isoAmount.isEmpty()) return BigDecimal.ZERO;
        long amountInCents = Long.parseLong(isoAmount);
        return new BigDecimal(amountInCents).divide(new BigDecimal("100"));
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("MMddHHmmss").format(new Date());
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HHmmss").format(new Date());
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("MMdd").format(new Date());
    }

    public String generateSTAN() {
        // Generate 6-digit STAN (System Trace Audit Number)
        return String.format("%06d", (int) (Math.random() * 999999));
    }

    private String generateRRN() {
        // Generate 12-digit RRN (Retrieval Reference Number)
        return String.format("%012d", System.currentTimeMillis() % 1000000000000L);
    }

    private String getPOSEntryMode(String channel) {
        switch (channel.toUpperCase()) {
            case "POS":
                return "051"; // Chip Card
            case "ATM":
                return "021"; // Magnetic Stripe
            case "UPI":
                return "071"; // Contactless
            default:
                return "012"; // Track 2 data
        }
    }

    private String getCurrencyCode(String currency) {
        // ISO 4217 numeric currency codes
        switch (currency.toUpperCase()) {
            case "USD":
                return "840";
            case "EUR":
                return "978";
            case "INR":
                return "356";
            case "GBP":
                return "826";
            default:
                return "356"; // Default to INR
        }
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) return cardNumber;
        return cardNumber.substring(0, 6) + "******" + cardNumber.substring(cardNumber.length() - 4);
    }

    private void mapAdditionalFields(ISOMsg isoMsg, TransactionRequest request) throws ISOException {
        // Map any additional fields from the request metadata
        if (request.getMetadata() != null) {
            for (Map.Entry<String, Object> entry : request.getMetadata().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Map specific metadata fields to ISO fields
                switch (key.toLowerCase()) {
                    case "cvv":
                    case "cvv2":
                        // Don't include CVV in ISO message for security
                        break;
                    case "track2":
                        isoMsg.set(35, value.toString());
                        break;
                    case "pin":
                        // Handle PIN block (field 52)
                        // In production, this should be properly encrypted
                        break;
                    default:
                        // Additional processing for other fields
                        break;
                }
            }
        }
    }

    private String getResponseDescription(String responseCode) {
        if (responseCode == null) return "Unknown";

        switch (responseCode) {
            case "00":
                return "Approved";
            case "01":
                return "Refer to card issuer";
            case "02":
                return "Refer to card issuer, special condition";
            case "03":
                return "Invalid merchant";
            case "04":
                return "Pick up card";
            case "05":
                return "Do not honor";
            case "06":
                return "Error";
            case "07":
                return "Pick up card, special condition";
            case "08":
                return "Honor with identification";
            case "09":
                return "Request in progress";
            case "10":
                return "Approved for partial amount";
            case "11":
                return "Approved (VIP)";
            case "12":
                return "Invalid transaction";
            case "13":
                return "Invalid amount";
            case "14":
                return "Invalid card number";
            case "15":
                return "No such issuer";
            case "51":
                return "Insufficient funds";
            case "54":
                return "Expired card";
            case "55":
                return "Incorrect PIN";
            case "57":
                return "Transaction not permitted to cardholder";
            case "58":
                return "Transaction not permitted to terminal";
            case "61":
                return "Exceeds withdrawal amount limit";
            case "62":
                return "Restricted card";
            case "63":
                return "Security violation";
            case "65":
                return "Exceeds withdrawal frequency limit";
            case "68":
                return "Response received too late";
            case "75":
                return "Allowable number of PIN tries exceeded";
            case "91":
                return "Issuer or switch is inoperative";
            case "92":
                return "Financial institution or intermediate network facility cannot be found";
            case "94":
                return "Duplicate transmission";
            case "96":
                return "System malfunction";
            default:
                return "Unknown response code: " + responseCode;
        }
    }

    private GenericPackager createDefaultAsciiPackager() {
        try {
            return new GenericPackager("org/jpos/iso/packager/iso87ascii.xml");
        } catch (Exception e) {
            logger.error("Failed to create default ASCII packager", e);
            throw new RuntimeException("Failed to create ASCII ISO8583 packager", e);
        }
    }

    /*private GenericPackager createDefaultBinaryPackager() {
        try {
            return new GenericPackager("org/jpos/iso/packager/iso87binary.xml");
        } catch (Exception e) {
            logger.error("Failed to create default binary packager", e);
            throw new RuntimeException("Failed to create binary ISO8583 packager", e);
        }
    }*/

    private GenericPackager createDefaultBinaryPackager() {
        try {
            // First try the built-in binary packager
            return new GenericPackager("org/jpos/iso/packager/iso93binary.xml");
        } catch (Exception e1) {
            try {
                // If that fails, try another common binary packager
                return new GenericPackager("org/jpos/iso/packager/genericpackager.xml");
            } catch (Exception e2) {
                logger.warn("No binary packager found, using ASCII packager as fallback");
                // Last resort - use ASCII packager
                return createDefaultAsciiPackager();
            }
        }
    }
}