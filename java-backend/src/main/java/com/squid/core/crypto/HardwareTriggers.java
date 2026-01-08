package com.squid.core.crypto;

import java.util.Locale;

/**
 * HardwareTriggers exposes higher-level signals derived from timing and
 * environment inspection. These are heuristics only and are designed to be
 * safe fallbacks for the research plan's anti-VM and variability triggers.
 */
public class HardwareTriggers {

    private final HardwareEntropy entropy = new HardwareEntropy();

    /**
     * Heuristic virtualization detector based on VM-related strings in
     * JVM and OS properties.
     */
    public boolean detectVirtualization() {
        String vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);

        String combined = vmName + " " + osName + " " + vendor;
        return combined.contains("vmware")
                || combined.contains("virtualbox")
                || combined.contains("kvm")
                || combined.contains("hyper-v")
                || combined.contains("microsoft hyper-v")
                || combined.contains("qemu");
    }

    /**
     * Execution variability is estimated as a normalized standard deviation
     * of several nanoTime measurements on a simple loop.
     */
    public double getExecutionVariability() {
        final int samples = 32;
        long[] values = new long[samples];

        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            // tiny deterministic workload
            for (int j = 0; j < 10_000; j++) {
                // no-op loop
            }
            long end = System.nanoTime();
            values[i] = Math.max(1L, end - start);
        }

        double mean = 0.0;
        for (long v : values) {
            mean += v;
        }
        mean /= samples;
        if (mean <= 0.0) {
            return 0.0;
        }

        double var = 0.0;
        for (long v : values) {
            double d = v - mean;
            var += d * d;
        }
        var /= samples;
        double std = Math.sqrt(var);

        // Normalize by mean and squash to [0,1]
        double ratio = std / mean;
        return Math.tanh(ratio);
    }

    /**
     * Simple cache timing probe: returns elapsed times for sequential
     * access over an array sized to exceed typical L1 cache.
     */
    public long[] getCacheTimings() {
        final int size = 64 * 1024; // 64 KiB of ints
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = i;
        }

        long[] timings = new long[3];
        for (int pass = 0; pass < timings.length; pass++) {
            long start = System.nanoTime();
            int acc = 0;
            for (int i = 0; i < size; i += 16) {
                acc += arr[i];
            }
            long end = System.nanoTime();
            // ensure acc is used
            if (acc == -1) {
                timings[pass] = 0L;
            } else {
                timings[pass] = Math.max(1L, end - start);
            }
        }
        return timings;
    }
}
