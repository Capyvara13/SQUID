package com.squid.core.controller;

import com.squid.core.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/instances")
public class InstancesController {

    private final InstanceService instanceService;
    private final GlobalIntegrityTree globalTree;
    private final DynamicMerkleTreeService dynamicService;
    private final IterativeSeedEngine iterativeSeedEngine;
    private final CryptoPipelineService pipeline;
    private final AIDecisionStateService aiState;
    private final PQCService pqcService;

    public InstancesController(InstanceService instanceService,
                               GlobalIntegrityTree globalTree,
                               DynamicMerkleTreeService dynamicService,
                               IterativeSeedEngine iterativeSeedEngine,
                               CryptoPipelineService pipeline,
                               AIDecisionStateService aiState,
                               PQCService pqcService) {
        this.instanceService = instanceService;
        this.globalTree = globalTree;
        this.dynamicService = dynamicService;
        this.iterativeSeedEngine = iterativeSeedEngine;
        this.pipeline = pipeline;
        this.aiState = aiState;
        this.pqcService = pqcService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        try {
            return ResponseEntity.ok(instanceService.list());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create a new SQUID instance.
     * POST /api/v1/instances
     * Body: { "name": "inst1", "config": { "B": 2, "M": 8, "T": 256, "data": "..." } }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) body.getOrDefault("config", new LinkedHashMap<>());
            Map<String, Object> result = instanceService.create(name, config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    /**
     * Remove leaves from an instance.
     * POST /api/v1/instances/{id}/remove-leaves
     * Body: { "leaf_indices": [0, 1, 5] }
     */
    @PostMapping("/{id}/remove-leaves")
    public ResponseEntity<Map<String, Object>> removeLeaves(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> indices = new ArrayList<>();
            Object raw = body.get("leaf_indices");
            if (raw instanceof List<?>) {
                for (Object o : (List<?>) raw) {
                    if (o instanceof Number) indices.add(((Number) o).intValue());
                }
            }
            if (indices.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(instanceService.removeLeaves(id, indices));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Re-encrypt an instance (new seed, new tree, new signature).
     * POST /api/v1/instances/{id}/reencrypt
     */
    @PostMapping("/{id}/reencrypt")
    public ResponseEntity<Map<String, Object>> reencrypt(@PathVariable String id) {
        try {
            return ResponseEntity.ok(instanceService.reencrypt(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Decrypt an instance to a file.
     * POST /api/v1/instances/{id}/decrypt
     * Body: { "file_extension": "txt" }
     */
    @PostMapping("/{id}/decrypt")
    public ResponseEntity<Map<String, Object>> decrypt(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String ext = (String) body.getOrDefault("file_extension", "txt");
            return ResponseEntity.ok(instanceService.decrypt(id, ext));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export instance to database (MySQL/PostgreSQL).
     * POST /api/v1/instances/{id}/export
     * Body: { "target": "mysql" }
     */
    @PostMapping("/{id}/export")
    public ResponseEntity<Map<String, Object>> exportToDb(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String target = (String) body.getOrDefault("target", "mysql");
            return ResponseEntity.ok(instanceService.exportToDb(id, target));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List all leaves of an instance with per-leaf metadata.
     * GET /api/v1/instances/{id}/leaves
     */
    @GetMapping("/{id}/leaves")
    public ResponseEntity<Map<String, Object>> getLeaves(@PathVariable String id) {
        try {
            return ResponseEntity.ok(instanceService.getLeaves(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get detailed info for a single leaf.
     * GET /api/v1/instances/{id}/leaves/{index}
     */
    @GetMapping("/{id}/leaves/{index}")
    public ResponseEntity<Map<String, Object>> getLeafDetail(
            @PathVariable String id, @PathVariable int index) {
        try {
            return ResponseEntity.ok(instanceService.getLeafDetail(id, index));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the full history of an instance.
     * GET /api/v1/instances/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String id) {
        try {
            return ResponseEntity.ok(instanceService.getHistory(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        try {
            Map<String,Object> status = dynamicService.getTreeStatus();
            String dynamicRootHex = (String) status.get("rootHash");
            List<String> iterativeRoots = iterativeSeedEngine.getRecentIterativeRoots();
            List<Map<String,Object>> ops = pipeline.getOperationsSnapshot();
            List<String> opRoots = new ArrayList<>();
            for (Map<String,Object> op : ops) {
                Object meta = op.get("metadata");
                if (meta instanceof Map<?,?>) {
                    Object root = ((Map<?,?>) meta).get("merkle_root");
                    if (root instanceof String) {
                        opRoots.add((String) root);
                    }
                }
            }
            String aiHash = aiState.getLastDecisionHashHex();
            String globalRoot = globalTree.computeGlobalRoot(dynamicRootHex, iterativeRoots, opRoots, aiHash);

            String snapshot = globalRoot;
            String signature = pqcService.sign(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String finalSeedHex = aiState.getLastFinalSeedHex();

            Map<String,Object> out = instanceService.cancel(id, globalRoot, signature, finalSeedHex);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
