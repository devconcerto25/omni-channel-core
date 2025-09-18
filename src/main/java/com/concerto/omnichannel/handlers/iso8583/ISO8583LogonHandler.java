package com.concerto.omnichannel.handlers.iso8583;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ISO8583LogonHandler implements OperationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ISO8583LogonHandler.class);
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

            // Process through connector
            String responsePayload = connector.process(request);
//            String responsePayload = connector.processAsync(request).get();

            // Parse connector response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            // Create transaction response
            return createSuccessResponse(request, responsePayload, responseMap);

        } catch (Exception e) {
            logger.error("Error processing ISO8583 purchase", e);
            return createErrorResponse(request, e);
        }
    }

    @Override
    public CompletableFuture<TransactionResponse> handleAsync(TransactionRequest request) {
        logger.info("Processing ISO8583 refund request asynchronously");

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Get the appropriate connector
                        Connector connector = connectorFactory.getConnector(request.getChannel());

                        // Convert request to JSON payload (fast operation, can stay sync)
                        String requestPayload = objectMapper.writeValueAsString(request);

                        return new RefundProcessingContext(connector, requestPayload, request);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize refund processing context", e);
                    }
                })
                .thenCompose(context -> processRefundWithConnectorAsync(context))
                .exceptionally(throwable -> {
                    logger.error("Error processing ISO8583 refund asynchronously", throwable);
                    return createErrorResponse(request,
                            throwable instanceof RuntimeException ?
                                    (Exception) throwable.getCause() :
                                    new Exception(throwable));
                });
    }

    private CompletableFuture<TransactionResponse> processRefundWithConnectorAsync(RefundProcessingContext context) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Process through connector - this should ideally be made async too
                        String responsePayload = context.connector.process(context.request);
                        // Alternative if connector has async method:
                        // return context.connector.processAsync(context.request);

                        return responsePayload;
                    } catch (Exception e) {
                        throw new RuntimeException("Connector processing failed for refund", e);
                    }
                })
                .thenApply(responsePayload -> {
                    try {
                        // Parse connector response
                        @SuppressWarnings("unchecked")
                        Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

                        // Create transaction response
                        return createSuccessResponse(context.request, responsePayload, responseMap);
                    } catch (Exception e) {
                        logger.error("Error parsing refund connector response", e);
                        return createErrorResponse(context.request, e);
                    }
                });
    }

    private TransactionResponse createSuccessResponse(TransactionRequest request, String responsePayload, Map<String, Object> responseMap) {
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

            logger.info("ISO8583 refund completed successfully");
        } else {
            // Extract error information
            response.setErrorCode((String) responseMap.get("errorCode"));
            response.setErrorMessage((String) responseMap.get("errorMessage"));

            logger.info("ISO8583 refund completed with failure");
        }

        return response;
    }

    private TransactionResponse createErrorResponse(TransactionRequest request, Exception e) {
        TransactionResponse errorResponse = new TransactionResponse();
        errorResponse.setChannel(request.getChannel());
        errorResponse.setOperation(request.getOperation());
        errorResponse.setSuccess(false);
        errorResponse.setErrorMessage("Refund processing failed: " + e.getMessage());
        errorResponse.setErrorCode("PROCESSING_ERROR");

        return errorResponse;
    }

    // Helper class to carry context through the async chain
    private static class RefundProcessingContext {
        final Connector connector;
        final String requestPayload;
        final TransactionRequest request;

        RefundProcessingContext(Connector connector, String requestPayload, TransactionRequest request) {
            this.connector = connector;
            this.requestPayload = requestPayload;
            this.request = request;
        }
    }

    @Override
    public String getOperationType() {
        return "logon";
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
                "logon".equalsIgnoreCase(operation);
    }
}
