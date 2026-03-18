package com.squid.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides database health metrics for the dashboard.
 */
@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final DataSource dataSource;
    private final SquidDatabaseService dbService;

    @Value("${squid.database.type:h2}")
    private String configuredDbType;

    @Value("${squid.database.ssl-enabled:false}")
    private boolean sslEnabled;

    public DatabaseHealthService(DataSource dataSource, SquidDatabaseService dbService) {
        this.dataSource = dataSource;
        this.dbService = dbService;
    }

    /**
     * Returns a comprehensive health snapshot.
     */
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            long latency = System.currentTimeMillis() - start;
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();

            health.put("connected", true);
            health.put("db_type", configuredDbType);
            health.put("db_product", dbProduct);
            health.put("db_version", dbVersion);
            health.put("latency_ms", latency);
            health.put("ssl_enabled", sslEnabled);

            // Active connections (HikariCP exposes this via JMX, but we
            // approximate by querying the DB if possible)
            health.put("active_connections", getActiveConnections(conn, dbProduct));

            // Database size
            health.put("db_size_mb", getDatabaseSizeMb(conn, dbProduct));

            // Table row counts
            health.put("table_counts", dbService.getTableCounts());

        } catch (Exception e) {
            health.put("connected", false);
            health.put("db_type", configuredDbType);
            health.put("error", e.getMessage());
        }
        return health;
    }

    /**
     * Minimal config info (no secrets).
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", configuredDbType);
        cfg.put("ssl_enabled", sslEnabled);
        try (Connection conn = dataSource.getConnection()) {
            cfg.put("connected", true);
            cfg.put("db_product", conn.getMetaData().getDatabaseProductName());
            cfg.put("url_masked", maskUrl(conn.getMetaData().getURL()));
        } catch (Exception e) {
            cfg.put("connected", false);
        }
        return cfg;
    }

    // ───────────────── internals ─────────────────

    private int getActiveConnections(Connection conn, String dbProduct) {
        try (Statement st = conn.createStatement()) {
            String sql;
            if (dbProduct.toLowerCase().contains("postgres")) {
                sql = "SELECT COUNT(*) FROM pg_stat_activity WHERE state = 'active'";
            } else if (dbProduct.toLowerCase().contains("mysql")) {
                sql = "SELECT COUNT(*) FROM information_schema.processlist";
            } else {
                return -1; // H2 doesn't expose this easily
            }
            ResultSet rs = st.executeQuery(sql);
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private double getDatabaseSizeMb(Connection conn, String dbProduct) {
        try (Statement st = conn.createStatement()) {
            String sql;
            if (dbProduct.toLowerCase().contains("postgres")) {
                sql = "SELECT pg_database_size(current_database()) / 1048576.0";
            } else if (dbProduct.toLowerCase().contains("mysql")) {
                sql = "SELECT SUM(data_length + index_length) / 1048576.0 " +
                      "FROM information_schema.tables WHERE table_schema = DATABASE()";
            } else {
                return -1.0;
            }
            ResultSet rs = st.executeQuery(sql);
            return rs.next() ? Math.round(rs.getDouble(1) * 100.0) / 100.0 : -1.0;
        } catch (Exception e) {
            return -1.0;
        }
    }

    private String maskUrl(String url) {
        if (url == null) return "unknown";
        // Hide password if embedded in URL
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
