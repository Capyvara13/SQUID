package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class VerifyRequest {
    @NotBlank
    @JsonProperty("token")
    private String token;

    @NotBlank
    @JsonProperty("proof")
    private String proof;

    @JsonProperty("merkle_root")
    private String merkleRoot;

    @JsonProperty("signature")
    private String signature;

    public VerifyRequest() {}

    public VerifyRequest(String token, String proof, String merkleRoot, String signature) {
        this.token = token;
        this.proof = proof;
        this.merkleRoot = merkleRoot;
        this.signature = signature;
    }

    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getProof() { return proof; }
    public void setProof(String proof) { this.proof = proof; }

    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
