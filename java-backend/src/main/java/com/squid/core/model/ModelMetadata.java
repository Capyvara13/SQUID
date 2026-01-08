package com.squid.core.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata for a model version including metrics and architecture details
 */
public class ModelMetadata {
    private String id;
    private String version;
    private Instant createdAt;
    private Instant trainedAt;
    private String hash;
    private String architecture;
    private Map<String, Double> metrics;
    private String description;
    private boolean isActive;
    
    public ModelMetadata() {
        this.metrics = new HashMap<>();
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }
    
    public ModelMetadata(String version, Instant trainedAt, String hash) {
        this();
        this.version = version;
        this.trainedAt = trainedAt;
        this.hash = hash;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getTrainedAt() {
        return trainedAt;
    }

    public void setTrainedAt(Instant trainedAt) {
        this.trainedAt = trainedAt;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Double> metrics) {
        this.metrics = metrics;
    }

    public void addMetric(String key, Double value) {
        this.metrics.put(key, value);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
