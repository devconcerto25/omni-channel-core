package com.concerto.omnichannel.handlers.hsm;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class HSMOperationHandler implements OperationHandler {

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(String channel, String operation) {
        return "HSM".equalsIgnoreCase(channel) &&
                (operation.equalsIgnoreCase("PIN_VERIFY") ||
                        operation.equalsIgnoreCase("KEY_EXCHANGE") ||
                        operation.equalsIgnoreCase("ENCRYPT_DATA") ||
                        operation.equalsIgnoreCase("DECRYPT_DATA") ||
                        operation.equalsIgnoreCase("GENERATE_MAC") ||
                        operation.equalsIgnoreCase("VERIFY_MAC") ||
                        operation.equalsIgnoreCase("GENERATE_KEY") ||
                        operation.equalsIgnoreCase("TRANSLATE_PIN") ||
                        operation.equalsIgnoreCase("GENERATE_CVV") ||
                        operation.equalsIgnoreCase("VERIFY_CVV"));
    }



    @Override
    public TransactionResponse handle(TransactionRequest request) {
        try {
            Connector connector = connectorFactory.getConnector("HSM");
            String requestPayload = objectMapper.writeValueAsString(request);
            String responsePayload = connector.process(request);
            return createSuccessResponse(responsePayload, request);
        } catch (Exception e) {
            return createErrorResponse(request, e);
        }
    }

    @Override
    public CompletableFuture<TransactionResponse> handleAsync(TransactionRequest request) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Get HSM connector
                        Connector connector = connectorFactory.getConnector("HSM");

                        // Convert request to JSON payload (fast operation, can stay sync)
                        String requestPayload = objectMapper.writeValueAsString(request);

                        return new HSMProcessingContext(connector, requestPayload, request);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize HSM processing context", e);
                    }
                })
                .thenCompose(context -> processHSMOperationAsync(context))
                .exceptionally(throwable -> {
                    return createErrorResponse(request,
                            throwable instanceof RuntimeException ?
                                    (Exception) throwable.getCause() :
                                    new Exception(throwable));
                });
    }

    private CompletableFuture<TransactionResponse> processHSMOperationAsync(HSMProcessingContext context) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Process through HSM connector
                        String responsePayload = context.connector.process(context.request);
                        // Alternative if connector has async method:
                        // return context.connector.processAsync(context.request);

                        return responsePayload;
                    } catch (Exception e) {
                        throw new RuntimeException("HSM connector processing failed", e);
                    }
                })
                .thenApply(responsePayload -> {
                    try {
                        return createSuccessResponse(responsePayload, context.request);
                    } catch (Exception e) {
                        return createErrorResponse(context.request, e);
                    }
                });
    }

    private TransactionResponse createSuccessResponse(String responsePayload, TransactionRequest request) {
        try {
            TransactionResponse response = new TransactionResponse();
            response.setChannel(request.getChannel());
            response.setOperation(request.getOperation());
            response.setPayload(responsePayload);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            // Check success status
            Boolean success = (Boolean) responseMap.get("success");
            if (success == null) {
                // Check HSM specific success indicator
                success = "00".equals(responseMap.get("responseCode"));
            }

            response.setSuccess(success);

            if (success) {
                // Extract HSM operation specific data based on operation type
                extractHSMOperationData(response, responseMap, request.getOperation());
            } else {
                // Extract error information
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            return response;
        } catch (Exception e) {
            return createErrorResponse(request, e);
        }
    }

    private void extractHSMOperationData(TransactionResponse response, Map<String, Object> responseMap, String operation) {
        // Common HSM response fields
        response.setExternalReference((String) responseMap.get("rrn"));
        response.addAdditionalData("transactionId", responseMap.get("transactionId"));

        // Operation-specific data extraction
        switch (operation.toUpperCase()) {
            case "PIN_VERIFY":
                response.addAdditionalData("pinVerificationResult", responseMap.get("pinVerificationResult"));
                response.addAdditionalData("pinOffset", responseMap.get("pinOffset"));
                break;

            case "KEY_EXCHANGE":
                response.addAdditionalData("keyExchangeData", responseMap.get("keyExchangeData"));
                response.addAdditionalData("keyCheckValue", responseMap.get("keyCheckValue"));
                break;

            case "ENCRYPT_DATA":
            case "DECRYPT_DATA":
                response.addAdditionalData("encryptedData", responseMap.get("encryptedData"));
                response.addAdditionalData("keyId", responseMap.get("keyId"));
                break;

            case "GENERATE_MAC":
            case "VERIFY_MAC":
                response.addAdditionalData("macValue", responseMap.get("macValue"));
                response.addAdditionalData("macAlgorithm", responseMap.get("macAlgorithm"));
                break;

            case "GENERATE_KEY":
                response.addAdditionalData("generatedKey", responseMap.get("generatedKey"));
                response.addAdditionalData("keyType", responseMap.get("keyType"));
                response.addAdditionalData("keyCheckValue", responseMap.get("keyCheckValue"));
                break;

            case "TRANSLATE_PIN":
                response.addAdditionalData("translatedPin", responseMap.get("translatedPin"));
                response.addAdditionalData("sourceKeyId", responseMap.get("sourceKeyId"));
                response.addAdditionalData("destinationKeyId", responseMap.get("destinationKeyId"));
                break;

            case "GENERATE_CVV":
            case "VERIFY_CVV":
                response.addAdditionalData("cvvValue", responseMap.get("cvvValue"));
                response.addAdditionalData("cvvKeyId", responseMap.get("cvvKeyId"));
                break;

            default:
                // For unknown operations, add generic HSM response data
                response.addAdditionalData("hsmResponse", responseMap.get("hsmResponse"));
                break;
        }
    }

    // Helper class to carry context through the async chain
    private static class HSMProcessingContext {
        final Connector connector;
        final String requestPayload;
        final TransactionRequest request;

        HSMProcessingContext(Connector connector, String requestPayload, TransactionRequest request) {
            this.connector = connector;
            this.requestPayload = requestPayload;
            this.request = request;
        }
    }

    private TransactionResponse createErrorResponse(TransactionRequest request, Exception e) {
        TransactionResponse errorResponse = new TransactionResponse();
        errorResponse.setChannel(request.getChannel());
        errorResponse.setOperation(request.getOperation());
        errorResponse.setSuccess(false);
        errorResponse.setErrorMessage("Purchase processing failed: " + e.getMessage());
        errorResponse.setErrorCode("PROCESSING_ERROR");

        return errorResponse;
    }


    @Override
    public String getOperationType() {
        return "CRYPTOGRAPHIC";
    }

    @Override
    public String getChannel() {
        return "HSM";
    }
}

