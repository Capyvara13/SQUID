package com.squid.core.model;

/**
 * High-level security profile for SQUID operations.
 *
 * Controls default AI mode, quantum behaviour, fingerprint mode and
 * inspection depth. Concrete semantics are implemented in services but
 * this enum is the canonical contract for REST and IPC.
 */
public enum SecurityProfile {
    RESEARCH,
    PRODUCTION,
    COMPLIANCE
}