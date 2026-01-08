package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PublicRootResponse {
    @JsonProperty("merkle_root")
    private String merkleRoot;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("seed_model_hash")
    private String seedModelHash;

    @JsonProperty("model_hash")
    private String modelHash;

    public PublicRootResponse() {}

    public PublicRootResponse(String merkleRoot, String signature, String timestamp,
                             String seedModelHash, String modelHash) {
        this.merkleRoot = merkleRoot;
        this.signature = signature;
        this.timestamp = timestamp;
        this.seedModelHash = seedModelHash;
        this.modelHash = modelHash;
    }

    // Getters and Setters
    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSeedModelHash() { return seedModelHash; }
    public void setSeedModelHash(String seedModelHash) { this.seedModelHash = seedModelHash; }

    public String getModelHash() { return modelHash; }
    public void setModelHash(String modelHash) { this.modelHash = modelHash; }
}
