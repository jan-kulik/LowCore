package dev.jalikdev.lowCore.trialdrops;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TrialDropManager implements Listener {

    private static final List<Material> GUI_MATERIALS = List.of(
            Material.HEAVY_CORE,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.FLOW_BANNER_PATTERN,
            Material.MUSIC_DISC_CREATOR,
            Material.EMERALD_BLOCK,
            Material.IRON_BLOCK,
            Material.DIAMOND_BLOCK,
            Material.GOLDEN_APPLE,
            Material.DIAMOND,
            Material.EMERALD,
            Material.IRON_INGOT,
            Material.DIAMOND_AXE,
            Material.DIAMOND_CHESTPLATE,
            Material.IRON_AXE,
            Material.IRON_CHESTPLATE,
            Material.CROSSBOW,
            Material.BOW,
            Material.SHIELD,
            Material.TRIDENT,
            Material.ENCHANTED_BOOK,
            Material.BOOK,
            Material.WIND_CHARGE,
            Material.OMINOUS_BOTTLE,
            Material.TIPPED_ARROW,
            Material.ARROW,
            Material.HONEY_BOTTLE,
            Material.GOLDEN_CARROT,
            Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.GUSTER_BANNER_PATTERN,
            Material.MUSIC_DISC_PRECIPICE,
            Material.TRIAL_KEY,
            Material.OMINOUS_TRIAL_KEY
    );

    private final LowCore plugin;

    public TrialDropManager(LowCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("trial-drops.enabled", true);
    }

    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("trial-drops.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isBlocked(Material material) {
        return getBlockedMaterials().contains(material);
    }

    public boolean setBlocked(Material material, boolean blocked) {
        if (material == null || !material.isItem()) {
            return false;
        }

        Set<Material> materials = new LinkedHashSet<>(getBlockedMaterials());
        boolean changed = blocked ? materials.add(material) : materials.remove(material);
        if (!changed) {
            return false;
        }

        List<String> serialized = materials.stream()
                .map(Material::name)
                .sorted()
                .toList();
        plugin.getConfig().set("trial-drops.disabled-items", serialized);
        plugin.saveConfig();
        return true;
    }

    public Set<Material> getBlockedMaterials() {
        Set<Material> materials = new LinkedHashSet<>();
        for (String configured : plugin.getConfig().getStringList("trial-drops.disabled-items")) {
            Material material = parseMaterial(configured);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Ignoring invalid trial-drops.disabled-items entry: " + configured);
                continue;
            }
            materials.add(material);
        }
        return Set.copyOf(materials);
    }

    public List<Material> getGuiMaterials() {
        LinkedHashSet<Material> materials = new LinkedHashSet<>(GUI_MATERIALS);
        getBlockedMaterials().stream()
                .sorted(Comparator.comparing(Material::name))
                .forEach(materials::add);
        return List.copyOf(materials);
    }

    public void audit(CommandSender actor, String action) {
        plugin.audit(actor, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!isEnabled() || !isTrialChamberLootTable(event.getLootTable().getKey())) {
            return;
        }

        List<ItemStack> filtered = withoutBlockedItems(event.getLoot());
        if (filtered.size() != event.getLoot().size()) {
            event.setLoot(filtered);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVaultDispenseLoot(BlockDispenseLootEvent event) {
        if (!isEnabled() || !isTrialChamberLootTable(event.getLootTable().getKey())) {
            return;
        }

        List<ItemStack> filtered = withoutBlockedItems(event.getDispensedLoot());
        if (filtered.size() != event.getDispensedLoot().size()) {
            event.setDispensedLoot(filtered);
        }
    }

    private List<ItemStack> withoutBlockedItems(Collection<ItemStack> loot) {
        Set<Material> blocked = getBlockedMaterials();
        if (blocked.isEmpty()) {
            return new ArrayList<>(loot);
        }
        return loot.stream()
                .filter(item -> !isBlockedItem(item, blocked))
                .toList();
    }

    private boolean isBlockedItem(ItemStack item, Set<Material> blocked) {
        return item != null && !item.getType().isAir() && blocked.contains(item.getType());
    }

    public static boolean isTrialChamberLootTable(NamespacedKey key) {
        if (key == null || !NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
            return false;
        }
        String path = key.getKey().toLowerCase(Locale.ROOT);
        return path.startsWith("chests/trial_chambers/")
                || path.startsWith("spawners/trial_chamber/")
                || path.startsWith("spawners/ominous/trial_chamber/")
                || path.startsWith("chests/spawner_trial_chamber/");
    }

    public static Material parseMaterial(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return Material.matchMaterial(input.trim().replace('-', '_').replace(' ', '_'));
    }

    public static String displayName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> formatted = new ArrayList<>(words.length);
        for (String word : words) {
            formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", formatted);
    }
}
