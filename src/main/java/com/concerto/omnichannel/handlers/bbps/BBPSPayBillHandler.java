package com.concerto.omnichannel.handlers.bbps;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BBPSPayBillHandler implements OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(BBPSPayBillHandler.class);

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        logger.info("Processing BBPS fetch bill request");
        try{
            // Get the BBPS connector
            Connector connector = connectorFactory.getConnector(request.getChannel());

            // Convert request to JSON payload
            String requestPayload = objectMapper.writeValueAsString(request);

            // Process through connector
            String responsePayload = connector.process(request);

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
                // Check BBPS specific success indicator
                success = "SUCCESS".equalsIgnoreCase((String) responseMap.get("status"));
            }

            response.setSuccess(success);

            if (success) {
                // Extract bill information
                response.setExternalReference((String) responseMap.get("billId"));
                response.addAdditionalData("billAmount", responseMap.get("billAmount"));
                response.addAdditionalData("dueDate", responseMap.get("dueDate"));
                response.addAdditionalData("billerName", responseMap.get("billerName"));
            } else {
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            logger.info("BBPS fetch bill completed with status: {}", success ? "SUCCESS" : "FAILED");
            return response;
        }catch (Exception e) {
            logger.error("Error processing BBPS pay bill", e);

            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setChannel(request.getChannel());
            errorResponse.setOperation(request.getOperation());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("pay bill processing failed: " + e.getMessage());
            errorResponse.setErrorCode("PROCESSING_ERROR");

            return errorResponse;
        }

    }

    @Override
    public String getOperationType() {
        return "payBill";
    }

    @Override
    public String getChannel() {
        return "BBPS";
    }

    @Override
    public boolean supports(String channel, String operation) {
        return "BBPS".equalsIgnoreCase(channel) &&
                "payBill".equalsIgnoreCase(operation);
    }

    @Override
    public CompletableFuture<TransactionResponse> handleAsync(TransactionRequest request) {
        return null;
    }
}
