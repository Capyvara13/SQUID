package com.squid.core.merkle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estrutura da Merkle Tree para analise de dependencias.
 * 
 * Representa a arvore completa com todos os nos e suas
 * relacoes, permitindo analise de impacto de alteracoes.
 */
public class MerkleTreeStructure {
    
    private final Map<String, MerkleNode> nodes = new ConcurrentHashMap<>();
    private final AtomicReference<String> rootId = new AtomicReference<>();
    private final String instanceId;
    
    public MerkleTreeStructure(String instanceId) {
        this.instanceId = instanceId;
    }
    
    /**
     * Adiciona um no a arvore.
     */
    public void addNode(MerkleNode node) {
        nodes.put(node.getNodeId(), node);
        if (node.isRoot()) {
            rootId.set(node.getNodeId());
        }
    }
    
    /**
     * Encontra um no pelo ID.
     */
    public MerkleNode findNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    /**
     * Retorna todas as leafs da arvore.
     */
    public List<MerkleNode> getAllLeaves() {
        return nodes.values().stream()
            .filter(MerkleNode::isLeaf)
            .toList();
    }
    
    /**
     * Remove um no da arvore.
     */
    public void removeNode(String nodeId) {
        MerkleNode node = nodes.remove(nodeId);
        if (node != null && node.isRoot()) {
            // recalcula a raiz
            recalculateRoot();
        }
    }
    
    /**
     * Atualiza os dados de um no.
     */
    public void updateNodeData(String nodeId, String newData) {
        MerkleNode node = nodes.get(nodeId);
        if (node != null) {
            node.updateData(newData);
        }
    }
    
    /**
     * Recalcula o hash de um no.
     */
    public void recalculateHash(String nodeId) {
        MerkleNode node = nodes.get(nodeId);
        if (node != null) {
            node.recalculateHash();
        }
    }
    
    /**
     * Atualiza referencias em um no.
     */
    public void updateReferences(String nodeId, String targetLeafId) {
        MerkleNode node = nodes.get(nodeId);
        if (node != null) {
            node.updateReferences(targetLeafId);
        }
    }
    
    /**
     * Retorna o hash da raiz atual.
     */
    public String getRootHash() {
        String root = rootId.get();
        if (root == null) return null;
        
        MerkleNode rootNode = nodes.get(root);
        return rootNode != null ? rootNode.getHash() : null;
    }
    
    /**
     * Recalcula a raiz da arvore.
     */
    private void recalculateRoot() {
        // encontra o no mais alto (menor profundidade)
        Optional<MerkleNode> newRoot = nodes.values().stream()
            .min(Comparator.comparingInt(MerkleNode::getDepth));
        
        newRoot.ifPresent(node -> rootId.set(node.getNodeId()));
    }
    
    /**
     * Retorna todos os nos.
     */
    public Collection<MerkleNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }
    
    /**
     * Constroi a arvore a partir de uma lista de leafs.
     */
    public void buildFromLeaves(List<String> leafData) {
        nodes.clear();
        
        // cria leafs
        List<MerkleNode> leaves = new ArrayList<>();
        for (int i = 0; i < leafData.size(); i++) {
            MerkleNode leaf = new MerkleNode(
                "leaf_" + i,
                leafData.get(i),
                "VALID",
                0,
                true,
                new ArrayList<>(),
                calculateHash(leafData.get(i)),
                null,
                false
            );
            leaves.add(leaf);
            nodes.put(leaf.getNodeId(), leaf);
        }
        
        // constroi niveis intermediarios ate a raiz
        buildTreeLevels(leaves);
    }
    
    /**
     * Constroi os niveis intermediarios da arvore.
     */
    private void buildTreeLevels(List<MerkleNode> currentLevel) {
        if (currentLevel.size() <= 1) {
            if (currentLevel.size() == 1) {
                rootId.set(currentLevel.get(0).getNodeId());
                currentLevel.get(0).setRoot(true);
            }
            return;
        }
        
        List<MerkleNode> nextLevel = new ArrayList<>();
        int level = currentLevel.get(0).getDepth() + 1;
        
        for (int i = 0; i < currentLevel.size(); i += 2) {
            MerkleNode left = currentLevel.get(i);
            MerkleNode right = (i + 1 < currentLevel.size()) ? 
                currentLevel.get(i + 1) : left;
            
            String combinedHash = calculateHash(left.getHash() + right.getHash());
            
            MerkleNode parent = new MerkleNode(
                "node_" + level + "_" + (i / 2),
                null,
                "VALID",
                level,
                false,
                Arrays.asList(left.getNodeId(), right.getNodeId()),
                combinedHash,
                null,
                false
            );
            
            left.setParentId(parent.getNodeId());
            right.setParentId(parent.getNodeId());
            
            nextLevel.add(parent);
            nodes.put(parent.getNodeId(), parent);
        }
        
        buildTreeLevels(nextLevel);
    }
    
    /**
     * Calcula hash simples (usado para construcao da arvore).
     */
    private String calculateHash(String data) {
        try {
            java.security.MessageDigest digest = 
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR_" + System.nanoTime();
        }
    }
    
    /**
     * Classe representando um no da arvore.
     */
    public static class MerkleNode {
        private final String nodeId;
        private String data;
        private String state;
        private final int depth;
        private final boolean isLeaf;
        private final List<String> dependencies;
        private String hash;
        private String parentId;
        private boolean isRoot;
        private boolean isCritical;
        private boolean isRootProtected;
        
        public MerkleNode(String nodeId, String data, String state, int depth,
                         boolean isLeaf, List<String> dependencies, String hash,
                         String parentId, boolean isCritical) {
            this.nodeId = nodeId;
            this.data = data;
            this.state = state;
            this.depth = depth;
            this.isLeaf = isLeaf;
            this.dependencies = new ArrayList<>(dependencies);
            this.hash = hash;
            this.parentId = parentId;
            this.isCritical = isCritical;
            this.isRootProtected = false;
        }
        
        public String getNodeId() { return nodeId; }
        public String getData() { return data; }
        public String getState() { return state; }
        public int getDepth() { return depth; }
        public boolean isLeaf() { return isLeaf; }
        public List<String> getDependencies() { return new ArrayList<>(dependencies); }
        public String getHash() { return hash; }
        public String getParentId() { return parentId; }
        public boolean isRoot() { return isRoot; }
        public boolean isCritical() { return isCritical; }
        public boolean isRootProtected() { return isRootProtected; }
        
        public void setParentId(String parentId) { this.parentId = parentId; }
        public void setRoot(boolean root) { this.isRoot = root; }
        
        public void updateData(String newData) {
            this.data = newData;
            this.hash = calculateHash(newData);
        }
        
        public void recalculateHash() {
            if (data != null) {
                this.hash = calculateHash(data);
            }
        }
        
        public void updateReferences(String targetLeafId) {
            // atualiza referencias se necessario
            // implementacao depende da logica de negocio
        }
        
        private String calculateHash(String input) {
            try {
                java.security.MessageDigest digest = 
                    java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return "HASH_ERROR";
            }
        }
    }
}
