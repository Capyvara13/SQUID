package com.squid.core.model;

import java.time.Instant;

/**
 * Audit log entry for encrypted data access
 */
public class AuditLogEntry {
    private String id;
    private String dataHash;
    private String action;
    private Instant timestamp;
    private String user;
    private String ipAddress;
    private String details;

    public AuditLogEntry() {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public AuditLogEntry(String dataHash, String action, String user) {
        this();
        this.dataHash = dataHash;
        this.action = action;
        this.user = user;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
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

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
