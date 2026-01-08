package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

public class GenerateRequest {
    @NotBlank
    @JsonProperty("version")
    private String version;

    @NotBlank
    @JsonProperty("ts")
    private String timestamp;

    @NotBlank
    @JsonProperty("payload")
    private String payload;

    @JsonProperty("meta")
    private Map<String, Object> meta;

    @JsonProperty("params")
    private BranchingParams params;

    // Constructors
    public GenerateRequest() {}

    public GenerateRequest(String version, String timestamp, String payload, 
                          Map<String, Object> meta, BranchingParams params) {
        this.version = version;
        this.timestamp = timestamp;
        this.payload = payload;
        this.meta = meta;
        this.params = params;
    }

    // Getters and Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public BranchingParams getParams() { return params; }
    public void setParams(BranchingParams params) { this.params = params; }

    public static class BranchingParams {
        @JsonProperty("b")
        private int b = 4; // default branching factor

        @JsonProperty("m")
        private int m = 3; // default depth

        @JsonProperty("t")
        private int t = 128; // default bits per leaf

        public BranchingParams() {}

        public BranchingParams(int b, int m, int t) {
            this.b = b;
            this.m = m;
            this.t = t;
        }

        public int getB() { return b; }
        public void setB(int b) { this.b = b; }

        public int getM() { return m; }
        public void setM(int m) { this.m = m; }

        public int getT() { return t; }
        public void setT(int t) { this.t = t; }
    }
}
