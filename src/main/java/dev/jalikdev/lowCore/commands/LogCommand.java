package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.database.AdminAuditLogRepository;
import dev.jalikdev.lowCore.database.AdminAuditLogRepository.AuditEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LogCommand implements CommandExecutor, Listener, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final LowCore plugin;
    private final AdminAuditLogRepository repository;
    private int entriesUntilTrim = 100;

    public LogCommand(LowCore plugin, AdminAuditLogRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        trimRepository();
    }

    public void logAction(CommandSender actor, String action) {
        UUID actorId = actor instanceof Player player ? player.getUniqueId() : null;
        String actorName = actor == null ? "SYSTEM" : actor.getName();
        try {
            repository.save(actorId, actorName, action);
            if (--entriesUntilTrim <= 0) trimRepository();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }

    private void trimRepository() {
        try {
            repository.trimTo(plugin.getConfig().getInt("audit-log.max-entries", 5000));
            entriesUntilTrim = 100;
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String label = message.substring(1).split(" ", 2)[0];
        org.bukkit.command.PluginCommand command = plugin.getCommand(label);
        if (command != null && command.getPlugin() == plugin) logAction(event.getPlayer(), "Executed " + message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.log")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            if (sender instanceof Player player) openClearGui(player);
            else clear(sender);
            return true;
        }
        if (sender instanceof Player player && args.length == 0) {
            openLogsGui(player, 0);
            return true;
        }

        int amount = 10;
        if (args.length > 0) {
            try {
                amount = Math.max(1, Math.min(50, Integer.parseInt(args[0])));
            } catch (NumberFormatException exception) {
                LowCore.sendMessage(sender, "&cUsage: &e/log [amount|clear]");
                return true;
            }
        }
        List<AuditEntry> entries = repository.findRecent(amount, 0);
        LowCore.sendMessage(sender, "&7Recent admin audit entries:");
        for (AuditEntry entry : entries) {
            LowCore.sendMessage(sender, "&8- &e" + entry.actorName() + " &7" + entry.action()
                    + " &8(" + TIME.format(Instant.ofEpochMilli(entry.createdAt())) + ")");
        }
        return true;
    }

    private void openLogsGui(Player player, int requestedPage) {
        int total = repository.count();
        int maximumPage = Math.max(0, (total - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maximumPage));
        AuditHolder holder = new AuditHolder(AuditPage.LOGS, page,
                "§8Admin Audit Log §7(" + (page + 1) + "/" + (maximumPage + 1) + ")");
        fill(holder.inventory);
        List<AuditEntry> entries = repository.findRecent(PAGE_SIZE, page * PAGE_SIZE);
        for (int slot = 0; slot < entries.size(); slot++) holder.inventory.setItem(slot, entryItem(entries.get(slot)));
        if (page > 0) holder.inventory.setItem(45, item(Material.ARROW, "&ePrevious page"));
        holder.inventory.setItem(48, item(Material.OAK_DOOR, "&eLowCore menu", "&7Return to the control center."));
        holder.inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + " / " + (maximumPage + 1),
                "&7Stored actions: &e" + total));
        holder.inventory.setItem(51, item(Material.LAVA_BUCKET, "&cClear audit log", "&7Delete all entries."));
        if (page < maximumPage) holder.inventory.setItem(53, item(Material.ARROW, "&eNext page"));
        player.openInventory(holder.inventory);
    }

    private void openClearGui(Player player) {
        AuditHolder holder = new AuditHolder(AuditPage.CLEAR, 0, "§cClear Admin Audit Log?");
        fill(holder.inventory);
        holder.inventory.setItem(11, item(Material.LIME_CONCRETE, "&aCancel"));
        holder.inventory.setItem(15, item(Material.RED_CONCRETE, "&cDelete all", "&7This cannot be undone."));
        player.openInventory(holder.inventory);
    }

    private void clear(CommandSender sender) {
        int count = repository.clear();
        LowCore.sendMessage(sender, "&aDeleted &e" + count + " &aaudit log entries.");
        logAction(sender, "Cleared the admin audit log (" + count + " entries)");
    }

    private ItemStack entryItem(AuditEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Time: &f" + TIME.format(Instant.ofEpochMilli(entry.createdAt())));
        lore.add("");
        String action = entry.action();
        for (int start = 0; start < action.length(); start += 42) {
            lore.add("&7" + action.substring(start, Math.min(action.length(), start + 42)));
        }
        return item(Material.COMMAND_BLOCK, "&e" + entry.actorName(), lore.toArray(String[]::new));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AuditHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        int slot = event.getSlot();
        if (holder.page == AuditPage.CLEAR) {
            if (slot == 11) openLogsGui(player, 0);
            else if (slot == 15) {
                clear(player);
                openLogsGui(player, 0);
            }
            return;
        }
        if (slot == 45 && holder.pageNumber > 0) openLogsGui(player, holder.pageNumber - 1);
        else if (slot == 48) player.performCommand("lowcore");
        else if (slot == 51) openClearGui(player);
        else if (slot == 53) openLogsGui(player, holder.pageNumber + 1);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AuditHolder) event.setCancelled(true);
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
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.log") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return List.of("10", "20", "50", "clear").stream().filter(value -> value.startsWith(input)).toList();
    }

    private enum AuditPage { LOGS, CLEAR }

    private static final class AuditHolder implements InventoryHolder {
        private final AuditPage page;
        private final int pageNumber;
        private final Inventory inventory;
        private AuditHolder(AuditPage page, int pageNumber, String title) {
            this.page = page;
            this.pageNumber = pageNumber;
            this.inventory = Bukkit.createInventory(this, page == AuditPage.LOGS ? 54 : 27, title);
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
