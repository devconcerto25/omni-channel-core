package com.concerto.omnichannel.handlers.bbps;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.utils.Channels;
import com.concerto.omnichannel.utils.Operations;
import org.springframework.stereotype.Component;

@Component
public class BBPSFetchBillHandler  implements OperationHandler {

    @Override
    public TransactionResponse handle(TransactionRequest request) {
        // TODO: Parse and validate request.payload, connect to BBPS endpoint, handle response

        TransactionResponse response = new TransactionResponse();
        response.setChannel(request.getChannel());
        response.setTransactionId(0L); // Will be replaced with actual ID at service level
        response.setSuccess(true);

        // Set the business response payload
        response.setPayload("{ \"billAmount\": 500, \"dueDate\": \"2025-08-20\" }");
        return response;
    }

    @Override
    public String getOperationType() {
        return Operations.FETCH_BILL.toString();
    }

    @Override
    public String getChannel() {
        return Channels.BBPS.toString();
    }
}
