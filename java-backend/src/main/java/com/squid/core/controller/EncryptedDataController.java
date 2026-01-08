package com.squid.core.controller;

import com.squid.core.model.AuditLogEntry;
import com.squid.core.service.EncryptedDataViewService;
import com.squid.core.service.EncryptedDataViewService.DataPreviewRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for secure encrypted data visualization
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/encrypted")
public class EncryptedDataController {

    @Autowired
    private EncryptedDataViewService dataViewService;

    /**
     * Preview encrypted data (single element)
     * POST /api/v1/encrypted/preview
     * Body: {
     *   "encryptedData": "base64-encoded-encrypted-data",
     *   "encryptionKey": "decryption-key",
     *   "user": "username",
     *   "ipAddress": "client-ip"
     * }
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewData(
            @RequestBody DataPreviewRequest request,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-IP", required = false) String ipAddress) {
        try {
            request.previewMultiple = false;
            if (user == null) user = "ANONYMOUS";
            if (ipAddress == null) ipAddress = "0.0.0.0";
            
            Map<String, Object> preview = dataViewService.previewEncryptedData(request, user, ipAddress);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Preview multiple encrypted data items
     * POST /api/v1/encrypted/preview-multiple
     * Body: {
     *   "encryptedData": "base64-encoded-encrypted-data",
     *   "encryptionKey": "decryption-key",
     *   "user": "username",
     *   "ipAddress": "client-ip"
     * }
     */
    @PostMapping("/preview-multiple")
    public ResponseEntity<Map<String, Object>> previewMultipleData(
            @RequestBody DataPreviewRequest request,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-IP", required = false) String ipAddress) {
        try {
            request.previewMultiple = true;
            if (user == null) user = "ANONYMOUS";
            if (ipAddress == null) ipAddress = "0.0.0.0";
            
            Map<String, Object> preview = dataViewService.previewEncryptedData(request, user, ipAddress);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cached preview from session
     * GET /api/v1/encrypted/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionPreview(@PathVariable String sessionId) {
        try {
            Map<String, Object> preview = dataViewService.getPreview(sessionId);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Upload and encrypt multiple data items
     * POST /api/v1/encrypted/upload
     * Body: {
     *   "dataItems": ["item1", "item2", "item3"],
     *   "encryptionKey": "encryption-key",
     *   "user": "username",
     *   "ipAddress": "client-ip"
     * }
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadData(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-IP", required = false) String ipAddress) {
        try {
            List<String> dataItems = (List<String>) request.get("dataItems");
            String encryptionKey = (String) request.get("encryptionKey");

            if (dataItems == null || dataItems.isEmpty() || encryptionKey == null) {
                return ResponseEntity.badRequest().build();
            }

            if (user == null) user = "ANONYMOUS";
            if (ipAddress == null) ipAddress = "0.0.0.0";

            Map<String, Object> result = dataViewService.uploadEncryptedData(
                dataItems, encryptionKey, user, ipAddress
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit log for a specific data hash
     * GET /api/v1/encrypted/audit/{dataHash}
     */
    @GetMapping("/audit/{dataHash}")
    public ResponseEntity<List<AuditLogEntry>> getAuditLog(@PathVariable String dataHash) {
        try {
            List<AuditLogEntry> auditLog = dataViewService.getAuditLog(dataHash);
            return ResponseEntity.ok(auditLog);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all audit logs
     * GET /api/v1/encrypted/audit/all
     */
    @GetMapping("/audit/all")
    public ResponseEntity<List<AuditLogEntry>> getAllAuditLogs() {
        try {
            List<AuditLogEntry> auditLog = dataViewService.getAllAuditLogs();
            return ResponseEntity.ok(auditLog);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get audit logs for a specific user
     * GET /api/v1/encrypted/audit/user/{user}
     */
    @GetMapping("/audit/user/{user}")
    public ResponseEntity<List<AuditLogEntry>> getAuditLogByUser(@PathVariable String user) {
        try {
            List<AuditLogEntry> auditLog = dataViewService.getAuditLogByUser(user);
            return ResponseEntity.ok(auditLog);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get data viewing statistics
     * GET /api/v1/encrypted/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = dataViewService.getDataViewingStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cleanup expired sessions (admin endpoint)
     * POST /api/v1/encrypted/admin/cleanup
     */
    @PostMapping("/admin/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupSessions() {
        try {
            dataViewService.cleanupExpiredSessions();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("status", "Cleanup completed");
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
