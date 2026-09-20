package dev.jalikdev.lowCore.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager.Dimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LowcoreCommand implements CommandExecutor, TabCompleter, Listener {

    private final LowCore plugin;
    private final List<String> mainSubcommands = Arrays.asList("gui", "help", "info", "reload", "dimension");
    private final List<String> dimensions = Arrays.asList("nether", "end");
    private final List<String> dimensionActions = Arrays.asList("lock", "unlock", "status");
    private final List<String> helpTopics = Arrays.asList(
            "lowcore", "ec", "enchant", "feed", "fly", "gm", "hat", "heal", "invsee", "spawnmob"
    );

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

        if (sub.equals("dimension") || sub.equals("dimensions")) {
            handleDimensionCommand(sender, args);
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
        LowCore.sendMessage(sender, "&a/lowcore dimension &7- Lock or unlock the Nether and End.");
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
                LowCore.sendMessage(sender, "&a/lowcore dimension <nether|end> <lock|unlock|status> &7- Manage dimension access.");
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
                "&7Client detection, rules, bypass and logs.", "", "&eClick to manage"));
        inventory.setItem(16, item(Material.HEAVY_CORE, "&6Trial Chamber Drops",
                "&7Blocked items: &e" + plugin.getConfig().getStringList("trial-drops.disabled-items").size(),
                "", "&eClick to manage"));
        inventory.setItem(28, item(Material.CLOCK, "&aPerformance",
                "&7Show TPS, MSPT, memory and chunks.", "", "&eClick to view"));
        inventory.setItem(30, item(Material.LAVA_BUCKET, "&cLag Cleanup",
                "&7Open entity cleanup controls.", "", "&eClick to manage"));
        inventory.setItem(32, item(Material.WRITABLE_BOOK, "&eAdmin Audit Log",
                "&7Commands and settings changes.", "", "&eClick to view"));
        inventory.setItem(34, item(Material.COMMAND_BLOCK, "&fReload Configuration",
                "&7Reload LowCore's config from disk.", "", "&eClick to reload"));
        inventory.setItem(49, item(Material.BARRIER, "&cClose", "&7Close the control center."));
        player.openInventory(inventory);
    }

    private void openCrystalGui(Player player) {
        CoreGuiHolder holder = new CoreGuiHolder(CorePage.CRYSTAL, "§8Crystal Cooldown");
        Inventory inventory = holder.inventory;
        fill(inventory);
        int[] values = {0, 1, 2, 5, 10, 20, 40};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int current = Math.max(0, plugin.getConfig().getInt("crystal-cooldown.ticks", 0));
        for (int index = 0; index < values.length; index++) {
            int ticks = values[index];
            holder.crystalTicksBySlot.put(slots[index], ticks);
            inventory.setItem(slots[index], item(ticks == 0 ? Material.BARRIER : Material.CLOCK,
                    (current == ticks ? "&a" : "&e") + (ticks == 0 ? "Disabled" : ticks + " ticks"),
                    ticks == 0 ? "&7Disable placement cooldown." : "&7Approximately " + formatSeconds(ticks) + " seconds.",
                    current == ticks ? "&aCurrently selected" : "&eClick to apply"));
        }
        inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to the control center."));
        player.openInventory(inventory);
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

        switch (slot) {
            case 10 -> player.performCommand("lock-dimension");
            case 12 -> openCrystalGui(player);
            case 14 -> player.performCommand("anti-mods");
            case 16 -> player.performCommand("trial-drops");
            case 28 -> {
                player.closeInventory();
                player.performCommand("performance");
            }
            case 30 -> player.performCommand("cleanup");
            case 32 -> player.performCommand("log");
            case 34 -> {
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

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(colored);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : mainSubcommands) {
                if (sub.startsWith(input)) {
                    result.add(sub);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            String input = args[1].toLowerCase();
            for (String topic : helpTopics) {
                if (topic.startsWith(input)) {
                    result.add(topic);
                }
            }
            return result;
        }

        if (args.length == 2 && isDimensionSubcommand(args[0])) {
            return matching(dimensions, args[1]);
        }

        if (args.length == 3 && isDimensionSubcommand(args[0])) {
            return matching(dimensionActions, args[2]);
        }

        return result;
    }

    private void handleDimensionCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lowcore.dimensions")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return;
        }

        if (args.length != 3 || !dimensions.contains(args[1].toLowerCase())
                || !dimensionActions.contains(args[2].toLowerCase())) {
            LowCore.sendConfigMessage(sender, "dimensions.usage");
            return;
        }

        String dimension = args[1].toLowerCase();
        String action = args[2].toLowerCase();
        Dimension selected = Dimension.fromInput(dimension).orElseThrow();

        if (action.equals("status")) {
            sendDimensionStatus(sender, dimension, plugin.getDimensionLockManager().isLocked(selected), "dimensions.status");
            return;
        }

        boolean locked = action.equals("lock");
        if (locked) {
            plugin.getDimensionLockManager().lock(selected, 0L);
        } else {
            plugin.getDimensionLockManager().unlock(selected);
        }
        sendDimensionStatus(sender, dimension, locked, "dimensions.updated");
    }

    private void sendDimensionStatus(CommandSender sender, String dimension, boolean locked, String messageKey) {
        LowCore.sendConfigMessage(sender, messageKey,
                "dimension", dimension.equals("nether") ? "Nether" : "End",
                "status", locked ? ChatColor.RED + "locked" : ChatColor.GREEN + "unlocked");
    }

    private boolean isDimensionSubcommand(String value) {
        return value.equalsIgnoreCase("dimension") || value.equalsIgnoreCase("dimensions");
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }

    private enum CorePage { MAIN, CRYSTAL }

    private static final class CoreGuiHolder implements InventoryHolder {
        private final CorePage page;
        private final Map<Integer, Integer> crystalTicksBySlot = new HashMap<>();
        private final Inventory inventory;

        private CoreGuiHolder(CorePage page, String title) {
            this.page = page;
            this.inventory = Bukkit.createInventory(this, 54, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
