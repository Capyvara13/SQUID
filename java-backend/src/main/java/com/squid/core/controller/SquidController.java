package com.squid.core.controller;

import com.squid.core.model.*;
import com.squid.core.service.SquidCoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/v1")
public class SquidController {

    @Autowired
    private SquidCoreService squidService;

    @PostMapping("/generate")
    public ResponseEntity<EnhancedGenerateResponse> generate(@Valid @RequestBody GenerateRequest request) {
        try {
            EnhancedGenerateResponse response = squidService.generate(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request) {
        try {
            VerifyResponse response = squidService.verify(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/public/root")
    public ResponseEntity<PublicRootResponse> getPublicRoot() {
        try {
            PublicRootResponse response = squidService.getPublicRoot();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get leaf history records created during AI decisions
     */
    @GetMapping("/leaf/history")
    public ResponseEntity<List<com.squid.core.model.LeafHistory>> getLeafHistory() {
        try {
            List<com.squid.core.model.LeafHistory> history = squidService.getLeafHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SQUID Core is running");
    }
}
