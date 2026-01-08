package com.squid.core.service;

import com.squid.core.model.AuditLogEntry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for secure encrypted data visualization
 * Handles decryption for display, auditoria, and ensures no persistence of decrypted data
 */
@Service
public class EncryptedDataViewService {
    
    private final List<AuditLogEntry> auditLog = new CopyOnWriteArrayList<>();
    private final Map<String, EncryptedDataSession> activeSessions = new HashMap<>();

    /**
     * Represents a temporary session for encrypted data viewing
     */
    public static class EncryptedDataSession {
        public String sessionId;
        public String dataHash;
        public String decryptedPreview;
        public long createdAt;
        public long expiresAt;
        public boolean isMultipleElements;

        public EncryptedDataSession(String sessionId, String dataHash, String preview, boolean multiple) {
            this.sessionId = sessionId;
            this.dataHash = dataHash;
            this.decryptedPreview = preview;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + 300000; // 5 minutes TTL
            this.isMultipleElements = multiple;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public void resetPreview() {
            this.decryptedPreview = null;
        }
    }

    /**
     * Request for encrypted data preview
     */
    public static class DataPreviewRequest {
        public String encryptedData;
        public String encryptionKey;
        public boolean previewMultiple;
        public String user;
        public String ipAddress;

        public DataPreviewRequest() {}

        public DataPreviewRequest(String encryptedData, String encryptionKey, boolean multiple) {
            this.encryptedData = encryptedData;
            this.encryptionKey = encryptionKey;
            this.previewMultiple = multiple;
        }
    }

    /**
     * Preview encrypted data without persistence
     * Simulates decryption and returns preview with audit log
     */
    public Map<String, Object> previewEncryptedData(DataPreviewRequest request, String user, String ipAddress) 
            throws NoSuchAlgorithmException {
        
        if (request == null || request.encryptedData == null || request.encryptedData.isEmpty()) {
            throw new IllegalArgumentException("Invalid encrypted data");
        }

        // Generate data hash for audit
        String dataHash = generateHash(request.encryptedData);

        // Create session
        String sessionId = UUID.randomUUID().toString();
        String preview = simulateDecryption(request.encryptedData, request.encryptionKey);
        
        EncryptedDataSession session = new EncryptedDataSession(
            sessionId,
            dataHash,
            preview,
            request.previewMultiple
        );
        
        activeSessions.put(sessionId, session);

        // Log audit entry
        AuditLogEntry auditEntry = new AuditLogEntry(dataHash, "PREVIEW", user != null ? user : "UNKNOWN");
        auditEntry.setIpAddress(ipAddress);
        auditEntry.setDetails("Preview " + (request.previewMultiple ? "multiple" : "single") + " element(s)");
        auditLog.add(auditEntry);

        // Return preview with session info
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("dataHash", dataHash);
        response.put("preview", preview);
        response.put("elementCount", request.previewMultiple ? estimateElementCount(preview) : 1);
        response.put("preview_expires_in_seconds", 300);
        response.put("is_multiple", request.previewMultiple);
        
        return response;
    }

    /**
     * Get cached preview for a session
     */
    public Map<String, Object> getPreview(String sessionId) {
        EncryptedDataSession session = activeSessions.get(sessionId);
        
        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }

        if (session.isExpired()) {
            activeSessions.remove(sessionId);
            throw new IllegalArgumentException("Session expired");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("dataHash", session.dataHash);
        response.put("preview", session.decryptedPreview);
        response.put("is_multiple", session.isMultipleElements);
        
        return response;
    }

    /**
     * Upload and encrypt multiple data items
     */
    public Map<String, Object> uploadEncryptedData(List<String> dataItems, String encryptionKey, 
                                                   String user, String ipAddress) 
            throws NoSuchAlgorithmException {
        
        if (dataItems == null || dataItems.isEmpty()) {
            throw new IllegalArgumentException("No data items provided");
        }

        List<String> encryptedItems = new ArrayList<>();
        List<String> hashes = new ArrayList<>();

        for (String item : dataItems) {
            String encrypted = simulateEncryption(item, encryptionKey);
            String hash = generateHash(encrypted);
            
            encryptedItems.add(encrypted);
            hashes.add(hash);

            // Log audit entry
            AuditLogEntry auditEntry = new AuditLogEntry(hash, "UPLOAD", user != null ? user : "UNKNOWN");
            auditEntry.setIpAddress(ipAddress);
            auditEntry.setDetails("Encrypted data item uploaded");
            auditLog.add(auditEntry);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("uploadId", UUID.randomUUID().toString());
        response.put("itemCount", encryptedItems.size());
        response.put("encryptedItems", encryptedItems);
        response.put("hashes", hashes);
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * Get audit log for a data hash
     */
    public List<AuditLogEntry> getAuditLog(String dataHash) {
        return auditLog.stream()
            .filter(entry -> entry.getDataHash().equals(dataHash))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all audit logs
     */
    public List<AuditLogEntry> getAllAuditLogs() {
        return new ArrayList<>(auditLog);
    }

    /**
     * Get audit logs for a user
     */
    public List<AuditLogEntry> getAuditLogByUser(String user) {
        return auditLog.stream()
            .filter(entry -> entry.getUser().equals(user))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Cleanup expired sessions
     */
    public void cleanupExpiredSessions() {
        List<String> expiredSessions = activeSessions.entrySet().stream()
            .filter(e -> e.getValue().isExpired())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
        
        expiredSessions.forEach(sessionId -> {
            EncryptedDataSession session = activeSessions.remove(sessionId);
            session.resetPreview(); // Ensure preview is cleared
        });
    }

    /**
     * Get statistics about data viewing
     */
    public Map<String, Object> getDataViewingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAuditEntries", auditLog.size());
        stats.put("activeSessions", activeSessions.size());
        stats.put("previewCount", auditLog.stream().filter(e -> e.getAction().equals("PREVIEW")).count());
        stats.put("uploadCount", auditLog.stream().filter(e -> e.getAction().equals("UPLOAD")).count());
        stats.put("uniqueUsers", auditLog.stream().map(AuditLogEntry::getUser).distinct().count());
        return stats;
    }

    // Private helper methods

    /**
     * Simulate encryption (in production, use real encryption like AES-256)
     */
    private String simulateEncryption(String data, String key) {
        // Base64 encode as simulation
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Simulate decryption (in production, use real decryption)
     */
    private String simulateDecryption(String encryptedData, String key) {
        try {
            // Base64 decode as simulation
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Return first 100 chars of encrypted data as preview if decoding fails
            return encryptedData.substring(0, Math.min(100, encryptedData.length())) + "...";
        }
    }

    /**
     * Generate SHA-256 hash
     */
    private String generateHash(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Estimate number of elements in preview
     */
    private int estimateElementCount(String preview) {
        if (preview == null) return 0;
        int count = 1;
        // Count JSON objects or comma-separated items as estimation
        count += preview.split("[{,]").length - 1;
        return Math.max(1, count);
    }
}
