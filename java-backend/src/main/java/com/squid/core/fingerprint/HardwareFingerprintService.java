package com.squid.core.fingerprint;

import com.squid.core.crypto.HardwareEntropy;
import com.squid.core.crypto.HardwareTriggers;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Operationalizes the low-level fingerprinting primitives into a single
 * confidence score and mode, without ever exposing raw fingerprint data.
 */
@Service
public class HardwareFingerprintService {

    private final MicroarchitecturalFingerprint micro;
    private final TemporalFingerprint temporal;
    private final HardwareTriggers triggers;
    private final HardwareEntropy entropy;

    private final byte[] baselineSignatureHash;
    private final double baselineMeanLoopNs;

    public HardwareFingerprintService() {
        this.micro = new MicroarchitecturalFingerprint();
        this.temporal = new TemporalFingerprint();
        this.triggers = new HardwareTriggers();
        this.entropy = new HardwareEntropy();

        // Establish a coarse baseline at startup (FULL mode)
        HardwareSignature sig = micro.generateSignature();
        this.baselineSignatureHash = sig.getSignatureHash();

        FingerprintVector vec = temporal.generateFingerprint();
        this.baselineMeanLoopNs = mean(vec.getExecutionTimes());
    }

    public FingerprintSnapshot capture(FingerprintMode mode) {
        long now = System.currentTimeMillis();

        boolean virt = triggers.detectVirtualization();
        double variability = triggers.getExecutionVariability();

        FingerprintVector fpVec = temporal.generateFingerprint();
        double meanNs = mean(fpVec.getExecutionTimes());

        // Relative drift in temporal fingerprint
        double base = baselineMeanLoopNs <= 0.0 ? 1.0 : baselineMeanLoopNs;
        double drift = Math.abs(meanNs - baselineMeanLoopNs) / base;

        // Map drift + virtualization into confidence in [0,1]
        double score = 1.0;
        // penalize drift: 0.0 <= drift
        score -= Math.min(0.8, drift * 2.0);
        if (virt) {
            score *= 0.4;
        }
        // penalize high variability slightly
        score -= Math.min(0.2, variability * 0.2);
        score = Math.max(0.0, Math.min(1.0, score));

        return new FingerprintSnapshot(mode, score, virt, variability, now);
    }

    private double mean(List<Long> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Long v : values) {
            if (v != null) sum += v;
        }
        return sum / values.size();
    }
}