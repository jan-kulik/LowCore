package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.Punishment;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.StartResult;
import dev.jalikdev.lowCore.antifreecam.AntiModClient;
import dev.jalikdev.lowCore.database.AntiFreecamLogRepository.AntiFreecamLogEntry;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AntiFreecamCommand implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> ACTIONS = List.of("gui", "on", "off", "toggle", "status",
            "punishment", "check", "logs", "allow", "block", "bypass-permission");
    private static final List<String> PUNISHMENTS = List.of("notify", "kick", "ban");
    private static final int[] LOG_PAGE_OPTIONS = {1, 3, 5, 10, 20};
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AntiFreecamManager manager;

    public AntiFreecamCommand(AntiFreecamManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.antimods.admin")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) openMainGui(player);
            else sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "toggle" -> setEnabled(sender, !manager.isEnabled());
            case "status" -> sendStatus(sender);
            case "punishment" -> setPunishment(sender, args);
            case "check" -> checkPlayer(sender, args);
            case "logs" -> showLogs(sender);
            case "allow", "block" -> setClient(sender, args);
            case "bypass-permission" -> setBypassPermission(sender, args);
            default -> LowCore.sendConfigMessage(sender, "anti-mods.usage");
        }
        return true;
    }

    private void setEnabled(CommandSender sender, boolean enabled) {
        manager.setEnabled(enabled);
        LowCore.sendConfigMessage(sender, enabled ? "anti-mods.enabled" : "anti-mods.disabled");
    }

    private void setPunishment(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-mods.punishment-usage");
            return;
        }
        Punishment punishment = Punishment.fromConfig(args[1]);
        if (!punishment.configName().equalsIgnoreCase(args[1])) {
            LowCore.sendConfigMessage(sender, "anti-mods.punishment-usage");
            return;
        }
        manager.setPunishment(punishment);
        LowCore.sendConfigMessage(sender, "anti-mods.punishment-updated", "punishment", punishment.displayName());
    }

    private void setClient(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-mods.client-usage");
            return;
        }
        AntiModClient client = findClient(args[1]);
        if (client == null) {
            LowCore.sendConfigMessage(sender, "anti-mods.unknown-client", "client", args[1]);
            return;
        }
        boolean blocked = args[0].equalsIgnoreCase("block");
        manager.setClientBlocked(client, blocked);
        LowCore.sendConfigMessage(sender, blocked ? "anti-mods.client-blocked" : "anti-mods.client-allowed",
                "client", client.displayName());
    }

    private void setBypassPermission(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-mods.bypass-permission-usage");
            return;
        }
        String permission = args[1].equalsIgnoreCase("off") ? "" : args[1].strip();
        if (!permission.isEmpty() && !permission.matches("[a-zA-Z0-9_.-]+")) {
            LowCore.sendConfigMessage(sender, "anti-mods.bypass-permission-usage");
            return;
        }
        manager.setBypassPermission(permission);
        LowCore.sendConfigMessage(sender, "anti-mods.bypass-permission-updated",
                "permission", permission.isEmpty() ? "disabled" : permission);
    }

    private void checkPlayer(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-mods.check-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) LowCore.sendConfigMessage(sender, "unknown-player");
        else startCheck(sender, target);
    }

    private void startCheck(CommandSender sender, Player target) {
        StartResult result = manager.startManualCheck(target, sender);
        switch (result) {
            case STARTED -> LowCore.sendConfigMessage(sender, "anti-mods.check-started", "player", target.getName());
            case ALREADY_RUNNING -> LowCore.sendConfigMessage(sender, "anti-mods.already-running", "player", target.getName());
            case BYPASSED -> LowCore.sendConfigMessage(sender, "anti-mods.bypassed", "player", target.getName());
            case BEDROCK -> LowCore.sendConfigMessage(sender, "anti-mods.bedrock-skipped", "player", target.getName());
            case OFFLINE -> LowCore.sendConfigMessage(sender, "unknown-player");
            case FAILED -> LowCore.sendConfigMessage(sender, "anti-mods.failed", "player", target.getName());
        }
    }

    private void sendStatus(CommandSender sender) {
        LowCore.sendConfigMessage(sender, "anti-mods.status",
                "status", manager.isEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled",
                "punishment", manager.getPunishment().displayName());
    }

    private void showLogs(CommandSender sender) {
        if (sender instanceof Player player) {
            openLogsGui(player, 0);
            return;
        }
        List<AntiFreecamLogEntry> logs = manager.getRecentLogs(10, 0);
        LowCore.sendMessage(sender, "&7Recent Anti-Mod logs (&e" + manager.getLogCount() + " total&7):");
        for (AntiFreecamLogEntry entry : logs) {
            LowCore.sendMessage(sender, "&8- &e" + entry.playerName() + " &7" + entry.result()
                    + " &8[" + entry.mods() + "] &7" + LOG_TIME.format(Instant.ofEpochMilli(entry.checkedAt())));
        }
    }

    private void openMainGui(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.MAIN, 54, "§8Anti-Mods Settings", 0);
        Inventory inventory = holder.inventory;
        fill(inventory);

        boolean enabled = manager.isEnabled();
        inventory.setItem(10, item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&aDetection enabled" : "&cDetection disabled",
                "&7Checks joining Java players.", "&7Failures never trigger punishment.", "", "&eClick to toggle"));

        Punishment punishment = manager.getPunishment();
        Material punishmentMaterial = switch (punishment) {
            case NOTIFY -> Material.BELL;
            case KICK -> Material.LEATHER_BOOTS;
            case BAN -> Material.IRON_BARS;
        };
        inventory.setItem(12, item(punishmentMaterial, "&ePunishment: &f" + punishment.displayName(),
                "&7Only blocked detected clients are punished.", "", "&eClick to cycle"));
        inventory.setItem(14, item(Material.COMPARATOR, "&bClient rules",
                "&7Allow or block each detectable client.", "", "&eClick to configure"));
        inventory.setItem(16, item(Material.REPEATER, "&dGeneral settings",
                "&7Bypass, confirmation and log pages.", "", "&eClick to configure"));
        inventory.setItem(29, item(Material.SPYGLASS, "&bCheck a player",
                "&7Run a manual multi-client check.", "", "&eClick to select"));
        inventory.setItem(31, item(Material.WRITABLE_BOOK, "&6Detection logs",
                "&7Stored checks: &e" + manager.getLogCount(),
                "&7Includes clean, allowed and blocked results.", "", "&eClick to open"));
        inventory.setItem(33, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        player.openInventory(inventory);
    }

    private void openClientsGui(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.CLIENTS, 27, "§8Anti-Mods: Client Rules", 0);
        fill(holder.inventory);
        int[] slots = {10, 11, 12, 14, 15};
        List<AntiModClient> clients = manager.getClients();
        for (int index = 0; index < clients.size(); index++) {
            AntiModClient client = clients.get(index);
            int slot = slots[index];
            holder.clientsBySlot.put(slot, client);
            boolean blocked = manager.isClientBlocked(client);
            holder.inventory.setItem(slot, item(client.icon(), (blocked ? "&c" : "&a") + client.displayName(),
                    "&7Status: " + (blocked ? "&cBLOCKED" : "&aALLOWED"),
                    blocked ? "&7A confirmed match uses the punishment." : "&7Matches are logged without punishment.",
                    "", "&eClick to toggle"));
        }
        holder.inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to Anti-Mods settings."));
        player.openInventory(holder.inventory);
    }

    private void openSettingsGui(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.SETTINGS, 27, "§8Anti-Mods: General", 0);
        fill(holder.inventory);
        holder.inventory.setItem(10, item(manager.isDoubleCheckEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                manager.isDoubleCheckEnabled() ? "&aConfirmation enabled" : "&cConfirmation disabled",
                "&7Requires a client match twice.", "", "&eClick to toggle"));
        holder.inventory.setItem(12, item(manager.isOpBypassEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                manager.isOpBypassEnabled() ? "&aOP bypass enabled" : "&cOP bypass disabled",
                "&7When enabled, operators are never checked.", "", "&eClick to toggle"));
        String permission = manager.getBypassPermission();
        holder.inventory.setItem(14, item(Material.NAME_TAG, "&bPermission bypass",
                "&7Current: &f" + (permission.isBlank() ? "disabled" : permission),
                "&7Set with:", "&e/anti-mods bypass-permission <permission|off>"));
        holder.inventory.setItem(16, item(Material.BOOKSHELF, "&6Log pages: &f" + manager.getMaxLogPages(),
                "&7Each page stores 36 checks.", "&7Old entries are removed automatically.", "", "&eClick to cycle"));
        holder.inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to Anti-Mods settings."));
        player.openInventory(holder.inventory);
    }

    private void openPlayerGui(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.PLAYERS, 54, "§8Anti-Mods: Select Player", 0);
        fill(holder.inventory);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .filter(target -> !manager.isBedrockPlayer(target))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList()) {
            if (slot >= 45) break;
            holder.playersBySlot.put(slot, target.getUniqueId());
            holder.inventory.setItem(slot++, playerHead(target));
        }
        holder.inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to Anti-Mods settings."));
        player.openInventory(holder.inventory);
    }

    private void openLogsGui(Player player, int requestedPage) {
        int pageSize = manager.getLogPageSize();
        int total = Math.min(manager.getLogCount(), pageSize * manager.getMaxLogPages());
        int maximumPage = Math.max(0, (total - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maximumPage));
        GuiHolder holder = new GuiHolder(GuiPage.LOGS, 54,
                "§8Anti-Mod Logs §7(" + (page + 1) + "/" + (maximumPage + 1) + ")", page);
        fill(holder.inventory);
        List<AntiFreecamLogEntry> logs = manager.getRecentLogs(pageSize, page * pageSize);
        for (int slot = 0; slot < logs.size(); slot++) holder.inventory.setItem(slot, logItem(logs.get(slot)));
        if (page > 0) holder.inventory.setItem(45, item(Material.ARROW, "&ePrevious page"));
        holder.inventory.setItem(47, item(Material.OAK_DOOR, "&eBack", "&7Return to settings."));
        holder.inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + " / " + (maximumPage + 1),
                "&7Stored checks: &e" + total, "&7Configured maximum: &e" + manager.getMaxLogPages() + " pages"));
        holder.inventory.setItem(51, item(Material.LAVA_BUCKET, "&cClear logs", "&7Delete all Anti-Mod logs.", "", "&cClick to continue"));
        if (page < maximumPage) holder.inventory.setItem(53, item(Material.ARROW, "&eNext page"));
        player.openInventory(holder.inventory);
    }

    private void openClearLogsGui(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.CLEAR_LOGS, 27, "§cClear Anti-Mod Logs?", 0);
        fill(holder.inventory);
        holder.inventory.setItem(11, item(Material.LIME_CONCRETE, "&aCancel", "&7Keep all logs."));
        holder.inventory.setItem(15, item(Material.RED_CONCRETE, "&cDelete all logs", "&7This cannot be undone."));
        player.openInventory(holder.inventory);
    }

    private ItemStack logItem(AntiFreecamLogEntry entry) {
        Material material = switch (entry.result().toUpperCase(Locale.ROOT)) {
            case "DETECTED" -> Material.REDSTONE_BLOCK;
            case "ALLOWED" -> Material.LIME_CONCRETE;
            case "CLEAN" -> Material.LIME_DYE;
            case "PROTECTED" -> Material.SHIELD;
            case "TIMEOUT" -> Material.CLOCK;
            default -> Material.YELLOW_DYE;
        };
        String color = switch (entry.result().toUpperCase(Locale.ROOT)) {
            case "DETECTED" -> "&c";
            case "ALLOWED", "CLEAN" -> "&a";
            case "PROTECTED", "INCONCLUSIVE" -> "&e";
            default -> "&7";
        };
        return item(material, "&e" + entry.playerName() + " &8- " + color + entry.result(),
                "&7Clients: &f" + entry.mods(), "&7Source: &f" + entry.source(),
                "&7Action: &f" + entry.punishment(),
                "&7Time: &f" + LOG_TIME.format(Instant.ofEpochMilli(entry.checkedAt())), "", "&8" + entry.details());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        int slot = event.getSlot();

        switch (holder.page) {
            case MAIN -> handleMainClick(player, slot);
            case CLIENTS -> {
                AntiModClient client = holder.clientsBySlot.get(slot);
                if (client != null) {
                    manager.setClientBlocked(client, !manager.isClientBlocked(client));
                    manager.audit(player, "Set Anti-Mod client " + client.displayName() + " to "
                            + (manager.isClientBlocked(client) ? "BLOCKED" : "ALLOWED"));
                    openClientsGui(player);
                } else if (slot == 22) openMainGui(player);
            }
            case SETTINGS -> {
                if (slot == 10) {
                    manager.setDoubleCheckEnabled(!manager.isDoubleCheckEnabled());
                    manager.audit(player, "Set Anti-Mod confirmation to " + manager.isDoubleCheckEnabled());
                    openSettingsGui(player);
                } else if (slot == 12) {
                    manager.setOpBypassEnabled(!manager.isOpBypassEnabled());
                    manager.audit(player, "Set Anti-Mod OP bypass to " + manager.isOpBypassEnabled());
                    openSettingsGui(player);
                } else if (slot == 16) {
                    manager.setMaxLogPages(nextLogPageOption(manager.getMaxLogPages()));
                    manager.audit(player, "Set Anti-Mod log pages to " + manager.getMaxLogPages());
                    openSettingsGui(player);
                } else if (slot == 22) openMainGui(player);
            }
            case PLAYERS -> {
                if (slot == 49) openMainGui(player);
                else {
                    UUID id = holder.playersBySlot.get(slot);
                    if (id != null) {
                        Player target = Bukkit.getPlayer(id);
                        player.closeInventory();
                        if (target == null) LowCore.sendConfigMessage(player, "unknown-player");
                        else startCheck(player, target);
                    }
                }
            }
            case LOGS -> {
                if (slot == 45 && holder.pageNumber > 0) openLogsGui(player, holder.pageNumber - 1);
                else if (slot == 47) openMainGui(player);
                else if (slot == 51) openClearLogsGui(player);
                else if (slot == 53) openLogsGui(player, holder.pageNumber + 1);
            }
            case CLEAR_LOGS -> {
                if (slot == 11) openLogsGui(player, 0);
                else if (slot == 15) {
                    int deleted = manager.clearLogs();
                    manager.audit(player, "Cleared Anti-Mod logs (" + deleted + " entries)");
                    LowCore.sendConfigMessage(player, "anti-mods.logs-cleared", "count", Integer.toString(deleted));
                    openLogsGui(player, 0);
                }
            }
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 10) {
            manager.setEnabled(!manager.isEnabled());
            manager.audit(player, "Set Anti-Mod detection to " + manager.isEnabled());
            openMainGui(player);
        } else if (slot == 12) {
            manager.setPunishment(manager.getPunishment().next());
            manager.audit(player, "Set Anti-Mod punishment to " + manager.getPunishment().displayName());
            openMainGui(player);
        } else if (slot == 14) openClientsGui(player);
        else if (slot == 16) openSettingsGui(player);
        else if (slot == 29) openPlayerGui(player);
        else if (slot == 31) openLogsGui(player, 0);
        else if (slot == 33) player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    private int nextLogPageOption(int current) {
        for (int option : LOG_PAGE_OPTIONS) if (option > current) return option;
        return LOG_PAGE_OPTIONS[0];
    }

    private AntiModClient findClient(String input) {
        for (AntiModClient client : manager.getClients()) {
            if (client.id().equalsIgnoreCase(input) || client.displayName().replace(" ", "").equalsIgnoreCase(input)) {
                return client;
            }
        }
        return null;
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = item(Material.PLAYER_HEAD, "&e" + player.getName(),
                manager.isBypassed(player) ? "&7Status: &eBypassed" : "&7Run all translation-key probes.", "", "&eClick to check");
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(color(line));
        meta.setLore(colored);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.antimods.admin")) return List.of();
        if (args.length == 1) return matching(ACTIONS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("punishment")) return matching(PUNISHMENTS, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("block"))) {
            return matching(manager.getClients().stream().map(AntiModClient::id).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    private enum GuiPage { MAIN, CLIENTS, SETTINGS, PLAYERS, LOGS, CLEAR_LOGS }

    private static final class GuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final int pageNumber;
        private final Map<Integer, UUID> playersBySlot = new HashMap<>();
        private final Map<Integer, AntiModClient> clientsBySlot = new HashMap<>();
        private final Inventory inventory;

        private GuiHolder(GuiPage page, int size, String title, int pageNumber) {
            this.page = page;
            this.pageNumber = pageNumber;
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
