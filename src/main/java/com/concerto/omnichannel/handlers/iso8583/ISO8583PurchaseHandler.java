package com.concerto.omnichannel.operations.handlers;

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

            // Process through connector
            String responsePayload = connector.process(requestPayload);

            // Parse connector response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            // Create transaction response
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

        } catch (Exception e) {
            logger.error("Error processing ISO8583 purchase", e);

            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setChannel(request.getChannel());
            errorResponse.setOperation(request.getOperation());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Purchase processing failed: " + e.getMessage());
            errorResponse.setErrorCode("PROCESSING_ERROR");

            return errorResponse;
        }
    }

    @Override
    public String getOperationType() {
        return "purchase";
    }

    @Override
    public String getChannel() {
        return "ISO8583";
    }

    @Override
    public boolean supports(String channel, String operation) {
        return ("ISO8583".equalsIgnoreCase(channel) ||
                "POS".equalsIgnoreCase(channel) ||
                "ATM".equalsIgnoreCase(channel)) &&
                "purchase".equalsIgnoreCase(operation);
    }
}