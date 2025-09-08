package com.concerto.omnichannel.handlers.bbps;

import com.concerto.omnichannel.connector.BBPSConnector;
import com.concerto.omnichannel.dto.Payload;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.service.BBPSResponseParserService;
import com.concerto.omnichannel.service.BBPSXmlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
public class BBPSOperationHandler implements OperationHandler {

    @Autowired
    private BBPSXmlParserService parserService;

    @Autowired
    private BBPSResponseParserService responseParserService;

    @Autowired
    private BBPSConnector bbpsConnector;

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        try {
            // Convert generic TransactionRequest to BBPSTransactionRequest
            TransactionRequest bbpsRequest = mapToBBPSRequest(request);

            // Parse JSON to BBPS XML
            String xmlRequest = parserService.parseToXML(bbpsRequest);

            // Send to BBPS via connector
            String xmlResponse = bbpsConnector.sendToBBPSCU(xmlRequest);

            // Parse XML response back to JSON
            return responseParserService.parseFromXML(xmlResponse, request.getOperation());

        } catch (Exception e) {
            return createErrorResponse(request, e);
        }
    }

    @Override
    public String getOperationType() {
        return "";
    }

    @Override
    public String getChannel() {
        return "BBPS";
    }

    @Override
    public boolean supports(String channel, String operation) {
        return "BBPS".equalsIgnoreCase(channel) &&
                Set.of("fetchbill", "pay", "checkstatus", "validation")
                        .contains(operation.toLowerCase());
    }

    private TransactionRequest mapToBBPSRequest(TransactionRequest request) {
        // Map from generic request to BBPS-specific request
        // This would extract BBPS-specific fields from the generic payload
        TransactionRequest bbpsRequest = new TransactionRequest();
        bbpsRequest.setChannel(request.getChannel());
        bbpsRequest.setOperation(request.getOperation());

        // Map payload fields
        Payload payload = new Payload();
        // Extract and map fields from request.getPayload()
        bbpsRequest.setPayload(payload);

        return bbpsRequest;
    }

    private TransactionResponse createErrorResponse(TransactionRequest request, Exception e) {
        return TransactionResponse.builder()
                .success(false)
                .status("ERROR")
                .message("BBPS processing failed: " + e.getMessage())
                .correlationId(request.getCorrelationId())
                .timestamp(LocalDateTime.now())
                .build();
    }
}

