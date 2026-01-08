package com.squid.core.service;

import com.squid.core.crypto.AssemblyHashMix;
import com.squid.core.crypto.CanonicalJson;
import com.squid.core.crypto.MerkleTree;
import com.squid.core.fingerprint.FingerprintMode;
import com.squid.core.fingerprint.FingerprintSnapshot;
import com.squid.core.fingerprint.HardwareFingerprintService;
import com.squid.core.model.EncryptDecryptModels;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * High-level Encrypt / Decrypt pipeline orchestrator.
 *
 * This service wires together canonicalization, pre-hash, hardware-influenced
 * mixing, PQC encryption/decryption, Merkle anchoring, and basic audit
 * metadata. It intentionally reuses existing PQCService and MerkleTree
 * primitives so that we do not duplicate crypto logic.
 */
@Service
public class CryptoPipelineService {

    private final PQCService pqcService;
    private final AssemblyHashMix assemblyHashMix = new AssemblyHashMix();
    private final HardwareFingerprintService fingerprintService;

    // In-memory operation log for observability (can be replaced with DB later)
    private final List<Map<String, Object>> operations = Collections.synchronizedList(new ArrayList<>());

    public CryptoPipelineService(PQCService pqcService,
                                 HardwareFingerprintService fingerprintService) {
        this.pqcService = pqcService;
        this.fingerprintService = fingerprintService;
    }

    /**
     * Encrypt according to the SQUID pipeline and return ciphertext + metadata.
     */
    public EncryptDecryptModels.EncryptResponse encrypt(EncryptDecryptModels.EncryptRequest request) throws Exception {
        String payload = Optional.ofNullable(request.getData()).orElse("");

        // 1) Canonicalization
        byte[] canonical;
        try {
            // Try to treat input as JSON first
            if (payload.trim().startsWith("{") || payload.trim().startsWith("[")) {
                canonical = CanonicalJson.canonicalize(payload);
            } else {
                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("payload", payload);
                canonical = CanonicalJson.canonicalize(wrapper);
            }
        } catch (Exception e) {
            // Fallback: raw UTF-8 bytes
            canonical = payload.getBytes(StandardCharsets.UTF_8);
        }

        // 2) Pre-hash
        byte[] preHash = sha256(canonical);

        // 3) Assembly-based hash mixing (hardware-influenced)
        long hwSeed = assemblyHashMix.getHardwareSeed();
        byte[] mixedHash = assemblyHashMix.customHashMix(preHash, hwSeed);

        // Capture fingerprint snapshot for this operation
        FingerprintSnapshot fpSnap = fingerprintService.capture(FingerprintMode.FULL);

        // 4) PQC encryption using Kyber + signature over mixed hash
        Map<String, String> enc = pqcService.encryptWithKyber(canonical);
        String ciphertextB64 = enc.get("ciphertext");
        String encapsulatedKeyB64 = enc.get("encapsulated_key");
        String signature = pqcService.sign(mixedHash);

        // 5) Merkle insertion (per-operation ephemeral tree for now)
        List<byte[]> leaves = Collections.singletonList(mixedHash);
        MerkleTree tree = new MerkleTree(leaves, assemblyHashMix);
        byte[] root = tree.getRoot();

        // 6) Build metadata for decrypt context and observability
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("encapsulated_key", encapsulatedKeyB64);
        meta.put("signature", signature);
        meta.put("kyber_public_key", pqcService.getKyberPublicKey());
        meta.put("dilithium_public_key", pqcService.getDilithiumPublicKey());
        meta.put("merkle_root", bytesToHex(root));
        meta.put("pre_hash", bytesToHex(preHash));
        meta.put("mixed_hash", bytesToHex(mixedHash));
        meta.put("created_at", Instant.now().toString());
        meta.put("fingerprint_mode", fpSnap.getMode().name());
        meta.put("fingerprint_confidence", fpSnap.getConfidenceScore());

        String opId = UUID.randomUUID().toString();
        meta.put("op_id", opId);

        recordOperation("ENCRYPT", true, null, meta);

        EncryptDecryptModels.EncryptResponse resp = new EncryptDecryptModels.EncryptResponse();
        resp.setCiphertext(ciphertextB64);
        resp.setMetadata(meta);
        return resp;
    }

    /**
     * Context-dependent decrypt. For now this validates signature and Merkle
     * root and returns plaintext; hardware fingerprint and AI gates will be
     * layered on top.
     */
    public EncryptDecryptModels.DecryptResponse decrypt(EncryptDecryptModels.DecryptRequest request) throws Exception {
        EncryptDecryptModels.DecryptResponse resp = new EncryptDecryptModels.DecryptResponse();
        Map<String, Object> meta = Optional.ofNullable(request.getMetadata()).orElse(Collections.emptyMap());

        String ciphertextB64 = request.getCiphertext();
        String encapsulatedKeyB64 = (String) meta.get("encapsulated_key");
        String signature = (String) meta.get("signature");
        String merkleRootHex = (String) meta.get("merkle_root");

        if (ciphertextB64 == null || encapsulatedKeyB64 == null || signature == null || merkleRootHex == null) {
            resp.setAuthorized(false);
            resp.setFailureReason("missing_metadata");
            recordOperation("DECRYPT", false, "missing_metadata", meta);
            return resp;
        }

        // 1) Decrypt with Kyber
        byte[] plaintext = pqcService.decryptWithKyber(encapsulatedKeyB64, ciphertextB64);

        // 2) Recompute hashes
        byte[] canonical = plaintext; // we always encrypted canonical form
        byte[] preHash = sha256(canonical);
        long hwSeed = assemblyHashMix.getHardwareSeed();
        byte[] mixedHash = assemblyHashMix.customHashMix(preHash, hwSeed);

        // Capture fingerprint snapshot at decrypt time
        FingerprintSnapshot fpSnap = fingerprintService.capture(FingerprintMode.FULL);
        resp.setFingerprintConfidence(fpSnap.getConfidenceScore());

        // 3) Verify signature
        boolean sigValid = pqcService.verify(signature, mixedHash);
        if (!sigValid) {
            resp.setAuthorized(false);
            resp.setFailureReason("signature_invalid");
            recordOperation("DECRYPT", false, "signature_invalid", meta);
            return resp;
        }

        // 4) Verify Merkle root
        MerkleTree tree = new MerkleTree(Collections.singletonList(mixedHash), assemblyHashMix);
        boolean merkleOk = bytesToHex(tree.getRoot()).equalsIgnoreCase(merkleRootHex);
        resp.setMerkleVerified(merkleOk);
        if (!merkleOk) {
            resp.setAuthorized(false);
            resp.setFailureReason("merkle_root_mismatch");
            recordOperation("DECRYPT", false, "merkle_root_mismatch", meta);
            return resp;
        }

        // 5) Context checks (placeholder: we will wire fingerprint + AI gating here)
        resp.setAuthorized(true);
        resp.setPlaintext(new String(plaintext, StandardCharsets.UTF_8));
        // fingerprint_confidence and ai_decision will be filled once those services exist

        recordOperation("DECRYPT", true, null, meta);
        return resp;
    }

    public List<Map<String, Object>> getOperationsSnapshot() {
        synchronized (operations) {
            return new ArrayList<>(operations);
        }
    }

    private void recordOperation(String type, boolean success, String failureReason, Map<String, Object> metaSnapshot) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", UUID.randomUUID().toString());
        entry.put("type", type);
        entry.put("success", success);
        entry.put("failure_reason", failureReason);
        entry.put("timestamp", Instant.now().toString());
        entry.put("metadata", new LinkedHashMap<>(metaSnapshot));
        operations.add(entry);
    }

    private byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}