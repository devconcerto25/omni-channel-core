package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BillerResponse {

    @XmlAttribute(name = "ts")
    private String timestamp;

    @XmlElement(name = "CustomerName")
    private String customerName;

    @XmlElement(name = "Amount")
    private String amount; // Amount in paise

    @XmlElement(name = "DueDate")
    private String dueDate;

    @XmlElement(name = "BillNumber")
    private String billNumber;

    @XmlElement(name = "BillPeriod")
    private String billPeriod;

    @XmlElement(name = "BillDate")
    private String billDate;

    @XmlElement(name = "AcceptPartPay")
    private String acceptPartPay;

    @XmlElement(name = "AcceptAdvPay")
    private String acceptAdvPay;

    @XmlElement(name = "CustConvFee")
    private String custConvFee;

    @XmlElement(name = "ExactPaymentRequired")
    private String exactPaymentRequired;

    @XmlElement(name = "SplitPay")
    private String splitPay;

    @XmlElement(name = "AdditionalInfo")
    private AdditionalInfo additionalInfo;

    // Constructors
    public BillerResponse() {}

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public String getBillPeriod() {
        return billPeriod;
    }

    public void setBillPeriod(String billPeriod) {
        this.billPeriod = billPeriod;
    }

    public String getBillDate() {
        return billDate;
    }

    public void setBillDate(String billDate) {
        this.billDate = billDate;
    }

    public String getAcceptPartPay() {
        return acceptPartPay;
    }

    public void setAcceptPartPay(String acceptPartPay) {
        this.acceptPartPay = acceptPartPay;
    }

    public String getAcceptAdvPay() {
        return acceptAdvPay;
    }

    public void setAcceptAdvPay(String acceptAdvPay) {
        this.acceptAdvPay = acceptAdvPay;
    }

    public String getCustConvFee() {
        return custConvFee;
    }

    public void setCustConvFee(String custConvFee) {
        this.custConvFee = custConvFee;
    }

    public String getExactPaymentRequired() {
        return exactPaymentRequired;
    }

    public void setExactPaymentRequired(String exactPaymentRequired) {
        this.exactPaymentRequired = exactPaymentRequired;
    }

    public String getSplitPay() {
        return splitPay;
    }

    public void setSplitPay(String splitPay) {
        this.splitPay = splitPay;
    }

    public AdditionalInfo getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(AdditionalInfo additionalInfo) {
        this.additionalInfo = additionalInfo;
    }
}
