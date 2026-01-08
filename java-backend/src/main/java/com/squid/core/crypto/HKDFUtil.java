package com.squid.core.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * HKDF implementation following RFC 5869
 * Used for deterministic key derivation in SQUID system
 */
public class HKDFUtil {
    
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    /**
     * HKDF Extract step - derives a pseudo-random key from input key material
     */
    public static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        try {
            if (salt == null || salt.length == 0) {
                salt = new byte[32]; // Zero salt for HMAC-SHA256
            }
            
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(salt, HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(inputKeyMaterial);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HKDF Extract failed", e);
        }
    }
    
    /**
     * HKDF Expand step - expands pseudo-random key to desired length
     */
    public static byte[] expand(byte[] pseudoRandomKey, String info, int length) {
        try {
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
            
            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            HKDFParameters params = new HKDFParameters(pseudoRandomKey, null, infoBytes);
            hkdf.init(params);
            
            byte[] result = new byte[length];
            hkdf.generateBytes(result, 0, length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("HKDF Expand failed", e);
        }
    }
    
    /**
     * Combined HKDF Extract + Expand
     */
    public static byte[] deriveKey(byte[] salt, byte[] inputKeyMaterial, String info, int length) {
        byte[] prk = extract(salt, inputKeyMaterial);
        return expand(prk, info, length);
    }
    
    /**
     * Derive branch key for tree structure
     * Uses deterministic info string: "branch|level|index"
     */
    public static byte[] deriveBranchKey(byte[] parentKey, int level, int index, int keyLength) {
        String info = String.format("branch|%d|%d", level, index);
        return expand(parentKey, info, keyLength);
    }
    
    /**
     * Derive leaf value using HMAC
     */
    public static byte[] deriveLeaf(byte[] branchKey, int leafIndex, int leafBits) {
        try {
            String info = String.format("leaf|%d", leafIndex);
            byte[] leafKey = expand(branchKey, info, 32); // 256-bit key for HMAC
            
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(leafKey, HMAC_ALGORITHM);
            mac.init(keySpec);
            
            byte[] leafValue = mac.doFinal(info.getBytes(StandardCharsets.UTF_8));
            
            // Truncate to desired bit length
            int bytes = (leafBits + 7) / 8;
            byte[] result = new byte[bytes];
            System.arraycopy(leafValue, 0, result, 0, Math.min(bytes, leafValue.length));
            
            // Clear excess bits if not byte-aligned
            if (leafBits % 8 != 0) {
                int excessBits = 8 - (leafBits % 8);
                result[result.length - 1] &= (0xFF << excessBits);
            }
            
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Leaf derivation failed", e);
        }
    }
}
