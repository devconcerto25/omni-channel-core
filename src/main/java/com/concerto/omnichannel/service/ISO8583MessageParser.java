// Enhanced ISO8583 Message Parser with ASCII/Binary support
package com.concerto.omnichannel.service;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.packager.ISO87BPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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

    //@Value("${iso8583.packager.type:ascii}")
    @Value("${iso8583.packager.type:iso87b}")
    private String defaultPackagerType;


    @Value("${iso8583.tpdu.enabled:true}")
    private boolean tpduEnabled;

    @Value("${iso8583.tpdu.identifier:0x60}")
    private String tpduIdentifier;

    @Value("${iso8583.tpdu.source:0x0526}")
    private String tpduSource;

    @Value("${iso8583.tpdu.destination:0x00F1}")
    private String tpduDestination;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private STANGenerationService stanGenerationService;

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
        //isoMsg.set(62, "1234");
        logger.debug("ISO8583 message created with MTI: {} using {} correlationId", mti, MDC.get("correlationId"));
        return isoMsg;
    }

    /**
     * Convert TransactionRequest to ISO8583 message (backward compatibility)
     */
    /*public ISOMsg jsonToISO8583(TransactionRequest request) throws ISOException {
        return jsonToISO8583(request, defaultPackagerType);
    }*/

    /**
     * Convert TransactionRequest to ISO8583 message with specific packager type
     */
/*
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
*/

    /**
     * Convert ISO8583 response message to JSON
     */
    public Map<String, Object> iso8583ToJson(ISOMsg isoMsg) throws ISOException {
        logger.debug("Converting ISO8583 to JSON, MTI: {}", isoMsg.getMTI());
        logger.info("Converting ISO8583 to JSON MTI: {}", isoMsg.getMTI());
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
        ISOPackager binaryPackager = new ISO87BPackager();
        isoMsg.setPackager(binaryPackager);
        printISOMessage(isoMsg);
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
        printISOMessage(isoMsg);
        return isoMsg;
    }

    public ISOMsg unpackMessageWithBinaryPackager(byte[] messageBytes) throws Exception {
        ISOPackager binaryPackager = new ISO87BPackager();

        ISOMsg msg = new ISOMsg();
        msg.setPackager(binaryPackager);
        msg.unpack(messageBytes);

        return msg;
    }

    private void printISOMessage(ISOMsg isoMsg) {
        try {
            System.out.println("MTI = " + isoMsg.getMTI());
            for (int i = 1; i <= isoMsg.getMaxField(); i++) {
                if (isoMsg.hasField(i)) {
                    System.out.println("Field (" + i + ") = " + isoMsg.getString(i));
                }
            }
            System.out.println("Message: " + isoMsg);
        } catch (ISOException e) {
            e.printStackTrace();
        }
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
        return switch (packagerType.toLowerCase()) {
            case "binary", "iso87b" -> binaryPackager != null ? binaryPackager : asciiPackager;
            default -> asciiPackager;
        };
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
        //isoMsg.set(7, getCurrentTimestamp());

        // Field 11: System Trace Audit Number (STAN)
        //isoMsg.set(11, generateSTAN());
        //Generate Terminal specific STAN and store it in Redis
        if (payload.has("merchantId") && payload.has("terminalId")) {
            String merchantId = payload.get("merchantId").asText();
            String terminalId = payload.get("terminalId").asText();
            String stan = stanGenerationService.generateSTAN(merchantId, terminalId);
            logger.info("STAN for terminal {} is {}", terminalId, stan);
            isoMsg.set(11, stan);
        } else {
            //Random STAN in case of MerchantId and TerminalId is not present
            String stan = generateSTAN();
            isoMsg.set(11, stan);
            logger.info("STAN without terminal is {}", stan);
        }

        // Field 12: Time, Local Transaction
        isoMsg.set(12, getCurrentTime());

        // Field 13: Date, Local Transaction
        isoMsg.set(13, getCurrentDate());

        // Field 15: Date, Settlement
        //isoMsg.set(15, getCurrentDate());

        // Field 18: Merchant Category Code
        /*if (payload.has("merchantCategoryCode")) {
            isoMsg.set(18, payload.get("merchantCategoryCode").asText());
        } else {
            isoMsg.set(18, "5999"); // Default MCC
        }*/

        // Field 22: Point of Service Entry Mode
        String channel = jsonNode.get("channel").asText();
        isoMsg.set(22, getPOSEntryMode(channel, jsonNode));

        // Field 25: Point of Service Condition Code
        isoMsg.set(25, "00");

        // Field 32: Acquiring Institution ID Code
        /*if (payload.has("merchantId")) {
            String merchantId = payload.get("merchantId").asText();
            isoMsg.set(32, merchantId.substring(0, Math.min(11, merchantId.length())));
        }*/

        // Field 37: Retrieval Reference Number
        //isoMsg.set(37, generateRRN());

        // Field 41: Card Acceptor Terminal ID
        if (payload.has("terminalId")) {
            isoMsg.set(41, payload.get("terminalId").asText());
        }

        // Field 42: Card Acceptor ID Code
        if (payload.has("merchantId")) {
            isoMsg.set(42, payload.get("merchantId").asText());
        }

        // Field 49: Currency Code, Transaction
        /*if (payload.has("currency")) {
            isoMsg.set(49, getCurrencyCode(payload.get("currency").asText()));
        }*/


        // Handle additional fields from payload
        if (payload.has("additionalFields")) {
            mapAdditionalFieldsFromJson(isoMsg, payload);
        }

    }

    private void mapAdditionalFieldsFromJson(ISOMsg isoMsg, JsonNode payload) throws Exception {
        payload.get("additionalFields").fields().forEachRemaining(entry -> {
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
                    /*case "acquiringinstitutioncode":
                        isoMsg.set(32, value);
                        break;
                    case "merchantcategorycode":
                        isoMsg.set(18, value);
                        break;*/
                    /*case "emvdata":
                        isoMsg.set(55, value);*/
                        // Add more field mappings as needed
                }
            } catch (Exception e) {
                logger.warn("Failed to set ISO field for key: {}", key, e);
            }
        });
    }

    // ... (Keep all the existing helper methods from previous implementation)
    private String getMTIForOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "purchase", "sale" -> "0200"; // Authorization Request
            case "refund" -> "0200"; // Advice Request
            case "balance", "balance_inquiry" -> "0100"; // Authorization Request (Balance Inquiry)
            case "withdrawal" -> "0200"; // Authorization Request (Cash Withdrawal)
            case "reversal" -> "0400"; // Reversal Request
            case "logon" -> "0800"; // Network Request handshake etc
            default -> "0200"; // Default to Authorization Request
        };
    }

/*
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
        String channel = request.getChannel();
        String enrtyMode = request.getPayload()
        isoMsg.set(22, getPOSEntryMode(channel, ));

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
*/

    private void mapISOFieldsToJson(ISOMsg isoMsg, Map<String, Object> jsonResponse) throws ISOException {
        // Map important response fields
        if (isoMsg.hasField(2)) {
            jsonResponse.put("cardNumber", maskCardNumber(isoMsg.getString(2)));
        }

        if (isoMsg.hasField(3)){
            jsonResponse.put("processingCode", isoMsg.getString(3));
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
        return switch (operation.toLowerCase()) {
            case "purchase", "sale" -> "001000"; // Purchase
            case "withdrawal" -> "010000"; // Cash Withdrawal
            case "balance", "balance_inquiry" -> "310000"; // Balance Inquiry
            case "refund" -> "200000"; // Refund
            case "logon" -> "001000";
            default -> "000000";
        };
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

    /*private String getPOSEntryMode(String channel) {
        return switch (channel.toUpperCase()) {
            case "POS" -> "051"; // Chip Card
            case "ATM" -> "021"; // Magnetic Stripe
            case "UPI" -> "071"; // Contactless
            default -> "012"; // Track 2 data
        };
    }*/

    private String getPOSEntryMode(String channel, JsonNode payload) {
        return switch (channel.toUpperCase()) {
            case "ATM" -> "051"; // ATM always uses chip card entry

            case "POS" -> determinePOSEntryMode(payload);
            case "UPI" -> "071"; // Contactless for UPI

            default -> "012"; // Track 2 data fallback
        };
    }

    private String determinePOSEntryMode(JsonNode payload) {
        // Check if entry mode is explicitly specified in additional fields
        if (payload.has("additionalFields")) {
            JsonNode additionalFields = payload.get("additionalFields");

            if (additionalFields.has("entryMode")) {
                String entryMode = additionalFields.get("entryMode").asText().toUpperCase();
                return mapEntryModeToCode(entryMode);
            }

            if (additionalFields.has("posEntryMode")) {
                return additionalFields.get("posEntryMode").asText();
            }
        }

        // Determine based on available card data
        boolean hasTrack2 = payload.has("additionalFields") &&
                payload.get("additionalFields").has("track2");
        boolean hasChipData = payload.has("additionalFields") &&
                payload.get("additionalFields").has("chipData");
        boolean hasContactless = payload.has("additionalFields") &&
                payload.get("additionalFields").has("contactless");

        if (hasContactless) {
            // Check if PIN was entered for contactless
            boolean pinEntered = payload.has("additionalFields") &&
                    payload.get("additionalFields").has("pinEntered") &&
                    payload.get("additionalFields").get("pinEntered").asBoolean();

            return pinEntered ? "072" : "071"; // Contactless with/without PIN
        } else if (hasChipData) {
            return "051"; // Chip card
        } else if (hasTrack2) {
            return "021"; // Magnetic stripe
        }

        // Default to chip card for POS
        return "051";
    }

    private String mapEntryModeToCode(String entryMode) {
        return switch (entryMode) {
            case "CHIP", "EMV", "ICC" -> "051";
            case "MAGNETIC_STRIPE", "MAG_STRIPE", "SWIPE" -> "021";
            case "CONTACTLESS", "NFC", "TAP" -> "071";
            case "CONTACTLESS_PIN", "NFC_PIN", "TAP_PIN" -> "072";
            case "MANUAL", "KEYED" -> "012";
            case "HYBRID" -> "052"; // Chip with magnetic stripe fallback
            default -> "051"; // Default to chip
        };
    }

    private String getCurrencyCode(String currency) {
        // ISO 4217 numeric currency codes
        return switch (currency.toUpperCase()) {
            case "USD" -> "840";
            case "EUR" -> "978";
            case "INR" -> "356";
            case "GBP" -> "826";
            default -> "356"; // Default to INR
        };
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

        return switch (responseCode) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "02" -> "Refer to card issuer, special condition";
            case "03" -> "Invalid merchant";
            case "04" -> "Pick up card";
            case "05" -> "Do not honor";
            case "06" -> "Error";
            case "07" -> "Pick up card, special condition";
            case "08" -> "Honor with identification";
            case "09" -> "Request in progress";
            case "10" -> "Approved for partial amount";
            case "11" -> "Approved (VIP)";
            case "12" -> "Invalid transaction";
            case "13" -> "Invalid amount";
            case "14" -> "Invalid card number";
            case "15" -> "No such issuer";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
            case "57" -> "Transaction not permitted to cardholder";
            case "58" -> "Transaction not permitted to terminal";
            case "61" -> "Exceeds withdrawal amount limit";
            case "62" -> "Restricted card";
            case "63" -> "Security violation";
            case "65" -> "Exceeds withdrawal frequency limit";
            case "68" -> "Response received too late";
            case "75" -> "Allowable number of PIN tries exceeded";
            case "91" -> "Issuer or switch is inoperative";
            case "92" -> "Financial institution or intermediate network facility cannot be found";
            case "94" -> "Duplicate transmission";
            case "96" -> "System malfunction";
            default -> "Unknown response code: " + responseCode;
        };
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

    /* Get TPDU header bytes*/
    private byte[] getTpduHeader() {
        return new byte[]{
                (byte) 0x60, // Identifier
                (byte) 0x05, // Source (first byte)
                (byte) 0x26, // Source (second byte)
                (byte) 0x00, // Destination (first byte)
                (byte) 0xF1  // Destination (second byte)
        };
    }

    /**
     * Get configurable TPDU header from configuration
     */
    private byte[] getConfigurableTpduHeader(String channelId) {
        try {
            // Get channel-specific TPDU configuration
            String identifier = configurationService.getConfigValue(channelId, "tpduIdentifier", tpduIdentifier);
            String source = configurationService.getConfigValue(channelId, "tpduSource", tpduSource);
            String destination = configurationService.getConfigValue(channelId, "tpduDestination", tpduDestination);

            return new byte[]{
                    (byte) Integer.parseInt(identifier.replace("0x", ""), 16),
                    (byte) ((Integer.parseInt(source.replace("0x", ""), 16) >> 8) & 0xFF),
                    (byte) (Integer.parseInt(source.replace("0x", ""), 16) & 0xFF),
                    (byte) ((Integer.parseInt(destination.replace("0x", ""), 16) >> 8) & 0xFF),
                    (byte) (Integer.parseInt(destination.replace("0x", ""), 16) & 0xFF)
            };
        } catch (Exception e) {
            logger.warn("Failed to parse TPDU configuration, using defaults", e);
            return getTpduHeader();
        }
    }

    public byte[] packMessageWithTpdu(ISOMsg isoMsg, String channelId) throws ISOException {
        byte[] isoBytes = isoMsg.pack();

        if (!tpduEnabled) {
            return isoBytes;
        }

        byte[] tpduHeader = getConfigurableTpduHeader(channelId);
        byte[] messageWithTpdu = new byte[tpduHeader.length + isoBytes.length];

        // Copy TPDU header
        System.arraycopy(tpduHeader, 0, messageWithTpdu, 0, tpduHeader.length);

        // Copy ISO message
        System.arraycopy(isoBytes, 0, messageWithTpdu, tpduHeader.length, isoBytes.length);

        logger.debug("ISO8583 message packed with TPDU header, total length: {} bytes", messageWithTpdu.length);
        return messageWithTpdu;
    }

    /**
     * Unpack ISO8583 message by removing TPDU header
     */
    public ISOMsg unpackMessageWithTpdu(byte[] messageBytes, String packagerType, String channelId) throws ISOException {
        if (!tpduEnabled || messageBytes.length < 5) {
            return unpackMessage(messageBytes, packagerType);
        }

        // Skip TPDU header (first 5 bytes)
        byte[] isoBytes = new byte[messageBytes.length - 5];
        System.arraycopy(messageBytes, 5, isoBytes, 0, isoBytes.length);

        logger.debug("Removed TPDU header, ISO message length: {} bytes", isoBytes.length);
        return unpackMessage(isoBytes, packagerType);
    }

    /**
     * Extract TPDU header information for logging/debugging
     */
    public TpduInfo extractTpduInfo(byte[] messageBytes) {
        if (messageBytes.length < 5) {
            return null;
        }

        return new TpduInfo(
                messageBytes[0] & 0xFF,  // Identifier
                ((messageBytes[1] & 0xFF) << 8) | (messageBytes[2] & 0xFF), // Source
                ((messageBytes[3] & 0xFF) << 8) | (messageBytes[4] & 0xFF)  // Destination
        );
    }


    public static class TpduInfo {
        private final int identifier;
        private final int source;
        private final int destination;

        public TpduInfo(int identifier, int source, int destination) {
            this.identifier = identifier;
            this.source = source;
            this.destination = destination;
        }

        public int getIdentifier() {
            return identifier;
        }

        public int getSource() {
            return source;
        }

        public int getDestination() {
            return destination;
        }

        @Override
        public String toString() {
            return String.format("TPDU[ID:0x%02X, SRC:0x%04X, DST:0x%04X]", identifier, source, destination);
        }
    }
}

