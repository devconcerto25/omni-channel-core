package com.concerto.omnichannel.model;

public class STANStatistics {
    private int activeFutures;
    private int totalTerminals;
    private int pendingRequests;

    public STANStatistics(int activeFutures, int totalTerminals, int pendingRequests) {
        this.activeFutures = activeFutures;
        this.totalTerminals = totalTerminals;
        this.pendingRequests = pendingRequests;
    }

    // Getters
    public int getActiveFutures() { return activeFutures; }
    public int getTotalTerminals() { return totalTerminals; }
    public int getPendingRequests() { return pendingRequests; }
}
