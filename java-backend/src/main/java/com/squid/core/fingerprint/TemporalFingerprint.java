package com.squid.core.fingerprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporal fingerprint based on execution timings of simple operations.
 * This is a lightweight, JVM-friendly approximation of the research plan's
 * temporal fingerprinting module.
 */
public class TemporalFingerprint {

    private final List<Long> executionTimes = new ArrayList<>();
    private final List<Double> nopLatencies = new ArrayList<>();
    private final Map<String, Double> statisticalDeviations = new HashMap<>();

    public FingerprintVector generateFingerprint() {
        executionTimes.clear();
        nopLatencies.clear();
        statisticalDeviations.clear();

        final int samples = 32;
        for (int i = 0; i < samples; i++) {
            long t = measureLoopTime(10_000);
            executionTimes.add(t);
            nopLatencies.add((double) t / 10_000.0);
        }

        double mean = mean(executionTimes);
        double std = stddev(executionTimes, mean);
        statisticalDeviations.put("mean_loop_time_ns", mean);
        statisticalDeviations.put("std_loop_time_ns", std);

        return new FingerprintVector(executionTimes, nopLatencies, statisticalDeviations);
    }

    private long measureLoopTime(int iterations) {
        long start = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            acc += i;
        }
        long end = System.nanoTime();
        // use acc to prevent JIT from eliminating loop
        if (acc == -1) {
            return 0L;
        }
        return Math.max(1L, end - start);
    }

    private double mean(List<Long> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (long v : values) sum += v;
        return sum / values.size();
    }

    private double stddev(List<Long> values, double mean) {
        if (values.isEmpty()) return 0.0;
        double var = 0.0;
        for (long v : values) {
            double d = v - mean;
            var += d * d;
        }
        var /= values.size();
        return Math.sqrt(var);
    }
}
