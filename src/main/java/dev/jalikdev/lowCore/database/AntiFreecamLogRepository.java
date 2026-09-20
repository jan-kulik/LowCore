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
        return findRecent(limit, offset, null, null, null);
    }

    public List<AntiFreecamLogEntry> findRecent(int limit, int offset, String resultFilter,
                                                 String clientFilter, String playerFilter) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        List<AntiFreecamLogEntry> entries = new ArrayList<>();
        FilterQuery filter = filterQuery(resultFilter, clientFilter, playerFilter);
        String sql = """
                SELECT id, uuid, name, result, mods, source, punishment, details, checked_at
                FROM anti_freecam_logs
                """ + filter.whereClause() + """
                ORDER BY checked_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            int parameter = bindFilters(statement, filter.values());
            statement.setInt(parameter++, safeLimit);
            statement.setInt(parameter, safeOffset);
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
        return count(null, null, null);
    }

    public int count(String resultFilter, String clientFilter, String playerFilter) {
        FilterQuery filter = filterQuery(resultFilter, clientFilter, playerFilter);
        String sql = "SELECT COUNT(*) FROM anti_freecam_logs" + filter.whereClause();
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bindFilters(statement, filter.values());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count anti-freecam logs", exception);
        }
    }

    private FilterQuery filterQuery(String resultFilter, String clientFilter, String playerFilter) {
        List<String> conditions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (resultFilter != null && !resultFilter.isBlank()) {
            conditions.add("UPPER(result) = UPPER(?)");
            values.add(resultFilter.strip());
        }
        if (clientFilter != null && !clientFilter.isBlank()) {
            conditions.add("LOWER(mods) LIKE LOWER(?)");
            values.add("%" + clientFilter.strip() + "%");
        }
        if (playerFilter != null && !playerFilter.isBlank()) {
            conditions.add("LOWER(name) = LOWER(?)");
            values.add(playerFilter.strip());
        }
        String where = conditions.isEmpty() ? "\n" : " WHERE " + String.join(" AND ", conditions) + "\n";
        return new FilterQuery(where, values);
    }

    private int bindFilters(PreparedStatement statement, List<String> values) throws SQLException {
        int parameter = 1;
        for (String value : values) statement.setString(parameter++, value);
        return parameter;
    }

    public int clear() {
        try (Statement statement = connection().createStatement()) {
            return statement.executeUpdate("DELETE FROM anti_freecam_logs");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear anti-mod logs", exception);
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

    private record FilterQuery(String whereClause, List<String> values) {
    }
}
