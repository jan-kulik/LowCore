package dev.jalikdev.lowCore.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class LastLocationRepository {

    private final DatabaseManager db;

    public LastLocationRepository(DatabaseManager db) {
        this.db = db;
    }

    public void saveLogoutLocation(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        String sql = "INSERT OR REPLACE INTO last_locations " +
                "(uuid, name, world, x, y, z, yaw, pitch, last_seen) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.setString(3, world.getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            db.logFailure("save logout location", e);
        }
    }

    public Location getLastLocationByName(String name) {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM last_locations " +
                "WHERE name = ? ORDER BY last_seen DESC LIMIT 1;";

        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        return null;
                    }

                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");

                    return new Location(world, x, y, z, yaw, pitch);
                }
            }
        } catch (SQLException e) {
            db.logFailure("load logout location", e);
        }

        return null;
    }

    private Connection connection() throws SQLException {
        Connection connection = db.getConnection();
        if (connection == null || connection.isClosed()) throw new SQLException("Database connection is closed");
        return connection;
    }
}
