package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class TxnStatusResp {

    @XmlAttribute(name = "msgId")
    private String msgId;

    @XmlAttribute(name = "txnReferenceId")
    private String txnReferenceId;

    @XmlAttribute(name = "complaintType")
    private String complaintType;

    @XmlElement(name = "OriginalTxn")
    private OriginalTxn originalTxn;

    @XmlElement(name = "StatusInfo")
    private StatusInfo statusInfo;

    // Constructors
    public TxnStatusResp() {}

    // Getters and Setters
    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getTxnReferenceId() {
        return txnReferenceId;
    }

    public void setTxnReferenceId(String txnReferenceId) {
        this.txnReferenceId = txnReferenceId;
    }

    public String getComplaintType() {
        return complaintType;
    }

    public void setComplaintType(String complaintType) {
        this.complaintType = complaintType;
    }

    public OriginalTxn getOriginalTxn() {
        return originalTxn;
    }

    public void setOriginalTxn(OriginalTxn originalTxn) {
        this.originalTxn = originalTxn;
    }

    public StatusInfo getStatusInfo() {
        return statusInfo;
    }

    public void setStatusInfo(StatusInfo statusInfo) {
        this.statusInfo = statusInfo;
    }
}

