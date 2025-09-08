package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

public class Transaction {
    @XmlAttribute
    private String ts;

    @XmlAttribute
    private String msgId;

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String txnReferenceId;

    @XmlAttribute
    private String paymentRefId;

    @XmlElement(name = "RiskScores")
    private RiskScores riskScores;

    @XmlAttribute
    private String xchangeId;

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
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

    public RiskScores getRiskScores() {
        return riskScores;
    }

    public void setRiskScores(RiskScores riskScores) {
        this.riskScores = riskScores;
    }


    public void setXchangeId(String number) {
        xchangeId = number;
    }

    public String getXchangeId(){
        return this.xchangeId;
    }
}
