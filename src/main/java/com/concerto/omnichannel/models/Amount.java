package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Amount {

    @XmlElement(name = "Amt")
    private Amt amt;

    // Constructors
    public Amount() {}

    // Getters and Setters
    public Amt getAmt() {
        return amt;
    }

    public void setAmt(Amt amt) {
        this.amt = amt;
    }
}

