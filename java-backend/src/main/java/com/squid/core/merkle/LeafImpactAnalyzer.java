package com.squid.core.merkle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sistema de detecao de impacto de alteracao de leaf.
 * 
 * Quando uma leaf e modificada ou removida, o sistema identifica
 * todas as leafs dependentes que serao afetadas pela operacao.
 * 
 * A detecao ocorre exclusivamente no backend, retornando ao
 * frontend apenas a lista de leafs afetadas.
 */
public class LeafImpactAnalyzer {
    
    private final MerkleTreeStructure treeStructure;
    
    public LeafImpactAnalyzer(MerkleTreeStructure treeStructure) {
        this.treeStructure = treeStructure;
    }
    
    /**
     * Analisa o impacto de remover uma leaf especifica.
     * 
     * Retorna um relatório completo com:
     * - lista de leafs dependentes
     * - caminho na arvore ate a raiz
     * - estimativa de nos afetados
     * - recomendacao de operacao
     */
    public ImpactReport analyzeRemovalImpact(String leafId) {
        MerkleNode targetLeaf = treeStructure.findNode(leafId);
        if (targetLeaf == null || !targetLeaf.isLeaf()) {
            throw new IllegalArgumentException("Leaf nao encontrada: " + leafId);
        }
        
        // detecta todas as leafs dependentes
        Set<String> dependentLeaves = findDependentLeaves(leafId);
        
        // calcula o caminho ate a raiz
        List<String> pathToRoot = calculatePathToRoot(leafId);
        
        // identifica nos intermediarios afetados
        Set<String> affectedNodes = findAffectedNodes(leafId);
        
        return new ImpactReport(
            leafId,
            ImpactType.REMOVAL,
            dependentLeaves,
            pathToRoot,
            affectedNodes,
            targetLeaf.isRootProtected(),
            generateRecommendations(dependentLeaves.size(), targetLeaf)
        );
    }
    
    /**
     * Analisa o impacto de modificar uma leaf especifica.
     */
    public ImpactReport analyzeModificationImpact(String leafId, String newData) {
        MerkleNode targetLeaf = treeStructure.findNode(leafId);
        if (targetLeaf == null || !targetLeaf.isLeaf()) {
            throw new IllegalArgumentException("Leaf nao encontrada: " + leafId);
        }
        
        // para modificacao, as dependencias sao os nos no caminho ate a raiz
        Set<String> dependentLeaves = findDependentLeaves(leafId);
        
        // leafs que referenciam esta leaf diretamente
        Set<String> referencingLeaves = findReferencingLeaves(leafId);
        dependentLeaves.addAll(referencingLeaves);
        
        List<String> pathToRoot = calculatePathToRoot(leafId);
        Set<String> affectedNodes = findAffectedNodes(leafId);
        
        return new ImpactReport(
            leafId,
            ImpactType.MODIFICATION,
            dependentLeaves,
            pathToRoot,
            affectedNodes,
            targetLeaf.isRootProtected(),
            generateRecommendations(dependentLeaves.size(), targetLeaf)
        );
    }
    
    /**
     * Encontra todas as leafs que dependem de uma leaf especifica.
     * 
     * Dependencias incluem:
     * - leafs no mesmo ramo da arvore (compartilham ancestrais)
     * - leafs que referenciam esta leaf em seus dados
     * - leafs que dependem de hashes calculados a partir desta
     */
    private Set<String> findDependentLeaves(String leafId) {
        Set<String> dependents = new HashSet<>();
        MerkleNode leaf = treeStructure.findNode(leafId);
        
        if (leaf == null) return dependents;
        
        // adiciona todas as leafs no mesmo caminho ate a raiz
        Set<String> siblings = findSiblingLeaves(leafId);
        dependents.addAll(siblings);
        
        // adiciona todas as leafs que dependem desta
        for (MerkleNode node : treeStructure.getAllLeaves()) {
            if (!node.getNodeId().equals(leafId)) {
                // verifica se esta leaf depende da target
                if (hasDependency(node, leafId)) {
                    dependents.add(node.getNodeId());
                }
            }
        }
        
        return dependents;
    }
    
    /**
     * Encontra leafs que referenciam uma leaf especifica em seus dados.
     */
    private Set<String> findReferencingLeaves(String leafId) {
        Set<String> referencing = new HashSet<>();
        
        for (MerkleNode node : treeStructure.getAllLeaves()) {
            if (!node.getNodeId().equals(leafId)) {
                String data = node.getData();
                if (data != null && data.contains(leafId)) {
                    referencing.add(node.getNodeId());
                }
            }
        }
        
        return referencing;
    }
    
    /**
     * Verifica se um no depende de outro.
     */
    private boolean hasDependency(MerkleNode node, String targetLeafId) {
        // verifica dependencias declaradas
        if (node.getDependencies().contains(targetLeafId)) {
            return true;
        }
        
        // verifica se compartilha caminho na arvore
        Set<String> nodeSiblings = findSiblingLeaves(node.getNodeId());
        if (nodeSiblings.contains(targetLeafId)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Encontra leafs irmaos (no mesmo nivel e com mesmo pai).
     */
    private Set<String> findSiblingLeaves(String leafId) {
        Set<String> siblings = new HashSet<>();
        MerkleNode leaf = treeStructure.findNode(leafId);
        
        if (leaf == null) return siblings;
        
        String parentId = leaf.getParentId();
        if (parentId == null) return siblings;
        
        for (MerkleNode node : treeStructure.getAllLeaves()) {
            if (!node.getNodeId().equals(leafId) && 
                parentId.equals(node.getParentId())) {
                siblings.add(node.getNodeId());
            }
        }
        
        return siblings;
    }
    
    /**
     * Calcula o caminho da leaf ate a raiz.
     */
    private List<String> calculatePathToRoot(String leafId) {
        List<String> path = new ArrayList<>();
        MerkleNode current = treeStructure.findNode(leafId);
        
        while (current != null) {
            path.add(current.getNodeId());
            String parentId = current.getParentId();
            if (parentId == null) break;
            current = treeStructure.findNode(parentId);
        }
        
        return path;
    }
    
    /**
     * Encontra todos os nos afetados por uma operacao.
     */
    private Set<String> findAffectedNodes(String leafId) {
        Set<String> affected = new HashSet<>();
        
        // todos os nos no caminho ate a raiz precisam ser recalculados
        List<String> path = calculatePathToRoot(leafId);
        affected.addAll(path);
        
        // nos irmaos tambem sao afetados (seu hash muda se o irmao for removido)
        affected.addAll(findSiblingLeaves(leafId));
        
        return affected;
    }
    
    /**
     * Gera recomendacoes baseado no impacto.
     */
    private List<String> generateRecommendations(int dependentCount, MerkleNode targetLeaf) {
        List<String> recommendations = new ArrayList<>();
        
        if (targetLeaf.isRootProtected()) {
            recommendations.add("Esta leaf esta protegida contra remocao (root protegida)");
        }
        
        if (dependentCount == 0) {
            recommendations.add("Nenhuma dependencia detectada - operacao segura");
        } else if (dependentCount < 5) {
            recommendations.add("Poucas dependencias - atualizacao conjunta recomendada");
        } else {
            recommendations.add("Muitas dependencias - considere exclusao em cascata");
        }
        
        if (targetLeaf.isCritical()) {
            recommendations.add("Leaf critica - requer aprovacao adicional");
        }
        
        return recommendations;
    }
    
    /**
     * Executa operacao de exclusao em cascata.
     * Remove a leaf original e todas as dependentes.
     */
    public CascadeResult executeCascadeRemoval(String leafId) {
        ImpactReport report = analyzeRemovalImpact(leafId);
        
        // nao permite remover leaf protegida
        if (report.isRootProtected()) {
            throw new IllegalStateException("Nao e possivel remover leaf protegida");
        }
        
        Set<String> toRemove = new HashSet<>();
        toRemove.add(leafId);
        toRemove.addAll(report.getDependentLeaves());
        
        // remove todas as leafs identificadas
        for (String nodeId : toRemove) {
            treeStructure.removeNode(nodeId);
        }
        
        return new CascadeResult(
            leafId,
            toRemove,
            report.getPathToRoot(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * Executa operacao de atualizacao conjunta.
     * Atualiza a leaf e recalcula hashes das dependentes.
     */
    public JointUpdateResult executeJointUpdate(String leafId, String newData) {
        ImpactReport report = analyzeModificationImpact(leafId, newData);
        
        // atualiza a leaf principal
        treeStructure.updateNodeData(leafId, newData);
        
        // recalcula hashes do caminho ate a raiz
        Set<String> updatedNodes = new HashSet<>();
        for (String nodeId : report.getPathToRoot()) {
            treeStructure.recalculateHash(nodeId);
            updatedNodes.add(nodeId);
        }
        
        // atualiza referencias nas leafs dependentes
        for (String dependentId : report.getDependentLeaves()) {
            treeStructure.updateReferences(dependentId, leafId);
        }
        
        return new JointUpdateResult(
            leafId,
            newData,
            report.getDependentLeaves(),
            updatedNodes,
            treeStructure.getRootHash(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * Enum de tipos de impacto.
     */
    public enum ImpactType {
        REMOVAL,
        MODIFICATION
    }
    
    /**
     * Relatorio de impacto de uma operacao.
     */
    public static class ImpactReport {
        private final String targetLeafId;
        private final ImpactType impactType;
        private final Set<String> dependentLeaves;
        private final List<String> pathToRoot;
        private final Set<String> affectedNodes;
        private final boolean rootProtected;
        private final List<String> recommendations;
        
        public ImpactReport(String targetLeafId, ImpactType impactType,
                           Set<String> dependentLeaves, List<String> pathToRoot,
                           Set<String> affectedNodes, boolean rootProtected,
                           List<String> recommendations) {
            this.targetLeafId = targetLeafId;
            this.impactType = impactType;
            this.dependentLeaves = new HashSet<>(dependentLeaves);
            this.pathToRoot = new ArrayList<>(pathToRoot);
            this.affectedNodes = new HashSet<>(affectedNodes);
            this.rootProtected = rootProtected;
            this.recommendations = new ArrayList<>(recommendations);
        }
        
        public String getTargetLeafId() { return targetLeafId; }
        public ImpactType getImpactType() { return impactType; }
        public Set<String> getDependentLeaves() { return new HashSet<>(dependentLeaves); }
        public int getDependentCount() { return dependentLeaves.size(); }
        public List<String> getPathToRoot() { return new ArrayList<>(pathToRoot); }
        public Set<String> getAffectedNodes() { return new HashSet<>(affectedNodes); }
        public boolean isRootProtected() { return rootProtected; }
        public List<String> getRecommendations() { return new ArrayList<>(recommendations); }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("targetLeafId", targetLeafId);
            map.put("impactType", impactType.name());
            map.put("dependentLeaves", new ArrayList<>(dependentLeaves));
            map.put("dependentCount", dependentLeaves.size());
            map.put("pathToRoot", pathToRoot);
            map.put("affectedNodes", new ArrayList<>(affectedNodes));
            map.put("rootProtected", rootProtected);
            map.put("recommendations", recommendations);
            return map;
        }
    }
    
    /**
     * Resultado de operacao em cascata.
     */
    public static class CascadeResult {
        private final String removedLeafId;
        private final Set<String> allRemovedLeaves;
        private final List<String> pathToRoot;
        private final long timestamp;
        
        public CascadeResult(String removedLeafId, Set<String> allRemovedLeaves,
                            List<String> pathToRoot, long timestamp) {
            this.removedLeafId = removedLeafId;
            this.allRemovedLeaves = new HashSet<>(allRemovedLeaves);
            this.pathToRoot = new ArrayList<>(pathToRoot);
            this.timestamp = timestamp;
        }
        
        public String getRemovedLeafId() { return removedLeafId; }
        public Set<String> getAllRemovedLeaves() { return new HashSet<>(allRemovedLeaves); }
        public List<String> getPathToRoot() { return new ArrayList<>(pathToRoot); }
        public long getTimestamp() { return timestamp; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("removedLeafId", removedLeafId);
            map.put("allRemovedLeaves", new ArrayList<>(allRemovedLeaves));
            map.put("removedCount", allRemovedLeaves.size());
            map.put("pathToRoot", pathToRoot);
            map.put("timestamp", timestamp);
            return map;
        }
    }
    
    /**
     * Resultado de atualizacao conjunta.
     */
    public static class JointUpdateResult {
        private final String updatedLeafId;
        private final String newData;
        private final Set<String> dependentLeaves;
        private final Set<String> updatedNodes;
        private final String newRootHash;
        private final long timestamp;
        
        public JointUpdateResult(String updatedLeafId, String newData,
                                Set<String> dependentLeaves, Set<String> updatedNodes,
                                String newRootHash, long timestamp) {
            this.updatedLeafId = updatedLeafId;
            this.newData = newData;
            this.dependentLeaves = new HashSet<>(dependentLeaves);
            this.updatedNodes = new HashSet<>(updatedNodes);
            this.newRootHash = newRootHash;
            this.timestamp = timestamp;
        }
        
        public String getUpdatedLeafId() { return updatedLeafId; }
        public String getNewData() { return newData; }
        public Set<String> getDependentLeaves() { return new HashSet<>(dependentLeaves); }
        public Set<String> getUpdatedNodes() { return new HashSet<>(updatedNodes); }
        public String getNewRootHash() { return newRootHash; }
        public long getTimestamp() { return timestamp; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("updatedLeafId", updatedLeafId);
            map.put("newData", newData);
            map.put("dependentLeaves", new ArrayList<>(dependentLeaves));
            map.put("updatedNodes", new ArrayList<>(updatedNodes));
            map.put("newRootHash", newRootHash);
            map.put("timestamp", timestamp);
            return map;
        }
    }
}
