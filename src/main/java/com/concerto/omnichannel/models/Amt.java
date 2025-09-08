package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Amt {

    @XmlAttribute(name = "amt")
    private String amount;

    @XmlAttribute(name = "custConvFee")
    private String custConvFee;

    @XmlAttribute(name = "currency")
    private String currency;

    // Constructors
    public Amt() {}

    // Getters and Setters
    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCustConvFee() {
        return custConvFee;
    }

    public void setCustConvFee(String custConvFee) {
        this.custConvFee = custConvFee;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
