package com.squid.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates the SQUID schema tables on application startup.
 * Supports PostgreSQL, MySQL and H2 (dev/fallback).
 */
@Component
@Order(1)
public class SchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            log.info("SQUID SchemaInitializer: detected DB = {}", dbProduct);

            boolean isPostgres = dbProduct.contains("postgres");
            boolean isMySQL = dbProduct.contains("mysql");

            // Enable PostgreSQL extensions (pgcrypto, uuid-ossp)
            if (isPostgres) {
                enablePostgresExtensions(conn);
            }

            createUsersTable(conn, isPostgres, isMySQL);
            createAuditLogsTable(conn, isPostgres, isMySQL);
            createMerkleNodesTable(conn, isPostgres, isMySQL);
            createProbabilityModelsTable(conn, isPostgres, isMySQL);
            createDecoyOperationsTable(conn, isPostgres, isMySQL);

            log.info("SQUID schema initialized successfully.");
        } catch (Exception e) {
            log.warn("SQUID SchemaInitializer: could not create schema ({}). " +
                     "The application will still start.", e.getMessage());
        }
    }

    // ────────── PostgreSQL extensions ──────────

    private void enablePostgresExtensions(Connection conn) {
        execSafe(conn, "CREATE EXTENSION IF NOT EXISTS pgcrypto");
        execSafe(conn, "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        log.info("SQUID: PostgreSQL extensions pgcrypto and uuid-ossp enabled.");
    }

    // ────────── users ──────────

    private void createUsersTable(Connection conn, boolean pg, boolean mysql) throws Exception {
        String uuidType = pg ? "VARCHAR(36) DEFAULT uuid_generate_v4()::TEXT" :
                          mysql ? "VARCHAR(36)" : "VARCHAR(36) DEFAULT RANDOM_UUID()";
        String tsType = pg ? "TIMESTAMPTZ DEFAULT NOW()" :
                         mysql ? "TIMESTAMP DEFAULT CURRENT_TIMESTAMP" :
                                 "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";

        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id " + uuidType + " PRIMARY KEY, " +
                "public_key TEXT, " +
                "role VARCHAR(20) DEFAULT 'operator' CHECK (role IN ('admin','auditor','operator','system')), " +
                "created_at " + tsType + ", " +
                "last_auth TIMESTAMP" +
                ")";
        exec(conn, sql);
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)");
    }

    // ────────── audit_logs (hash-chained) ──────────

    private void createAuditLogsTable(Connection conn, boolean pg, boolean mysql) throws Exception {
        String autoId = pg ? "BIGSERIAL" : mysql ? "BIGINT AUTO_INCREMENT" : "BIGINT AUTO_INCREMENT";
        String tsType = pg ? "TIMESTAMPTZ DEFAULT NOW()" :
                         mysql ? "TIMESTAMP DEFAULT CURRENT_TIMESTAMP" :
                                 "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";

        if (pg) {
            // PostgreSQL: partitioned audit_logs by month for scalability
            String partitioned = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                    "id BIGSERIAL, " +
                    "action VARCHAR(100), " +
                    "actor VARCHAR(100), " +
                    "timestamp TIMESTAMPTZ DEFAULT NOW(), " +
                    "hash VARCHAR(128), " +
                    "previous_hash VARCHAR(128), " +
                    "merkle_leaf VARCHAR(128), " +
                    "signature TEXT" +
                    ") PARTITION BY RANGE (timestamp)";
            execSafe(conn, partitioned);
            // Create default partition to catch all rows (prevents insert failures)
            execSafe(conn, "CREATE TABLE IF NOT EXISTS audit_logs_default PARTITION OF audit_logs DEFAULT");
        } else {
            String sql = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                    "id " + autoId + " PRIMARY KEY, " +
                    "action VARCHAR(100), " +
                    "actor VARCHAR(100), " +
                    "timestamp " + tsType + ", " +
                    "hash VARCHAR(128), " +
                    "previous_hash VARCHAR(128), " +
                    "merkle_leaf VARCHAR(128), " +
                    "signature TEXT" +
                    ")";
            exec(conn, sql);
        }
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_audit_hash ON audit_logs(hash)");
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_logs(timestamp)");
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs(actor)");
    }

    // ────────── merkle_nodes ──────────

    private void createMerkleNodesTable(Connection conn, boolean pg, boolean mysql) throws Exception {
        String uuidType = pg ? "VARCHAR(36) DEFAULT gen_random_uuid()::TEXT" :
                          mysql ? "VARCHAR(36)" : "VARCHAR(36) DEFAULT RANDOM_UUID()";

        String sql = "CREATE TABLE IF NOT EXISTS merkle_nodes (" +
                "node_id " + uuidType + " PRIMARY KEY, " +
                "parent_hash VARCHAR(128), " +
                "left_hash VARCHAR(128), " +
                "right_hash VARCHAR(128), " +
                "node_hash VARCHAR(128), " +
                "tree_version INT DEFAULT 1" +
                ")";
        exec(conn, sql);
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_mn_hash ON merkle_nodes(node_hash)");
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_mn_ver ON merkle_nodes(tree_version)");
    }

    // ────────── probability_models ──────────

    private void createProbabilityModelsTable(Connection conn, boolean pg, boolean mysql) throws Exception {
        String uuidType = pg ? "VARCHAR(36) DEFAULT gen_random_uuid()::TEXT" :
                          mysql ? "VARCHAR(36)" : "VARCHAR(36) DEFAULT RANDOM_UUID()";

        String sql = "CREATE TABLE IF NOT EXISTS probability_models (" +
                "model_id " + uuidType + " PRIMARY KEY, " +
                "sr_value DOUBLE PRECISION, " +
                "correlation_coefficient DOUBLE PRECISION, " +
                "decision VARCHAR(50), " +
                "entropy_score DOUBLE PRECISION" +
                ")";
        exec(conn, sql);
    }

    // ────────── decoy_operations ──────────

    private void createDecoyOperationsTable(Connection conn, boolean pg, boolean mysql) throws Exception {
        String uuidType = pg ? "VARCHAR(36) DEFAULT gen_random_uuid()::TEXT" :
                          mysql ? "VARCHAR(36)" : "VARCHAR(36) DEFAULT RANDOM_UUID()";
        String tsType = pg ? "TIMESTAMPTZ DEFAULT NOW()" :
                         mysql ? "TIMESTAMP DEFAULT CURRENT_TIMESTAMP" :
                                 "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";

        String sql = "CREATE TABLE IF NOT EXISTS decoy_operations (" +
                "decoy_id " + uuidType + " PRIMARY KEY, " +
                "triggered_by VARCHAR(100), " +
                "leaf_target VARCHAR(128), " +
                "mutation_type VARCHAR(50), " +
                "probability DOUBLE PRECISION, " +
                "timestamp " + tsType +
                ")";
        exec(conn, sql);
        execSafe(conn, "CREATE INDEX IF NOT EXISTS idx_decoy_ts ON decoy_operations(timestamp)");
    }

    // ────────── helpers ──────────

    private void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void execSafe(Connection conn, String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception ignored) {
            // index may already exist on MySQL which lacks IF NOT EXISTS for indexes
        }
    }
}
