package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TestVector {
    @JsonProperty("id")
    private String id;

    @JsonProperty("input")
    private String input;

    @JsonProperty("root_key_hex")
    private String rootKeyHex;

    @JsonProperty("leaves")
    private List<String> leaves;

    @JsonProperty("merkle_root_hex")
    private String merkleRootHex;

    @JsonProperty("signature_hex")
    private String signatureHex;

    @JsonProperty("seed_model_hash")
    private String seedModelHash;

    @JsonProperty("actions")
    private List<String> actions;

    @JsonProperty("sr")
    private Double sr;

    @JsonProperty("c")
    private Double c;

    @JsonProperty("params")
    private com.squid.core.model.GenerateRequest.BranchingParams params;

    @JsonProperty("model_hash")
    private String modelHash;

    public TestVector() {}

    public TestVector(String id, String input, String rootKeyHex, List<String> leaves,
                     String merkleRootHex, String signatureHex, String seedModelHash) {
        this.id = id;
        this.input = input;
        this.rootKeyHex = rootKeyHex;
        this.leaves = leaves;
        this.merkleRootHex = merkleRootHex;
        this.signatureHex = signatureHex;
        this.seedModelHash = seedModelHash;
    }

    public TestVector(String id, String input, String rootKeyHex, List<String> leaves,
                     String merkleRootHex, String signatureHex, String seedModelHash,
                     List<String> actions, Double sr, Double c, String modelHash) {
        this.id = id;
        this.input = input;
        this.rootKeyHex = rootKeyHex;
        this.leaves = leaves;
        this.merkleRootHex = merkleRootHex;
        this.signatureHex = signatureHex;
        this.seedModelHash = seedModelHash;
        this.actions = actions;
        this.sr = sr;
        this.c = c;
        this.modelHash = modelHash;
    }

    // Optional params setter
    public com.squid.core.model.GenerateRequest.BranchingParams getParams() { return params; }
    public void setParams(com.squid.core.model.GenerateRequest.BranchingParams params) { this.params = params; }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getRootKeyHex() { return rootKeyHex; }
    public void setRootKeyHex(String rootKeyHex) { this.rootKeyHex = rootKeyHex; }

    public List<String> getLeaves() { return leaves; }
    public void setLeaves(List<String> leaves) { this.leaves = leaves; }

    public String getMerkleRootHex() { return merkleRootHex; }
    public void setMerkleRootHex(String merkleRootHex) { this.merkleRootHex = merkleRootHex; }

    public String getSignatureHex() { return signatureHex; }
    public void setSignatureHex(String signatureHex) { this.signatureHex = signatureHex; }

    public String getSeedModelHash() { return seedModelHash; }
    public void setSeedModelHash(String seedModelHash) { this.seedModelHash = seedModelHash; }

    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }

    public Double getSr() { return sr; }
    public void setSr(Double sr) { this.sr = sr; }

    public Double getC() { return c; }
    public void setC(Double c) { this.c = c; }

    public String getModelHash() { return modelHash; }
    public void setModelHash(String modelHash) { this.modelHash = modelHash; }
}
