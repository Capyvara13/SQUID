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

    public CryptoPipelineController(CryptoPipelineService pipelineService) {
        this.pipelineService = pipelineService;
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
}