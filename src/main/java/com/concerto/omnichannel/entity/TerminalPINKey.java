package com.concerto.omnichannel.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "terminal_pin_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "terminal_id"}))
public class TerminalPINKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "terminal_id", nullable = false, length = 50)
    private String terminalId;

    @Column(name = "pin_key", nullable = false, length = 64)
    private String pinKey; // Encrypted PIN key

    @Column(name = "key_check_value", length = 6)
    private String keyCheckValue;

    @Column(name = "key_status", length = 20)
    private String keyStatus = "ACTIVE";

    @Column(name = "key_type", length = 20)
    private String keyType = "DES"; // DES, 3DES, AES

    @Column(name = "key_usage", length = 50)
    private String keyUsage = "PIN_VERIFICATION";

    @Column(name = "key_version", length = 10)
    private String keyVersion = "V1";

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "expires_date")
    private LocalDateTime expiresDate;

    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;

    @Column(name = "usage_count")
    private Long usageCount = 0L;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    // Constructors
    public TerminalPINKey() {}

    public TerminalPINKey(String merchantId, String terminalId, String pinKey, String keyCheckValue) {
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.pinKey = pinKey;
        this.keyCheckValue = keyCheckValue;
    }

    // Getters and Setters
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

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getPinKey() {
        return pinKey;
    }

    public void setPinKey(String pinKey) {
        this.pinKey = pinKey;
    }

    public String getKeyCheckValue() {
        return keyCheckValue;
    }

    public void setKeyCheckValue(String keyCheckValue) {
        this.keyCheckValue = keyCheckValue;
    }

    public String getKeyStatus() {
        return keyStatus;
    }

    public void setKeyStatus(String keyStatus) {
        this.keyStatus = keyStatus;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getKeyUsage() {
        return keyUsage;
    }

    public void setKeyUsage(String keyUsage) {
        this.keyUsage = keyUsage;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getExpiresDate() {
        return expiresDate;
    }

    public void setExpiresDate(LocalDateTime expiresDate) {
        this.expiresDate = expiresDate;
    }

    public LocalDateTime getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(LocalDateTime lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

    public Long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Long usageCount) {
        this.usageCount = usageCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    // Utility methods
    public boolean isActive() {
        return "ACTIVE".equals(keyStatus);
    }

    public boolean isExpired() {
        return expiresDate != null && expiresDate.isBefore(LocalDateTime.now());
    }

    public void incrementUsageCount() {
        this.usageCount++;
        this.lastUsedDate = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TerminalPINKey{" +
                "id=" + id +
                ", merchantId='" + merchantId + '\'' +
                ", terminalId='" + terminalId + '\'' +
                ", keyStatus='" + keyStatus + '\'' +
                ", keyType='" + keyType + '\'' +
                ", keyVersion='" + keyVersion + '\'' +
                ", createdDate=" + createdDate +
                ", expiresDate=" + expiresDate +
                ", usageCount=" + usageCount +
                '}';
    }
}
