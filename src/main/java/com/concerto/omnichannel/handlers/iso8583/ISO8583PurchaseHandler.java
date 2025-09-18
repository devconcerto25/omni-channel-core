package com.concerto.omnichannel.handlers.iso8583;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.connector.HSMConnector;
import com.concerto.omnichannel.connector.ISO8583Connector;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ISO8583PurchaseHandler implements OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ISO8583PurchaseHandler.class);

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        logger.info("Processing ISO8583 purchase request");

        try {
            // Get the appropriate connector
            Connector connector = connectorFactory.getConnector(request.getChannel());

            // Convert request to JSON payload
            String requestPayload = objectMapper.writeValueAsString(request);
            //boolean hasPINBlock = true;
            if (hasPINBlock(request)) {
                logger.debug("PIN Block found in request, processing PIN verification");
                boolean pinVerified = verifyPINInternally(request);
                if (!pinVerified) {

                    return errorResponse(request, new Exception("PIN Verification Failed.."));
                }
            }else{
                logger.debug("PIN Block now found in request, skipping PIN verification");
            }

            // Process through connector
            String responsePayload = connector.process(request);
//            String responsePayload = connector.processAsync(request).get();

            // Parse connector response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            // Create transaction response
            return successResponse(request, responsePayload, responseMap);

        } catch (Exception e) {
            logger.error("Error processing ISO8583 purchase", e);
            return errorResponse(request, e);
        }
    }


    @Override
    public CompletableFuture<TransactionResponse> handleAsync(TransactionRequest request) {
        logger.info("Processing ISO8583 purchase request asynchronously");

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Get the appropriate connector
                        Connector connector = connectorFactory.getConnector(request.getChannel());

                        // Convert request to JSON payload (fast operation, can stay sync)
                        String requestPayload = objectMapper.writeValueAsString(request);

                        return new ProcessingContext(connector, requestPayload, request);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize processing context", e);
                    }
                })
                .thenCompose(context -> {
                    // Handle PIN verification if needed
                    if (hasPINBlock(context.request)) {
                        logger.debug("PIN Block found in request, processing PIN verification asynchronously");
                        return verifyPINInternallyAsync(context.request)
                                .thenCompose(pinVerified -> {
                                    if (!pinVerified) {
                                        return CompletableFuture.completedFuture(
                                                errorResponse(context.request, new Exception("PIN Verification Failed.."))
                                        );
                                    }
                                    // PIN verified, continue with connector processing
                                    return processWithConnectorAsync(context);
                                });
                    } else {
                        logger.debug("PIN Block not found in request, skipping PIN verification");
                        // No PIN verification needed, go directly to connector processing
                        return processWithConnectorAsync(context);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error processing ISO8583 purchase asynchronously", throwable);
                    return errorResponse(request,
                            throwable instanceof RuntimeException ?
                                    (Exception) throwable.getCause() :
                                    new Exception(throwable));
                });
    }

    private CompletableFuture<TransactionResponse> processWithConnectorAsync(ProcessingContext context) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Process through connector - this should ideally be made async too
                        String responsePayload = context.connector.process(context.request);
                        // Alternative if connector has async method:
                        // return context.connector.processAsync(context.request);

                        return responsePayload;
                    } catch (Exception e) {
                        throw new RuntimeException("Connector processing failed", e);
                    }
                })
                .thenApply(responsePayload -> {
                    try {
                        // Parse connector response
                        @SuppressWarnings("unchecked")
                        Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

                        // Create transaction response
                        return successResponse(context.request, responsePayload, responseMap);
                    } catch (Exception e) {
                        logger.error("Error parsing connector response", e);
                        return errorResponse(context.request, e);
                    }
                });
    }

    private CompletableFuture<Boolean> verifyPINInternallyAsync(TransactionRequest request) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Get PIN key from database (this should ideally be async too)

                        // Call HSM connector internally
                        HSMConnector hsmConnector = (HSMConnector) connectorFactory.getConnector("HSM");
                        String hsmResponse = hsmConnector.process(request);
                        // Alternative if HSM connector has async method:
                        // return hsmConnector.processAsync(request);

                        return hsmResponse;
                    } catch (Exception e) {
                        throw new RuntimeException("HSM PIN verification failed", e);
                    }
                })
                .thenApply(hsmResponse -> {
                    try {
                        return parseHSMPINVerificationResponse(hsmResponse);
                    } catch (Exception e) {
                        logger.error("Error parsing HSM response", e);
                        return false;
                    }
                });
    }


    private static TransactionResponse successResponse(TransactionRequest request, String responsePayload, Map<String, Object> responseMap) {
        TransactionResponse response = new TransactionResponse();
        response.setChannel(request.getChannel());
        response.setOperation(request.getOperation());
        response.setPayload(responsePayload);

        // Check success status
        Boolean success = (Boolean) responseMap.get("success");
        if (success == null) {
            // Check ISO8583 specific success indicator
            success = "00".equals(responseMap.get("responseCode"));
        }

        response.setSuccess(success);

        if (success) {
            // Extract success data
            response.setExternalReference((String) responseMap.get("rrn"));
            response.addAdditionalData("authorizationCode", responseMap.get("authorizationCode"));
            response.addAdditionalData("stan", responseMap.get("stan"));
        } else {
            // Extract error information
            response.setErrorCode((String) responseMap.get("errorCode"));
            response.setErrorMessage((String) responseMap.get("errorMessage"));
        }

        logger.info("ISO8583 purchase completed with status: {}", success ? "SUCCESS" : "FAILED");
        return response;
    }

    private static TransactionResponse errorResponse(TransactionRequest request, Exception e) {
        TransactionResponse errorResponse = new TransactionResponse();
        errorResponse.setChannel(request.getChannel());
        errorResponse.setOperation(request.getOperation());
        errorResponse.setSuccess(false);
        errorResponse.setErrorMessage("Purchase processing failed: " + e.getMessage());
        errorResponse.setErrorCode("PROCESSING_ERROR");

        return errorResponse;
    }

    private boolean verifyPINInternally(TransactionRequest request) throws Exception {
        // Get PIN key from database

        // Call HSM connector internally
        HSMConnector hsmConnector = (HSMConnector) connectorFactory.getConnector("HSM");
        String hsmResponse = hsmConnector.process(request);
        // Parse HSM response
        return parseHSMPINVerificationResponse(hsmResponse);
    }

    private boolean parseHSMPINVerificationResponse(String hsmResponse) throws JsonProcessingException {
        Map<String, Object> responseMap = objectMapper.readValue(hsmResponse, Map.class);
        return responseMap.get("success") == "00";
    }

    private boolean hasPINBlock(TransactionRequest request) {
        return request.getPayload().getAdditionalFields().get("pinBlock") != null;
    }

    private static class ProcessingContext {
        final Connector connector;
        final String requestPayload;
        final TransactionRequest request;

        ProcessingContext(Connector connector, String requestPayload, TransactionRequest request) {
            this.connector = connector;
            this.requestPayload = requestPayload;
            this.request = request;
        }
    }

    @Override
    public String getOperationType() {
        return "purchase";
    }

    @Override
    public String getChannel() {
        return "POS";
    }

    @Override
    public boolean supports(String channel, String operation) {
        return ("ISO8583".equalsIgnoreCase(channel) ||
                "POS".equalsIgnoreCase(channel) ||
                "ATM".equalsIgnoreCase(channel)) &&
                "purchase".equalsIgnoreCase(operation);
    }
}