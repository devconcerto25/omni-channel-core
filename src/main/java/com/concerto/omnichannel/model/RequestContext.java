package com.concerto.omnichannel.model;

import com.concerto.omnichannel.dto.TransactionRequest;

import java.time.LocalDateTime;

public class RequestContext {
    private String merchantId;
    private String terminalId;
    private String stan;
    private TransactionRequest request;
    private LocalDateTime timestamp;

    // Constructors, getters, setters
    public RequestContext(String merchantId, String terminalId, String stan,
                          TransactionRequest request, LocalDateTime timestamp) {
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.stan = stan;
        this.request = request;
        this.timestamp = timestamp;
    }

    // Getters
    public String getMerchantId() { return merchantId; }
    public String getTerminalId() { return terminalId; }
    public String getStan() { return stan; }
    public TransactionRequest getRequest() { return request; }
    public LocalDateTime getTimestamp() { return timestamp; }
}