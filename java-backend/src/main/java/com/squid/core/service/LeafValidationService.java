package com.squid.core.service;

import com.squid.core.crypto.AssemblyHashMix;
import com.squid.core.fingerprint.FingerprintMode;
import com.squid.core.fingerprint.FingerprintSnapshot;
import com.squid.core.fingerprint.HardwareFingerprintService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class LeafValidationService {

    private final HardwareFingerprintService fingerprintService;
    private final AssemblyHashMix asm = new AssemblyHashMix();

    public LeafValidationService(HardwareFingerprintService fingerprintService) {
        this.fingerprintService = fingerprintService;
    }

    public boolean validateContext(byte[] mixedHash) {
        FingerprintSnapshot snap = fingerprintService.capture(FingerprintMode.FULL);
        double score = snap.getConfidenceScore();
        long hwSeed = asm.getHardwareSeed();
        byte[] tag = asm.customHashMix(mixedHash != null ? mixedHash : new byte[0], hwSeed);
        double tagNorm = normalize(hashToDouble(tag));
        double combined = 0.7 * score + 0.3 * tagNorm;
        return combined >= 0.5;
    }

    private double hashToDouble(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            long acc = 0;
            for (int i = 0; i < Math.min(8, h.length); i++) {
                acc = (acc << 8) | (h[i] & 0xff);
            }
            return Math.abs((double) acc % 1_000_000) / 1_000_000.0;
        } catch (NoSuchAlgorithmException e) {
            return 0.5;
        }
    }

    private double normalize(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
