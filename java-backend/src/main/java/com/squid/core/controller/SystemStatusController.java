package com.squid.core.controller;

import com.squid.core.service.CryptoPipelineService;
import com.squid.core.service.SystemStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Global system health and observability endpoints for dashboards.
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final SystemStatusService statusService;
    private final CryptoPipelineService cryptoPipelineService;

    public SystemStatusController(SystemStatusService statusService,
                                  CryptoPipelineService cryptoPipelineService) {
        this.statusService = statusService;
        this.cryptoPipelineService = cryptoPipelineService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            Map<String, Object> body = new HashMap<>(statusService.getGlobalStatus());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Convenience endpoint returning a compact summary for cryptographic
     * operations, suitable for the QT and React dashboards.
     */
    @GetMapping("/crypto-summary")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getCryptoSummary() {
        try {
            return ResponseEntity.ok(cryptoPipelineService.getOperationsSnapshot());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}