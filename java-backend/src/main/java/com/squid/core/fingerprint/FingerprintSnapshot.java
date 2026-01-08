package com.squid.core.fingerprint;

/**
 * Immutable snapshot of the current hardware fingerprint state reduced to a
 * safe, operational form (no raw timing vectors or microarchitectural data).
 */
public class FingerprintSnapshot {

    private final FingerprintMode mode;
    private final double confidenceScore; // 0.0 – 1.0
    private final boolean virtualizationSuspected;
    private final double executionVariability;
    private final long capturedAtMillis;

    public FingerprintSnapshot(FingerprintMode mode,
                               double confidenceScore,
                               boolean virtualizationSuspected,
                               double executionVariability,
                               long capturedAtMillis) {
        this.mode = mode;
        this.confidenceScore = confidenceScore;
        this.virtualizationSuspected = virtualizationSuspected;
        this.executionVariability = executionVariability;
        this.capturedAtMillis = capturedAtMillis;
    }

    public FingerprintMode getMode() {
        return mode;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public boolean isVirtualizationSuspected() {
        return virtualizationSuspected;
    }

    public double getExecutionVariability() {
        return executionVariability;
    }

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }
}