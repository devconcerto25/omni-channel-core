package com.concerto.omnichannel.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_channel_config")
public class MerchantChannelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    // Channel Settings
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount = BigDecimal.valueOf(1.00);

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount = BigDecimal.valueOf(200000.00);

    // Fee Configuration
    @Column(name = "transaction_fee_type", length = 20)
    private String transactionFeeType;

    @Column(name = "transaction_fee", precision = 10, scale = 4)
    private BigDecimal transactionFee;

    @Column(name = "minimum_fee", precision = 10, scale = 2)
    private BigDecimal minimumFee;

    @Column(name = "maximum_fee", precision = 10, scale = 2)
    private BigDecimal maximumFee;

    // Settlement
    @Column(name = "settlement_frequency", length = 20)
    private String settlementFrequency;

    @Column(name = "settlement_account", length = 50)
    private String settlementAccount;

    // Risk Settings
    @Column(name = "velocity_check_enabled")
    private Boolean velocityCheckEnabled = true;

    @Column(name = "max_transactions_per_hour")
    private Integer maxTransactionsPerHour = 100;

    @Column(name = "max_amount_per_hour", precision = 15, scale = 2)
    private BigDecimal maxAmountPerHour;

    // Timestamps
    @Column(name = "created_date")
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date")
    private LocalDateTime updatedDate = LocalDateTime.now();

    // Relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", referencedColumnName = "merchant_id", insertable = false, updatable = false)
    private MerchantInfo merchantInfo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Boolean getEnabled() {
        return isEnabled;
    }

    public void setEnabled(Boolean enabled) {
        isEnabled = enabled;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public String getTransactionFeeType() {
        return transactionFeeType;
    }

    public void setTransactionFeeType(String transactionFeeType) {
        this.transactionFeeType = transactionFeeType;
    }

    public BigDecimal getTransactionFee() {
        return transactionFee;
    }

    public void setTransactionFee(BigDecimal transactionFee) {
        this.transactionFee = transactionFee;
    }

    public BigDecimal getMinimumFee() {
        return minimumFee;
    }

    public void setMinimumFee(BigDecimal minimumFee) {
        this.minimumFee = minimumFee;
    }

    public BigDecimal getMaximumFee() {
        return maximumFee;
    }

    public void setMaximumFee(BigDecimal maximumFee) {
        this.maximumFee = maximumFee;
    }

    public String getSettlementFrequency() {
        return settlementFrequency;
    }

    public void setSettlementFrequency(String settlementFrequency) {
        this.settlementFrequency = settlementFrequency;
    }

    public String getSettlementAccount() {
        return settlementAccount;
    }

    public void setSettlementAccount(String settlementAccount) {
        this.settlementAccount = settlementAccount;
    }

    public Boolean getVelocityCheckEnabled() {
        return velocityCheckEnabled;
    }

    public void setVelocityCheckEnabled(Boolean velocityCheckEnabled) {
        this.velocityCheckEnabled = velocityCheckEnabled;
    }

    public Integer getMaxTransactionsPerHour() {
        return maxTransactionsPerHour;
    }

    public void setMaxTransactionsPerHour(Integer maxTransactionsPerHour) {
        this.maxTransactionsPerHour = maxTransactionsPerHour;
    }

    public BigDecimal getMaxAmountPerHour() {
        return maxAmountPerHour;
    }

    public void setMaxAmountPerHour(BigDecimal maxAmountPerHour) {
        this.maxAmountPerHour = maxAmountPerHour;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public MerchantInfo getMerchantInfo() {
        return merchantInfo;
    }

    public void setMerchantInfo(MerchantInfo merchantInfo) {
        this.merchantInfo = merchantInfo;
    }
}

