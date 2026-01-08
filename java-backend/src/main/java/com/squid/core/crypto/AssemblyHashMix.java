package com.squid.core.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Compatibility layer for hardware-dependent hash mixing.
 *
 * The original research plan assumes a JNI bridge to C/assembly routines.
 * This implementation provides a pure-Java fallback that is safe on all
 * platforms and preserves deterministic behaviour while being "hardware-aware"
 * via JVM/OS properties.
 */
public class AssemblyHashMix {

    /**
     * Flag indicating whether a native implementation is available.
     * We try to load a JNI library named "squid_hashmix"; if it succeeds,
     * the native implementations will be used for customHashMix and
     * getHardwareSeed.
     */
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("squid_hashmix");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        NATIVE_AVAILABLE = ok;
    }

    /**
     * Compute a custom hash mixing of the given input with a hardware
     * fingerprint/seed. When native code is not available, this performs
     * SHA-256 over (input || hardwareSeedBytes).
     */
    public byte[] customHashMix(byte[] input, long hardwareFingerprint) {
        if (input == null) {
            input = new byte[0];
        }

        if (NATIVE_AVAILABLE) {
            try {
                return nativeCustomHashMix(input, hardwareFingerprint);
            } catch (UnsatisfiedLinkError ignored) {
                // fall through to pure-Java implementation
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input);
            digest.update(longToBytes(hardwareFingerprint));
            digest.update(getStaticHardwareDescriptor());
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("customHashMix failed", e);
        }
    }

    /**
     * Derive a hardware-dependent seed using JVM and OS properties.
     * This is not a strong security primitive but introduces a stable
     * tie to the runtime environment, which can be replaced later by
     * real hardware fingerprinting via JNI.
     */
    public long getHardwareSeed() {
        if (NATIVE_AVAILABLE) {
            try {
                return nativeGetHardwareSeed();
            } catch (UnsatisfiedLinkError ignored) {
                // fall back to Java implementation below
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(getStaticHardwareDescriptor());
            byte[] hash = digest.digest();
            // Take first 8 bytes as long
            ByteBuffer buf = ByteBuffer.wrap(hash, 0, 8);
            return buf.getLong();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to a simple combination of properties
            String desc = System.getProperty("os.name", "")
                    + System.getProperty("os.arch", "")
                    + Runtime.getRuntime().availableProcessors();
            return desc.hashCode();
        }
    }

    // Native entry points (optional)
    private static native byte[] nativeCustomHashMix(byte[] input, long hardwareFingerprint);
    private static native long nativeGetHardwareSeed();

    private byte[] getStaticHardwareDescriptor() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("os.name", ""));
        sb.append('|').append(System.getProperty("os.arch", ""));
        sb.append('|').append(System.getProperty("os.version", ""));
        sb.append('|').append(Runtime.getRuntime().availableProcessors());
        sb.append('|').append(System.getProperty("java.vendor", ""));
        sb.append('|').append(System.getProperty("java.version", ""));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] longToBytes(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }
}
