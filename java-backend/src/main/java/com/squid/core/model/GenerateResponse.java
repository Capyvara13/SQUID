package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GenerateResponse {
    @JsonProperty("ciphertext")
    private String ciphertext;

    @JsonProperty("merkle_root")
    private String merkleRoot;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("seed_model_hash")
    private String seedModelHash;

    @JsonProperty("model_hash")
    private String modelHash;

    @JsonProperty("timestamp")
    private String timestamp;

    public GenerateResponse() {}

    public GenerateResponse(String ciphertext, String merkleRoot, String signature,
                           String seedModelHash, String modelHash, String timestamp) {
        this.ciphertext = ciphertext;
        this.merkleRoot = merkleRoot;
        this.signature = signature;
        this.seedModelHash = seedModelHash;
        this.modelHash = modelHash;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }

    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getSeedModelHash() { return seedModelHash; }
    public void setSeedModelHash(String seedModelHash) { this.seedModelHash = seedModelHash; }

    public String getModelHash() { return modelHash; }
    public void setModelHash(String modelHash) { this.modelHash = modelHash; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
