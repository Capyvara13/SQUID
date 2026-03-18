package com.squid.core.controller;

import com.squid.core.model.MerkleTreeTransitionEvent;
import com.squid.core.service.DynamicMerkleTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for dynamic Merkle Tree management and monitoring
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/merkle")
public class DynamicMerkleTreeController {

    @Autowired
    private DynamicMerkleTreeService merkleTreeService;

    /**
     * Get current Merkle Tree status
     * GET /api/v1/merkle/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = merkleTreeService.getTreeStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add new leaves to the Merkle Tree
     * POST /api/v1/merkle/add-leaves
     * Body: {
     *   "leaves": ["leaf1", "leaf2", "leaf3"],
     *   "reason": "New data insertion"
     * }
     */
    @PostMapping("/add-leaves")
    public ResponseEntity<Map<String, Object>> addLeaves(@RequestBody Map<String, Object> request) {
        try {
            List<String> leaves = (List<String>) request.get("leaves");
            String reason = (String) request.get("reason");

            if (leaves == null || leaves.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Map<String, Object> result = merkleTreeService.addLeaves(leaves, reason);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update existing leaves in the Merkle Tree
     * PUT /api/v1/merkle/update-leaves
     * Body: {
     *   "updates": {
     *     "0": "new-leaf-value-0",
     *     "2": "new-leaf-value-2"
     *   },
     *   "reason": "Model retraining"
     * }
     */
    @PutMapping("/update-leaves")
    public ResponseEntity<Map<String, Object>> updateLeaves(@RequestBody Map<String, Object> request) {
        try {
            Map<Integer, String> updates = new java.util.HashMap<>();
            Map<String, Object> updateMap = (Map<String, Object>) request.get("updates");
            
            if (updateMap != null) {
                updateMap.forEach((key, value) -> {
                    try {
                        int index = Integer.parseInt(key);
                        updates.put(index, (String) value);
                    } catch (NumberFormatException ignored) {}
                });
            }

            String reason = (String) request.get("reason");

            Map<String, Object> result = merkleTreeService.updateLeaves(updates, reason);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Rotate keys and rebuild tree
     * POST /api/v1/merkle/rotate-keys
     * Body: {
     *   "reason": "Security key rotation"
     * }
     */
    @PostMapping("/rotate-keys")
    public ResponseEntity<Map<String, Object>> rotateKeys(@RequestBody(required = false) Map<String, String> request) {
        try {
            String reason = request != null ? request.get("reason") : null;
            Map<String, Object> result = merkleTreeService.rotateKeysAndRebuild(reason);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verify tree integrity
     * POST /api/v1/merkle/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyIntegrity() {
        try {
            Map<String, Object> result = merkleTreeService.verifyIntegrity();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get transition history
     * GET /api/v1/merkle/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<MerkleTreeTransitionEvent>> getHistory() {
        try {
            List<MerkleTreeTransitionEvent> history = merkleTreeService.getTransitionHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get transitions filtered by event type
     * GET /api/v1/merkle/history?type=ADD_LEAVES
     */
    @GetMapping("/history/type")
    public ResponseEntity<List<MerkleTreeTransitionEvent>> getHistoryByType(
            @RequestParam String type) {
        try {
            List<MerkleTreeTransitionEvent> history = merkleTreeService.getTransitionsByType(type);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent transitions
     * GET /api/v1/merkle/history/recent?limit=10
     */
    @GetMapping("/history/recent")
    public ResponseEntity<List<MerkleTreeTransitionEvent>> getRecentHistory(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<MerkleTreeTransitionEvent> history = merkleTreeService.getRecentTransitions(limit);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get audit trail
     * GET /api/v1/merkle/audit
     */
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditTrail() {
        try {
            Map<String, Object> audit = merkleTreeService.getAuditTrail();
            return ResponseEntity.ok(audit);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get statistics about Merkle Tree operations
     * GET /api/v1/merkle/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> status = merkleTreeService.getTreeStatus();
            Map<String, Object> stats = new java.util.HashMap<>(status);
            stats.put("historySize", merkleTreeService.getTransitionHistory().size());
            
            // Add event type breakdown
            java.util.Map<String, Long> eventCounts = new java.util.HashMap<>();
            merkleTreeService.getTransitionHistory().forEach(e ->
                eventCounts.put(e.getEventType(),
                    eventCounts.getOrDefault(e.getEventType(), 0L) + 1)
            );
            stats.put("eventCounts", eventCounts);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate deterministic leaves from a seed and append to the Merkle tree.
     * Mirrors the IPCMain "generate" behaviour for HTTP callers.
     * POST /api/v1/merkle/generate-from-seed
     * Body: {
     *   "seed": "...",
     *   "params": { "b": 4, "m": 3, "t": 128 }
     * }
     */
    @PostMapping("/generate-from-seed")
    public ResponseEntity<Map<String, Object>> generateFromSeed(@RequestBody Map<String, Object> request) {
        try {
            String seed = (String) request.get("seed");
            if (seed == null || seed.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            int b = 4, m = 3;
            if (params != null) {
                Object bVal = params.get("b");
                Object mVal = params.get("m");
                if (bVal instanceof Number) b = ((Number) bVal).intValue();
                if (mVal instanceof Number) m = ((Number) mVal).intValue();
            }

            int totalLeaves = 1;
            try {
                totalLeaves = (int) Math.pow(b, m);
            } catch (Exception ignored) {}
            totalLeaves = Math.max(1, Math.min(totalLeaves, 256));

            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            java.util.List<String> newLeaves = new java.util.ArrayList<>();
            for (int i = 0; i < totalLeaves; i++) {
                md.reset();
                md.update(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                md.update((byte) ':');
                md.update(Integer.toString(i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byte[] h = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte bb : h) sb.append(String.format("%02x", bb));
                newLeaves.add(sb.toString());
            }

            Map<String, Object> result = merkleTreeService.addLeaves(newLeaves, "generate_from_seed_http");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Rotate specific leaf indices.
     * Mirrors the IPCMain "rotate_indices" behaviour for HTTP callers.
     * POST /api/v1/merkle/rotate-indices
     * Body: { "indices": [0,1,2], "reason": "..." }
     */
    @PostMapping("/rotate-indices")
    public ResponseEntity<Map<String, Object>> rotateByIndices(@RequestBody Map<String, Object> request) {
        try {
            java.util.List<Integer> indices = new java.util.ArrayList<>();
            Object idx = request.get("indices");
            if (idx instanceof java.util.List<?>) {
                for (Object o : (java.util.List<?>) idx) {
                    if (o instanceof Number) {
                        indices.add(((Number) o).intValue());
                    }
                }
            }
            String reason = (String) request.get("reason");
            Map<String, Object> res = merkleTreeService.rotateLeavesByIndex(indices, reason);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
