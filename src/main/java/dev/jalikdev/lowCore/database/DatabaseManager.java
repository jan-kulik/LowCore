package dev.jalikdev.lowCore.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "data.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        connection = DriverManager.getConnection(url);
        createTables();
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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlLastLocations);
        }

        String sqlOfflineInv = "CREATE TABLE IF NOT EXISTS offline_inventories (" +
                "uuid TEXT PRIMARY KEY," +
                "inv_snapshot TEXT," +
                "ec_snapshot TEXT," +
                "inv_pending TEXT," +
                "ec_pending TEXT," +
                "updated_at INTEGER" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlOfflineInv);
        }

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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlAntiFreecamLogs);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_anti_freecam_logs_checked_at " +
                    "ON anti_freecam_logs(checked_at DESC)");
        }

        String sqlAuditLogs = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "actor_uuid TEXT," +
                "actor_name TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlAuditLogs);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC)");
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }

    public Connection getConnection() {
        return connection;
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
