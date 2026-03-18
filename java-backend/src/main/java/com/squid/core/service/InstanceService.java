package com.squid.core.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InstanceService {
    private final Map<String, SquidInstance> instances = new ConcurrentHashMap<>();
    private final PQCService pqcService;

    public InstanceService(PQCService pqcService) {
        this.pqcService = pqcService;
    }

    // ──────────────────── LIST ────────────────────

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SquidInstance s : instances.values()) {
            out.add(instanceToMap(s));
        }
        return out;
    }

    // ──────────────────── GET ────────────────────

    public SquidInstance get(String id) {
        return instances.get(id);
    }

    public SquidInstance getOrCreate(String id) {
        return instances.computeIfAbsent(id, k -> {
            SquidInstance s = new SquidInstance();
            s.id = k;
            s.name = "instance-" + k;
            s.status = "ACTIVE";
            s.ephemeralKeysCount = 4;
            s.createdAt = Instant.now().toString();
            return s;
        });
    }

    // ──────────────────── CREATE ────────────────────

    public Map<String, Object> create(String name, Map<String, Object> config) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);

        SquidInstance inst = new SquidInstance();
        inst.id = id;
        inst.name = name;
        inst.status = "ACTIVE";
        inst.createdAt = Instant.now().toString();

        // Configuration (B, M, T)
        int b = getInt(config, "B", 2);
        int m = getInt(config, "M", 8);
        int t = Math.min((int) Math.pow(b, m), 4096);
        inst.configB = b;
        inst.configM = m;
        inst.configT = t;
        inst.dataOriginal = config.get("data") != null ? config.get("data").toString() : "";

        // Step 1: Generate seed_root via hash + PQC signature
        byte[] dataBytes = inst.dataOriginal.getBytes(StandardCharsets.UTF_8);
        byte[] dataHash = sha256(dataBytes);
        inst.seedRoot = bytesToHex(dataHash);
        inst.signature = pqcService.sign(dataHash);

        // Step 2: Generate Merkle leaves from seed
        List<String> leaves = new ArrayList<>();
        for (int i = 0; i < t; i++) {
            byte[] leafData = sha256((inst.seedRoot + ":" + i).getBytes(StandardCharsets.UTF_8));
            leaves.add(bytesToHex(leafData));
        }
        inst.leaves = leaves;
        inst.ephemeralKeysCount = t;

        // Step 3: Compute Merkle root
        inst.merkleRoot = computeMerkleRoot(leaves);

        // Log history
        inst.history.add(historyEntry("CREATED",
                "Instance created with B=" + b + " M=" + m + " T=" + t));

        instances.put(id, inst);

        Map<String, Object> out = instanceToMap(inst);
        out.put("seed_root", inst.seedRoot);
        out.put("signature", inst.signature);
        return out;
    }

    // ──────────────────── REMOVE LEAVES ────────────────────

    public Map<String, Object> removeLeaves(String id, List<Integer> indices) throws Exception {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        // Sort descending to safely remove by index
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Collections.reverseOrder());
        int removed = 0;
        for (int idx : sorted) {
            if (idx >= 0 && idx < inst.leaves.size()) {
                inst.leaves.remove(idx);
                removed++;
            }
        }
        inst.ephemeralKeysCount = inst.leaves.size();

        String oldRoot = inst.merkleRoot;
        inst.merkleRoot = computeMerkleRoot(inst.leaves);

        inst.history.add(historyEntry("REMOVE_LEAVES",
                "Removed " + removed + " leaves. Old root: " + oldRoot));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", inst.id);
        out.put("removed_count", removed);
        out.put("new_leaf_count", inst.leaves.size());
        out.put("old_merkle_root", oldRoot);
        out.put("new_merkle_root", inst.merkleRoot);
        return out;
    }

    // ──────────────────── REENCRYPT ────────────────────

    public Map<String, Object> reencrypt(String id) throws Exception {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        String oldRoot = inst.merkleRoot;

        // New seed from current time + old seed
        byte[] newSeedBytes = sha256((inst.seedRoot + ":" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        inst.seedRoot = bytesToHex(newSeedBytes);
        inst.signature = pqcService.sign(newSeedBytes);

        // Regenerate all leaves
        List<String> newLeaves = new ArrayList<>();
        for (int i = 0; i < inst.leaves.size(); i++) {
            byte[] leafData = sha256((inst.seedRoot + ":" + i).getBytes(StandardCharsets.UTF_8));
            newLeaves.add(bytesToHex(leafData));
        }
        inst.leaves = newLeaves;
        inst.merkleRoot = computeMerkleRoot(newLeaves);

        inst.history.add(historyEntry("REENCRYPT",
                "Re-encrypted. Old root: " + oldRoot + " New root: " + inst.merkleRoot));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", inst.id);
        out.put("old_merkle_root", oldRoot);
        out.put("new_merkle_root", inst.merkleRoot);
        out.put("new_seed_root", inst.seedRoot);
        out.put("signature", inst.signature);
        out.put("leaf_count", inst.leaves.size());
        return out;
    }

    // ──────────────────── DECRYPT ────────────────────

    public Map<String, Object> decrypt(String id, String fileExtension) throws Exception {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        // Verify Merkle integrity
        String currentRoot = computeMerkleRoot(inst.leaves);
        boolean merkleValid = currentRoot.equals(inst.merkleRoot);

        // Verify signature
        boolean signatureValid = pqcService.verify(inst.signature,
                hexToBytes(inst.seedRoot));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", inst.id);
        out.put("merkle_valid", merkleValid);
        out.put("signature_valid", signatureValid);
        out.put("file_extension", fileExtension);

        if (merkleValid && signatureValid) {
            out.put("content", inst.dataOriginal);
            out.put("status", "SUCCESS");
            inst.history.add(historyEntry("DECRYPT",
                    "Decrypted to ." + fileExtension));
        } else {
            out.put("content", "");
            out.put("status", "INTEGRITY_FAILED");
            out.put("error", !merkleValid ? "Merkle root mismatch" : "Signature invalid");
            inst.history.add(historyEntry("DECRYPT_FAILED",
                    "Integrity check failed"));
        }
        return out;
    }

    // ──────────────────── EXPORT ────────────────────

    public Map<String, Object> exportToDb(String id, String dbType) {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        // Build full snapshot for export
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instance", instanceToMap(inst));
        payload.put("leaves", inst.leaves);
        payload.put("history", inst.history);
        payload.put("ai_logs", inst.aiLogs);
        payload.put("config", Map.of("B", inst.configB, "M", inst.configM, "T", inst.configT));

        inst.history.add(historyEntry("EXPORT",
                "Exported to " + dbType.toUpperCase()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "exported");
        out.put("target", dbType);
        out.put("record_count", inst.leaves.size());
        out.put("payload", payload);
        out.put("timestamp", Instant.now().toString());
        return out;
    }

    // ──────────────────── CANCEL ────────────────────

    public Map<String, Object> cancel(String id, String globalRootHex, String signature, String finalSeedHex) {
        SquidInstance s = getOrCreate(id);
        int destroyed = s.ephemeralKeysCount;
        s.ephemeralKeysCount = 0;
        s.status = "FINALIZED";
        s.finalizedAt = Instant.now().toString();
        s.lastGlobalRootHex = globalRootHex;
        s.snapshotSignature = signature;
        s.finalSeedHex = finalSeedHex;
        s.history.add(historyEntry("FINALIZED", "Instance destroyed"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id);
        out.put("status", s.status);
        out.put("globalRoot", globalRootHex);
        out.put("signature", signature);
        out.put("finalSeedHex", finalSeedHex);
        out.put("destroyedEphemeralKeys", destroyed);
        out.put("finalizedAt", s.finalizedAt);
        return out;
    }

    // ──────────────────── LEAVES & HISTORY ────────────────────

    /**
     * Returns all leaves of an instance with computed per-leaf metadata.
     */
    public Map<String, Object> getLeaves(String id) {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        List<Map<String, Object>> leafList = new ArrayList<>();
        int totalLeaves = inst.leaves.size();
        int treeDepth = inst.configM;

        for (int i = 0; i < totalLeaves; i++) {
            leafList.add(buildLeafSummary(inst, i, treeDepth));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance_id", inst.id);
        out.put("instance_name", inst.name);
        out.put("total", totalLeaves);
        out.put("merkle_root", inst.merkleRoot);
        out.put("config", Map.of("B", inst.configB, "M", inst.configM, "T", inst.configT));
        out.put("leaves", leafList);
        return out;
    }

    /**
     * Returns detailed info for a single leaf including related history.
     */
    public Map<String, Object> getLeafDetail(String id, int index) {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);
        if (index < 0 || index >= inst.leaves.size())
            throw new IllegalArgumentException("Leaf index out of range: " + index);

        int treeDepth = inst.configM;
        Map<String, Object> leaf = buildLeafSummary(inst, index, treeDepth);

        // Full hash
        leaf.put("hash_full", inst.leaves.get(index));

        // Merkle proof path (siblings from leaf to root)
        List<String> proofPath = computeMerkleProof(inst.leaves, index);
        leaf.put("merkle_proof", proofPath);

        // Related history entries (those that mention leaves or this index)
        List<Map<String, Object>> related = new ArrayList<>();
        for (Map<String, Object> entry : inst.history) {
            String action = String.valueOf(entry.get("action"));
            String details = String.valueOf(entry.get("details"));
            if (action.equals("CREATED") || action.equals("REENCRYPT")
                    || action.equals("REMOVE_LEAVES") || details.contains(String.valueOf(index))) {
                related.add(entry);
            }
        }
        leaf.put("history", related);
        leaf.put("instance_id", inst.id);
        leaf.put("instance_name", inst.name);
        leaf.put("merkle_root", inst.merkleRoot);
        return leaf;
    }

    /**
     * Returns the full history of an instance.
     */
    public Map<String, Object> getHistory(String id) {
        SquidInstance inst = instances.get(id);
        if (inst == null) throw new IllegalArgumentException("Instance not found: " + id);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance_id", inst.id);
        out.put("instance_name", inst.name);
        out.put("status", inst.status);
        out.put("total_events", inst.history.size());
        out.put("history", inst.history);
        out.put("ai_logs", inst.aiLogs);
        return out;
    }

    // ──────────────────── HELPERS ────────────────────

    /**
     * Builds a summary map for a single leaf at the given index.
     */
    private Map<String, Object> buildLeafSummary(SquidInstance inst, int index, int treeDepth) {
        String hash = inst.leaves.get(index);
        byte[] hashBytes = hexToBytes(hash);

        // Depth: position in tree based on B and index
        int depth = Math.min(treeDepth, (int) (Math.log(index + 1) / Math.log(inst.configB)) + 1);

        // Shannon entropy of the hash bytes (0–8 bits)
        double entropy = shannonEntropy(hashBytes);

        // Deterministic state from hash — mostly VALID with realistic distribution
        String state = determineLeafState(hashBytes, index);

        // Local Security Rating (SR): derived from entropy + hash uniformity
        double sr = Math.min(1.0, entropy / 8.0 + uniformityScore(hashBytes) * 0.15);

        // Confidence score (C): how far from expected random distribution
        double c = confidenceScore(hashBytes);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("hash", hash.substring(0, Math.min(24, hash.length())));
        m.put("depth", depth);
        m.put("state", state);
        m.put("entropy", Math.round(entropy * 100.0) / 100.0);
        m.put("sr", Math.round(sr * 1000.0) / 1000.0);
        m.put("c", Math.round(c * 1000.0) / 1000.0);
        return m;
    }

    /**
     * Shannon entropy of byte array (bits per byte, 0–8).
     */
    private double shannonEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int[] freq = new int[256];
        for (byte b : data) freq[b & 0xFF]++;
        double entropy = 0;
        double len = data.length;
        for (int f : freq) {
            if (f > 0) {
                double p = f / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * Uniformity score: how evenly distributed the byte values are (0–1).
     */
    private double uniformityScore(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int[] freq = new int[256];
        for (byte b : data) freq[b & 0xFF]++;
        double expected = data.length / 256.0;
        double chiSq = 0;
        for (int f : freq) chiSq += Math.pow(f - expected, 2) / expected;
        // Normalise: perfect uniformity → 1.0
        double maxChiSq = 255.0 * data.length;
        return Math.max(0, 1.0 - chiSq / maxChiSq);
    }

    /**
     * Confidence that the hash behaves as a good pseudorandom value (0–1).
     */
    private double confidenceScore(byte[] data) {
        if (data == null || data.length == 0) return 0;
        // Bit balance: count of 1-bits should be ~50%
        int ones = 0;
        for (byte b : data) ones += Integer.bitCount(b & 0xFF);
        double bitRatio = ones / (double) (data.length * 8);
        double balance = 1.0 - Math.abs(bitRatio - 0.5) * 4; // 1.0 when 50%, 0 when 25% or 75%
        return Math.max(0, Math.min(1.0, balance * 0.7 + uniformityScore(data) * 0.3));
    }

    /**
     * Determines the state of a leaf from its hash.
     * ~85% VALID, ~8% DECOY, ~5% MUTATE, ~2% REASSIGN
     */
    private String determineLeafState(byte[] hashBytes, int index) {
        int v = (hashBytes[0] & 0xFF);
        if (v < 217) return "VALID";      // ~85%
        if (v < 237) return "DECOY";      // ~8%
        if (v < 250) return "MUTATE";     // ~5%
        return "REASSIGN";                // ~2%
    }

    /**
     * Computes a Merkle proof (sibling hashes from leaf to root).
     */
    private List<String> computeMerkleProof(List<String> leaves, int leafIndex) {
        List<String> proof = new ArrayList<>();
        List<byte[]> current = new ArrayList<>();
        for (String leaf : leaves) {
            current.add(sha256(leaf.getBytes(StandardCharsets.UTF_8)));
        }
        int idx = leafIndex;
        while (current.size() > 1) {
            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                if (i + 1 < current.size()) {
                    // Record sibling
                    if (i == idx || i + 1 == idx) {
                        int sibling = (i == idx) ? i + 1 : i;
                        proof.add(bytesToHex(current.get(sibling)));
                    }
                    byte[] combined = new byte[64];
                    System.arraycopy(current.get(i), 0, combined, 0, 32);
                    System.arraycopy(current.get(i + 1), 0, combined, 32, 32);
                    next.add(sha256(combined));
                } else {
                    next.add(current.get(i));
                }
            }
            idx = idx / 2;
            current = next;
        }
        return proof;
    }

    private Map<String, Object> instanceToMap(SquidInstance s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("status", s.status);
        m.put("leaf_count", s.leaves != null ? s.leaves.size() : s.ephemeralKeysCount);
        m.put("merkle_root", s.merkleRoot != null ? s.merkleRoot : "N/A");
        m.put("created_at", s.createdAt);
        m.put("config_B", s.configB);
        m.put("config_M", s.configM);
        m.put("config_T", s.configT);
        return m;
    }

    private Map<String, Object> historyEntry(String action, String details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("action", action);
        entry.put("details", details);
        return entry;
    }

    private String computeMerkleRoot(List<String> leaves) {
        if (leaves == null || leaves.isEmpty()) return "empty";
        List<byte[]> current = new ArrayList<>();
        for (String leaf : leaves) {
            current.add(sha256(leaf.getBytes(StandardCharsets.UTF_8)));
        }
        while (current.size() > 1) {
            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                if (i + 1 < current.size()) {
                    byte[] combined = new byte[64];
                    System.arraycopy(current.get(i), 0, combined, 0, 32);
                    System.arraycopy(current.get(i + 1), 0, combined, 32, 32);
                    next.add(sha256(combined));
                } else {
                    next.add(current.get(i));
                }
            }
            current = next;
        }
        return bytesToHex(current.get(0));
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    // ──────────────────── MODEL ────────────────────

    public static class SquidInstance {
        public String id;
        public String name;
        public String status;
        public int ephemeralKeysCount;
        public String createdAt;
        public String finalizedAt;
        public String lastGlobalRootHex;
        public String snapshotSignature;
        public String finalSeedHex;

        // New fields
        public int configB = 2;
        public int configM = 8;
        public int configT = 256;
        public String seedRoot;
        public String merkleRoot;
        public String signature;
        public String dataOriginal = "";
        public List<String> leaves = new ArrayList<>();
        public List<Map<String, Object>> history = new ArrayList<>();
        public List<Map<String, Object>> aiLogs = new ArrayList<>();
    }
}
