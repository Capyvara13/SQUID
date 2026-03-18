package com.squid.core.controller;

import com.squid.core.model.EncryptDecryptModels;
import com.squid.core.service.CryptoPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * REST controller exposing the explicit Encrypt / Decrypt pipeline.
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/crypto")
public class CryptoPipelineController {

    private final CryptoPipelineService pipelineService;
    private final com.squid.core.service.IterativeSeedEngine iterativeSeedEngine;

    public CryptoPipelineController(CryptoPipelineService pipelineService) {
        this.pipelineService = pipelineService;
        this.iterativeSeedEngine = null;
    }

    public CryptoPipelineController(CryptoPipelineService pipelineService,
                                    com.squid.core.service.IterativeSeedEngine iterativeSeedEngine) {
        this.pipelineService = pipelineService;
        this.iterativeSeedEngine = iterativeSeedEngine;
    }

    @PostMapping("/encrypt")
    public ResponseEntity<EncryptDecryptModels.EncryptResponse> encrypt(
            @Valid @RequestBody EncryptDecryptModels.EncryptRequest request) {
        try {
            return ResponseEntity.ok(pipelineService.encrypt(request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/decrypt")
    public ResponseEntity<EncryptDecryptModels.DecryptResponse> decrypt(
            @Valid @RequestBody EncryptDecryptModels.DecryptRequest request) {
        try {
            return ResponseEntity.ok(pipelineService.decrypt(request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Expose recent crypto operations for dashboards.
     */
    @GetMapping("/operations")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getOperations() {
        try {
            return ResponseEntity.ok(pipelineService.getOperationsSnapshot());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Iterative seed run: Seed -> 2 Leafs -> Encrypt -> Promotion, repeated to depth.
     * Body: { "seed": "hex|base64|string", "depth": 3 }
     */
    @PostMapping("/iterative-seed-run")
    public ResponseEntity<?> iterativeSeedRun(@RequestBody java.util.Map<String,Object> body) {
        try {
            if (iterativeSeedEngine == null) {
                return ResponseEntity.internalServerError().body(java.util.Collections.singletonMap("error", "iterative_engine_unavailable"));
            }
            Object seedObj = body.get("initialSeed");
            if (seedObj == null) seedObj = body.get("seed"); // fallback to "seed" if "initialSeed" is missing

            int depth = 1;
            Object dObj = body.get("depth");
            if (dObj instanceof Number) {
                depth = Math.max(1, ((Number) dObj).intValue());
            }
            byte[] seedBytes;
            if (seedObj instanceof String) {
                String s = (String) seedObj;
                try {
                    // Try hex first
                    seedBytes = s.matches("^[0-9a-fA-F]+$") ? hexToBytes(s) : s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    seedBytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "invalid_seed"));
            }
            var res = iterativeSeedEngine.run(seedBytes, depth);
            java.util.Map<String,Object> out = new java.util.LinkedHashMap<>();
            out.put("finalSeedHex", res.finalSeedHex);
            out.put("depth", res.depth);
            out.put("createdAt", res.createdAt);
            out.put("levels", res.levels);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace(); // Log the full stack trace to console
            java.util.Map<String, String> error = new java.util.HashMap<>();
            error.put("error", "iterative_run_failed");
            error.put("details", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
