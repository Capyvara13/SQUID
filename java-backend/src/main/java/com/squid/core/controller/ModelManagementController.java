package com.squid.core.controller;

import com.squid.core.model.ModelHistoryEntry;
import com.squid.core.model.ModelMetadata;
import com.squid.core.service.ModelManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for model management, history, and switching
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/models")
public class ModelManagementController {

    @Autowired
    private ModelManagementService modelManagementService;

    /**
     * Get the currently active model
     * GET /api/v1/models/active
     */
    @GetMapping("/active")
    public ResponseEntity<ModelMetadata> getActiveModel() {
        try {
            ModelMetadata active = modelManagementService.getActiveModel();
            if (active == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(active);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all registered models
     * GET /api/v1/models/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<ModelMetadata>> listModels() {
        try {
            List<ModelMetadata> models = modelManagementService.listModels();
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get a specific model by version
     * GET /api/v1/models/{version}
     */
    @GetMapping("/{version}")
    public ResponseEntity<ModelMetadata> getModel(@PathVariable String version) {
        try {
            return modelManagementService.getModelByVersion(version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Register a new model
     * POST /api/v1/models/register
     * Body: {
     *   "version": "1.0.1",
     *   "architecture": "PyTorch[13->128->64->4]",
     *   "description": "Improved model with better accuracy",
     *   "metrics": {
     *     "loss": 0.12,
     *     "accuracy": 0.96,
     *     "f1_score": 0.95
     *   }
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<ModelMetadata> registerModel(@RequestBody Map<String, Object> request) {
        try {
            String version = (String) request.get("version");
            String architecture = (String) request.get("architecture");
            String description = (String) request.get("description");
            Map<String, Double> metrics = (Map<String, Double>) request.get("metrics");

            if (version == null || version.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            ModelMetadata model = modelManagementService.registerModel(
                version, architecture, description, metrics
            );
            return ResponseEntity.ok(model);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Switch to a different model version
     * POST /api/v1/models/switch
     * Body: {
     *   "version": "1.0.1",
     *   "reason": "Performance improvement",
     *   "initiator": "admin"
     * }
     */
    @PostMapping("/switch")
    public ResponseEntity<ModelMetadata> switchModel(@RequestBody Map<String, String> request) {
        try {
            String version = request.get("version");
            String reason = request.get("reason");
            String initiator = request.get("initiator");

            if (version == null || version.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            ModelMetadata model = modelManagementService.switchModel(version, reason, initiator);
            return ResponseEntity.ok(model);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get complete model history
     * GET /api/v1/models/history/all
     */
    @GetMapping("/history/all")
    public ResponseEntity<List<ModelHistoryEntry>> getHistory() {
        try {
            List<ModelHistoryEntry> history = modelManagementService.getHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get history for a specific model version
     * GET /api/v1/models/history/{version}
     */
    @GetMapping("/history/{version}")
    public ResponseEntity<List<ModelHistoryEntry>> getHistoryByVersion(@PathVariable String version) {
        try {
            List<ModelHistoryEntry> history = modelManagementService.getHistoryByVersion(version);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update metrics for a model
     * PUT /api/v1/models/{version}/metrics
     * Body: {
     *   "loss": 0.11,
     *   "accuracy": 0.97,
     *   "f1_score": 0.96
     * }
     */
    @PutMapping("/{version}/metrics")
    public ResponseEntity<Void> updateMetrics(
            @PathVariable String version,
            @RequestBody Map<String, Double> metrics) {
        try {
            modelManagementService.updateModelMetrics(version, metrics);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get model statistics
     * GET /api/v1/models/stats/overview
     */
    @GetMapping("/stats/overview")
    public ResponseEntity<Map<String, Object>> getModelStatistics() {
        try {
            Map<String, Object> stats = modelManagementService.getModelStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
