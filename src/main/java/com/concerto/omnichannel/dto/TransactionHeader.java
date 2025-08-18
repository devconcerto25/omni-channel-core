package com.concerto.omnichannel.dto;

import java.time.LocalDateTime;

public class TransactionHeader {
    private Long id;
    private String channel;
    private String operation;
    private String status;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;

    // --- Constructors ---
    public TransactionHeader() {}

    public TransactionHeader(Long id, String channel, String operation, String status,
                             LocalDateTime receivedAt, LocalDateTime completedAt) {
        this.id = id;
        this.channel = channel;
        this.operation = operation;
        this.status = status;
        this.receivedAt = receivedAt;
        this.completedAt = completedAt;
    }

    // --- Getters & Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

