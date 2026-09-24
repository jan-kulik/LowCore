package dev.jalikdev.lowCore.commands;

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
import org.bukkit.inventory.meta.SkullMeta;
import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager.Dimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.jalikdev.lowCore.utils.GuiUtil.fill;
import static dev.jalikdev.lowCore.utils.GuiUtil.item;
import static dev.jalikdev.lowCore.utils.GuiUtil.title;

public class LowcoreCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int PLAYER_PAGE_SIZE = 45;
    private static final int[] CRYSTAL_VALUES = {0, 1, 2, 5, 10, 20, 40};
    private static final int[] CRYSTAL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final double[] WARNING_THRESHOLDS = {19.0, 18.0, 17.0, 16.0};
    private static final double[] SEVERE_THRESHOLDS = {17.0, 15.0, 12.0, 10.0};
    private static final List<String> MAIN_SUBCOMMANDS = List.of("gui", "help", "info", "reload");
    private static final List<String> HELP_TOPICS = List.of(
            "lowcore", "ec", "enchant", "feed", "fly", "gm", "hat", "heal", "invsee", "spawnmob"
    );

    private final LowCore plugin;

    public LowcoreCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            if (sender instanceof Player player) openMainGui(player);
            else sendMainHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("gui")) {
            if (sender instanceof Player player) openMainGui(player);
            else LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        if (sub.equals("help")) {
            if (args.length == 1) {
                sendMainHelp(sender);
                return true;
            }

            String topic = args[1].toLowerCase();
            sendDetailedHelp(sender, topic);
            return true;
        }

        if (sub.equals("info")) {
            LowCore.sendMessage(sender, "&aLowCore &7by &ajalikdev");
            LowCore.sendMessage(sender, "&7Lightweight core plugin with essential commands.");
            LowCore.sendMessage(sender, "&7Use &a/lowcore help &7for the full command list.");
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("lowcore.reload")) {
                LowCore.sendConfigMessage(sender, "no-permission");
                return true;
            }

            plugin.reloadLowCoreConfig(sender);
            return true;
        }

        sendMainHelp(sender);
        return true;
    }

    private void sendMainHelp(CommandSender sender) {
        LowCore.sendMessage(sender, "&8&m-------------------------------");
        LowCore.sendMessage(sender, "&aLowCore &7Command Overview:");

        LowCore.sendMessage(sender, "&a/lowcore help &7- Show this help.");
        LowCore.sendMessage(sender, "&a/lowcore info &7- Plugin information.");
        LowCore.sendMessage(sender, "&a/lowcore reload &7- Reload the config.");
        LowCore.sendMessage(sender, "&a/lock-dimension <nether|end> [time] &7- Permanent or timed dimension lock.");
        LowCore.sendMessage(sender, "&a/crystal-cooldown <ticks|off|status> &7- Set the Crystal placement delay.");
        LowCore.sendMessage(sender, "&a/anti-mods &7- Configure client-mod detection.");

        LowCore.sendMessage(sender, "&a/ec &7- Open your ender chest.");
        LowCore.sendMessage(sender, "&a/enchant &7- Advanced enchanting / renaming.");
        LowCore.sendMessage(sender, "&a/feed &7- Feed yourself or others.");
        LowCore.sendMessage(sender, "&a/fly &7- Toggle flight.");
        LowCore.sendMessage(sender, "&a/gm &7- Change your gamemode.");
        LowCore.sendMessage(sender, "&a/hat &7- Put the held item on your head.");
        LowCore.sendMessage(sender, "&a/heal &7- Heal yourself or another player.");
        LowCore.sendMessage(sender, "&a/invsee &7- View another player's inventory.");
        LowCore.sendMessage(sender, "&a/spawnmob &7- Spawn mobs where you look.");
        LowCore.sendMessage(sender, "&a/anvil &7- Open an anvil GUI.");
        LowCore.sendMessage(sender, "&a/repair &7- Repair your item or inventory.");
        LowCore.sendMessage(sender, "&a/craft &7- Open a workbench.");
        LowCore.sendMessage(sender, "&a/vanish &7- Toggle vanish mode.");
        LowCore.sendMessage(sender, "&a/speed &7- Set walk/fly speed.");
        LowCore.sendMessage(sender, "&a/god &7- Toggle invulnerability.");
        LowCore.sendMessage(sender, "&a/killall &7- Kill all mobs or by type/radius.");

        LowCore.sendMessage(sender, "&8&m-------------------------------");
    }


    private void sendDetailedHelp(CommandSender sender, String topic) {
        switch (topic) {
            case "lowcore":
                LowCore.sendMessage(sender, "&a/lowcore help &7- Show all available LowCore commands.");
                LowCore.sendMessage(sender, "&a/lowcore help <command> &7- Detailed help for one command.");
                LowCore.sendMessage(sender, "&a/lowcore info &7- Show plugin information.");
                LowCore.sendMessage(sender, "&a/lowcore reload &7- Reload the config (requires &flowcore.reload&7).");
                break;

            case "ec":
                LowCore.sendMessage(sender, "&a/ec");
                LowCore.sendMessage(sender, "&7Open your ender chest quickly.");
                break;

            case "feed":
                LowCore.sendMessage(sender, "&a/feed &7- Feed yourself.");
                LowCore.sendMessage(sender, "&a/feed <player> &7- Feed another player.");
                break;

            case "fly":
                LowCore.sendMessage(sender, "&a/fly");
                LowCore.sendMessage(sender, "&7Toggle flight for yourself (permission required).");
                break;

            case "gm":
                LowCore.sendMessage(sender, "&a/gm <mode>");
                LowCore.sendMessage(sender, "&7Quickly change your gamemode (0/1/2/3 or names).");
                break;

            case "hat":
                LowCore.sendMessage(sender, "&a/hat");
                LowCore.sendMessage(sender, "&7Move the item in your hand to your helmet slot.");
                break;

            case "heal":
                LowCore.sendMessage(sender, "&a/heal &7- Heal yourself.");
                LowCore.sendMessage(sender, "&a/heal <player> &7- Heal another player.");
                break;

            case "invsee":
                LowCore.sendMessage(sender, "&a/invsee <player>");
                LowCore.sendMessage(sender, "&7View and live-sync another player's inventory.");
                break;

            case "spawnmob":
                LowCore.sendMessage(sender, "&a/spawnmob <mob> [amount]");
                LowCore.sendMessage(sender, "&7Spawn mobs at the block/location you are looking at.");
                LowCore.sendMessage(sender, "&7Respects a max-amount and restricted mobs from config.");
                LowCore.sendMessage(sender, "&7Tab completion helps with valid mob names.");
                break;

            case "enchant":
                LowCore.sendMessage(sender, "&a/enchant <enchant> [level]");
                LowCore.sendMessage(sender, "&7Enchant the item in your hand.");
                LowCore.sendMessage(sender, "&a/enchant remove <enchant> &7- Remove a specific enchantment.");
                LowCore.sendMessage(sender, "&a/enchant clear &7- Remove all enchantments from the held item.");
                LowCore.sendMessage(sender, "&a/enchant name <name> &7- Rename the held item (& codes supported).");
                LowCore.sendMessage(sender, "&a/enchant resetname &7- Reset the custom name of the held item.");
                LowCore.sendMessage(sender, "&7Without bypass: only compatible enchants and vanilla max levels.");
                LowCore.sendMessage(sender, "&7With &alowcore.enchant.bypass&7: unsafe up to 255, incompatible allowed (with warning).");
                break;

            case "speed":
                LowCore.sendMessage(sender, "&a/speed <1-10>");
                LowCore.sendMessage(sender, "&7Sets your fly/walk speed depending on your state.");
                break;

            case "god":
                LowCore.sendMessage(sender, "&a/god");
                LowCore.sendMessage(sender, "&7Toggle invulnerability for yourself.");
                break;

            case "anvil":
                LowCore.sendMessage(sender, "&a/anvil");
                LowCore.sendMessage(sender, "&7Open a virtual anvil.");
                break;

            case "repair":
                LowCore.sendMessage(sender, "&a/repair &7- Repair held item.");
                LowCore.sendMessage(sender, "&a/repair all &7- Repair entire inventory.");
                break;

            case "craft":
                LowCore.sendMessage(sender, "&a/craft &7- Open a virtual crafting table.");
                LowCore.sendMessage(sender, "&a/workbench &7- Alias for /craft.");
                break;

            case "vanish":
                LowCore.sendMessage(sender, "&a/vanish");
                LowCore.sendMessage(sender, "&7Become invisible (entity + tab list), fake join/quit.");
                break;

            case "killall":
                LowCore.sendMessage(sender, "&a/killall &7- Kill all mobs in the world.");
                LowCore.sendMessage(sender, "&a/killall <type> &7- Kill specific mob type.");
                LowCore.sendMessage(sender, "&a/killall <type> <radius> &7- Kill only mobs of that type near you.");
                break;

            default:
                LowCore.sendConfigMessage(sender, "lowcore.help-unknown-topic", "topic", topic);
                break;
        }
    }

    private void openMainGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.MAIN, "§8LowCore Control Center");
        Inventory inventory = holder.inventory;
        fill(inventory);

        boolean netherLocked = plugin.getDimensionLockManager().isLocked(Dimension.NETHER);
        boolean endLocked = plugin.getDimensionLockManager().isLocked(Dimension.END);
        inventory.setItem(10, item(Material.RESPAWN_ANCHOR, "&cDimension Locks",
                "&7Nether: " + (netherLocked ? "&cLocked" : "&aOpen"),
                "&7End: " + (endLocked ? "&cLocked" : "&aOpen"), "", "&eClick to manage"));

        int crystalTicks = Math.max(0, plugin.getConfig().getInt("crystal-cooldown.ticks", 0));
        inventory.setItem(12, item(Material.END_CRYSTAL, "&dCrystal Cooldown",
                "&7Current: &e" + crystalTicks + " ticks", "", "&eClick to configure"));
        inventory.setItem(14, item(Material.SHIELD, "&bAnti-Mods",
                "&7Status: " + (plugin.getConfig().getBoolean("anti-mods.enabled", false) ? "&aEnabled" : "&cDisabled"),
                "&7Punishment: &e" + plugin.getConfig().getString("anti-mods.punishment", "kick"),
                "", "&eClick to manage"));
        inventory.setItem(16, item(Material.HEAVY_CORE, "&6Trial Chamber Drops",
                "&7Status: " + (plugin.getConfig().getBoolean("trial-drops.enabled", true) ? "&aEnabled" : "&cDisabled"),
                "&7Blocked items: &e" + plugin.getConfig().getStringList("trial-drops.disabled-items").size(),
                "", "&eClick to manage"));
        inventory.setItem(28, item(Material.CLOCK, "&aPerformance",
                "&7Monitor: " + (plugin.getConfig().getBoolean("performance-monitor.enabled", true) ? "&aEnabled" : "&cDisabled"),
                "&7View metrics and configure monitoring.", "", "&eClick to manage"));
        inventory.setItem(30, item(Material.LAVA_BUCKET, "&cLag Cleanup",
                "&7Status: " + (plugin.getConfig().getBoolean("lag-cleanup.enabled", true) ? "&aEnabled" : "&cDisabled"),
                "&7Open entity cleanup controls.", "", "&eClick to manage"));
        inventory.setItem(32, item(Material.WRITABLE_BOOK, "&eAdmin Audit Log",
                "&7Commands and settings changes.", "", "&eClick to view"));
        inventory.setItem(34, item(Material.COMPARATOR, "&fGeneral Settings",
                "&7Messages, MOTD and system toggles.", "", "&eClick to manage"));
        inventory.setItem(46, item(Material.CHEST, "&bAdmin & Utility GUIs",
                "&7Inventories, Ender Chests, crafting and anvil.", "", "&eClick to open"));
        inventory.setItem(48, item(Material.COMMAND_BLOCK, "&fReload Configuration",
                "&7Reload LowCore's config from disk.", "", "&eClick to reload"));
        inventory.setItem(49, item(Material.BARRIER, "&cClose", "&7Close the control center."));
        player.openInventory(inventory);
    }

    private void openCrystalGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.CRYSTAL, "§8Crystal Cooldown");
        Inventory inventory = holder.inventory;
        fill(inventory);
        int current = Math.max(0, plugin.getConfig().getInt("crystal-cooldown.ticks", 0));
        for (int index = 0; index < CRYSTAL_VALUES.length; index++) {
            int ticks = CRYSTAL_VALUES[index];
            holder.crystalTicksBySlot.put(CRYSTAL_SLOTS[index], ticks);
            inventory.setItem(CRYSTAL_SLOTS[index], item(ticks == 0 ? Material.BARRIER : Material.CLOCK,
                    (current == ticks ? "&a" : "&e") + (ticks == 0 ? "Disabled" : ticks + " ticks"),
                    ticks == 0 ? "&7Disable placement cooldown." : "&7Approximately " + formatSeconds(ticks) + " seconds.",
                    current == ticks ? "&aCurrently selected" : "&eClick to apply"));
        }
        inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to the control center."));
        player.openInventory(inventory);
    }

    private void openGeneralGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.GENERAL, "§8LowCore General Settings");
        Inventory inventory = holder.inventory;
        fill(inventory);
        toggle(inventory, 10, Material.NAME_TAG, "Join/Quit messages", "join-quit-messages.enabled");
        toggle(inventory, 12, Material.OAK_SIGN, "Server list MOTD", "motd.enabled");
        toggle(inventory, 14, Material.ENDER_EYE, "Update checker", "update-checker.enabled");
        toggle(inventory, 16, Material.COMPASS, "Last logout command", "lastlogout.enabled");
        toggle(inventory, 28, Material.CLOCK, "Performance command", "performance.enabled");
        toggle(inventory, 30, Material.REDSTONE_TORCH, "Performance monitor", "performance-monitor.enabled");
        toggle(inventory, 32, Material.LAVA_BUCKET, "Lag cleanup", "lag-cleanup.enabled");
        toggle(inventory, 34, Material.SHIELD, "Cleanup confirmation", "lag-cleanup.confirm-required");
        inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to the control center."));
        player.openInventory(inventory);
    }

    private void openPerformanceGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.PERFORMANCE, "§8Performance Dashboard");
        Inventory inventory = holder.inventory;
        fill(inventory);
        double[] tps = Bukkit.getServer().getTPS();
        double currentTps = tps.length == 0 ? 20.0 : Math.min(20.0, tps[0]);
        double mspt = Bukkit.getServer().getAverageTickTime();
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maximum = runtime.maxMemory() / (1024 * 1024);
        int chunks = Bukkit.getWorlds().stream().mapToInt(org.bukkit.World::getChunkCount).sum();
        inventory.setItem(10, item(Material.CLOCK, "&aTPS: &f" + String.format(java.util.Locale.ROOT, "%.2f", currentTps),
                "&7MSPT: &f" + String.format(java.util.Locale.ROOT, "%.2f", mspt)));
        inventory.setItem(12, item(Material.REDSTONE, "&cMemory",
                "&7Used: &e" + used + " MB", "&7Maximum: &e" + maximum + " MB"));
        inventory.setItem(14, item(Material.PLAYER_HEAD, "&bPlayers: &f" + Bukkit.getOnlinePlayers().size(),
                "&7Maximum: &e" + Bukkit.getMaxPlayers()));
        inventory.setItem(16, item(Material.MAP, "&eLoaded chunks: &f" + chunks));
        inventory.setItem(20, item(Material.YELLOW_DYE, "&eWarning TPS: &f"
                        + plugin.getConfig().getDouble("performance-monitor.warn-tps", 18.0),
                "&7Click to cycle 19 / 18 / 17 / 16."));
        inventory.setItem(24, item(Material.RED_DYE, "&cSevere TPS: &f"
                        + plugin.getConfig().getDouble("performance-monitor.severe-tps", 15.0),
                "&7Click to cycle 17 / 15 / 12 / 10."));
        toggle(inventory, 28, Material.COMPARATOR, "Performance command", "performance.enabled");
        toggle(inventory, 30, Material.REDSTONE_TORCH, "Performance monitor", "performance-monitor.enabled");
        toggle(inventory, 32, Material.REPEATER, "Show MSPT", "performance.show-mspt");
        toggle(inventory, 34, Material.MAP, "Show chunks", "performance.show-chunks");
        inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to the control center."));
        player.openInventory(inventory);
    }

    private void openUtilitiesGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.UTILITIES, "§8Admin & Utility GUIs");
        Inventory inventory = holder.inventory;
        fill(inventory);
        inventory.setItem(10, item(Material.ENDER_CHEST, "&dYour Ender Chest", "&7Open your Ender Chest."));
        inventory.setItem(12, item(Material.CRAFTING_TABLE, "&6Crafting Table", "&7Open a virtual crafting table."));
        inventory.setItem(14, item(Material.ANVIL, "&fAnvil", "&7Open a virtual anvil."));
        inventory.setItem(16, item(Material.CHEST, "&bPlayer Inventory", "&7Select an online player's inventory."));
        inventory.setItem(30, item(Material.ENDER_EYE, "&5Player Ender Chest", "&7Select an online player's Ender Chest."));
        inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to the control center."));
        player.openInventory(inventory);
    }

    private void openPlayerSelector(Player player, CorePage page, int requestedPage) {
        String inventoryTitle = page == CorePage.INVSEE_PLAYERS ? "§8Select Inventory" : "§8Select Ender Chest";
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(target -> page != CorePage.INVSEE_PLAYERS || !target.getUniqueId().equals(player.getUniqueId()))
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
        int maximumPage = Math.max(0, (targets.size() - 1) / PLAYER_PAGE_SIZE);
        int selectedPage = Math.max(0, Math.min(requestedPage, maximumPage));
        CoreGuiHolder holder = new CoreGuiHolder(page, inventoryTitle, selectedPage, maximumPage);
        fill(holder.inventory);
        int start = selectedPage * PLAYER_PAGE_SIZE;
        int end = Math.min(start + PLAYER_PAGE_SIZE, targets.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Player target = targets.get(index);
            holder.playersBySlot.put(slot, target.getUniqueId());
            holder.inventory.setItem(slot, playerHead(target));
        }
        if (selectedPage > 0) holder.inventory.setItem(45, item(Material.ARROW, "&ePrevious page"));
        holder.inventory.setItem(48, item(Material.PAPER, "&fPage " + (selectedPage + 1)
                + " / " + (maximumPage + 1), "&7Players: &e" + targets.size()));
        holder.inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to utilities."));
        if (selectedPage < maximumPage) holder.inventory.setItem(53, item(Material.ARROW, "&eNext page"));
        player.openInventory(holder.inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CoreGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        int slot = event.getSlot();
        if (holder.page == CorePage.CRYSTAL) {
            if (slot == 22) openMainGui(player);
            else if (holder.crystalTicksBySlot.containsKey(slot)) {
                player.performCommand("crystal-cooldown " + holder.crystalTicksBySlot.get(slot));
                openCrystalGui(player);
            }
            return;
        }

        if (holder.page == CorePage.GENERAL) {
            switch (slot) {
                case 10 -> toggleSetting(player, "join-quit-messages.enabled", "Join/Quit messages");
                case 12 -> toggleSetting(player, "motd.enabled", "MOTD");
                case 14 -> toggleSetting(player, "update-checker.enabled", "Update checker");
                case 16 -> toggleSetting(player, "lastlogout.enabled", "Last logout command");
                case 28 -> toggleSetting(player, "performance.enabled", "Performance command");
                case 30 -> togglePerformanceSetting(player, "performance-monitor.enabled", "Performance monitor");
                case 32 -> toggleSetting(player, "lag-cleanup.enabled", "Lag cleanup");
                case 34 -> toggleSetting(player, "lag-cleanup.confirm-required", "Cleanup confirmation");
                case 49 -> openMainGui(player);
                default -> { return; }
            }
            if (slot != 49) openGeneralGui(player);
            return;
        }

        if (holder.page == CorePage.PERFORMANCE) {
            switch (slot) {
                case 20 -> cyclePerformanceThreshold(player, "performance-monitor.warn-tps",
                        WARNING_THRESHOLDS, "warning TPS");
                case 24 -> cyclePerformanceThreshold(player, "performance-monitor.severe-tps",
                        SEVERE_THRESHOLDS, "severe TPS");
                case 28 -> toggleSetting(player, "performance.enabled", "Performance command");
                case 30 -> togglePerformanceSetting(player, "performance-monitor.enabled", "Performance monitor");
                case 32 -> toggleSetting(player, "performance.show-mspt", "Performance MSPT display");
                case 34 -> toggleSetting(player, "performance.show-chunks", "Performance chunk display");
                case 49 -> openMainGui(player);
                default -> { return; }
            }
            if (slot != 49) openPerformanceGui(player);
            return;
        }

        if (holder.page == CorePage.UTILITIES) {
            switch (slot) {
                case 10 -> player.performCommand("ec");
                case 12 -> player.performCommand("craft");
                case 14 -> player.performCommand("anvil");
                case 16 -> openPlayerSelector(player, CorePage.INVSEE_PLAYERS, 0);
                case 30 -> openPlayerSelector(player, CorePage.EC_PLAYERS, 0);
                case 49 -> openMainGui(player);
                default -> { }
            }
            return;
        }

        if (holder.page == CorePage.INVSEE_PLAYERS || holder.page == CorePage.EC_PLAYERS) {
            if (slot == 45 && holder.pageNumber > 0) {
                openPlayerSelector(player, holder.page, holder.pageNumber - 1);
                return;
            }
            if (slot == 49) {
                openUtilitiesGui(player);
                return;
            }
            if (slot == 53 && holder.pageNumber < holder.maximumPage) {
                openPlayerSelector(player, holder.page, holder.pageNumber + 1);
                return;
            }
            UUID targetId = holder.playersBySlot.get(slot);
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            if (target != null) player.performCommand((holder.page == CorePage.INVSEE_PLAYERS ? "invsee " : "ec ")
                    + target.getName());
            return;
        }

        switch (slot) {
            case 10 -> player.performCommand("lock-dimension");
            case 12 -> openCrystalGui(player);
            case 14 -> player.performCommand("anti-mods");
            case 16 -> player.performCommand("trial-drops");
            case 28 -> openPerformanceGui(player);
            case 30 -> player.performCommand("cleanup");
            case 32 -> player.performCommand("log");
            case 34 -> openGeneralGui(player);
            case 46 -> openUtilitiesGui(player);
            case 48 -> {
                player.closeInventory();
                player.performCommand("lowcore reload");
            }
            case 49 -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CoreGuiHolder) event.setCancelled(true);
    }

    private String formatSeconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(java.util.Locale.ROOT, "%.2f", ticks / 20.0);
    }

    private void toggle(Inventory inventory, int slot, Material material, String title, String path) {
        boolean enabled = plugin.getConfig().getBoolean(path, true);
        inventory.setItem(slot, item(material, (enabled ? "&a" : "&c") + title,
                "&7Status: " + (enabled ? "&aEnabled" : "&cDisabled"), "", "&eClick to toggle"));
    }

    private void toggleSetting(Player player, String path, String description) {
        boolean enabled = !plugin.getConfig().getBoolean(path, true);
        plugin.getConfig().set(path, enabled);
        plugin.saveConfig();
        if (path.equals("update-checker.enabled") && enabled) plugin.checkForUpdatesNow();
        plugin.audit(player, "Set " + description + " to " + enabled);
    }

    private void togglePerformanceSetting(Player player, String path, String description) {
        toggleSetting(player, path, description);
        plugin.reloadPerformanceMonitor();
    }

    private void cyclePerformanceThreshold(Player player, String path, double[] values, String description) {
        double current = plugin.getConfig().getDouble(path, values[0]);
        double next = values[0];
        for (int index = 0; index < values.length; index++) {
            if (Double.compare(values[index], current) == 0) {
                next = values[(index + 1) % values.length];
                break;
            }
        }
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadPerformanceMonitor();
        plugin.audit(player, "Set performance " + description + " to " + next);
    }

    private ItemStack playerHead(Player player) {
        ItemStack result = item(Material.PLAYER_HEAD, "&e" + player.getName(), "&7Click to select." );
        if (result.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            result.setItemMeta(meta);
        }
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : MAIN_SUBCOMMANDS) {
                if (sub.startsWith(input)) {
                    result.add(sub);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            String input = args[1].toLowerCase();
            for (String topic : HELP_TOPICS) {
                if (topic.startsWith(input)) {
                    result.add(topic);
                }
            }
            return result;
        }

        return result;
    }

    private enum CorePage { MAIN, CRYSTAL, GENERAL, PERFORMANCE, UTILITIES, INVSEE_PLAYERS, EC_PLAYERS }

    private static final class CoreGuiHolder implements InventoryHolder {
        private final CorePage page;
        private final Map<Integer, Integer> crystalTicksBySlot;
        private final Map<Integer, UUID> playersBySlot;
        private final Inventory inventory;
        private final int pageNumber;
        private final int maximumPage;

        private CoreGuiHolder(CorePage page, String inventoryTitle) {
            this(page, inventoryTitle, 0, 0);
        }

        private CoreGuiHolder(CorePage page, String inventoryTitle, int pageNumber, int maximumPage) {
            this.page = page;
            this.pageNumber = pageNumber;
            this.maximumPage = maximumPage;
            this.crystalTicksBySlot = page == CorePage.CRYSTAL ? new HashMap<>() : Map.of();
            this.playersBySlot = page == CorePage.INVSEE_PLAYERS || page == CorePage.EC_PLAYERS
                    ? new HashMap<>() : Map.of();
            this.inventory = Bukkit.createInventory(this, 54, title(inventoryTitle));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
