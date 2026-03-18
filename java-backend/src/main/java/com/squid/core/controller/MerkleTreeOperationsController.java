package com.squid.core.controller;

import com.squid.core.lock.InstanceLockManager;
import com.squid.core.merkle.*;
import com.squid.core.optimization.OptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller para operacoes avancadas da Merkle Tree.
 * 
 * Suporta:
 * - visualizacao hierarquica completa
 * - deteccao de impacto de alteracao de leaf
 * - versionamento da arvore
 * - operacoes protegidas por lock de instancia
 */
@RestController
@RequestMapping("/api/merkle")
public class MerkleTreeOperationsController {
    
    private final Map<String, MerkleTreeStructure> instanceTrees = new ConcurrentHashMap<>();
    private final Map<String, MerkleTreeVersioning> instanceVersions = new ConcurrentHashMap<>();
    private final InstanceLockManager lockManager;
    private final OptimizationService optimizationService;
    
    public MerkleTreeOperationsController(InstanceLockManager lockManager,
                                         OptimizationService optimizationService) {
        this.lockManager = lockManager;
        this.optimizationService = optimizationService;
    }
    
    /**
     * Retorna a estrutura hierarquica completa da arvore.
     */
    @GetMapping("/{instanceId}/structure")
    public ResponseEntity<Map<String, Object>> getTreeStructure(@PathVariable String instanceId) {
        MerkleTreeStructure tree = getOrCreateTree(instanceId);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("instanceId", instanceId);
        response.put("rootHash", tree.getRootHash());
        
        // constroi representacao hierarquica
        List<Map<String, Object>> hierarchicalNodes = buildHierarchicalStructure(tree);
        response.put("tree", hierarchicalNodes);
        
        // estatisticas
        response.put("totalNodes", tree.getAllNodes().size());
        response.put("totalLeaves", tree.getAllLeaves().size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Analisa o impacto de remover uma leaf.
     */
    @PostMapping("/{instanceId}/impact/removal")
    public ResponseEntity<Map<String, Object>> analyzeRemovalImpact(
            @PathVariable String instanceId,
            @RequestBody Map<String, String> request) {
        
        String leafId = request.get("leafId");
        if (leafId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "leafId obrigatorio"));
        }
        
        MerkleTreeStructure tree = getOrCreateTree(instanceId);
        LeafImpactAnalyzer analyzer = new LeafImpactAnalyzer(tree);
        
        try {
            LeafImpactAnalyzer.ImpactReport report = analyzer.analyzeRemovalImpact(leafId);
            return ResponseEntity.ok(report.toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Analisa o impacto de modificar uma leaf.
     */
    @PostMapping("/{instanceId}/impact/modification")
    public ResponseEntity<Map<String, Object>> analyzeModificationImpact(
            @PathVariable String instanceId,
            @RequestBody Map<String, String> request) {
        
        String leafId = request.get("leafId");
        String newData = request.get("newData");
        
        if (leafId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "leafId obrigatorio"));
        }
        
        MerkleTreeStructure tree = getOrCreateTree(instanceId);
        LeafImpactAnalyzer analyzer = new LeafImpactAnalyzer(tree);
        
        try {
            LeafImpactAnalyzer.ImpactReport report = analyzer.analyzeModificationImpact(leafId, newData);
            return ResponseEntity.ok(report.toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Executa exclusao em cascata de uma leaf.
     */
    @PostMapping("/{instanceId}/cascade-remove")
    public ResponseEntity<Map<String, Object>> executeCascadeRemoval(
            @PathVariable String instanceId,
            @RequestBody Map<String, String> request) {
        
        String leafId = request.get("leafId");
        if (leafId == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "leafId obrigatorio"));
        }
        
        String operationId = UUID.randomUUID().toString();
        
        // adquire lock de escrita
        boolean lockAcquired = lockManager.acquireWriteLock(instanceId, operationId);
        if (!lockAcquired) {
            return ResponseEntity.status(423) // Locked
                .body(Map.of("error", "Instancia bloqueada para operacoes de escrita"));
        }
        
        try {
            MerkleTreeStructure tree = getOrCreateTree(instanceId);
            LeafImpactAnalyzer analyzer = new LeafImpactAnalyzer(tree);
            
            LeafImpactAnalyzer.CascadeResult result = analyzer.executeCascadeRemoval(leafId);
            
            // cria nova versao da arvore
            MerkleTreeVersioning versioning = getOrCreateVersioning(instanceId);
            versioning.createVersion(
                tree.getAllLeaves(),
                tree.getRootHash(),
                "CASCADE_REMOVAL",
                "Remocao em cascata da leaf " + leafId
            );
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operation", "cascade_removal");
            response.put("result", result.toMap());
            response.put("newRootHash", tree.getRootHash());
            response.put("versioningEnabled", true);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } finally {
            lockManager.releaseLock(instanceId, operationId);
        }
    }
    
    /**
     * Executa atualizacao conjunta de uma leaf.
     */
    @PostMapping("/{instanceId}/joint-update")
    public ResponseEntity<Map<String, Object>> executeJointUpdate(
            @PathVariable String instanceId,
            @RequestBody Map<String, String> request) {
        
        String leafId = request.get("leafId");
        String newData = request.get("newData");
        
        if (leafId == null || newData == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "leafId e newData obrigatorios"));
        }
        
        String operationId = UUID.randomUUID().toString();
        
        // adquire lock de escrita
        boolean lockAcquired = lockManager.acquireWriteLock(instanceId, operationId);
        if (!lockAcquired) {
            return ResponseEntity.status(423)
                .body(Map.of("error", "Instancia bloqueada para operacoes de escrita"));
        }
        
        try {
            MerkleTreeStructure tree = getOrCreateTree(instanceId);
            LeafImpactAnalyzer analyzer = new LeafImpactAnalyzer(tree);
            
            LeafImpactAnalyzer.JointUpdateResult result = analyzer.executeJointUpdate(leafId, newData);
            
            // cria nova versao da arvore
            MerkleTreeVersioning versioning = getOrCreateVersioning(instanceId);
            versioning.createVersion(
                tree.getAllLeaves(),
                tree.getRootHash(),
                "JOINT_UPDATE",
                "Atualizacao conjunta da leaf " + leafId
            );
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operation", "joint_update");
            response.put("result", result.toMap());
            response.put("versioningEnabled", true);
            
            return ResponseEntity.ok(response);
            
        } finally {
            lockManager.releaseLock(instanceId, operationId);
        }
    }
    
    /**
     * Retorna todas as versoes da arvore.
     */
    @GetMapping("/{instanceId}/versions")
    public ResponseEntity<Map<String, Object>> getAllVersions(@PathVariable String instanceId) {
        MerkleTreeVersioning versioning = getOrCreateVersioning(instanceId);
        
        List<Map<String, Object>> versions = new ArrayList<>();
        for (MerkleTreeVersioning.TreeVersion version : versioning.getAllVersions()) {
            versions.add(version.toMap());
        }
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("instanceId", instanceId);
        response.put("versions", versions);
        response.put("totalVersions", versions.size());
        response.put("currentVersionId", versioning.getCurrentVersion().getId());
        response.put("chainIntegrity", versioning.verifyVersionChain());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Retorna uma versao especifica.
     */
    @GetMapping("/{instanceId}/versions/{versionId}")
    public ResponseEntity<Map<String, Object>> getVersion(
            @PathVariable String instanceId,
            @PathVariable long versionId) {
        
        MerkleTreeVersioning versioning = getOrCreateVersioning(instanceId);
        MerkleTreeVersioning.TreeVersion version = versioning.getVersion(versionId);
        
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(version.toMap());
    }
    
    /**
     * Compara duas versoes.
     */
    @GetMapping("/{instanceId}/compare")
    public ResponseEntity<Map<String, Object>> compareVersions(
            @PathVariable String instanceId,
            @RequestParam long version1,
            @RequestParam long version2) {
        
        MerkleTreeVersioning versioning = getOrCreateVersioning(instanceId);
        
        try {
            MerkleTreeVersioning.VersionDiff diff = versioning.compareVersions(version1, version2);
            return ResponseEntity.ok(diff.toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Retorna o status de lock de uma instancia.
     */
    @GetMapping("/{instanceId}/lock-status")
    public ResponseEntity<Map<String, Object>> getLockStatus(@PathVariable String instanceId) {
        InstanceLockManager.LockStatus status = lockManager.getLockStatus(instanceId);
        return ResponseEntity.ok(status.toMap());
    }
    
    /**
     * Retorna as metricas de otimizacao.
     */
    @GetMapping("/optimization/status")
    public ResponseEntity<Map<String, Object>> getOptimizationStatus() {
        return ResponseEntity.ok(optimizationService.getStatus());
    }
    
    /**
     * Ativa ou desativa otimizacao.
     */
    @PostMapping("/optimization/toggle")
    public ResponseEntity<Map<String, Object>> toggleOptimization(
            @RequestBody Map<String, Boolean> request) {
        
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "enabled obrigatorio"));
        }
        
        optimizationService.setEnabled(enabled);
        
        return ResponseEntity.ok(Map.of(
            "enabled", enabled,
            "status", optimizationService.getStatus()
        ));
    }
    
    // ===== METODOS AUXILIARES =====
    
    private MerkleTreeStructure getOrCreateTree(String instanceId) {
        return instanceTrees.computeIfAbsent(instanceId, id -> {
            MerkleTreeStructure tree = new MerkleTreeStructure(id);
            // inicializa com dados de exemplo
            tree.buildFromLeaves(Arrays.asList(
                "genesis_" + System.nanoTime(),
                "initial_" + System.nanoTime(),
                "seed_" + System.nanoTime()
            ));
            return tree;
        });
    }
    
    private MerkleTreeVersioning getOrCreateVersioning(String instanceId) {
        return instanceVersions.computeIfAbsent(instanceId, MerkleTreeVersioning::new);
    }
    
    private List<Map<String, Object>> buildHierarchicalStructure(MerkleTreeStructure tree) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // encontra a raiz
        MerkleTreeStructure.MerkleNode root = null;
        for (MerkleTreeStructure.MerkleNode node : tree.getAllNodes()) {
            if (node.isRoot()) {
                root = node;
                break;
            }
        }
        
        if (root == null && !tree.getAllNodes().isEmpty()) {
            // se nao houver raiz marcada, usa o no de maior nivel
            root = tree.getAllNodes().iterator().next();
        }
        
        if (root != null) {
            result.add(nodeToMap(root, tree, 0));
        }
        
        return result;
    }
    
    private Map<String, Object> nodeToMap(MerkleTreeStructure.MerkleNode node, 
                                          MerkleTreeStructure tree, int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.getNodeId());
        map.put("hash", node.getHash());
        map.put("hashShort", node.getHash().substring(0, Math.min(16, node.getHash().length())) + "...");
        map.put("state", node.getState());
        map.put("depth", depth);
        map.put("isLeaf", node.isLeaf());
        map.put("isRoot", node.isRoot());
        
        // encontra filhos (nos que tem este como pai)
        List<Map<String, Object>> children = new ArrayList<>();
        for (MerkleTreeStructure.MerkleNode n : tree.getAllNodes()) {
            if (node.getNodeId().equals(n.getParentId())) {
                children.add(nodeToMap(n, tree, depth + 1));
            }
        }
        
        if (!children.isEmpty()) {
            map.put("children", children);
        }
        
        return map;
    }
}
