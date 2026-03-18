package com.squid.core.controller;

import com.squid.core.db.DatabaseHealthService;
import com.squid.core.db.SquidDatabaseService;
import com.squid.core.service.DynamicMerkleTreeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Database observability and management endpoints for the dashboard.
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/database")
public class DatabaseController {

    private final DatabaseHealthService healthService;
    private final SquidDatabaseService dbService;
    private final DynamicMerkleTreeService merkleService;

    public DatabaseController(DatabaseHealthService healthService,
                              SquidDatabaseService dbService,
                              DynamicMerkleTreeService merkleService) {
        this.healthService = healthService;
        this.dbService = dbService;
        this.merkleService = merkleService;
    }

    /** GET /api/v1/database/health */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(healthService.getHealth());
    }

    /** GET /api/v1/database/config */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(healthService.getConfig());
    }

    /** GET /api/v1/database/audit-logs?limit=50 */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> auditLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(dbService.getAuditLogs(limit));
    }

    /** GET /api/v1/database/audit-verify */
    @GetMapping("/audit-verify")
    public ResponseEntity<Map<String, Object>> auditVerify() {
        return ResponseEntity.ok(dbService.verifyAuditChain());
    }

    /** GET /api/v1/database/merkle-integrity */
    @GetMapping("/merkle-integrity")
    public ResponseEntity<Map<String, Object>> merkleIntegrity() {
        // Use the dynamic tree's current root as the "live" root
        Map<String, Object> treeStatus = merkleService.getTreeStatus();
        String liveRoot = (String) treeStatus.get("rootHash");
        return ResponseEntity.ok(dbService.getMerkleIntegrity(liveRoot));
    }

    /**
     * POST /api/v1/database/test-connection
     * Body: { "url": "jdbc:postgresql://...", "user": "squid", "password": "..." }
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String user = body.get("user");
        String password = body.get("password");
        if (url == null || url.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "url is required");
            return ResponseEntity.badRequest().body(err);
        }
        return ResponseEntity.ok(dbService.testConnection(url, user, password));
    }
}
