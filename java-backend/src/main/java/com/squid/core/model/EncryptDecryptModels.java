package com.squid.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTOs for the explicit Encrypt / Decrypt pipeline.
 *
 * These are intentionally minimal and do not expose any key material.
 */
public class EncryptDecryptModels {

    public static class EncryptRequest {
        @JsonProperty("data")
        private String data;

        @JsonProperty("security_profile")
        private SecurityProfile securityProfile = SecurityProfile.PRODUCTION;

        @JsonProperty("options")
        private EncryptOptions options = new EncryptOptions();

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public SecurityProfile getSecurityProfile() {
            return securityProfile;
        }

        public void setSecurityProfile(SecurityProfile securityProfile) {
            this.securityProfile = securityProfile;
        }

        public EncryptOptions getOptions() {
            return options;
        }

        public void setOptions(EncryptOptions options) {
            this.options = options;
        }
    }

    public static class EncryptOptions {
        @JsonProperty("hardware_bound")
        private boolean hardwareBound = true;

        @JsonProperty("quantum_mode")
        private boolean quantumMode = true;

        @JsonProperty("audit_level")
        private String auditLevel = "FULL"; // FULL | LIMITED

        public boolean isHardwareBound() {
            return hardwareBound;
        }

        public void setHardwareBound(boolean hardwareBound) {
            this.hardwareBound = hardwareBound;
        }

        public boolean isQuantumMode() {
            return quantumMode;
        }

        public void setQuantumMode(boolean quantumMode) {
            this.quantumMode = quantumMode;
        }

        public String getAuditLevel() {
            return auditLevel;
        }

        public void setAuditLevel(String auditLevel) {
            this.auditLevel = auditLevel;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EncryptResponse {
        @JsonProperty("ciphertext")
        private String ciphertext;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        public String getCiphertext() {
            return ciphertext;
        }

        public void setCiphertext(String ciphertext) {
            this.ciphertext = ciphertext;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public static class DecryptRequest {
        @JsonProperty("ciphertext")
        private String ciphertext;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        @JsonProperty("context")
        private DecryptContext context;

        public String getCiphertext() {
            return ciphertext;
        }

        public void setCiphertext(String ciphertext) {
            this.ciphertext = ciphertext;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public DecryptContext getContext() {
            return context;
        }

        public void setContext(DecryptContext context) {
            this.context = context;
        }
    }

    public static class DecryptContext {
        @JsonProperty("agent_id")
        private String agentId;

        @JsonProperty("timestamp")
        private String timestamp;

        @JsonProperty("hardware_state")
        private Map<String, Object> hardwareState;

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Map<String, Object> getHardwareState() {
            return hardwareState;
        }

        public void setHardwareState(Map<String, Object> hardwareState) {
            this.hardwareState = hardwareState;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DecryptResponse {
        @JsonProperty("authorized")
        private boolean authorized;

        @JsonProperty("plaintext")
        private String plaintext;

        @JsonProperty("failure_reason")
        private String failureReason;

        @JsonProperty("fingerprint_confidence")
        private Double fingerprintConfidence;

        @JsonProperty("merkle_verified")
        private Boolean merkleVerified;

        @JsonProperty("ai_decision")
        private Map<String, Object> aiDecision;

        public boolean isAuthorized() {
            return authorized;
        }

        public void setAuthorized(boolean authorized) {
            this.authorized = authorized;
        }

        public String getPlaintext() {
            return plaintext;
        }

        public void setPlaintext(String plaintext) {
            this.plaintext = plaintext;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public Double getFingerprintConfidence() {
            return fingerprintConfidence;
        }

        public void setFingerprintConfidence(Double fingerprintConfidence) {
            this.fingerprintConfidence = fingerprintConfidence;
        }

        public Boolean getMerkleVerified() {
            return merkleVerified;
        }

        public void setMerkleVerified(Boolean merkleVerified) {
            this.merkleVerified = merkleVerified;
        }

        public Map<String, Object> getAiDecision() {
            return aiDecision;
        }

        public void setAiDecision(Map<String, Object> aiDecision) {
            this.aiDecision = aiDecision;
        }
    }
}