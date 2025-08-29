package com.concerto.omnichannel.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name = "upi_transaction_details")
public class UPITransactionDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_header_id", nullable = false, unique = true)
    private TransactionHeader transactionHeader;

    // UPI specific fields
    @Column(name = "payer_vpa")
    private String payerVPA;

    @Column(name = "payee_vpa")
    private String payeeVPA;

    @Column(name = "payer_account_number")
    private String payerAccountNumber;

    @Column(name = "payee_account_number")
    private String payeeAccountNumber;

    @Column(name = "payer_ifsc")
    private String payerIFSC;

    @Column(name = "payee_ifsc")
    private String payeeIFSC;

    // UPI identifiers
    @Column(name = "upi_transaction_id")
    private String upiTransactionId;

    @Column(name = "customer_reference")
    private String customerReference;

    @Column(name = "merchant_transaction_id")
    private String merchantTransactionId;

    // UPI specific fields
    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "upi_request_id")
    private String upiRequestId;

    // Additional UPI data
    private String note;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Column(name = "merchant_category_code")
    private String merchantCategoryCode;

    @Column(name = "sub_merchant_id")
    private String subMerchantId;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    // Constructors, getters, setters...
    public UPITransactionDetails() {}

    // Getters and setters...
    public String getPayerVPA() { return payerVPA; }
    public void setPayerVPA(String payerVPA) { this.payerVPA = payerVPA; }

    public String getUpiTransactionId() { return upiTransactionId; }
    public void setUpiTransactionId(String upiTransactionId) { this.upiTransactionId = upiTransactionId; }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionHeader getTransactionHeader() {
        return transactionHeader;
    }

    public void setTransactionHeader(TransactionHeader transactionHeader) {
        this.transactionHeader = transactionHeader;
    }

    public String getPayeeVPA() {
        return payeeVPA;
    }

    public void setPayeeVPA(String payeeVPA) {
        this.payeeVPA = payeeVPA;
    }

    public String getPayerAccountNumber() {
        return payerAccountNumber;
    }

    public void setPayerAccountNumber(String payerAccountNumber) {
        this.payerAccountNumber = payerAccountNumber;
    }

    public String getPayeeAccountNumber() {
        return payeeAccountNumber;
    }

    public void setPayeeAccountNumber(String payeeAccountNumber) {
        this.payeeAccountNumber = payeeAccountNumber;
    }

    public String getPayerIFSC() {
        return payerIFSC;
    }

    public void setPayerIFSC(String payerIFSC) {
        this.payerIFSC = payerIFSC;
    }

    public String getPayeeIFSC() {
        return payeeIFSC;
    }

    public void setPayeeIFSC(String payeeIFSC) {
        this.payeeIFSC = payeeIFSC;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getMerchantTransactionId() {
        return merchantTransactionId;
    }

    public void setMerchantTransactionId(String merchantTransactionId) {
        this.merchantTransactionId = merchantTransactionId;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getUpiRequestId() {
        return upiRequestId;
    }

    public void setUpiRequestId(String upiRequestId) {
        this.upiRequestId = upiRequestId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }

    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    public void setMerchantCategoryCode(String merchantCategoryCode) {
        this.merchantCategoryCode = merchantCategoryCode;
    }

    public String getSubMerchantId() {
        return subMerchantId;
    }

    public void setSubMerchantId(String subMerchantId) {
        this.subMerchantId = subMerchantId;
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

    public void setPaymentStatus(String string) {
        //todo
    }
}

