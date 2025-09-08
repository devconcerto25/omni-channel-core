package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "BillFetchResponse", namespace = "http://bbps.org/schema")
@XmlAccessorType(XmlAccessType.FIELD)
public class BillFetchResponse {

    @XmlElement(name = "Head")
    private BBPSHead head;

    @XmlElement(name = "Analytics")
    private Analytics analytics;

    @XmlElement(name = "Txn")
    private Transaction transaction;

    @XmlElement(name = "BillerResponse")
    private BillerResponse billerResponse;

    @XmlElement(name = "Reason")
    private Reason reason;

    // Constructors
    public BillFetchResponse() {}

    // Getters and Setters
    public BBPSHead getHead() {
        return head;
    }

    public void setHead(BBPSHead head) {
        this.head = head;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public void setAnalytics(Analytics analytics) {
        this.analytics = analytics;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public BillerResponse getBillerResponse() {
        return billerResponse;
    }

    public void setBillerResponse(BillerResponse billerResponse) {
        this.billerResponse = billerResponse;
    }

    public Reason getReason() {
        return reason;
    }

    public void setReason(Reason reason) {
        this.reason = reason;
    }
}
