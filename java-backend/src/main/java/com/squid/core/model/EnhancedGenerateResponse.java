package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public class EnhancedGenerateResponse {
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

    @JsonProperty("analysis")
    private AnalysisData analysis;

    public static class AnalysisData {
        @JsonProperty("sr")
        public Double sr;

        @JsonProperty("c")
        public Double c;

        @JsonProperty("total_leaves")
        public Integer totalLeaves;

        @JsonProperty("actions")
        public List<String> actions;

        @JsonProperty("action_distribution")
        public ActionDistribution actionDistribution;

        @JsonProperty("tree_params")
        public TreeParams treeParams;

        @JsonProperty("leaves_detail")
        public List<LeafDetail> leavesDetail;

        public AnalysisData() {}

        public AnalysisData(Double sr, Double c, Integer totalLeaves, List<String> actions,
                           ActionDistribution actionDistribution, TreeParams treeParams,
                           List<LeafDetail> leavesDetail) {
            this.sr = sr;
            this.c = c;
            this.totalLeaves = totalLeaves;
            this.actions = actions;
            this.actionDistribution = actionDistribution;
            this.treeParams = treeParams;
            this.leavesDetail = leavesDetail;
        }
    }

    public static class ActionDistribution {
        @JsonProperty("VALID")
        public Integer valid = 0;

        @JsonProperty("DECOY")
        public Integer decoy = 0;

        @JsonProperty("MUTATE")
        public Integer mutate = 0;

        @JsonProperty("REASSIGN")
        public Integer reassign = 0;

        public ActionDistribution() {}

        public void countAction(String action) {
            switch (action) {
                case "VALID": valid++; break;
                case "DECOY": decoy++; break;
                case "MUTATE": mutate++; break;
                case "REASSIGN": reassign++; break;
            }
        }
    }

    public static class TreeParams {
        @JsonProperty("b")
        public Integer b;

        @JsonProperty("m")
        public Integer m;

        @JsonProperty("t")
        public Integer t;

        public TreeParams() {}

        public TreeParams(Integer b, Integer m, Integer t) {
            this.b = b;
            this.m = m;
            this.t = t;
        }
    }

    public static class LeafDetail {
        @JsonProperty("index")
        public Integer index;

        @JsonProperty("path")
        public List<Integer> path;

        @JsonProperty("action")
        public String action;

        @JsonProperty("entropy")
        public Double entropy;

        public LeafDetail() {}

        public LeafDetail(Integer index, List<Integer> path, String action, Double entropy) {
            this.index = index;
            this.path = path;
            this.action = action;
            this.entropy = entropy;
        }
    }

    public EnhancedGenerateResponse() {}

    public EnhancedGenerateResponse(String ciphertext, String merkleRoot, String signature,
                                   String seedModelHash, String modelHash, String timestamp,
                                   AnalysisData analysis) {
        this.ciphertext = ciphertext;
        this.merkleRoot = merkleRoot;
        this.signature = signature;
        this.seedModelHash = seedModelHash;
        this.modelHash = modelHash;
        this.timestamp = timestamp;
        this.analysis = analysis;
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

    public AnalysisData getAnalysis() { return analysis; }
    public void setAnalysis(AnalysisData analysis) { this.analysis = analysis; }
}
