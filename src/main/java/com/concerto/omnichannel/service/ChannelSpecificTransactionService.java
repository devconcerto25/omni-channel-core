package com.concerto.omnichannel.service;


import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.entity.POSTransactionDetails;
import com.concerto.omnichannel.entity.UPITransactionDetails;
import com.concerto.omnichannel.repository.POSTransactionRepository;
import com.concerto.omnichannel.repository.UPITransactionRepository;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ChannelSpecificTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelSpecificTransactionService.class);

    @Autowired
    private POSTransactionRepository posTransactionRepository;

    @Autowired
    private UPITransactionRepository upiTransactionRepository;

    // Add other channel repositories...

    /**
     * Save channel-specific transaction details based on channel type
     */
    public void saveChannelSpecificDetails(TransactionHeader header, TransactionRequest request, TransactionResponse response) {
        String channel = header.getChannel().toUpperCase();

        try {
            switch (channel) {
                case "POS":
                    savePOSTransactionDetails(header, request, response);
                    break;
                case "UPI":
                    saveUPITransactionDetails(header, request, response);
                    break;
                case "ATM":
                    // saveATMTransactionDetails(header, request, response);
                    break;
                case "BBPS":
                    // saveBBPSTransactionDetails(header, request, response);
                    break;
                case "PG":
                    // savePGTransactionDetails(header, request, response);
                    break;
                default:
                    logger.warn("No specific handler for channel: {}", channel);
            }
        } catch (Exception e) {
            logger.error("Failed to save channel-specific details for channel: {}", channel, e);
            throw e;
        }
    }

    private void savePOSTransactionDetails(TransactionHeader header, TransactionRequest request, TransactionResponse response) {
        POSTransactionDetails posDetails = new POSTransactionDetails();
        posDetails.setTransactionHeader(header);

        // Extract POS-specific data from request payload
        if (request.getPayload() != null) {
            Map<String, Object> additionalFields = request.getPayload().getAdditionalFields();

            posDetails.setMerchantId(request.getPayload().getMerchantId());
            posDetails.setTerminalId(request.getPayload().getTerminalId());

            // Mask card number for security
            String cardNumber = request.getPayload().getCardNumber();
            if (cardNumber != null && cardNumber.length() >= 10) {
                String masked = cardNumber.substring(0, 6) + "****" + cardNumber.substring(cardNumber.length() - 4);
                posDetails.setCardNumberMasked(masked);
            }

            if (additionalFields != null) {
                posDetails.setPosEntryMode((String) additionalFields.get("posEntryMode"));
                posDetails.setEmvData((String) additionalFields.get("emvData"));
                posDetails.setTrackDataEncrypted((String) additionalFields.get("trackData"));
                posDetails.setPinVerified((Boolean) additionalFields.get("pinVerified"));
            }
        }

        // Extract POS-specific data from response
        if (response != null && response.getAdditionalData() != null) {
            posDetails.setRrn((String) response.getAdditionalData().get("rrn"));
            posDetails.setAuthorizationCode((String) response.getAdditionalData().get("authorizationCode"));
            posDetails.setResponseCode((String) response.getAdditionalData().get("responseCode"));
            posDetails.setStan((String) response.getAdditionalData().get("stan"));
            posDetails.setMti((String) response.getAdditionalData().get("mti"));
        }

        posTransactionRepository.save(posDetails);
        logger.debug("Saved POS transaction details for correlation ID: {}", header.getCorrelationId());
    }

    /*private void saveUPITransactionDetails(TransactionHeader header, TransactionRequest request, TransactionResponse response) {
        UPITransactionDetails upiDetails = new UPITransactionDetails();
        upiDetails.setTransactionHeader(header);

        // Extract UPI-specific data from request
        if (request.getPayload() != null) {
            upiDetails.setPayerVPA(request.getPayload().getPayerVPA());
            upiDetails.setPayeeVPA(request.getPayload().getPayeeVPA());
            upiDetails.setPaymentMode(request.getPayload().getPaymentMode());
            upiDetails.setNote(request.getPayload().getNote());
            upiDetails.setMerchantTransactionId(request.getPayload().getMerchantTransactionId());
            upiDetails.setDeviceFingerprint(request.getPayload().getDeviceFingerprint());
        }

        // Extract UPI-specific data from response
        if (response != null && response.getAdditionalData() != null) {
            upiDetails.setUpiTransactionId((String) response.getAdditionalData().get("upiTransactionId"));
            upiDetails.setCustomerReference((String) response.getAdditionalData().get("customerReference"));
            upiDetails.setUpiRequestId((String) response.getAdditionalData().get("upiRequestId"));
        }

        upiTransactionRepository.save(upiDetails);
        logger.debug("Saved UPI transaction details for correlation ID: {}", header.getCorrelationId());
    }*/

    private void saveUPITransactionDetails(TransactionHeader header, TransactionRequest request, TransactionResponse response) {
        UPITransactionDetails upiDetails = new UPITransactionDetails();
        upiDetails.setTransactionHeader(header);

        // Extract UPI-specific data from request payload using additionalFields
        if (request.getPayload() != null) {
            Map<String, Object> additionalFields = request.getPayload().getAdditionalFields();

            if (additionalFields != null) {
                // UPI VPA details
                upiDetails.setPayerVPA((String) additionalFields.get("payerVPA"));
                upiDetails.setPayeeVPA((String) additionalFields.get("payeeVPA"));

                // Account and IFSC details
                upiDetails.setPayerAccountNumber((String) additionalFields.get("payerAccountNumber"));
                upiDetails.setPayeeAccountNumber((String) additionalFields.get("payeeAccountNumber"));
                upiDetails.setPayerIFSC((String) additionalFields.get("payerIFSC"));
                upiDetails.setPayeeIFSC((String) additionalFields.get("payeeIFSC"));

                // UPI specific fields
                upiDetails.setPaymentMode((String) additionalFields.get("paymentMode"));
                upiDetails.setNote((String) additionalFields.get("note"));
                upiDetails.setMerchantTransactionId((String) additionalFields.get("merchantTransactionId"));
                upiDetails.setDeviceFingerprint((String) additionalFields.get("deviceFingerprint"));
                upiDetails.setAppName((String) additionalFields.get("appName"));

                // Handle potential null values safely
                Object expiryTime = additionalFields.get("expiryTime");
                if (expiryTime != null) {
                    if (expiryTime instanceof String) {
                        // Parse string to LocalDateTime if needed
                        try {
                            upiDetails.setExpiryTime(LocalDateTime.parse((String) expiryTime));
                        } catch (Exception e) {
                            logger.warn("Failed to parse expiry time: {}", expiryTime);
                        }
                    } else if (expiryTime instanceof LocalDateTime) {
                        upiDetails.setExpiryTime((LocalDateTime) expiryTime);
                    }
                }

                // Merchant category and sub-merchant
                upiDetails.setMerchantCategoryCode((String) additionalFields.get("merchantCategoryCode"));
                upiDetails.setSubMerchantId((String) additionalFields.get("subMerchantId"));
            }
        }

        // Extract UPI-specific data from response
        if (response != null && response.getAdditionalData() != null) {
            Map<String, Object> responseData = response.getAdditionalData();

            upiDetails.setUpiTransactionId((String) responseData.get("upiTransactionId"));
            upiDetails.setCustomerReference((String) responseData.get("customerReference"));
            upiDetails.setUpiRequestId((String) responseData.get("upiRequestId"));

            // Handle potential type conversion for response fields
            Object responseStatus = responseData.get("status");
            if (responseStatus != null) {
                upiDetails.setPaymentStatus(responseStatus.toString());
            }
        }

        upiTransactionRepository.save(upiDetails);
        logger.debug("Saved UPI transaction details for correlation ID: {}", header.getCorrelationId());
    }

    /**
     * Retrieve channel-specific transaction details
     */
    public Object getChannelSpecificDetails(TransactionHeader header) {
        String channel = header.getChannel().toUpperCase();

        return switch (channel) {
            case "POS" -> posTransactionRepository.findByTransactionHeader(header).orElse(null);
            case "UPI" -> upiTransactionRepository.findByTransactionHeader(header).orElse(null);
            // Add other channels...
            default -> null;
        };
    }

    /**
     * Search transactions by channel-specific criteria
     */
    public List<?> searchByChannelSpecificCriteria(String channel, String searchType, String searchValue) {
        return switch (channel.toUpperCase()) {
            case "POS" -> searchPOSTransactions(searchType, searchValue);
            case "UPI" -> searchUPITransactions(searchType, searchValue);
            default -> List.of();
        };
    }

    private List<POSTransactionDetails> searchPOSTransactions(String searchType, String searchValue) {
        return switch (searchType.toLowerCase()) {
            case "rrn" -> posTransactionRepository.findByRrn(searchValue)
                    .map(List::of)
                    .orElse(List.of());
            case "merchant" -> posTransactionRepository.findByMerchantIdAndTerminalId(searchValue, null);
            default -> List.of();
        };
    }

    private List<UPITransactionDetails> searchUPITransactions(String searchType, String searchValue) {
        return switch (searchType.toLowerCase()) {
            case "vpa" -> upiTransactionRepository.findByVPA(searchValue);
            case "upi_txn_id" -> upiTransactionRepository.findByUpiTransactionId(searchValue)
                    .map(List::of)
                    .orElse(List.of());
            default -> List.of();
        };
    }
}

