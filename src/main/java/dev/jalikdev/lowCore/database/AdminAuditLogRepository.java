package dev.jalikdev.lowCore.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdminAuditLogRepository {
    private final DatabaseManager databaseManager;

    public AdminAuditLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(UUID actorId, String actorName, String action) {
        String sql = "INSERT INTO audit_logs (actor_uuid, actor_name, action, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, actorId == null ? null : actorId.toString());
            statement.setString(2, actorName);
            statement.setString(3, action);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save admin audit log", exception);
        }
    }

    public List<AuditEntry> findRecent(int limit, int offset) {
        List<AuditEntry> entries = new ArrayList<>();
        String sql = "SELECT id, actor_uuid, actor_name, action, created_at FROM audit_logs " +
                "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            statement.setInt(2, Math.max(0, offset));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String uuid = result.getString("actor_uuid");
                    entries.add(new AuditEntry(result.getLong("id"),
                            uuid == null ? null : UUID.fromString(uuid), result.getString("actor_name"),
                            result.getString("action"), result.getLong("created_at")));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load admin audit logs", exception);
        }
        return entries;
    }

    public int count() {
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM audit_logs")) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count admin audit logs", exception);
        }
    }

    public int clear() {
        try (Statement statement = connection().createStatement()) {
            return statement.executeUpdate("DELETE FROM audit_logs");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear admin audit logs", exception);
        }
    }

    public void trimTo(int maximumEntries) {
        String sql = "DELETE FROM audit_logs WHERE id NOT IN (SELECT id FROM audit_logs " +
                "ORDER BY created_at DESC, id DESC LIMIT ?)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setInt(1, Math.max(100, maximumEntries));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not trim admin audit logs", exception);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = databaseManager.getConnection();
        if (connection == null || connection.isClosed()) throw new SQLException("Database connection is closed");
        return connection;
    }

    public record AuditEntry(long id, UUID actorId, String actorName, String action, long createdAt) {}
}
