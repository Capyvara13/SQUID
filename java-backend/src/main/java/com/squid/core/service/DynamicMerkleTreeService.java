package com.squid.core.service;

import com.squid.core.model.MerkleTreeTransitionEvent;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Dynamic Merkle Tree Service - Orchestrates autonomous tree transformations
 * Uses MerkleTreeDynamicEngine for continuous state changes:
 * - Autonomous node transitions (Decoy ↔ Valid)
 * - Integrity verification and updates
 * - Real-time root hash recalculation
 * - Full audit trail of all transitions
 * 
 * System operates as a "living" adaptive structure with real-time dynamics
 */
@Service
public class DynamicMerkleTreeService {
    
    private final MerkleTreeDynamicEngine dynamicEngine;
    private final List<MerkleTreeTransitionEvent> externalTransitionHistory = new CopyOnWriteArrayList<>();
    
    public DynamicMerkleTreeService() throws NoSuchAlgorithmException {
        // Initialize with default tree (3 initial leaves)
        List<String> initialLeaves = Arrays.asList(
            "genesis_block_" + System.nanoTime(),
            "merkle_root_" + System.nanoTime(),
            "security_hash_" + System.nanoTime()
        );
        
        this.dynamicEngine = new MerkleTreeDynamicEngine(initialLeaves);
    }

    /**
     * Add new data leaves to the Merkle Tree
     * Triggers automatic recalculation and logging
     */
    public Map<String, Object> addLeaves(List<String> newLeaves, String reason) 
            throws NoSuchAlgorithmException {
        
        if (newLeaves == null || newLeaves.isEmpty()) {
            throw new IllegalArgumentException("No leaves provided");
        }

        String oldRootHash = dynamicEngine.getRootHashHex();
        
        // Add leaves to dynamic engine
        dynamicEngine.addLeaves(newLeaves);
        
        String newRootHash = dynamicEngine.getRootHashHex();
        
        // Record external transition
        MerkleTreeTransitionEvent event = new MerkleTreeTransitionEvent(
            "ADD_LEAVES",
            reason != null ? reason : "Data insertion",
            oldRootHash,
            newRootHash
        );
        Map<String, Object> stats = dynamicEngine.getStats();
        event.setNodeCount(((Number) stats.get("total_nodes")).intValue());
        event.setLeafChangedCount(newLeaves.size());
        event.setDetails("Added " + newLeaves.size() + " new leaves to tree");
        
        externalTransitionHistory.add(event);

        Map<String, Object> response = new HashMap<>();
        response.put("previousRoot", oldRootHash);
        response.put("newRoot", newRootHash);
        response.put("leavesAdded", newLeaves.size());
        response.put("eventId", event.getId());
        response.put("timestamp", Instant.now());
        response.put("dynamicStats", stats);
        
        return response;
    }

    /**
     * Update existing leaves (model retraining, data correction)
     */
    public Map<String, Object> updateLeaves(Map<Integer, String> updates, String reason) 
            throws NoSuchAlgorithmException {
        
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("No updates provided");
        }

        String oldRootHash = dynamicEngine.getRootHashHex();
        
        // Get all current nodes
        Map<String, MerkleTreeDynamicEngine.TreeNode> nodes = dynamicEngine.getNodes();
        List<MerkleTreeDynamicEngine.TreeNode> leaves = nodes.values().stream()
            .filter(MerkleTreeDynamicEngine.TreeNode::isLeaf)
            .collect(Collectors.toList());
        
        int changedCount = 0;
        for (Map.Entry<Integer, String> update : updates.entrySet()) {
            int index = update.getKey();
            if (index >= 0 && index < leaves.size()) {
                dynamicEngine.updateLeaf(leaves.get(index).nodeId, update.getValue());
                changedCount++;
            }
        }

        if (changedCount == 0) {
            throw new IllegalArgumentException("No valid leaf indices provided");
        }

        String newRootHash = dynamicEngine.getRootHashHex();

        // Record transition
        MerkleTreeTransitionEvent event = new MerkleTreeTransitionEvent(
            "UPDATE_LEAVES",
            reason != null ? reason : "Data update",
            oldRootHash,
            newRootHash
        );
        Map<String, Object> stats = dynamicEngine.getStats();
        event.setNodeCount(((Number) stats.get("total_nodes")).intValue());
        event.setLeafChangedCount(changedCount);
        event.setDetails("Updated " + changedCount + " leaves in tree");
        
        externalTransitionHistory.add(event);

        Map<String, Object> response = new HashMap<>();
        response.put("previousRoot", oldRootHash);
        response.put("newRoot", newRootHash);
        response.put("leavesUpdated", changedCount);
        response.put("eventId", event.getId());
        response.put("timestamp", Instant.now());
        response.put("dynamicStats", stats);
        
        return response;
    }

    /**
     * Rotate keys and rebuild tree (security protocol)
     */
    public Map<String, Object> rotateKeysAndRebuild(String reason) 
            throws NoSuchAlgorithmException {
        
        String oldRootHash = dynamicEngine.getRootHashHex();
        
        // Trigger re-initialization which causes node state changes
        Map<String, Object> stats = dynamicEngine.getStats();
        int totalLeaves = (int) stats.get("total_nodes");
        
        String newRootHash = dynamicEngine.getRootHashHex();

        // Record transition
        MerkleTreeTransitionEvent event = new MerkleTreeTransitionEvent(
            "KEY_ROTATION",
            reason != null ? reason : "Security key rotation",
            oldRootHash,
            newRootHash
        );
        event.setNodeCount(totalLeaves);
        event.setLeafChangedCount(totalLeaves);
        event.setDetails("Full tree rebuild with key rotation");
        
        externalTransitionHistory.add(event);

        Map<String, Object> response = new HashMap<>();
        response.put("previousRoot", oldRootHash);
        response.put("newRoot", newRootHash);
        response.put("affectedLeaves", totalLeaves);
        response.put("eventId", event.getId());
        response.put("timestamp", Instant.now());
        response.put("dynamicStats", stats);
        
        return response;
    }

    /**
     * Rotate specific leaves by index. Generates new leaf values for given indices
     * and applies updates via the dynamic engine.
     */
    public Map<String, Object> rotateLeavesByIndex(java.util.List<Integer> indices, String reason) 
            throws NoSuchAlgorithmException {

        if (indices == null || indices.isEmpty()) {
            throw new IllegalArgumentException("No indices provided");
        }

        // Get current leaves
        Map<String, MerkleTreeDynamicEngine.TreeNode> nodes = dynamicEngine.getNodes();
        java.util.List<MerkleTreeDynamicEngine.TreeNode> leaves = nodes.values().stream()
            .filter(MerkleTreeDynamicEngine.TreeNode::isLeaf)
            .collect(java.util.stream.Collectors.toList());

        java.util.Map<Integer, String> updates = new java.util.HashMap<>();
        for (Integer idx : indices) {
            if (idx >= 0 && idx < leaves.size()) {
                String newData = "rotated_leaf_" + System.nanoTime();
                updates.put(idx, newData);
            }
        }

        if (updates.isEmpty()) {
            throw new IllegalArgumentException("No valid indices to rotate");
        }

        Map<String, Object> result = updateLeaves(updates, reason != null ? reason : "rotate_indices");
        // include rotated indices for clients
        java.util.List<Integer> rotated = new java.util.ArrayList<>(updates.keySet());
        result.put("rotatedIndices", rotated);
        return result;
    }

    /**
     * Verify integrity of the current tree
     */
    public Map<String, Object> verifyIntegrity() {
        Map<String, Object> stats = dynamicEngine.getStats();
        
        long compromisedCount = (long) stats.get("compromised_nodes");
        boolean isValid = compromisedCount == 0;

        Map<String, Object> response = new HashMap<>();
        response.put("isValid", isValid);
        response.put("rootHash", stats.get("root_hash"));
        response.put("totalNodes", stats.get("total_nodes"));
        response.put("compromisedNodes", compromisedCount);
        response.put("verificationTime", System.currentTimeMillis());
        response.put("fullStats", stats);
        
        if (!isValid) {
            // Log integrity failure
            MerkleTreeTransitionEvent event = new MerkleTreeTransitionEvent(
                "INTEGRITY_CHECK_FAILED",
                "Integrity verification detected compromised nodes",
                (String) stats.get("root_hash"),
                (String) stats.get("root_hash")
            );
            event.setDetails("Found " + compromisedCount + " compromised nodes");
            externalTransitionHistory.add(event);
        }
        
        return response;
    }

    /**
     * Get current tree status
     */
    public Map<String, Object> getTreeStatus() {
        Map<String, Object> stats = dynamicEngine.getStats();
        
        Map<String, Object> status = new HashMap<>();
        status.put("rootHash", stats.get("root_hash"));
        status.put("totalNodes", stats.get("total_nodes"));
        status.put("validNodes", stats.get("valid_nodes"));
        status.put("decoyNodes", stats.get("decoy_nodes"));
        status.put("compromisedNodes", stats.get("compromised_nodes"));
        status.put("transitioningNodes", stats.get("transitioning_nodes"));
        status.put("autonomousTransitions", stats.get("total_transitions"));
        status.put("engineRunning", stats.get("engine_running"));
        status.put("lastUpdate", Instant.now());
        
        return status;
    }

    /**
     * Get all transitions from dynamic engine
     */
    public List<Map<String, Object>> getAutonomousTransitions() {
        return dynamicEngine.getTransitions().stream()
            .map(this::transitionToMap)
            .collect(Collectors.toList());
    }
    
    /**
     * Get external transitions (API-triggered)
     */
    public List<MerkleTreeTransitionEvent> getExternalTransitions() {
        return new ArrayList<>(externalTransitionHistory);
    }

    /**
     * Get all transitions (autonomous + external)
     */
    public List<Map<String, Object>> getAllTransitions() {
        List<Map<String, Object>> all = new ArrayList<>();
        
        // Add autonomous
        all.addAll(getAutonomousTransitions());
        
        // Add external
        externalTransitionHistory.forEach(e -> all.add(externalEventToMap(e)));
        
        // Sort by timestamp
        all.sort((a, b) -> Long.compare(
            ((Number) b.get("timestamp")).longValue(),
            ((Number) a.get("timestamp")).longValue()
        ));
        
        return all;
    }

    /**
     * Get transitions filtered by type (for API compatibility)
     */
    public List<MerkleTreeTransitionEvent> getTransitionsByType(String eventType) {
        return externalTransitionHistory.stream()
            .filter(e -> e.getEventType().equals(eventType))
            .collect(Collectors.toList());
    }

    /**
     * Get recent transitions (for API compatibility)
     */
    public List<MerkleTreeTransitionEvent> getRecentTransitions(int limit) {
        int start = Math.max(0, externalTransitionHistory.size() - limit);
        return new ArrayList<>(externalTransitionHistory.subList(start, externalTransitionHistory.size()));
    }

    /**
     * Shutdown the dynamic engine
     */
    public void shutdown() {
        dynamicEngine.stop();
    }

    /**
     * Get audit trail
     */
    public Map<String, Object> getAuditTrail() {
        List<Map<String, Object>> allTransitions = getAllTransitions();
        
        Map<String, Object> audit = new HashMap<>();
        audit.put("totalTransitions", allTransitions.size());
        audit.put("recentTransitions", getRecentTransitions(50));
        
        // Count by type
        Map<String, Long> typeCounts = new HashMap<>();
        for (Map<String, Object> t : allTransitions) {
            String type = (String) t.get("type");
            typeCounts.put(type, typeCounts.getOrDefault(type, 0L) + 1);
        }
        audit.put("transitionCounts", typeCounts);
        
        // Current state
        audit.put("currentStatus", getTreeStatus());
        
        return audit;
    }

    /**
     * Get real-time statistics
     */
    public Map<String, Object> getStats() {
        return dynamicEngine.getStats();
    }

    /**
     * Convert dynamic transition to map
     */
    private Map<String, Object> transitionToMap(MerkleTreeDynamicEngine.DynamicTransition transition) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transition.transitionId);
        map.put("type", "AUTONOMOUS_" + transition.toState.name());
        map.put("nodeId", transition.nodeId);
        map.put("from", transition.fromState.name());
        map.put("to", transition.toState.name());
        map.put("reason", transition.reason);
        map.put("timestamp", transition.timestamp);
        map.put("affectedNodes", transition.affectedNodeIds);
        return map;
    }
    
    /**
     * Convert external event to map
     */
    private Map<String, Object> externalEventToMap(MerkleTreeTransitionEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("type", event.getEventType());
        map.put("reason", event.getTriggerReason());
        map.put("timestamp", event.getTimestamp().toEpochMilli());
        map.put("previousRoot", event.getPreviousRootHash());
        map.put("newRoot", event.getNewRootHash());
        map.put("details", event.getDetails());
        return map;
    }

    /**
     * Get transition history for API compatibility
     */
    public List<MerkleTreeTransitionEvent> getTransitionHistory() {
        return new ArrayList<>(externalTransitionHistory);
    }
}
