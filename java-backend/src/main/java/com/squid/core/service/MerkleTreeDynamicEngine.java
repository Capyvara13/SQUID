package com.squid.core.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Autonomous Merkle Tree Dynamic Engine
 * Continuously transforms tree state through:
 * - Node transitions (Decoy ↔ Valid)
 * - Integrity changes
 * - Node creation/replacement
 * - Subtree recalculation
 * - Root hash updates
 * 
 * System operates as a "living" adaptive structure with autonomous state changes
 */
public class MerkleTreeDynamicEngine {
    
    public enum NodeState {
        VALID,              // Green - valid data
        DECOY,              // Orange - decoy/placeholder
        TRANSITIONING,      // Purple - in transition
        COMPROMISED         // Red - integrity failed
    }
    
    public static final Map<NodeState, String> STATE_COLORS = new HashMap<NodeState, String>() {{
        put(NodeState.VALID, "#10b981");
        put(NodeState.DECOY, "#f59e0b");
        put(NodeState.TRANSITIONING, "#8b5cf6");
        put(NodeState.COMPROMISED, "#ef4444");
    }};
    
    public static class TreeNode {
        public String nodeId;
        public String parentId;
        public List<String> childrenIds;
        public byte[] dataHash;
        public NodeState state;
        public NodeState previousState;
        public boolean integrityValid;
        public long lastTransitionTime;
        public int transitionCount;
        public String metadata;
        
        public TreeNode(String nodeId, byte[] dataHash, NodeState state) {
            this.nodeId = nodeId;
            this.dataHash = dataHash;
            this.state = state;
            this.previousState = state;
            this.childrenIds = new ArrayList<>();
            this.integrityValid = true;
            this.lastTransitionTime = System.currentTimeMillis();
            this.transitionCount = 0;
            this.metadata = "";
        }
        
        public boolean isLeaf() {
            return childrenIds.isEmpty();
        }
    }
    
    public static class DynamicTransition {
        public String transitionId;
        public String nodeId;
        public NodeState fromState;
        public NodeState toState;
        public long timestamp;
        public String reason;
        public byte[] previousHash;
        public byte[] newHash;
        public List<String> affectedNodeIds;
        
        public DynamicTransition(String nodeId, NodeState from, NodeState to, String reason) {
            this.transitionId = UUID.randomUUID().toString();
            this.nodeId = nodeId;
            this.fromState = from;
            this.toState = to;
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
            this.affectedNodeIds = new ArrayList<>();
        }
    }
    
    private final Map<String, TreeNode> nodes = new ConcurrentHashMap<>();
    private final List<DynamicTransition> transitionLog = new CopyOnWriteArrayList<>();
    private byte[] rootHash;
    private String rootNodeId;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean isRunning = false;
    private final Random random = new Random();
    private long autonomousTransitionIntervalMs = 2000; // Every 2 seconds
    private int decoyNodesPercentage = 20; // 20% decoy nodes
    
    public MerkleTreeDynamicEngine(List<String> initialLeaves) throws NoSuchAlgorithmException {
        initializeTree(initialLeaves);
        startAutonomousEngine();
    }
    
    /**
     * Initialize tree with given data leaves
     */
    private void initializeTree(List<String> initialLeaves) throws NoSuchAlgorithmException {
        nodes.clear();
        transitionLog.clear();
        
        // Create leaf nodes
        List<String> leafNodeIds = new ArrayList<>();
        for (int i = 0; i < initialLeaves.size(); i++) {
            String leafId = "leaf_" + i + "_" + System.nanoTime();
            byte[] hash = hashData(initialLeaves.get(i).getBytes());
            
            // 20% chance to be decoy
            NodeState state = random.nextInt(100) < decoyNodesPercentage ? 
                NodeState.DECOY : NodeState.VALID;
            
            TreeNode leaf = new TreeNode(leafId, hash, state);
            nodes.put(leafId, leaf);
            leafNodeIds.add(leafId);
        }
        
        // Build tree bottom-up
        List<String> currentLevel = leafNodeIds;
        int level = 0;
        
        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String parentId = "node_L" + level + "_" + (i / 2) + "_" + System.nanoTime();
                
                TreeNode left = nodes.get(currentLevel.get(i));
                TreeNode right = nodes.get(i + 1 < currentLevel.size() ? 
                    currentLevel.get(i + 1) : currentLevel.get(i));
                
                byte[] parentHash = hashPair(left.dataHash, right.dataHash);
                TreeNode parent = new TreeNode(parentId, parentHash, NodeState.VALID);
                parent.childrenIds.add(left.nodeId);
                parent.childrenIds.add(right.nodeId);
                
                left.parentId = parentId;
                right.parentId = parentId;
                
                nodes.put(parentId, parent);
                nextLevel.add(parentId);
            }
            
            currentLevel = nextLevel;
            level++;
        }
        
        // Set root
        if (!currentLevel.isEmpty()) {
            this.rootNodeId = currentLevel.get(0);
            this.rootHash = nodes.get(rootNodeId).dataHash;
        }
    }
    
    /**
     * Start autonomous engine - triggers continuous state transitions
     */
    public void startAutonomousEngine() {
        if (isRunning) return;
        isRunning = true;
        
        // Schedule autonomous transitions every N milliseconds
        scheduler.scheduleAtFixedRate(this::performAutonomousTransition, 
            autonomousTransitionIntervalMs, 
            autonomousTransitionIntervalMs, 
            TimeUnit.MILLISECONDS);
        
        // Schedule integrity checks
        scheduler.scheduleAtFixedRate(this::performIntegrityUpdates, 
            3000, 
            3000, 
            TimeUnit.MILLISECONDS);
    }
    
    /**
     * Perform autonomous node state transition (Decoy ↔ Valid)
     */
    private void performAutonomousTransition() {
        if (nodes.isEmpty()) return;
        
        try {
            // Select random nodes for transition
            List<TreeNode> candidateNodes = nodes.values().stream()
                .filter(n -> n.state != NodeState.TRANSITIONING)
                .collect(Collectors.toList());
            
            if (candidateNodes.isEmpty()) return;
            
            // Transition 1-3 random nodes
            int transitionCount = 1 + random.nextInt(3);
            for (int i = 0; i < transitionCount && i < candidateNodes.size(); i++) {
                TreeNode node = candidateNodes.get(random.nextInt(candidateNodes.size()));
                
                // Determine new state
                NodeState newState = determineNextState(node);
                performTransition(node, newState, "autonomous_cycle");
            }
            
            // Recalculate affected subtrees
            recalculateRootHash();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Determine next state based on current state and logic
     */
    private NodeState determineNextState(TreeNode node) {
        // Transition logic
        switch (node.state) {
            case VALID:
                // 60% stay valid, 30% become decoy, 10% become transitioning
                int rand = random.nextInt(100);
                if (rand < 60) return NodeState.VALID;
                if (rand < 90) return NodeState.DECOY;
                return NodeState.TRANSITIONING;
                
            case DECOY:
                // 50% stay decoy, 40% become valid, 10% transitioning
                rand = random.nextInt(100);
                if (rand < 50) return NodeState.DECOY;
                if (rand < 90) return NodeState.VALID;
                return NodeState.TRANSITIONING;
                
            case TRANSITIONING:
                // Always move to next state
                return random.nextBoolean() ? NodeState.VALID : NodeState.DECOY;
                
            case COMPROMISED:
                // Stay compromised until explicitly fixed
                return NodeState.COMPROMISED;
                
            default:
                return node.state;
        }
    }
    
    /**
     * Perform actual transition with hash recalculation
     */
    private void performTransition(TreeNode node, NodeState newState, String reason) 
            throws NoSuchAlgorithmException {
        
        DynamicTransition transition = new DynamicTransition(
            node.nodeId, 
            node.state, 
            newState, 
            reason
        );
        
        // Store previous state
        transition.previousHash = node.dataHash.clone();
        node.previousState = node.state;
        
        // Update node state
        node.state = newState;
        node.lastTransitionTime = System.currentTimeMillis();
        node.transitionCount++;
        
        // Recalculate node hash if state changed integrity expectations
        if (newState == NodeState.DECOY || newState == NodeState.COMPROMISED) {
            // Hash includes state information
            byte[] stateBytes = newState.name().getBytes();
            byte[] combinedData = new byte[node.dataHash.length + stateBytes.length];
            System.arraycopy(node.dataHash, 0, combinedData, 0, node.dataHash.length);
            System.arraycopy(stateBytes, 0, combinedData, node.dataHash.length, stateBytes.length);
            
            node.dataHash = hashData(combinedData);
        }
        
        transition.newHash = node.dataHash.clone();
        transition.affectedNodeIds.add(node.nodeId);
        
        // Record transition
        transitionLog.add(transition);
    }
    
    /**
     * Perform integrity checks and updates
     */
    private void performIntegrityUpdates() {
        try {
            List<TreeNode> allNodes = new ArrayList<>(nodes.values());
            
            for (TreeNode node : allNodes) {
                // Simulate integrity check (5% chance of integrity failure)
                if (random.nextInt(100) < 5) {
                    node.integrityValid = false;
                    node.state = NodeState.COMPROMISED;
                    
                    transitionLog.add(new DynamicTransition(
                        node.nodeId,
                        NodeState.VALID,
                        NodeState.COMPROMISED,
                        "integrity_check_failed"
                    ));
                } else if (!node.integrityValid && random.nextInt(100) < 30) {
                    // 30% chance to recover from compromise
                    node.integrityValid = true;
                    node.state = NodeState.VALID;
                }
            }
            
            recalculateRootHash();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Recalculate root hash from all leaf hashes
     */
    private void recalculateRootHash() throws NoSuchAlgorithmException {
        if (nodes.isEmpty() || rootNodeId == null) return;
        
        // Collect all leaf nodes
        List<TreeNode> leaves = nodes.values().stream()
            .filter(TreeNode::isLeaf)
            .collect(Collectors.toList());
        
        if (leaves.isEmpty()) return;
        
        // Rebuild tree structure from leaves
        List<byte[]> hashes = leaves.stream()
            .map(n -> n.dataHash)
            .collect(Collectors.toList());
        
        byte[] currentHash = hashes.get(0);
        for (int i = 1; i < hashes.size(); i++) {
            currentHash = hashPair(currentHash, hashes.get(i));
        }
        
        this.rootHash = currentHash;
    }
    
    /**
     * Get current root hash as hex string
     */
    public String getRootHashHex() {
        if (rootHash == null) return "";
        return bytesToHex(rootHash);
    }
    
    /**
     * Add new leaves to the tree dynamically
     */
    public void addLeaves(List<String> newLeaves) throws NoSuchAlgorithmException {
        for (String leafData : newLeaves) {
            String leafId = "leaf_" + System.nanoTime();
            byte[] hash = hashData(leafData.getBytes());
            NodeState state = random.nextInt(100) < decoyNodesPercentage ? 
                NodeState.DECOY : NodeState.VALID;
            
            TreeNode leaf = new TreeNode(leafId, hash, state);
            nodes.put(leafId, leaf);
        }
        
        recalculateRootHash();
    }
    
    /**
     * Update specific leaf data
     */
    public void updateLeaf(String leafId, String newData) throws NoSuchAlgorithmException {
        TreeNode node = nodes.get(leafId);
        if (node != null && node.isLeaf()) {
            node.dataHash = hashData(newData.getBytes());
            recalculateRootHash();
        }
    }
    
    /**
     * Get all transitions
     */
    public List<DynamicTransition> getTransitions() {
        return new ArrayList<>(transitionLog);
    }
    
    /**
     * Get transitions by state type
     */
    public List<DynamicTransition> getTransitionsByState(NodeState state) {
        return transitionLog.stream()
            .filter(t -> t.toState == state)
            .collect(Collectors.toList());
    }
    
    /**
     * Get tree statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long validCount = nodes.values().stream()
            .filter(n -> n.state == NodeState.VALID).count();
        long decoyCount = nodes.values().stream()
            .filter(n -> n.state == NodeState.DECOY).count();
        long compromisedCount = nodes.values().stream()
            .filter(n -> n.state == NodeState.COMPROMISED).count();
        long transitioningCount = nodes.values().stream()
            .filter(n -> n.state == NodeState.TRANSITIONING).count();
        
        stats.put("total_nodes", nodes.size());
        stats.put("valid_nodes", validCount);
        stats.put("decoy_nodes", decoyCount);
        stats.put("compromised_nodes", compromisedCount);
        stats.put("transitioning_nodes", transitioningCount);
        stats.put("total_transitions", transitionLog.size());
        stats.put("root_hash", getRootHashHex());
        stats.put("engine_running", isRunning);
        
        return stats;
    }
    
    /**
     * Hash data using SHA-256
     */
    private byte[] hashData(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data);
        return md.digest();
    }
    
    /**
     * Hash pair of values
     */
    private byte[] hashPair(byte[] left, byte[] right) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(left);
        md.update(right);
        return md.digest();
    }
    
    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Stop autonomous engine
     */
    public void stop() {
        isRunning = false;
        scheduler.shutdown();
    }
    
    /**
     * Get all nodes
     */
    public Map<String, TreeNode> getNodes() {
        return new HashMap<>(nodes);
    }
}
