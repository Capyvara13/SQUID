package com.squid.core.controller;

import com.squid.core.service.ServiceRequirementsChecker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller para verificacao de requisitos de servicos.
 * 
 * Fornece endpoints para checar se todos os requisitos
 * necessarios estao satisfeitos antes de iniciar servicos.
 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementsController {
    
    private final ServiceRequirementsChecker requirementsChecker;
    
    public RequirementsController(ServiceRequirementsChecker requirementsChecker) {
        this.requirementsChecker = requirementsChecker;
    }
    
    /**
     * Verifica todos os requisitos e retorna status completo.
     * 
     * Retorna uma tabela com:
     * - Servico | Status | Requisitos | Problemas Detectados
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAllRequirements() {
        Map<String, Object> result = requirementsChecker.checkAllRequirements();
        return ResponseEntity.ok(result);
    }
    
    /**
     * Verifica se o sistema pode iniciar.
     */
    @GetMapping("/can-start")
    public ResponseEntity<Map<String, Object>> canSystemStart() {
        boolean canStart = requirementsChecker.canSystemStart();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("canStart", canStart);
        response.put("status", canStart ? "READY" : "BLOCKED");
        response.put("timestamp", new Date().toString());
        
        if (!canStart) {
            // inclui detalhes dos requisitos falhos
            Map<String, Object> fullCheck = requirementsChecker.checkAllRequirements();
            response.put("details", fullCheck.get("checkResults"));
            response.put("correctiveActions", fullCheck.get("correctiveActions"));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verifica um requisito especifico.
     */
    @GetMapping("/check/{requirementId}")
    public ResponseEntity<Map<String, Object>> checkRequirement(
            @PathVariable String requirementId) {
        
        ServiceRequirementsChecker.ServiceCheckResult result = 
            requirementsChecker.checkRequirement(requirementId);
        
        return ResponseEntity.ok(result.toMap());
    }
    
    /**
     * Retorna o status de todos os requisitos.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllStatus() {
        Map<String, ServiceRequirementsChecker.ServiceCheckResult> results = 
            requirementsChecker.getAllResults();
        
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> statusList = new ArrayList<>();
        
        int passed = 0;
        int failed = 0;
        
        for (ServiceRequirementsChecker.ServiceCheckResult result : results.values()) {
            statusList.add(result.toMap());
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        
        response.put("services", statusList);
        response.put("passed", passed);
        response.put("failed", failed);
        response.put("total", results.size());
        
        return ResponseEntity.ok(response);
    }
}
