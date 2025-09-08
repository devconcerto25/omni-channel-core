package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "BillValidationRequest", namespace = "http://bbps.org/schema")
@XmlAccessorType(XmlAccessType.FIELD)
public class BillValidationRequest {

    @XmlElement(name = "Head")
    private BBPSHead head;

    @XmlElement(name = "Analytics")
    private Analytics analytics;

    @XmlElement(name = "Txn")
    private Transaction transaction;

    @XmlElement(name = "Customer")
    private Customer customer;

    @XmlElement(name = "Agent")
    private Agent agent;

    @XmlElement(name = "BillDetails")
    private BillDetails billDetails;

    @XmlElement(name = "ValidationParams")
    private ValidationParams validationParams;

    // Constructors
    public BillValidationRequest() {}

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

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public BillDetails getBillDetails() {
        return billDetails;
    }

    public void setBillDetails(BillDetails billDetails) {
        this.billDetails = billDetails;
    }

    public ValidationParams getValidationParams() {
        return validationParams;
    }

    public void setValidationParams(ValidationParams validationParams) {
        this.validationParams = validationParams;
    }
}
