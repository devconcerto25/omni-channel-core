package com.concerto.omnichannel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Transaction request payload")
public class TransactionRequest {

    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "^(POS|ATM|UPI|BBPS|PG)$", message = "Invalid channel. Allowed values: POS, ATM, UPI, BBPS, PG")
    @Schema(description = "Transaction channel", example = "POS", required = true)
    private String channel;

    @NotBlank(message = "Operation is required")
    @Size(max = 100, message = "Operation must not exceed 100 characters")
    @Schema(description = "Transaction operation", example = "purchase", required = true)
    private String operation;

    @Valid
    @NotNull(message = "Payload is required")
    @Schema(description = "Transaction payload", required = true)
    private Payload payload;

    @Schema(description = "Request timestamp")
    @JsonProperty("requestTime")
    private LocalDateTime requestTime;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    // Constructors
    public TransactionRequest() {
        this.requestTime = LocalDateTime.now();
    }

    public TransactionRequest(String channel, String operation, Payload payload) {
        this.channel = channel;
        this.operation = operation;
        this.payload = payload;
        this.requestTime = LocalDateTime.now();
    }

    // Getters and Setters
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public Payload getPayload() { return payload; }
    public void setPayload(Payload payload) { this.payload = payload; }

    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return "TransactionRequest{" +
                "channel='" + channel + '\'' +
                ", operation='" + operation + '\'' +
                ", requestTime=" + requestTime +
                '}';
    }
}
