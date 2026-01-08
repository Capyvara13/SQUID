package com.squid.core.model;

import java.time.Instant;

/**
 * Represents a model history entry with transition information
 */
public class ModelHistoryEntry {
    private String id;
    private String modelVersion;
    private String action;
    private Instant timestamp;
    private String reason;
    private String initiator;
    private String details;

    public ModelHistoryEntry() {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public ModelHistoryEntry(String modelVersion, String action, String reason) {
        this();
        this.modelVersion = modelVersion;
        this.action = action;
        this.reason = reason;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
