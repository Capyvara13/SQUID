package com.squid.core.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypted Data Cryptographic Service
 * Handles encryption/decryption using Kyber + Dilithium
 * Manages secure sessions with integrity verification
 */
public class EncryptedDataCryptoService {

    private final PQCService pqcService;
    private final Map<String, EncryptedDataSession> sessions = new ConcurrentHashMap<>();
    private final List<AuditEntry> auditLog = new ArrayList<>();
    private final long sessionTTLMs = 5 * 60 * 1000; // 5 minutes

    public EncryptedDataCryptoService(PQCService pqcService) {
        this.pqcService = pqcService;
    }

    /**
     * Encrypt data with Kyber + Dilithium
     * Returns encrypted payload with signature and metadata
     */
    public EncryptedPayload encryptData(String data, String userId, String ipAddress) throws Exception {
        byte[] plaintext = data.getBytes(StandardCharsets.UTF_8);
        
        // Calculate data hash
        String dataHash = calculateHash(plaintext);
        
        // Encrypt with Kyber
        Map<String, String> encrypted = pqcService.encryptWithKyber(plaintext);
        
        // Sign the data hash with Dilithium
        String signature = pqcService.sign(plaintext);
        
        EncryptedPayload payload = new EncryptedPayload();
        payload.encryptedDataId = UUID.randomUUID().toString();
        payload.dataHash = dataHash;
        payload.encapsulatedKey = encrypted.get("encapsulated_key");
        payload.ciphertext = encrypted.get("ciphertext");
        payload.signature = signature;
        payload.algorithm = "KYBER_DILITHIUM";
        payload.timestamp = Instant.now();
        payload.kyberPublicKey = pqcService.getKyberPublicKey();
        payload.dilithiumPublicKey = pqcService.getDilithiumPublicKey();
        
        // Log audit trail
        auditLog.add(new AuditEntry(
            "ENCRYPT",
            payload.encryptedDataId,
            dataHash,
            userId,
            ipAddress,
            "Data encrypted with Kyber + Dilithium"
        ));
        
        return payload;
    }

    /**
     * Decrypt data with Kyber + verify Dilithium signature
     * Creates temporary session with TTL
     */
    public DecryptedPreview decryptData(EncryptedPayload payload, String userId, String ipAddress) throws Exception {
        // Verify signature first
        byte[] ciphertext = Base64.getDecoder().decode(payload.ciphertext);
        boolean signatureValid = pqcService.verify(payload.signature, ciphertext);
        
        if (!signatureValid) {
            auditLog.add(new AuditEntry(
                "DECRYPT_FAILED",
                payload.encryptedDataId,
                payload.dataHash,
                userId,
                ipAddress,
                "Signature verification failed"
            ));
            throw new SecurityException("Signature verification failed - data integrity compromised");
        }
        
        // Decrypt with Kyber
        byte[] plaintext = pqcService.decryptWithKyber(
            payload.encapsulatedKey,
            payload.ciphertext
        );
        
        String decryptedData = new String(plaintext, StandardCharsets.UTF_8);
        String computedHash = calculateHash(plaintext);
        
        // Verify hash integrity
        if (!computedHash.equals(payload.dataHash)) {
            auditLog.add(new AuditEntry(
                "DECRYPT_HASH_MISMATCH",
                payload.encryptedDataId,
                payload.dataHash,
                userId,
                ipAddress,
                "Hash mismatch after decryption"
            ));
            throw new SecurityException("Hash mismatch - data integrity compromised");
        }
        
        // Create preview with session
        DecryptedPreview preview = new DecryptedPreview();
        preview.sessionId = UUID.randomUUID().toString();
        preview.decryptedData = decryptedData;
        preview.dataHash = payload.dataHash;
        preview.isIntegrityValid = true;
        preview.timestamp = Instant.now();
        preview.expiresAt = Instant.now().plusMillis(sessionTTLMs);
        preview.signature = payload.signature;
        preview.signatureValid = signatureValid;
        
        // Store session
        EncryptedDataSession session = new EncryptedDataSession();
        session.sessionId = preview.sessionId;
        session.decryptedData = decryptedData;
        session.createdAt = Instant.now();
        session.expiresAt = preview.expiresAt;
        session.userId = userId;
        session.ipAddress = ipAddress;
        session.dataHash = payload.dataHash;
        
        sessions.put(preview.sessionId, session);
        
        // Log audit trail
        auditLog.add(new AuditEntry(
            "DECRYPT_SUCCESS",
            payload.encryptedDataId,
            payload.dataHash,
            userId,
            ipAddress,
            "Data decrypted and preview created (TTL: 5 minutes)"
        ));
        
        return preview;
    }

    /**
     * Get decrypted preview from session
     */
    public DecryptedPreview getPreview(String sessionId) throws Exception {
        EncryptedDataSession session = sessions.get(sessionId);
        
        if (session == null) {
            throw new IllegalArgumentException("Session not found or expired");
        }
        
        if (session.isExpired()) {
            sessions.remove(sessionId);
            throw new IllegalArgumentException("Session has expired");
        }
        
        DecryptedPreview preview = new DecryptedPreview();
        preview.sessionId = sessionId;
        preview.decryptedData = session.decryptedData;
        preview.dataHash = session.dataHash;
        preview.isIntegrityValid = true;
        preview.timestamp = session.createdAt;
        preview.expiresAt = session.expiresAt;
        preview.signatureValid = true;
        
        return preview;
    }

    /**
     * Cleanup expired sessions
     */
    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Get audit log
     */
    public List<AuditEntry> getAuditLog() {
        return new ArrayList<>(auditLog);
    }

    /**
     * Get audit entries by hash
     */
    public List<AuditEntry> getAuditLogByHash(String dataHash) {
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditEntry entry : auditLog) {
            if (entry.dataHash.equals(dataHash)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Calculate SHA-256 hash
     */
    private String calculateHash(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data);
        byte[] hash = md.digest();
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Encrypted payload structure
     */
    public static class EncryptedPayload {
        public String encryptedDataId;
        public String dataHash;
        public String encapsulatedKey;    // Kyber encapsulated key
        public String ciphertext;         // Encrypted data
        public String signature;           // Dilithium signature
        public String algorithm;
        public Instant timestamp;
        public String kyberPublicKey;     // For verification
        public String dilithiumPublicKey;  // For verification
    }

    /**
     * Decrypted preview with integrity info
     */
    public static class DecryptedPreview {
        public String sessionId;
        public String decryptedData;
        public String dataHash;
        public boolean isIntegrityValid;
        public boolean signatureValid;
        public Instant timestamp;
        public Instant expiresAt;
        public String signature;
    }

    /**
     * Encrypted data session (temporary)
     */
    private static class EncryptedDataSession {
        public String sessionId;
        public String decryptedData;
        public String dataHash;
        public Instant createdAt;
        public Instant expiresAt;
        public String userId;
        public String ipAddress;

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Audit log entry
     */
    public static class AuditEntry {
        public String action;
        public String encryptedDataId;
        public String dataHash;
        public String userId;
        public String ipAddress;
        public String details;
        public Instant timestamp;

        public AuditEntry(String action, String encryptedDataId, String dataHash, 
                         String userId, String ipAddress, String details) {
            this.action = action;
            this.encryptedDataId = encryptedDataId;
            this.dataHash = dataHash;
            this.userId = userId;
            this.ipAddress = ipAddress;
            this.details = details;
            this.timestamp = Instant.now();
        }
    }
}
