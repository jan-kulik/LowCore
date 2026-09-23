package dev.jalikdev.lowCore.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException("Could not create plugin data directory");
        }

        File dbFile = new File(plugin.getDataFolder(), "data.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        connection = DriverManager.getConnection(url);
        configureConnection();
        createTables();
    }

    private void configureConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void createTables() throws SQLException {
        String sqlLastLocations = "CREATE TABLE IF NOT EXISTS last_locations (" +
                "uuid TEXT PRIMARY KEY," +
                "name TEXT," +
                "world TEXT," +
                "x DOUBLE," +
                "y DOUBLE," +
                "z DOUBLE," +
                "yaw FLOAT," +
                "pitch FLOAT," +
                "last_seen TIMESTAMP" +
                ");";

        String sqlOfflineInv = "CREATE TABLE IF NOT EXISTS offline_inventories (" +
                "uuid TEXT PRIMARY KEY," +
                "inv_snapshot TEXT," +
                "ec_snapshot TEXT," +
                "inv_pending TEXT," +
                "ec_pending TEXT," +
                "updated_at INTEGER" +
                ");";

        String sqlAntiFreecamLogs = "CREATE TABLE IF NOT EXISTS anti_freecam_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "result TEXT NOT NULL," +
                "mods TEXT," +
                "source TEXT NOT NULL," +
                "punishment TEXT NOT NULL," +
                "details TEXT," +
                "checked_at INTEGER NOT NULL" +
                ");";

        String sqlAuditLogs = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "actor_uuid TEXT," +
                "actor_name TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlLastLocations);
            stmt.execute(sqlOfflineInv);
            stmt.execute(sqlAntiFreecamLogs);
            stmt.execute(sqlAuditLogs);
            stmt.execute("DROP INDEX IF EXISTS idx_anti_freecam_logs_checked_at");
            stmt.execute("DROP INDEX IF EXISTS idx_audit_logs_created_at");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_anti_freecam_logs_order " +
                    "ON anti_freecam_logs(checked_at DESC, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_logs_order " +
                    "ON audit_logs(created_at DESC, id DESC)");
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public Connection getConnection() {
        return connection;
    }

    void logFailure(String operation, Exception exception) {
        plugin.getLogger().log(Level.WARNING, "Database operation failed: " + operation, exception);
    }

    public boolean reconnect() {
        close();
        try {
            connect();
            plugin.getLogger().info("SQLite database reconnected.");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not reconnect to SQLite database!");
            e.printStackTrace();
            return false;
        }
    }
}
