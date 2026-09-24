package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager.Dimension;
import dev.jalikdev.lowCore.utils.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

import static dev.jalikdev.lowCore.utils.GuiUtil.fill;
import static dev.jalikdev.lowCore.utils.GuiUtil.item;
import static dev.jalikdev.lowCore.utils.GuiUtil.title;

public class LockDimensionCommand implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> DIMENSIONS = List.of("nether", "end");
    private static final List<String> ACTIONS = List.of("lock", "unlock", "status", "30m", "1h", "1d");
    private static final int[] DURATION_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final LowCore plugin;
    private final DimensionLockManager lockManager;

    public LockDimensionCommand(LowCore plugin, DimensionLockManager lockManager) {
        this.plugin = plugin;
        this.lockManager = lockManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.dimensions")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                openMainGui(player);
            } else {
                LowCore.sendConfigMessage(sender, "dimensions.command-usage");
            }
            return true;
        }

        if (args.length > 2) {
            LowCore.sendConfigMessage(sender, "dimensions.command-usage");
            return true;
        }

        Optional<Dimension> selected = Dimension.fromInput(args[0]);
        if (selected.isEmpty()) {
            LowCore.sendConfigMessage(sender, "dimensions.command-usage");
            return true;
        }

        Dimension dimension = selected.get();
        if (args.length == 1 || args[1].equalsIgnoreCase("lock")) {
            lockManager.lock(dimension, 0L);
            LowCore.sendConfigMessage(sender, "dimensions.locked-permanent",
                    "dimension", dimension.displayName());
            return true;
        }

        if (args[1].equalsIgnoreCase("unlock")) {
            lockManager.unlock(dimension);
            LowCore.sendConfigMessage(sender, "dimensions.unlocked", "dimension", dimension.displayName());
            return true;
        }

        if (args[1].equalsIgnoreCase("status")) {
            sendStatus(sender, dimension);
            return true;
        }

        try {
            long duration = DurationUtil.parseMillis(args[1]);
            lockManager.lock(dimension, duration);
            LowCore.sendConfigMessage(sender, "dimensions.locked-timed",
                    "dimension", dimension.displayName(), "duration", DurationUtil.formatMillis(duration));
        } catch (IllegalArgumentException exception) {
            LowCore.sendConfigMessage(sender, "dimensions.invalid-duration");
        }
        return true;
    }

    private void openMainGui(Player player) {
        DimensionGuiHolder holder = new DimensionGuiHolder(GuiPage.MAIN, null, 27, "§8Dimension Locks");
        Inventory inventory = holder.getInventory();

        fill(inventory);
        inventory.setItem(11, dimensionItem(Dimension.NETHER));
        inventory.setItem(15, dimensionItem(Dimension.END));
        inventory.setItem(22, item(Material.ARROW, "&eLowCore menu", "&7Return to the control center."));
        player.openInventory(inventory);
    }

    private void openSettingsGui(Player player, Dimension dimension) {
        DimensionGuiHolder holder = new DimensionGuiHolder(
                GuiPage.SETTINGS, dimension, 36, "§8Lock: §f" + dimension.displayName());
        Inventory inventory = holder.getInventory();

        fill(inventory);
        inventory.setItem(4, dimensionItem(dimension));

        List<String> configuredDurations = plugin.getConfig().getStringList("dimensions.gui.durations");
        int buttonIndex = 0;
        for (String configuredDuration : configuredDurations) {
            if (buttonIndex >= DURATION_SLOTS.length) {
                break;
            }
            try {
                long duration = DurationUtil.parseMillis(configuredDuration);
                int slot = DURATION_SLOTS[buttonIndex++];
                holder.durationBySlot().put(slot, duration);
                inventory.setItem(slot, item(Material.CLOCK, "&eLock for " + configuredDuration,
                        "&7Automatically unlocks afterwards.", "", "&eClick to apply"));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "Ignoring invalid dimensions.gui.durations entry: " + configuredDuration);
            }
        }

        inventory.setItem(20, item(Material.RED_CONCRETE, "&cPermanent lock",
                "&7Locks the " + dimension.displayName() + " until manually unlocked.", "", "&cClick to lock"));
        inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to the dimension overview."));
        inventory.setItem(24, item(Material.LIME_CONCRETE, "&aUnlock now",
                "&7Immediately opens the " + dimension.displayName() + ".", "", "&aClick to unlock"));
        player.openInventory(inventory);
    }

    private ItemStack dimensionItem(Dimension dimension) {
        long remaining = lockManager.getRemainingMillis(dimension);
        Material material = dimension == Dimension.NETHER ? Material.NETHERRACK : Material.END_STONE;
        String color = dimension == Dimension.NETHER ? "&c" : "&d";
        List<String> lore = new ArrayList<>();

        if (remaining < 0L) {
            lore.add("&7Status: &aUnlocked");
        } else if (remaining == 0L) {
            lore.add("&7Status: &cPermanently locked");
        } else {
            lore.add("&7Status: &cLocked");
            lore.add("&7Remaining: &e" + DurationUtil.formatMillis(remaining));
        }
        lore.add("");
        lore.add("&eClick to manage");
        return item(material, color + dimension.displayName(), lore.toArray(String[]::new));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DimensionGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        if (holder.page() == GuiPage.MAIN) {
            if (slot == 11) {
                openSettingsGui(player, Dimension.NETHER);
            } else if (slot == 15) {
                openSettingsGui(player, Dimension.END);
            } else if (slot == 22) {
                player.performCommand("lowcore");
            }
            return;
        }

        Dimension dimension = holder.dimension();
        if (slot == 20) {
            lockManager.lock(dimension, 0L);
            plugin.audit(player, "Permanently locked " + dimension.displayName());
            LowCore.sendConfigMessage(player, "dimensions.locked-permanent",
                    "dimension", dimension.displayName());
            openSettingsGui(player, dimension);
        } else if (slot == 22) {
            openMainGui(player);
        } else if (slot == 24) {
            lockManager.unlock(dimension);
            plugin.audit(player, "Unlocked " + dimension.displayName());
            LowCore.sendConfigMessage(player, "dimensions.unlocked", "dimension", dimension.displayName());
            openSettingsGui(player, dimension);
        } else {
            Long duration = holder.durationBySlot().get(slot);
            if (duration == null) return;
            lockManager.lock(dimension, duration);
            plugin.audit(player, "Locked " + dimension.displayName() + " for " + DurationUtil.formatMillis(duration));
            LowCore.sendConfigMessage(player, "dimensions.locked-timed",
                    "dimension", dimension.displayName(), "duration", DurationUtil.formatMillis(duration));
            openSettingsGui(player, dimension);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DimensionGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void sendStatus(CommandSender sender, Dimension dimension) {
        long remaining = lockManager.getRemainingMillis(dimension);
        if (remaining < 0L) {
            LowCore.sendConfigMessage(sender, "dimensions.status-open", "dimension", dimension.displayName());
        } else if (remaining == 0L) {
            LowCore.sendConfigMessage(sender, "dimensions.status-permanent", "dimension", dimension.displayName());
        } else {
            LowCore.sendConfigMessage(sender, "dimensions.status-timed",
                    "dimension", dimension.displayName(), "duration", DurationUtil.formatMillis(remaining));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return matching(DIMENSIONS, args[0]);
        }
        if (args.length == 2) {
            return matching(ACTIONS, args[1]);
        }
        return List.of();
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private enum GuiPage {
        MAIN,
        SETTINGS
    }

    private static final class DimensionGuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final Dimension dimension;
        private final Map<Integer, Long> durationBySlot;
        private final Inventory inventory;

        private DimensionGuiHolder(GuiPage page, Dimension dimension, int size, String inventoryTitle) {
            this.page = page;
            this.dimension = dimension;
            this.durationBySlot = page == GuiPage.SETTINGS ? new HashMap<>() : Map.of();
            this.inventory = Bukkit.createInventory(this, size, title(inventoryTitle));
        }

        private GuiPage page() {
            return page;
        }

        private Dimension dimension() {
            return dimension;
        }

        private Map<Integer, Long> durationBySlot() {
            return durationBySlot;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
