package com.squid.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQUID Database Layer — the single entry-point for all persistent DB writes.
 * Supports PostgreSQL, MySQL and H2 transparently.
 */
@Service
public class SquidDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(SquidDatabaseService.class);

    private final DataSource dataSource;

    public SquidDatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ───────────────── AUDIT LOGS (hash-chained) ─────────────────

    /**
     * Insert a hash-chained audit log entry.
     * hash = SHA-256(previous_hash || action || actor || timestamp)
     */
    public Map<String, Object> insertAuditLog(String action, String actor, String merkleleaf, String signature) {
        try (Connection conn = dataSource.getConnection()) {
            String previousHash = getLastAuditHash(conn);
            String now = Instant.now().toString();
            String hash = sha256Hex(previousHash + action + actor + now);

            String sql = "INSERT INTO audit_logs(action,actor,hash,previous_hash,merkle_leaf,signature) " +
                         "VALUES(?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, action);
                ps.setString(2, actor);
                ps.setString(3, hash);
                ps.setString(4, previousHash);
                ps.setString(5, merkleleaf);
                ps.setString(6, signature);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                long id = keys.next() ? keys.getLong(1) : -1;

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id);
                entry.put("action", action);
                entry.put("actor", actor);
                entry.put("hash", hash);
                entry.put("previous_hash", previousHash);
                entry.put("merkle_leaf", merkleleaf);
                entry.put("timestamp", now);
                return entry;
            }
        } catch (Exception e) {
            log.error("insertAuditLog failed: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> getAuditLogs(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT id,action,actor,timestamp,hash,previous_hash,merkle_leaf,signature " +
                     "FROM audit_logs ORDER BY id DESC";
        // H2/PostgreSQL/MySQL all support LIMIT
        if (limit > 0) sql += " LIMIT " + limit;

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("action", rs.getString("action"));
                row.put("actor", rs.getString("actor"));
                row.put("timestamp", rs.getString("timestamp"));
                row.put("hash", rs.getString("hash"));
                row.put("previous_hash", rs.getString("previous_hash"));
                row.put("merkle_leaf", rs.getString("merkle_leaf"));
                row.put("signature", rs.getString("signature"));
                out.add(row);
            }
        } catch (Exception e) {
            log.error("getAuditLogs failed: {}", e.getMessage());
        }
        return out;
    }

    /**
     * Walk the audit chain and verify every hash.
     */
    public Map<String, Object> verifyAuditChain() {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "SELECT id,action,actor,timestamp,hash,previous_hash FROM audit_logs ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            long total = 0, valid = 0, broken = 0;
            Long firstBrokenId = null;

            while (rs.next()) {
                total++;
                String prevHash = rs.getString("previous_hash");
                String action = rs.getString("action");
                String actor = rs.getString("actor");
                String ts = rs.getString("timestamp");
                String expected = sha256Hex(prevHash + action + actor + ts);
                String actual = rs.getString("hash");

                if (expected.equals(actual)) {
                    valid++;
                } else {
                    broken++;
                    if (firstBrokenId == null) firstBrokenId = rs.getLong("id");
                }
            }
            result.put("total_entries", total);
            result.put("valid_entries", valid);
            result.put("broken_entries", broken);
            result.put("chain_intact", broken == 0);
            result.put("first_broken_id", firstBrokenId);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ───────────────── MERKLE NODES ─────────────────

    public int insertMerkleSnapshot(List<String> leaves, String rootHash, int treeVersion) {
        String sql = "INSERT INTO merkle_nodes(node_id,parent_hash,left_hash,right_hash,node_hash,tree_version) " +
                     "VALUES(?,?,?,?,?,?)";
        int inserted = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (int i = 0; i < leaves.size(); i++) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, rootHash);            // parent_hash = root for leaf level
                ps.setString(3, i > 0 ? leaves.get(i - 1) : null);
                ps.setString(4, i + 1 < leaves.size() ? leaves.get(i + 1) : null);
                ps.setString(5, leaves.get(i));
                ps.setInt(6, treeVersion);
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            log.error("insertMerkleSnapshot failed: {}", e.getMessage());
        }
        return inserted;
    }

    public Map<String, Object> getMerkleIntegrity(String liveRootHash) {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            // Get latest tree version
            ResultSet rs = st.executeQuery(
                    "SELECT MAX(tree_version) AS v, COUNT(*) AS cnt FROM merkle_nodes");
            int latestVersion = 0;
            long totalNodes = 0;
            if (rs.next()) {
                latestVersion = rs.getInt("v");
                totalNodes = rs.getLong("cnt");
            }
            rs.close();

            // Get stored root for latest version
            ResultSet rs2 = st.executeQuery(
                    "SELECT DISTINCT parent_hash FROM merkle_nodes WHERE tree_version = " + latestVersion);
            String storedRoot = rs2.next() ? rs2.getString("parent_hash") : null;
            rs2.close();

            boolean match = liveRootHash != null && liveRootHash.equals(storedRoot);

            out.put("stored_root", storedRoot);
            out.put("live_root", liveRootHash);
            out.put("roots_match", match);
            out.put("latest_tree_version", latestVersion);
            out.put("total_stored_nodes", totalNodes);
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    // ───────────────── PROBABILITY MODELS ─────────────────

    public void insertProbabilityModel(double sr, double cc, String decision, double entropy) {
        String sql = "INSERT INTO probability_models(model_id,sr_value,correlation_coefficient,decision,entropy_score) " +
                     "VALUES(?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setDouble(2, sr);
            ps.setDouble(3, cc);
            ps.setString(4, decision);
            ps.setDouble(5, entropy);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("insertProbabilityModel failed: {}", e.getMessage());
        }
    }

    // ───────────────── DECOY OPERATIONS ─────────────────

    public void insertDecoyOperation(String triggeredBy, String leafTarget,
                                     String mutationType, double probability) {
        String sql = "INSERT INTO decoy_operations(decoy_id,triggered_by,leaf_target,mutation_type,probability) " +
                     "VALUES(?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, triggeredBy);
            ps.setString(3, leafTarget);
            ps.setString(4, mutationType);
            ps.setDouble(5, probability);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("insertDecoyOperation failed: {}", e.getMessage());
        }
    }

    // ───────────────── INSTANCE EXPORT ─────────────────

    /**
     * Persist a full instance snapshot into the database.
     * Inserts audit log + merkle nodes for the instance.
     */
    public Map<String, Object> exportInstance(String instanceId, String instanceName,
                                               List<String> leaves, String merkleRoot,
                                               String signature) {
        // 1) Audit log entry
        insertAuditLog("EXPORT_INSTANCE",
                       "system",
                       merkleRoot,
                       signature);

        // 2) Merkle snapshot
        int version = getNextTreeVersion();
        int nodesInserted = insertMerkleSnapshot(leaves, merkleRoot, version);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "exported");
        result.put("instance_id", instanceId);
        result.put("instance_name", instanceName);
        result.put("nodes_inserted", nodesInserted);
        result.put("tree_version", version);
        result.put("merkle_root", merkleRoot);
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    // ───────────────── TABLE ROW COUNTS ─────────────────

    public Map<String, Long> getTableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        String[] tables = {"users", "audit_logs", "merkle_nodes", "probability_models", "decoy_operations"};
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            for (String t : tables) {
                try {
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t);
                    counts.put(t, rs.next() ? rs.getLong(1) : 0L);
                    rs.close();
                } catch (Exception e) {
                    counts.put(t, -1L);
                }
            }
        } catch (Exception e) {
            log.error("getTableCounts failed: {}", e.getMessage());
        }
        return counts;
    }

    // ───────────────── RUNTIME RECONFIGURE ─────────────────
    // Note: Full runtime datasource switching requires recreating the DataSource bean.
    // For now we expose a "test connection" utility.

    public Map<String, Object> testConnection(String jdbcUrl, String user, String password) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            long latency = System.currentTimeMillis() - start;
            result.put("connected", true);
            result.put("db_product", conn.getMetaData().getDatabaseProductName());
            result.put("db_version", conn.getMetaData().getDatabaseProductVersion());
            result.put("latency_ms", latency);
        } catch (Exception e) {
            result.put("connected", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ───────────────── HELPERS ─────────────────

    private String getLastAuditHash(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT hash FROM audit_logs ORDER BY id DESC LIMIT 1")) {
            return rs.next() ? rs.getString("hash") : "GENESIS";
        } catch (Exception e) {
            return "GENESIS";
        }
    }

    private int getNextTreeVersion() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(tree_version),0) + 1 FROM merkle_nodes")) {
            return rs.next() ? rs.getInt(1) : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }
}
