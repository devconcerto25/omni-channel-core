package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.dto.TransactionRequest;

import java.util.concurrent.CompletableFuture;

public interface Connector {
    String process(TransactionRequest request) throws Exception;
    CompletableFuture<String>processAsync(TransactionRequest request) throws Exception;
    boolean supports(String channel);
    String getConnectorType();
}