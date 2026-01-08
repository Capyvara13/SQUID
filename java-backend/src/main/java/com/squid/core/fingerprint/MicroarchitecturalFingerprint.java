package com.squid.core.fingerprint;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * MicroarchitecturalFingerprint provides coarse-grained information about the
 * host microarchitecture: cache-like characteristics, pipeline hints and
 * speculative execution timing patterns. Values are synthetic but stable for
 * a given environment.
 */
public class MicroarchitecturalFingerprint {

    public int[] getCacheProfile() {
        // Very rough synthetic cache profile (KB) based on max memory.
        long maxMemMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int l1 = 32;
        int l2 = 256;
        int l3 = (int) Math.min(32 * 1024, Math.max(512, maxMemMb / 2));
        return new int[] { l1, l2, l3 };
    }

    public double[] getPipelineCharacteristics() {
        // Synthetic pipeline characteristics (issue width, depth, reorder size)
        return new double[] { 4.0, 14.0, 192.0 };
    }

    public long[] getSpeculativeExecutionPattern() {
        // Simple timing samples around a branch-heavy loop
        long[] samples = new long[8];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            int acc = 0;
            for (int j = 0; j < 50_000; j++) {
                if ((j & 3) == 0) acc++; else acc--;
            }
            long end = System.nanoTime();
            samples[i] = Math.max(1L, end - start);
            if (acc == -1) {
                samples[i] = 0L;
            }
        }
        return samples;
    }

    public HardwareSignature generateSignature() {
        int[] cache = getCacheProfile();
        double[] pipeline = getPipelineCharacteristics();
        long[] spec = getSpeculativeExecutionPattern();

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buf = ByteBuffer.allocate((cache.length + pipeline.length + spec.length) * 8);
            for (int c : cache) buf.putInt(c);
            for (double v : pipeline) buf.putDouble(v);
            for (long s : spec) buf.putLong(s);
            byte[] hash = md.digest(buf.array());
            return new HardwareSignature(cache, pipeline, spec, hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hardware signature", e);
        }
    }
}

class FingerprintVector {
    private final java.util.List<Long> executionTimes;
    private final java.util.List<Double> nopLatencies;
    private final java.util.Map<String, Double> statisticalDeviations;

    public FingerprintVector(java.util.List<Long> executionTimes,
                             java.util.List<Double> nopLatencies,
                             java.util.Map<String, Double> statisticalDeviations) {
        this.executionTimes = new java.util.ArrayList<>(executionTimes);
        this.nopLatencies = new java.util.ArrayList<>(nopLatencies);
        this.statisticalDeviations = new java.util.HashMap<>(statisticalDeviations);
    }

    public java.util.List<Long> getExecutionTimes() { return new java.util.ArrayList<>(executionTimes); }
    public java.util.List<Double> getNopLatencies() { return new java.util.ArrayList<>(nopLatencies); }
    public java.util.Map<String, Double> getStatisticalDeviations() { return new java.util.HashMap<>(statisticalDeviations); }
}

class HardwareSignature {
    private final int[] cacheProfile;
    private final double[] pipelineCharacteristics;
    private final long[] speculativePattern;
    private final byte[] signatureHash;

    public HardwareSignature(int[] cacheProfile, double[] pipelineCharacteristics,
                             long[] speculativePattern, byte[] signatureHash) {
        this.cacheProfile = cacheProfile.clone();
        this.pipelineCharacteristics = pipelineCharacteristics.clone();
        this.speculativePattern = speculativePattern.clone();
        this.signatureHash = signatureHash.clone();
    }

    public int[] getCacheProfile() { return cacheProfile.clone(); }
    public double[] getPipelineCharacteristics() { return pipelineCharacteristics.clone(); }
    public long[] getSpeculativePattern() { return speculativePattern.clone(); }
    public byte[] getSignatureHash() { return signatureHash.clone(); }

    @Override
    public String toString() {
        return "HardwareSignature{" +
                "cache=" + java.util.Arrays.toString(cacheProfile) +
                ", pipeline=" + java.util.Arrays.toString(pipelineCharacteristics) +
                ", spec=" + java.util.Arrays.toString(speculativePattern) +
                '}';
    }
}

enum SecurityMode {
    NORMAL,
    DEFENSIVE
}

class VirtualizationDetector {

    public boolean isHypervisorPresent() {
        String name = System.getProperty("java.vm.name", "").toLowerCase();
        String vendor = System.getProperty("java.vendor", "").toLowerCase();
        String osName = System.getProperty("os.name", "").toLowerCase();
        String combined = name + " " + vendor + " " + osName;
        return combined.contains("vmware") || combined.contains("virtualbox")
                || combined.contains("hyper-v") || combined.contains("kvm")
                || combined.contains("qemu");
    }

    public boolean hasInconsistentTSC() {
        long t1 = System.nanoTime();
        long ms1 = System.currentTimeMillis();
        try { Thread.sleep(10L); } catch (InterruptedException ignored) {}
        long t2 = System.nanoTime();
        long ms2 = System.currentTimeMillis();
        long deltaNs = t2 - t1;
        long deltaMs = ms2 - ms1;
        // Flag large discrepancy between nanoTime and currentTimeMillis
        long expectedNs = deltaMs * 1_000_000L;
        long diff = Math.abs(deltaNs - expectedNs);
        return diff > 50_000_000L; // >50ms discrepancy
    }

    public boolean detectEmulationArtifacts() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        // Very rough heuristic: unusual arch strings may indicate emulation.
        return arch.contains("arm") && System.getProperty("sun.arch.data.model", "").equals("64");
    }

    public SecurityMode getSecurityMode() {
        if (isHypervisorPresent() || hasInconsistentTSC() || detectEmulationArtifacts()) {
            return SecurityMode.DEFENSIVE;
        }
        return SecurityMode.NORMAL;
    }
}
