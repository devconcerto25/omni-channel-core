package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Reason {

    @XmlAttribute(name = "responseCode")
    private String responseCode;

    @XmlAttribute(name = "responseReason")
    private String responseReason;

    @XmlAttribute(name = "complianceReason")
    private String complianceReason;

    @XmlAttribute(name = "approvalRefNum")
    private String approvalRefNum;

    @XmlAttribute(name = "txnReferenceId")
    private String txnReferenceId;

    @XmlAttribute(name = "responseTs")
    private String responseTs;

    // Constructors
    public Reason() {}

    // Getters and Setters
    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseReason() {
        return responseReason;
    }

    public void setResponseReason(String responseReason) {
        this.responseReason = responseReason;
    }

    public String getComplianceReason() {
        return complianceReason;
    }

    public void setComplianceReason(String complianceReason) {
        this.complianceReason = complianceReason;
    }

    public String getApprovalRefNum() {
        return approvalRefNum;
    }

    public void setApprovalRefNum(String approvalRefNum) {
        this.approvalRefNum = approvalRefNum;
    }

    public String getTxnReferenceId() {
        return txnReferenceId;
    }

    public void setTxnReferenceId(String txnReferenceId) {
        this.txnReferenceId = txnReferenceId;
    }

    public String getResponseTs() {
        return responseTs;
    }

    public void setResponseTs(String responseTs) {
        this.responseTs = responseTs;
    }

    // Helper method to check if response is successful
    public boolean isSuccess() {
        return "000".equals(responseCode);
    }
}

