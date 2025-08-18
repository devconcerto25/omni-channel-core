package com.concerto.omnichannel.operations;


import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;

public interface OperationHandler {
    TransactionResponse handle(TransactionRequest request);
    String getOperationType(); // e.g., "fetchBill", "onboardMerchant"
    String getChannel();
}

