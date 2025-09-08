package com.concerto.omnichannel.models;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentInformation {

    @XmlElement(name = "Tag")
    private List<Tag> tags;

    public PaymentInformation() {
        this.tags = new ArrayList<>();
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public void addTag(String name, String value) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setValue(value);
        tags.add(tag);
    }

    public String getTagValue(String tagName) {
        if (tags != null) {
            return tags.stream()
                    .filter(tag -> tagName.equals(tag.getName()))
                    .map(Tag::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
