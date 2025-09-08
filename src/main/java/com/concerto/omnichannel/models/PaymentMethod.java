package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentMethod {

    @XmlAttribute(name = "quickPay")
    private String quickPay;

    @XmlAttribute(name = "splitPay")
    private String splitPay;

    @XmlAttribute(name = "offusPay")
    private String oFFUSPay;

    @XmlAttribute(name = "paymentMode")
    private String paymentMode;

    // Constructors
    public PaymentMethod() {}

    // Getters and Setters
    public String getQuickPay() {
        return quickPay;
    }

    public void setQuickPay(String quickPay) {
        this.quickPay = quickPay;
    }

    public String getSplitPay() {
        return splitPay;
    }

    public void setSplitPay(String splitPay) {
        this.splitPay = splitPay;
    }

    public String getOFFUSPay() {
        return oFFUSPay;
    }

    public void setOFFUSPay(String oFFUSPay) {
        this.oFFUSPay = oFFUSPay;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }
}
