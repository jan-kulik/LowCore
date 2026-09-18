package dev.jalikdev.lowCore.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntiFreecamLogRepository {

    private final DatabaseManager databaseManager;

    public AntiFreecamLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(UUID playerId, String playerName, String result, String mods,
                     String source, String punishment, String details) {
        String sql = """
                INSERT INTO anti_freecam_logs
                    (uuid, name, result, mods, source, punishment, details, checked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName);
            statement.setString(3, result);
            statement.setString(4, mods);
            statement.setString(5, source);
            statement.setString(6, punishment);
            statement.setString(7, details);
            statement.setLong(8, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save anti-freecam log", exception);
        }
    }

    public List<AntiFreecamLogEntry> findRecent(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        List<AntiFreecamLogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT id, uuid, name, result, mods, source, punishment, details, checked_at
                FROM anti_freecam_logs
                ORDER BY checked_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            statement.setInt(2, safeOffset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new AntiFreecamLogEntry(
                            resultSet.getLong("id"),
                            UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getString("name"),
                            resultSet.getString("result"),
                            resultSet.getString("mods"),
                            resultSet.getString("source"),
                            resultSet.getString("punishment"),
                            resultSet.getString("details"),
                            resultSet.getLong("checked_at")
                    ));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load anti-freecam logs", exception);
        }
        return entries;
    }

    public int count() {
        try (Statement statement = connection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM anti_freecam_logs")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count anti-freecam logs", exception);
        }
    }

    public void trimTo(int maximumEntries) {
        int safeMaximum = Math.max(100, maximumEntries);
        String sql = """
                DELETE FROM anti_freecam_logs
                WHERE id NOT IN (
                    SELECT id FROM anti_freecam_logs
                    ORDER BY checked_at DESC, id DESC
                    LIMIT ?
                )
                """;
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setInt(1, safeMaximum);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not trim anti-freecam logs", exception);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = databaseManager.getConnection();
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is closed");
        }
        return connection;
    }

    public record AntiFreecamLogEntry(long id, UUID playerId, String playerName,
                                      String result, String mods, String source,
                                      String punishment, String details, long checkedAt) {
    }
}
