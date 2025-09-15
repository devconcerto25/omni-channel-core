package com.concerto.omnichannel.handlers;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(Integer.MAX_VALUE) // Lowest priority - used as fallback
public class GenericOperationHandler implements OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(GenericOperationHandler.class);

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        logger.info("Processing generic operation: {} for channel: {}",
                request.getOperation(), request.getChannel());

        try {
            // Check if connector exists for the channel
            if (!connectorFactory.hasConnector(request.getChannel())) {
                return createUnsupportedChannelResponse(request);
            }

            // Get the appropriate connector
            Connector connector = connectorFactory.getConnector(request.getChannel());

            // Convert request to JSON payload
            String requestPayload = objectMapper.writeValueAsString(request);

            // Process through connector
            String responsePayload = connector.process(request);

            // Parse connector response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            // Create generic transaction response
            TransactionResponse response = new TransactionResponse();
            response.setChannel(request.getChannel());
            response.setOperation(request.getOperation());
            response.setPayload(responsePayload);
           /* ObjectMapper mapper = new ObjectMapper();
            ResponsePayload responsePayload1 = mapper.readValue(responsePayload, ResponsePayload.class);
            response.setPayload(responsePayload1);*/

            // Determine success status using generic checks
            Boolean success = determineSuccessStatus(responseMap);
            response.setSuccess(success);

            if (success) {
                // Extract common success fields
                extractSuccessFields(responseMap, response);
            } else {
                // Extract error information
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            logger.info("Generic operation completed with status: {}", success ? "SUCCESS" : "FAILED");
            return response;

        } catch (Exception e) {
            logger.error("Error processing generic operation", e);
            return createErrorResponse(request, e);
        }
    }

    @Override
    public String getOperationType() {
        return "generic";
    }

    @Override
    public String getChannel() {
        return "ALL";
    }

    @Override
    public boolean supports(String channel, String operation) {
        // This handler supports all channels and operations as a fallback
        // It will only be used if no specific handler is found
        return connectorFactory.hasConnector(channel);
    }

    private Boolean determineSuccessStatus(Map<String, Object> responseMap) {
        // Check various success indicators
        if (responseMap.containsKey("success")) {
            return (Boolean) responseMap.get("success");
        }

        // Check common status fields
        String status = (String) responseMap.get("status");
        if (status != null) {
            return "SUCCESS".equalsIgnoreCase(status) ||
                    "APPROVED".equalsIgnoreCase(status) ||
                    "COMPLETED".equalsIgnoreCase(status);
        }

        // Check ISO8583 response code
        String responseCode = (String) responseMap.get("responseCode");
        if (responseCode != null) {
            return "00".equals(responseCode);
        }

        // Default to true if no error indicators found
        return !responseMap.containsKey("error") &&
                !responseMap.containsKey("errorCode") &&
                !responseMap.containsKey("errorMessage");
    }

    private void extractSuccessFields(Map<String, Object> responseMap, TransactionResponse response) {
        // Extract common reference fields
        String[] referenceFields = {"rrn", "transactionId", "referenceNumber", "txnId"};
        for (String field : referenceFields) {
            if (responseMap.containsKey(field)) {
                response.setExternalReference((String) responseMap.get(field));
                break;
            }
        }

        // Add all response data as additional data
        responseMap.forEach((key, value) -> {
            if (!"success".equals(key) && !"status".equals(key)) {
                response.addAdditionalData(key, value);
            }
        });
    }

    private TransactionResponse createUnsupportedChannelResponse(TransactionRequest request) {
        TransactionResponse response = new TransactionResponse();
        response.setChannel(request.getChannel());
        response.setOperation(request.getOperation());
        response.setSuccess(false);
        response.setErrorCode("UNSUPPORTED_CHANNEL");
        response.setErrorMessage("No connector available for channel: " + request.getChannel());
        return response;
    }

    private TransactionResponse createErrorResponse(TransactionRequest request, Exception e) {
        TransactionResponse response = new TransactionResponse();
        response.setChannel(request.getChannel());
        response.setOperation(request.getOperation());
        response.setSuccess(false);
        response.setErrorCode("PROCESSING_ERROR");
        response.setErrorMessage("Operation processing failed: " + e.getMessage());
        return response;
    }
}
