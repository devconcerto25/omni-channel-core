package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "BillPaymentResponse", namespace = "http://bbps.org/schema")
@XmlAccessorType(XmlAccessType.FIELD)
public class BillPaymentResponse {

    @XmlElement(name = "Head")
    private BBPSHead head;

    @XmlElement(name = "Analytics")
    private Analytics analytics;

    @XmlElement(name = "Txn")
    private Transaction txn;

    @XmlElement(name = "BillerResponse")
    private BillerResponse billerResponse;

    @XmlElement(name = "Reason")
    private Reason reason;

    @XmlElement(name = "PaymentDetails")
    private PaymentDetails paymentDetails;

    // Constructors
    public BillPaymentResponse() {}

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

    public Transaction getTxn() {
        return txn;
    }

    public void setTxn(Transaction txn) {
        this.txn = txn;
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

    public PaymentDetails getPaymentDetails() {
        return paymentDetails;
    }

    public void setPaymentDetails(PaymentDetails paymentDetails) {
        this.paymentDetails = paymentDetails;
    }
}

