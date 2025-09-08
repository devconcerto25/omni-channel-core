package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class StatusInfo {

    @XmlAttribute(name = "status")
    private String status;

    @XmlAttribute(name = "statusTs")
    private String statusTs;

    @XmlElement(name = "StatusDetails")
    private StatusDetails statusDetails;

    // Constructors
    public StatusInfo() {}

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusTs() {
        return statusTs;
    }

    public void setStatusTs(String statusTs) {
        this.statusTs = statusTs;
    }

    public StatusDetails getStatusDetails() {
        return statusDetails;
    }

    public void setStatusDetails(StatusDetails statusDetails) {
        this.statusDetails = statusDetails;
    }
}

