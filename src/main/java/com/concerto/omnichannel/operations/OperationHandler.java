package com.concerto.omnichannel.operations;


import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;

import java.util.concurrent.CompletableFuture;

public interface OperationHandler {
    TransactionResponse handle(TransactionRequest request);
    String getOperationType(); // e.g., "purchase", "fetchBill", "onboardMerchant"
    String getChannel(); // e.g., "ISO8583", "BBPS", "UPI"
    boolean supports(String channel, String operation);
    CompletableFuture<TransactionResponse> handleAsync(TransactionRequest request);
}

