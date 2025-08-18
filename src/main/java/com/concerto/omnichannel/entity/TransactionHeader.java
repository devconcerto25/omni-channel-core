package com.concerto.omnichannel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "transaction_header", indexes = {
        @Index(name = "idx_correlation_id", columnList = "correlationId"),
        @Index(name = "idx_channel_status", columnList = "channel, status"),
        @Index(name = "idx_request_timestamp", columnList = "requestTimestamp")
})
public class TransactionHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", unique = true, nullable = false, length = 100)
    private String correlationId;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    @Column(name = "transaction_type", length = 100)
    private String transactionType;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "request_timestamp", nullable = false)
    private LocalDateTime requestTimestamp;

    @Column(name = "response_timestamp")
    private LocalDateTime responseTimestamp;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @OneToMany(mappedBy = "transactionHeader", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransactionDetail> transactionDetails;

    @PrePersist
    protected void onCreate() {
        if (requestTimestamp == null) {
            requestTimestamp = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (responseTimestamp != null && requestTimestamp != null) {
            processingTimeMs = java.time.Duration.between(requestTimestamp, responseTimestamp).toMillis();
        }
    }

    // Constructors
    public TransactionHeader() {}

    public TransactionHeader(String correlationId, String channel, String operation) {
        this.correlationId = correlationId;
        this.channel = channel;
        this.operation = operation;
        this.requestTimestamp = LocalDateTime.now();
        this.status = "RECEIVED";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public LocalDateTime getRequestTimestamp() { return requestTimestamp; }
    public void setRequestTimestamp(LocalDateTime requestTimestamp) { this.requestTimestamp = requestTimestamp; }

    public LocalDateTime getResponseTimestamp() { return responseTimestamp; }
    public void setResponseTimestamp(LocalDateTime responseTimestamp) { this.responseTimestamp = responseTimestamp; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public List<TransactionDetail> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(List<TransactionDetail> transactionDetails) { this.transactionDetails = transactionDetails; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionHeader that = (TransactionHeader) o;
        return Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId);
    }
}