package com.ihsannoob.aiplugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Simple per-player SQLite storage.
 * Each player gets a file <pluginFolder>/players/<uuid>.db
 * Table: messages(id, role, content, created_at)
 */
public class DatabaseManager {

    public static class StoredMessage {
        public final String role;
        public final String content;
        public final long createdAt;

        public StoredMessage(String role, String content, long createdAt) {
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    private final File baseFolder;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Connection> connections = new ConcurrentHashMap<>();

    public DatabaseManager(File pluginFolder, Logger logger) {
        this.baseFolder = new File(pluginFolder, "players");
        if (!baseFolder.exists()) baseFolder.mkdirs();
        this.logger = logger;
    }

    private File dbFile(UUID uuid) {
        return new File(baseFolder, uuid.toString() + ".db");
    }

    private Connection openConnection(UUID uuid) throws SQLException {
        return connections.computeIfAbsent(uuid, u -> {
            try {
                File f = dbFile(u);
                String url = "jdbc:sqlite:" + f.getAbsolutePath();
                Connection conn = DriverManager.getConnection(url);
                conn.setAutoCommit(true);
                // ensure table exists
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
                } catch (SQLException e) {
                    logger.severe("Failed to create messages table: " + e.getMessage());
                }
                return conn;
            } catch (SQLException ex) {
                logger.severe("Failed to open DB for " + u + ": " + ex.getMessage());
                return null;
            }
        });
    }

    public void addMessage(UUID uuid, String role, String content) {
        try {
            Connection conn = openConnection(uuid);
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO messages (role, content, created_at) VALUES (?, ?, ?)")) {
                ps.setString(1, role);
                ps.setString(2, content);
                ps.setLong(3, Instant.now().getEpochSecond());
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            logger.severe("Failed to add message for " + uuid + ": " + ex.getMessage());
        }
    }

    /**
     * Get latest 'limit' messages in chronological order (oldest -> newest).
     */
    public List<StoredMessage> getConversation(UUID uuid, int limit) {
        List<StoredMessage> out = new ArrayList<>();
        try {
            Connection conn = openConnection(uuid);
            if (conn == null) return out;
            try (PreparedStatement ps = conn.prepareStatement("SELECT role, content, created_at FROM messages ORDER BY created_at DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String role = rs.getString("role");
                        String content = rs.getString("content");
                        long created = rs.getLong("created_at");
                        out.add(new StoredMessage(role, content, created));
                    }
                }
            }
            // current list is newest-first; reverse to chronological
            java.util.Collections.reverse(out);
        } catch (SQLException ex) {
            logger.severe("Failed to read conversation for " + uuid + ": " + ex.getMessage());
        }
        return out;
    }

    /**
     * Remove all history for a UUID: closes connection, deletes DB file.
     */
    public void clearConversation(UUID uuid) {
        try {
            // close and remove connection if present
            Connection c = connections.remove(uuid);
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignore) {}
            }
            File f = dbFile(uuid);
            if (f.exists()) {
                if (!f.delete()) {
                    logger.warning("Failed to delete DB file: " + f.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            logger.severe("Failed to clear conversation for " + uuid + ": " + ex.getMessage());
        }
    }

    public void closeAll() {
        for (UUID u : connections.keySet()) {
            Connection c = connections.remove(u);
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignore) {}
            }
        }
    }
}
