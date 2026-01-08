package com.squid.core.fingerprint;

/**
 * Operational modes for hardware fingerprinting.
 *
 * FULL      -> microarchitectural + temporal + triggers
 * REDUCED   -> temporal + triggers
 * SOFTWARE  -> software-only fallback
 */
public enum FingerprintMode {
    FULL,
    REDUCED,
    SOFTWARE
}