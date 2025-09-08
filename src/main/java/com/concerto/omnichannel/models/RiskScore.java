package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class RiskScore {

    @XmlAttribute(name = "provider")
    private String provider;

    @XmlAttribute(name = "type")
    private String type;

    @XmlAttribute(name = "value")
    private String value;

    // Constructors
    public RiskScore() {}

    public RiskScore(String provider, String type, String value) {
        this.provider = provider;
        this.type = type;
        this.value = value;
    }

    // Getters and Setters
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}