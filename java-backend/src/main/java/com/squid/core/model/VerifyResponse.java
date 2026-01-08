package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyResponse {
    @JsonProperty("valid")
    private boolean valid;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("timestamp")
    private String timestamp;

    public VerifyResponse() {}

    public VerifyResponse(boolean valid, String reason, String timestamp) {
        this.valid = valid;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
