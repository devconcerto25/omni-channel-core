package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

public class Agent {
    @XmlAttribute
    private String id;

    @XmlElement(name = "Device")
    private Device device;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}
