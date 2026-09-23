package dev.jalikdev.lowCore.database;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.*;
import java.sql.*;
import java.util.*;

public class OfflineInventoryRepository {

    private static final int MAX_SERIALIZED_ITEMS = 256;
    private static final int MAX_SERIALIZED_LENGTH = 8 * 1024 * 1024;

    public static class OfflineInventoryMeta {
        public final boolean hasInvSnapshot;
        public final boolean hasEcSnapshot;
        public final boolean hasInvPending;
        public final boolean hasEcPending;
        public final long updatedAt;

        public OfflineInventoryMeta(boolean hasInvSnapshot, boolean hasEcSnapshot, boolean hasInvPending, boolean hasEcPending, long updatedAt) {
            this.hasInvSnapshot = hasInvSnapshot;
            this.hasEcSnapshot = hasEcSnapshot;
            this.hasInvPending = hasInvPending;
            this.hasEcPending = hasEcPending;
            this.updatedAt = updatedAt;
        }
    }

    private final DatabaseManager databaseManager;

    public OfflineInventoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection conn() throws SQLException {
        Connection c = databaseManager.getConnection();
        if (c == null || c.isClosed()) throw new SQLException("DB connection closed");
        return c;
    }

    private String itemArrayToBase64(ItemStack[] items) {
        if (items == null) return null;
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private ItemStack[] base64ToItemArray(String base64) {
        if (base64 == null) return null;
        if (base64.length() > MAX_SERIALIZED_LENGTH) {
            databaseManager.logFailure("deserialize inventory",
                    new IOException("Serialized inventory exceeds size limit"));
            return null;
        }
        byte[] serialized;
        try {
            serialized = Base64.getDecoder().decode(base64);
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
            if (items.length > MAX_SERIALIZED_ITEMS) {
                throw new IOException("Invalid serialized inventory length: " + items.length);
            }
            return items;
        } catch (IOException | IllegalArgumentException modernException) {
            try {
                return deserializeLegacyItems(serializedOrEmpty(base64));
            } catch (IOException legacyException) {
                legacyException.addSuppressed(modernException);
                databaseManager.logFailure("deserialize inventory", legacyException);
                return null;
            }
        }
    }

    private byte[] serializedOrEmpty(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack[] deserializeLegacyItems(byte[] serialized) throws IOException {
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(serialized);
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteIn)) {
            int length = in.readInt();
            if (length < 0 || length > MAX_SERIALIZED_ITEMS) {
                throw new IOException("Invalid serialized inventory length: " + length);
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                try {
                    items[i] = (ItemStack) in.readObject();
                } catch (ClassNotFoundException e) {
                    throw new IOException("Could not deserialize inventory item", e);
                }
            }
            return items;
        }
    }

    public void saveSnapshot(UUID uuid, ItemStack[] inv, ItemStack[] ec) {
        String sql = """
                INSERT INTO offline_inventories (uuid, inv_snapshot, ec_snapshot, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                  inv_snapshot = excluded.inv_snapshot,
                  ec_snapshot = excluded.ec_snapshot,
                  updated_at = excluded.updated_at
                """;

        try {
            String invB = itemArrayToBase64(inv);
            String ecB = itemArrayToBase64(ec);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, invB);
                ps.setString(3, ecB);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        } catch (Exception exception) {
            databaseManager.logFailure("save inventory snapshot", exception);
        }
    }

    public void savePendingInventory(UUID uuid, ItemStack[] inv) {
        String sql = """
                INSERT INTO offline_inventories (uuid, inv_pending, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                  inv_pending = excluded.inv_pending,
                  updated_at = excluded.updated_at
                """;

        try {
            String invB = itemArrayToBase64(inv);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, invB);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (Exception exception) {
            databaseManager.logFailure("save pending inventory", exception);
        }
    }

    public void savePendingEnderChest(UUID uuid, ItemStack[] ec) {
        String sql = """
                INSERT INTO offline_inventories (uuid, ec_pending, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                  ec_pending = excluded.ec_pending,
                  updated_at = excluded.updated_at
                """;

        try {
            String ecB = itemArrayToBase64(ec);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ecB);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (Exception exception) {
            databaseManager.logFailure("save pending ender chest", exception);
        }
    }

    public ItemStack[] loadPendingInventory(UUID uuid) {
        String sql = "SELECT inv_pending FROM offline_inventories WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return base64ToItemArray(rs.getString("inv_pending"));
            }
        } catch (Exception exception) {
            databaseManager.logFailure("load pending inventory", exception);
        }
        return null;
    }

    public ItemStack[] loadPendingEnderChest(UUID uuid) {
        String sql = "SELECT ec_pending FROM offline_inventories WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return base64ToItemArray(rs.getString("ec_pending"));
            }
        } catch (Exception exception) {
            databaseManager.logFailure("load pending ender chest", exception);
        }
        return null;
    }

    public void clearPending(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE offline_inventories SET inv_pending = NULL, ec_pending = NULL WHERE uuid = ?"
        )) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception exception) {
            databaseManager.logFailure("clear pending inventory", exception);
        }
    }

    public ItemStack[] loadEffectiveInventory(UUID uuid) {
        String sql = "SELECT inv_pending, inv_snapshot FROM offline_inventories WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String pending = rs.getString("inv_pending");
                    if (pending != null) return base64ToItemArray(pending);
                    return base64ToItemArray(rs.getString("inv_snapshot"));
                }
            }
        } catch (Exception exception) {
            databaseManager.logFailure("load effective inventory", exception);
        }
        return null;
    }

    public ItemStack[] loadEffectiveEnderChest(UUID uuid) {
        String sql = "SELECT ec_pending, ec_snapshot FROM offline_inventories WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String pending = rs.getString("ec_pending");
                    if (pending != null) return base64ToItemArray(pending);
                    return base64ToItemArray(rs.getString("ec_snapshot"));
                }
            }
        } catch (Exception exception) {
            databaseManager.logFailure("load effective ender chest", exception);
        }
        return null;
    }

    public void deletePlayer(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM offline_inventories WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception exception) {
            databaseManager.logFailure("delete player inventory", exception);
        }
    }

    public int deleteAll() {
        try (Statement st = conn().createStatement()) {
            return st.executeUpdate("DELETE FROM offline_inventories");
        } catch (Exception e) {
            databaseManager.logFailure("delete all inventories", e);
            return 0;
        }
    }

    public List<UUID> getAllWithData() {
        List<UUID> list = new ArrayList<>();
        String sql = "SELECT uuid FROM offline_inventories";

        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    list.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException exception) {
                    databaseManager.logFailure("parse stored player UUID", exception);
                }
            }
        } catch (Exception exception) {
            databaseManager.logFailure("list stored inventories", exception);
        }

        return list;
    }

    public OfflineInventoryMeta getMeta(UUID uuid) {
        String sql = "SELECT inv_snapshot, ec_snapshot, inv_pending, ec_pending, updated_at FROM offline_inventories WHERE uuid = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean hasInvSnap = rs.getString("inv_snapshot") != null;
                    boolean hasEcSnap = rs.getString("ec_snapshot") != null;
                    boolean hasInvPend = rs.getString("inv_pending") != null;
                    boolean hasEcPend = rs.getString("ec_pending") != null;
                    long updated = rs.getLong("updated_at");
                    return new OfflineInventoryMeta(hasInvSnap, hasEcSnap, hasInvPend, hasEcPend, updated);
                }
            }
        } catch (Exception exception) {
            databaseManager.logFailure("load inventory metadata", exception);
        }
        return null;
    }
}
