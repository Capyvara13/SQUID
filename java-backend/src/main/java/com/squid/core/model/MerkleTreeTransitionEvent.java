package com.squid.core.model;

import java.time.Instant;

/**
 * Merkle Tree transition event for auditing changes
 */
public class MerkleTreeTransitionEvent {
    private String id;
    private String eventType;
    private Instant timestamp;
    private String triggerReason;
    private String previousRootHash;
    private String newRootHash;
    private int nodeCount;
    private String details;
    private int leafChangedCount;

    public MerkleTreeTransitionEvent() {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public MerkleTreeTransitionEvent(String eventType, String triggerReason, 
                                     String previousRoot, String newRoot) {
        this();
        this.eventType = eventType;
        this.triggerReason = triggerReason;
        this.previousRootHash = previousRoot;
        this.newRootHash = newRoot;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getPreviousRootHash() {
        return previousRootHash;
    }

    public void setPreviousRootHash(String previousRootHash) {
        this.previousRootHash = previousRootHash;
    }

    public String getNewRootHash() {
        return newRootHash;
    }

    public void setNewRootHash(String newRootHash) {
        this.newRootHash = newRootHash;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public int getLeafChangedCount() {
        return leafChangedCount;
    }

    public void setLeafChangedCount(int leafChangedCount) {
        this.leafChangedCount = leafChangedCount;
    }
}
