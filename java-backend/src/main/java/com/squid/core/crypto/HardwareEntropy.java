package com.squid.core.crypto;

import java.util.concurrent.TimeUnit;

/**
 * HardwareEntropy provides a pure-Java approximation of the low-level
 * entropy and timing primitives described in the research plan.
 *
 * The methods are intentionally lightweight and deterministic enough for
 * reproducible testing, while still reflecting characteristics of the
 * underlying runtime environment.
 */
public class HardwareEntropy {

    /**
     * Simulated rdtsc using System.nanoTime().
     */
    public long rdtsc() {
        return System.nanoTime();
    }

    /**
     * Simulated rdtscp – in this context we simply return another
     * monotonic timestamp, but kept as separate entry point.
     */
    public long rdtscp() {
        return System.nanoTime();
    }

    /**
     * Rough estimate of CPU latency (in nanoseconds) for a small tight loop.
     * This is not meant as an accurate benchmark, only as a stable signal
     * source tied to the host.
     */
    public int getCpuLatency() {
        final int iterations = 100_000;
        long start = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            acc += i;
        }
        long end = System.nanoTime();
        long delta = Math.max(1L, end - start);
        // Normalize by iterations to get an average per-iteration cost
        long perIter = delta / iterations;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, perIter));
    }

    /**
     * Pseudo branch-misprediction "rate" based on variability of a simple
     * conditional loop. The value is dimensionless but stable for a given
     * runtime.
     */
    public double getBranchMispredictionRate() {
        final int iterations = 50_000;
        long start = System.nanoTime();
        int acc = 0;
        for (int i = 0; i < iterations; i++) {
            if ((i & 1) == 0) {
                acc++;
            } else {
                acc--;
            }
        }
        long end = System.nanoTime();
        long elapsed = Math.max(1L, end - start);
        // Map elapsed time to a bounded [0,1] score
        double scaled = Math.log1p(elapsed / 1_000.0);
        return Math.tanh(scaled / 10.0);
    }
}
