package com.squid.core.service;

import com.squid.core.crypto.HKDFUtil;
import com.squid.core.crypto.MerkleTree;
import com.squid.core.fingerprint.HardwareFingerprintService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class IterativeSeedEngine {

    private final PQCService pqcService;
    private final DynamicMerkleTreeService dynamicService;
    private final HardwareFingerprintService fingerprintService;
    private final AIDecisionStateService aiState;
    private final java.util.ArrayDeque<String> recentIterativeRoots = new java.util.ArrayDeque<>();

    public IterativeSeedEngine(PQCService pqcService,
                               DynamicMerkleTreeService dynamicService,
                               HardwareFingerprintService fingerprintService,
                               AIDecisionStateService aiState) {
        this.pqcService = pqcService;
        this.dynamicService = dynamicService;
        this.fingerprintService = fingerprintService;
        this.aiState = aiState;
    }

    public IterationResult run(byte[] initialSeed, int depth) throws Exception {
        if (initialSeed == null || initialSeed.length == 0) {
            throw new IllegalArgumentException("initialSeed must be non-empty");
        }
        if (depth < 1) {
            depth = 1;
        }
        byte[] currentSeed = Arrays.copyOf(initialSeed, initialSeed.length);

        List<LevelRecord> levels = new ArrayList<>();

        for (int level = 0; level < depth; level++) {
            byte[] l1 = sha3Split(currentSeed, (byte) 0x01);
            byte[] l2 = sha3Split(currentSeed, (byte) 0x02);

            PQCService.KEMResult kem1 = pqcService.encapsulate(l1);
            PQCService.KEMResult kem2 = pqcService.encapsulate(l2);

            if (kem1 == null || kem2 == null) {
                throw new IllegalStateException("PQC Encapsulate returned null. Check PQCService/liboqs status.");
            }

            String sig1 = pqcService.sign(l1);
            String sig2 = pqcService.sign(l2);

            if (sig1 == null || sig2 == null) {
                throw new IllegalStateException("PQC Sign returned null. Check PQCService/liboqs status.");
            }

            String ctHash1 = bytesToHex(sha256(kem1.getCiphertext()));
            String ctHash2 = bytesToHex(sha256(kem2.getCiphertext()));

            List<byte[]> merkleLeaves = Arrays.asList(l1, l2);
            MerkleTree mt = new MerkleTree(merkleLeaves);
            String merkleRootHex = bytesToHex(mt.getRoot());
            recentIterativeRoots.addLast(merkleRootHex);
            while (recentIterativeRoots.size() > 64) {
                recentIterativeRoots.removeFirst();
            }

            byte[] combinedSecrets = concat(kem1.getSharedSecret(), kem2.getSharedSecret());
            byte[] prk = HKDFUtil.extract("SQUID_PROMOTION".getBytes(java.nio.charset.StandardCharsets.UTF_8), combinedSecrets);
            currentSeed = HKDFUtil.expand(prk, "SQUID_PROMOTION|" + level, 32);

            LevelRecord rec = new LevelRecord();
            rec.level = level;
            rec.l1Hash = bytesToHex(sha256(l1));
            rec.l2Hash = bytesToHex(sha256(l2));
            rec.ciphertextHashL1 = ctHash1;
            rec.ciphertextHashL2 = ctHash2;
            rec.signatureL1 = sig1;
            rec.signatureL2 = sig2;
            rec.merkleRootHex = merkleRootHex;
            rec.timestamp = Instant.now().toString();
            levels.add(rec);

            try {
                dynamicService.addLeaves(java.util.Collections.singletonList("iterative_root_" + merkleRootHex), "iterative_seed_level_" + level);
            } catch (Exception ignored) {}
            try {
                fingerprintService.capture(com.squid.core.fingerprint.FingerprintMode.FULL);
            } catch (Exception ignored) {}
            aiState.consumeEntropy(0.5);
        }

        IterationResult result = new IterationResult();
        result.finalSeedHex = bytesToHex(currentSeed);
        result.depth = depth;
        result.levels = levels;
        result.createdAt = Instant.now().toString();
        aiState.setLastFinalSeedHex(result.finalSeedHex);
        return result;
    }

    private byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sha3Split(byte[] seed, byte tag) {
        try {
            // Attempt SHA3-256 first
            MessageDigest md = MessageDigest.getInstance("SHA3-256");
            md.update(seed);
            md.update(tag);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to SHA-256 with clear logging and separation
            try {
                // System.err.println("[IterativeSeedEngine] SHA3-256 unavailable, falling back to SHA-256. Ensure BouncyCastle is in classpath for production security.");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update("SHA3_FALLBACK".getBytes(java.nio.charset.StandardCharsets.UTF_8)); // Domain separation
                md.update(seed);
                md.update(tag);
                return md.digest();
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException("Critical crypto failure: SHA-256 not available", ex);
            }
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static class IterationResult {
        public String finalSeedHex;
        public int depth;
        public String createdAt;
        public List<LevelRecord> levels;
    }

    public java.util.List<String> getRecentIterativeRoots() {
        return new java.util.ArrayList<>(recentIterativeRoots);
    }

    public static class LevelRecord {
        public int level;
        public String l1Hash;
        public String l2Hash;
        public String ciphertextHashL1;
        public String ciphertextHashL2;
        public String signatureL1;
        public String signatureL2;
        public String merkleRootHex;
        public String timestamp;
    }
}
