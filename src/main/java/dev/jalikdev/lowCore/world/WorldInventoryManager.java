package dev.jalikdev.lowCore.world;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class WorldInventoryManager {

    private final LowCore plugin;

    public WorldInventoryManager(LowCore plugin) {
        this.plugin = plugin;
    }

    public void savePlayerInventory(Player player, String groupKey) {
        UUID uuid = player.getUniqueId();
        String basePath = "world.inventories." + uuid + "." + groupKey;

        try {
            String invData = itemStackArrayToBase64(player.getInventory().getContents());
            String armorData = itemStackArrayToBase64(player.getInventory().getArmorContents());
            String ecData = itemStackArrayToBase64(player.getEnderChest().getContents());

            plugin.getConfig().set(basePath + ".inventory", invData);
            plugin.getConfig().set(basePath + ".armor", armorData);
            plugin.getConfig().set(basePath + ".enderchest", ecData);
            plugin.saveConfig();
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to save inventory for " + player.getName() + " in group " + groupKey, exception);
        }
    }

    public void loadPlayerInventory(Player player, String groupKey) {
        UUID uuid = player.getUniqueId();
        String basePath = "world.inventories." + uuid + "." + groupKey;

        String invData = plugin.getConfig().getString(basePath + ".inventory");
        String armorData = plugin.getConfig().getString(basePath + ".armor");
        String ecData = plugin.getConfig().getString(basePath + ".enderchest");

        if (invData == null || armorData == null || ecData == null) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getEnderChest().clear();
            return;
        }

        try {
            player.getInventory().setContents(itemStackArrayFromBase64(invData));
            player.getInventory().setArmorContents(itemStackArrayFromBase64(armorData));
            player.getEnderChest().setContents(itemStackArrayFromBase64(ecData));
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to load inventory for " + player.getName() + " in group " + groupKey, exception);
        }
    }

    private String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data.length() > 8 * 1024 * 1024) throw new IOException("Serialized inventory exceeds size limit");
        byte[] serialized = Base64.getDecoder().decode(data);
        try {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(serialized);
            if (items.length > 256) throw new IOException("Invalid serialized inventory length: " + items.length);
            return items;
        } catch (IllegalArgumentException exception) {
            return deserializeLegacyItems(serialized);
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack[] deserializeLegacyItems(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(serialized);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            if (length < 0 || length > 256) throw new IOException("Invalid serialized inventory length: " + length);
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) items[i] = (ItemStack) dataInput.readObject();
            return items;
        }
    }
}
