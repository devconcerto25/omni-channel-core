package com.concerto.omnichannel.handlers.upi;

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
public class UPIPaymentHandler implements OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(UPIPaymentHandler.class);

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        logger.info("Processing UPI payment request");

        try {
            // Get the UPI connector
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
            /*ObjectMapper mapper = new ObjectMapper();
            ResponsePayload responsePayload1 = mapper.readValue(responsePayload, ResponsePayload.class);
            response.setPayload(responsePayload1);*/
            // Check success status
            Boolean success = (Boolean) responseMap.get("success");
            if (success == null) {
                // Check UPI specific success indicator
                success = "SUCCESS".equalsIgnoreCase((String) responseMap.get("status"));
            }

            response.setSuccess(success);

            if (success) {
                // Extract UPI transaction details
                response.setExternalReference((String) responseMap.get("upiTransactionId"));
                response.addAdditionalData("amount", responseMap.get("amount"));
                response.addAdditionalData("payer", responseMap.get("payerVpa"));
                response.addAdditionalData("payee", responseMap.get("payeeVpa"));
            } else {
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            logger.info("UPI payment completed with status: {}", success ? "SUCCESS" : "FAILED");
            return response;

        } catch (Exception e) {
            logger.error("Error processing UPI payment", e);

            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setChannel(request.getChannel());
            errorResponse.setOperation(request.getOperation());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("UPI payment processing failed: " + e.getMessage());
            errorResponse.setErrorCode("PROCESSING_ERROR");

            return errorResponse;
        }
    }

    @Override
    public String getOperationType() {
        return "payment";
    }

    @Override
    public String getChannel() {
        return "UPI";
    }

    @Override
    public boolean supports(String channel, String operation) {
        return "UPI".equalsIgnoreCase(channel) &&
                ("payment".equalsIgnoreCase(operation) ||
                        "transfer".equalsIgnoreCase(operation));
    }
}
