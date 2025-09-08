package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlElement;

public class BillDetails {
    @XmlElement(name = "Biller")
    private Biller biller;

    @XmlElement(name = "CustomerParams")
    private CustomerParams customerParams;

    // Constructors, getters, setters

    public Biller getBiller() {
        return biller;
    }

    public void setBiller(Biller biller) {
        this.biller = biller;
    }

    public CustomerParams getCustomerParams() {
        return customerParams;
    }

    public void setCustomerParams(CustomerParams customerParams) {
        this.customerParams = customerParams;
    }
}
