package com.concerto.omnichannel.handlers.iso8583;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.utils.Channels;
import com.concerto.omnichannel.utils.Operations;
import org.springframework.stereotype.Component;

@Component
public class ISOPurchaseHandler implements OperationHandler {

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        // Create response using the constructor or static factory method
        TransactionResponse response = new TransactionResponse();

        // Set basic response fields
        response.setChannel(request.getChannel());
     //   response.setCorrelationId(request.getCorrelationId()); // Assuming TransactionRequest has this
        response.setOperation("purchase");
        response.setTransactionId(0L); // Will be replaced with actual ID at service level
        response.setSuccess(true);

        // Set the business response payload
        response.setPayload("{ \"billAmount\": 500, \"dueDate\": \"2025-08-20\" }");

        // You could also use the static factory method instead:
        // TransactionResponse response = TransactionResponse.success(0L, request.getCorrelationId(),
        //     "{ \"billAmount\": 500, \"dueDate\": \"2025-08-20\" }");
        // response.setChannel(request.getChannel());
        // response.setOperation("purchase");

        return response;
    }

    @Override
    public String getOperationType() {
        return Operations.FINANCIAL.toString();
    }

    @Override
    public String getChannel() {
        return Channels.ISO8583.toString();
    }
}