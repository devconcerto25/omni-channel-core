package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAttribute;

public class BBPSHead {
    @XmlAttribute
    private String ver = "1.0";

    @XmlAttribute
    private String ts;

    @XmlAttribute
    private String origInst;

    @XmlAttribute
    private String refId;

    @XmlAttribute
    private String siTxn;

    @XmlAttribute
    private String origRefId;

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getOrigInst() {
        return origInst;
    }

    public void setOrigInst(String origInst) {
        this.origInst = origInst;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getSiTxn() {
        return siTxn;
    }

    public void setSiTxn(String siTxn) {
        this.siTxn = siTxn;
    }

    public String getOrigRefId() {
        return origRefId;
    }

    public void setOrigRefId(String origRefId) {
        this.origRefId = origRefId;
    }

    // Constructors, getters, setters
}
