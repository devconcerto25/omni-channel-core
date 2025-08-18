package com.concerto.omnichannel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Transaction response payload")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    @Schema(description = "Transaction ID")
    private Long transactionId;

    @Schema(description = "Correlation ID for tracking")
    private String correlationId;

    @Schema(description = "Transaction channel")
    private String channel;

    @Schema(description = "Transaction operation")
    private String operation;

    @Schema(description = "Transaction success status")
    private boolean success = true;

    @Schema(description = "Response payload")
    private String payload;

    @Schema(description = "Error message if transaction failed")
    private String errorMessage;

    @Schema(description = "Error code if transaction failed")
    private String errorCode;

    @Schema(description = "External reference number")
    private String externalReference;

    @Schema(description = "Response timestamp")
    private LocalDateTime responseTime;

    @Schema(description = "Processing time in milliseconds")
    private Long processingTimeMs;

    @Schema(description = "Additional response data")
    private Map<String, Object> additionalData;

    // Constructors
    public TransactionResponse() {
        this.responseTime = LocalDateTime.now();
    }

    public TransactionResponse(Long transactionId, String correlationId, boolean success) {
        this.transactionId = transactionId;
        this.correlationId = correlationId;
        this.success = success;
        this.responseTime = LocalDateTime.now();
    }

    // Static factory methods for common responses
    public static TransactionResponse success(Long transactionId, String correlationId, String payload) {
        TransactionResponse response = new TransactionResponse(transactionId, correlationId, true);
        response.setPayload(payload);
        return response;
    }

    public static TransactionResponse failure(Long transactionId, String correlationId, String errorMessage, String errorCode) {
        TransactionResponse response = new TransactionResponse(transactionId, correlationId, false);
        response.setErrorMessage(errorMessage);
        response.setErrorCode(errorCode);
        return response;
    }

    // Getters and Setters
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public LocalDateTime getResponseTime() { return responseTime; }
    public void setResponseTime(LocalDateTime responseTime) { this.responseTime = responseTime; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }

    // Convenience method to add additional data
    public void addAdditionalData(String key, Object value) {
        if (this.additionalData == null) {
            this.additionalData = new java.util.HashMap<>();
        }
        this.additionalData.put(key, value);
    }
}
