package com.concerto.omnichannel.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pos_transaction_details")
public class POSTransactionDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_header_id", nullable = false, unique = true)
    private TransactionHeader transactionHeader;

    // POS specific fields
    @Column(name = "card_number_masked")
    private String cardNumberMasked;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "terminal_id")
    private String terminalId;

    @Column(name = "terminal_type")
    private String terminalType;

    @Column(name = "pos_entry_mode")
    private String posEntryMode;

    @Column(name = "track_data_encrypted", columnDefinition = "TEXT")
    private String trackDataEncrypted;

    @Column(name = "emv_data", columnDefinition = "TEXT")
    private String emvData;

    @Column(name = "pin_verified")
    private Boolean pinVerified;

    // ISO8583 specific fields
    @Column(length = 6)
    private String stan;

    @Column(length = 12)
    private String rrn;

    @Column(name = "authorization_code", length = 6)
    private String authorizationCode;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(length = 4)
    private String mti;

    // Additional POS fields
    @Column(name = "cashback_amount")
    private BigDecimal cashbackAmount;

    @Column(name = "tip_amount")
    private BigDecimal tipAmount;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    // Constructors, getters, setters
    public POSTransactionDetails() {}

    // Getters and setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TransactionHeader getTransactionHeader() { return transactionHeader; }
    public void setTransactionHeader(TransactionHeader transactionHeader) { this.transactionHeader = transactionHeader; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }


    public String getCardNumberMasked() {
        return cardNumberMasked;
    }

    public void setCardNumberMasked(String cardNumberMasked) {
        this.cardNumberMasked = cardNumberMasked;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    public void setPosEntryMode(String posEntryMode) {
        this.posEntryMode = posEntryMode;
    }

    public String getTrackDataEncrypted() {
        return trackDataEncrypted;
    }

    public void setTrackDataEncrypted(String trackDataEncrypted) {
        this.trackDataEncrypted = trackDataEncrypted;
    }

    public String getEmvData() {
        return emvData;
    }

    public void setEmvData(String emvData) {
        this.emvData = emvData;
    }

    public Boolean getPinVerified() {
        return pinVerified;
    }

    public void setPinVerified(Boolean pinVerified) {
        this.pinVerified = pinVerified;
    }

    public String getStan() {
        return stan;
    }

    public void setStan(String stan) {
        this.stan = stan;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getMti() {
        return mti;
    }

    public void setMti(String mti) {
        this.mti = mti;
    }

    public BigDecimal getCashbackAmount() {
        return cashbackAmount;
    }

    public void setCashbackAmount(BigDecimal cashbackAmount) {
        this.cashbackAmount = cashbackAmount;
    }

    public BigDecimal getTipAmount() {
        return tipAmount;
    }

    public void setTipAmount(BigDecimal tipAmount) {
        this.tipAmount = tipAmount;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
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
}

