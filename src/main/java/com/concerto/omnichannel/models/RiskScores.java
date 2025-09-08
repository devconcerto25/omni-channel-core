package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class RiskScores {

    @XmlElement(name = "Score")
    private List<RiskScore> scores;

    public RiskScores() {
        this.scores = new ArrayList<>();
    }

    public List<RiskScore> getScores() {
        return scores;
    }

    public void setScores(List<RiskScore> scores) {
        this.scores = scores;
    }

    public void addScore(String provider, String type, String value) {
        if (scores == null) {
            scores = new ArrayList<>();
        }
        RiskScore score = new RiskScore();
        score.setProvider(provider);
        score.setType(type);
        score.setValue(value);
        scores.add(score);
    }
}

