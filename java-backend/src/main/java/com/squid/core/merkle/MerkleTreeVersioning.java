package com.squid.core.merkle;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Sistema de versionamento append-only da Merkle Tree.
 * 
 * Cada alteracao estrutural gera uma nova versao da arvore,
 * preservando estados anteriores para rastreabilidade e auditoria.
 * 
 * O banco funciona de forma append-only, garantindo prova
 * criptografica de estados passados.
 */
public class MerkleTreeVersioning {
    
    private final Map<Long, TreeVersion> versions = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // versao atual ativa
    private volatile long currentVersionId = 0;
    
    // instancia associada a este versionamento
    private final String instanceId;
    
    public MerkleTreeVersioning(String instanceId) {
        this.instanceId = instanceId;
        // cria versao genesis (v0)
        createGenesisVersion();
    }
    
    /**
     * Cria a versao genesis da arvore.
     */
    private void createGenesisVersion() {
        lock.writeLock().lock();
        try {
            long genesisId = versionCounter.incrementAndGet();
            TreeVersion genesis = new TreeVersion(
                genesisId,
                "GENESIS",
                new ArrayList<>(),
                "0".repeat(64), // hash vazio representado como zeros
                Instant.now(),
                "SYSTEM",
                "Criacao inicial da arvore"
            );
            versions.put(genesisId, genesis);
            currentVersionId = genesisId;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cria uma nova versao da arvore.
     * 
     * A nova versao e adicionada ao historico sem sobrescrever
     * as versoes anteriores (append-only).
     */
    public TreeVersion createVersion(
            List<MerkleNode> leaves,
            String rootHash,
            String operation,
            String reason) {
        
        lock.writeLock().lock();
        try {
            long newVersionId = versionCounter.incrementAndGet();
            TreeVersion previousVersion = versions.get(currentVersionId);
            
            // cria a nova versao com referencia a anterior
            TreeVersion newVersion = new TreeVersion(
                newVersionId,
                operation,
                new ArrayList<>(leaves),
                rootHash,
                Instant.now(),
                previousVersion != null ? previousVersion.getVersionHash() : "GENESIS",
                reason
            );
            
            versions.put(newVersionId, newVersion);
            currentVersionId = newVersionId;
            
            return newVersion;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Retorna a versao atual da arvore.
     */
    public TreeVersion getCurrentVersion() {
        lock.readLock().lock();
        try {
            return versions.get(currentVersionId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Retorna uma versao especifica pelo ID.
     */
    public TreeVersion getVersion(long versionId) {
        lock.readLock().lock();
        try {
            return versions.get(versionId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Retorna todas as versoes em ordem cronologica.
     */
    public List<TreeVersion> getAllVersions() {
        lock.readLock().lock();
        try {
            List<TreeVersion> sortedVersions = new ArrayList<>(versions.values());
            sortedVersions.sort(Comparator.comparingLong(TreeVersion::getId));
            return sortedVersions;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Retorna o historico de versoes (todas exceto a atual).
     */
    public List<TreeVersion> getVersionHistory() {
        lock.readLock().lock();
        try {
            return versions.values().stream()
                .filter(v -> v.getId() != currentVersionId)
                .sorted(Comparator.comparingLong(TreeVersion::getId).reversed())
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Compara duas versoes e retorna as diferencas.
     */
    public VersionDiff compareVersions(long versionId1, long versionId2) {
        lock.readLock().lock();
        try {
            TreeVersion v1 = versions.get(versionId1);
            TreeVersion v2 = versions.get(versionId2);
            
            if (v1 == null || v2 == null) {
                throw new IllegalArgumentException("Versao nao encontrada");
            }
            
            return new VersionDiff(v1, v2);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Verifica a integridade da cadeia de versoes.
     * Cada versao deve referenciar corretamente a anterior.
     */
    public boolean verifyVersionChain() {
        lock.readLock().lock();
        try {
            List<TreeVersion> sorted = getAllVersions();
            
            for (int i = 1; i < sorted.size(); i++) {
                TreeVersion current = sorted.get(i);
                TreeVersion previous = sorted.get(i - 1);
                
                // verifica se a referencia anterior esta correta
                if (!current.getPreviousVersionHash().equals(previous.getVersionHash())) {
                    return false;
                }
                
                // verifica a integridade do hash da versao
                String expectedHash = current.calculateVersionHash();
                if (!expectedHash.equals(current.getVersionHash())) {
                    return false;
                }
            }
            
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Retorna estatisticas do versionamento.
     */
    public Map<String, Object> getStatistics() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalVersions", versions.size());
            stats.put("currentVersionId", currentVersionId);
            stats.put("instanceId", instanceId);
            stats.put("chainIntegrity", verifyVersionChain());
            
            // contagem por tipo de operacao
            Map<String, Long> operationCounts = new HashMap<>();
            for (TreeVersion v : versions.values()) {
                operationCounts.merge(v.getOperation(), 1L, Long::sum);
            }
            stats.put("operationCounts", operationCounts);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Classe representando uma versao da arvore.
     */
    public static class TreeVersion {
        private final long id;
        private final String operation;
        private final List<MerkleNode> leaves;
        private final String rootHash;
        private final Instant timestamp;
        private final String previousVersionHash;
        private final String reason;
        private final String versionHash;
        
        public TreeVersion(long id, String operation, List<MerkleNode> leaves,
                          String rootHash, Instant timestamp, 
                          String previousVersionHash, String reason) {
            this.id = id;
            this.operation = operation;
            this.leaves = Collections.unmodifiableList(new ArrayList<>(leaves));
            this.rootHash = rootHash;
            this.timestamp = timestamp;
            this.previousVersionHash = previousVersionHash;
            this.reason = reason;
            this.versionHash = calculateVersionHash();
        }
        
        public long getId() { return id; }
        public String getOperation() { return operation; }
        public List<MerkleNode> getLeaves() { return leaves; }
        public String getRootHash() { return rootHash; }
        public Instant getTimestamp() { return timestamp; }
        public String getPreviousVersionHash() { return previousVersionHash; }
        public String getReason() { return reason; }
        public String getVersionHash() { return versionHash; }
        
        /**
         * Calcula o hash desta versao baseado em seus dados.
         */
        public String calculateVersionHash() {
            try {
                java.security.MessageDigest digest = 
                    java.security.MessageDigest.getInstance("SHA-256");
                
                String data = id + operation + rootHash + timestamp.toString() + 
                             previousVersionHash + reason;
                
                // inclui os hashes das leaves
                for (MerkleNode leaf : leaves) {
                    data += leaf.getHash();
                }
                
                byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return "ERROR_" + System.nanoTime();
            }
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("operation", operation);
            map.put("rootHash", rootHash);
            map.put("timestamp", timestamp.toString());
            map.put("previousVersionHash", previousVersionHash);
            map.put("versionHash", versionHash);
            map.put("reason", reason);
            map.put("leafCount", leaves.size());
            return map;
        }
    }
    
    /**
     * Classe representando um no da Merkle Tree.
     */
    public static class MerkleNode {
        private final String nodeId;
        private final String hash;
        private final String state;
        private final int depth;
        private final boolean isLeaf;
        private final List<String> dependencies;
        
        public MerkleNode(String nodeId, String hash, String state, 
                         int depth, boolean isLeaf, List<String> dependencies) {
            this.nodeId = nodeId;
            this.hash = hash;
            this.state = state;
            this.depth = depth;
            this.isLeaf = isLeaf;
            this.dependencies = new ArrayList<>(dependencies);
        }
        
        public String getNodeId() { return nodeId; }
        public String getHash() { return hash; }
        public String getState() { return state; }
        public int getDepth() { return depth; }
        public boolean isLeaf() { return isLeaf; }
        public List<String> getDependencies() { return new ArrayList<>(dependencies); }
    }
    
    /**
     * Classe representando a diferenca entre duas versoes.
     */
    public static class VersionDiff {
        private final TreeVersion version1;
        private final TreeVersion version2;
        private final List<MerkleNode> addedNodes;
        private final List<MerkleNode> removedNodes;
        private final List<MerkleNode> modifiedNodes;
        
        public VersionDiff(TreeVersion v1, TreeVersion v2) {
            this.version1 = v1;
            this.version2 = v2;
            this.addedNodes = new ArrayList<>();
            this.removedNodes = new ArrayList<>();
            this.modifiedNodes = new ArrayList<>();
            
            calculateDifferences();
        }
        
        private void calculateDifferences() {
            Map<String, MerkleNode> nodes1 = new HashMap<>();
            Map<String, MerkleNode> nodes2 = new HashMap<>();
            
            for (MerkleNode node : version1.getLeaves()) {
                nodes1.put(node.getNodeId(), node);
            }
            
            for (MerkleNode node : version2.getLeaves()) {
                nodes2.put(node.getNodeId(), node);
            }
            
            // nos adicionados (em v2 mas nao em v1)
            for (String nodeId : nodes2.keySet()) {
                if (!nodes1.containsKey(nodeId)) {
                    addedNodes.add(nodes2.get(nodeId));
                }
            }
            
            // nos removidos (em v1 mas nao em v2)
            for (String nodeId : nodes1.keySet()) {
                if (!nodes2.containsKey(nodeId)) {
                    removedNodes.add(nodes1.get(nodeId));
                }
            }
            
            // nos modificados (em ambos mas com hash diferente)
            for (String nodeId : nodes1.keySet()) {
                if (nodes2.containsKey(nodeId)) {
                    MerkleNode n1 = nodes1.get(nodeId);
                    MerkleNode n2 = nodes2.get(nodeId);
                    if (!n1.getHash().equals(n2.getHash())) {
                        modifiedNodes.add(n2);
                    }
                }
            }
        }
        
        public TreeVersion getVersion1() { return version1; }
        public TreeVersion getVersion2() { return version2; }
        public List<MerkleNode> getAddedNodes() { return addedNodes; }
        public List<MerkleNode> getRemovedNodes() { return removedNodes; }
        public List<MerkleNode> getModifiedNodes() { return modifiedNodes; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("version1", version1.toMap());
            map.put("version2", version2.toMap());
            map.put("addedCount", addedNodes.size());
            map.put("removedCount", removedNodes.size());
            map.put("modifiedCount", modifiedNodes.size());
            return map;
        }
    }
}
