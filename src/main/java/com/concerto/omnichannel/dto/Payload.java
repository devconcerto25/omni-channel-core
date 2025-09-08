package com.concerto.omnichannel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Transaction payload containing transaction details")
public class Payload {

    @NotBlank(message = "Transaction type is required")
    @Size(max = 100, message = "Transaction type must not exceed 100 characters")
    @Schema(description = "Type of transaction", example = "purchase", required = true)
    @JsonProperty("transactionType")
    private String transactionType;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount cannot exceed 999999999.99")
    @Schema(description = "Transaction amount", example = "100.50")
    private BigDecimal amount;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Size(max = 100, message = "Account number must not exceed 100 characters")
    @Schema(description = "Account number")
    @JsonProperty("accountNumber")
    private String accountNumber;

    @Size(max = 100, message = "Merchant ID must not exceed 100 characters")
    @Schema(description = "Merchant identifier")
    @JsonProperty("merchantId")
    private String merchantId;

    @Size(max = 50, message = "Terminal ID must not exceed 50 characters")
    @Schema(description = "Terminal identifier")
    @JsonProperty("terminalId")
    private String terminalId;

    @Pattern(regexp = "^[0-9]{12,19}$", message = "Card number must be 12-19 digits")
    @Schema(description = "Card number (for card transactions)")
    @JsonProperty("cardNumber")
    private String cardNumber;

    @Size(max = 200, message = "Description must not exceed 200 characters")
    @Schema(description = "Transaction description")
    private String description;

    @Schema(description = "Additional transaction fields")
    @JsonProperty("additionalFields")
    private Map<String, Object> additionalFields;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    // Constructors
    public Payload() {}

    public Payload(String transactionType, BigDecimal amount, String currency) {
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
    }

    // Getters and Setters
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getAdditionalFields() { return additionalFields; }
    public void setAdditionalFields(Map<String, Object> additionalFields) { this.additionalFields = additionalFields; }

    // Convenience method to add additional fields
    public void addAdditionalField(String key, Object value) {
        if (this.additionalFields == null) {
            this.additionalFields = new java.util.HashMap<>();
        }
        this.additionalFields.put(key, value);
    }

}
