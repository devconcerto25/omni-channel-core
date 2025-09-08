package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class OriginalTxn {

    @XmlAttribute(name = "ts")
    private String timestamp;

    @XmlAttribute(name = "msgId")
    private String msgId;

    @XmlAttribute(name = "type")
    private String type;

    @XmlAttribute(name = "txnReferenceId")
    private String txnReferenceId;

    @XmlAttribute(name = "paymentRefId")
    private String paymentRefId;

    @XmlElement(name = "Amount")
    private Amount amount;

    @XmlElement(name = "BillerResponse")
    private BillerResponse billerResponse;

    // Constructors
    public OriginalTxn() {}

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTxnReferenceId() {
        return txnReferenceId;
    }

    public void setTxnReferenceId(String txnReferenceId) {
        this.txnReferenceId = txnReferenceId;
    }

    public String getPaymentRefId() {
        return paymentRefId;
    }

    public void setPaymentRefId(String paymentRefId) {
        this.paymentRefId = paymentRefId;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public BillerResponse getBillerResponse() {
        return billerResponse;
    }

    public void setBillerResponse(BillerResponse billerResponse) {
        this.billerResponse = billerResponse;
    }
}

