package com.squid.core.db;

import com.squid.core.model.LeafHistory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LeafHistoryStore {
    private final String dbFile;
    private Connection conn;

    public LeafHistoryStore(String dbFile) throws Exception {
        this.dbFile = dbFile;
        init();
    }

    private void init() throws Exception {
        Path dbPath = Path.of(dbFile).toAbsolutePath();
        Path parent = dbPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        String url = "jdbc:sqlite:" + dbPath.toString();
        conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS leaf_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "idx INTEGER,"
                    + "previous TEXT,"
                    + "newval TEXT,"
                    + "action TEXT,"
                    + "timestamp TEXT"
                    + ")");
        }
    }

    public void insert(LeafHistory h) {
        String sql = "INSERT INTO leaf_history(idx,previous,newval,action,timestamp) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, h.getIndex());
            ps.setString(2, h.getPreviousValue());
            ps.setString(3, h.getNewValue());
            ps.setString(4, h.getAction());
            ps.setString(5, h.getTimestamp());
            ps.executeUpdate();
        } catch (Exception e) {
            // ignore persistence failures
        }
    }

    public List<LeafHistory> listAll() {
        List<LeafHistory> out = new ArrayList<>();
        String sql = "SELECT idx,previous,newval,action,timestamp FROM leaf_history ORDER BY id DESC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                LeafHistory h = new LeafHistory();
                h.setIndex(rs.getInt("idx"));
                h.setPreviousValue(rs.getString("previous"));
                h.setNewValue(rs.getString("newval"));
                h.setAction(rs.getString("action"));
                h.setTimestamp(rs.getString("timestamp"));
                out.add(h);
            }
        } catch (Exception e) {
            // ignore
        }
        return out;
    }
}
