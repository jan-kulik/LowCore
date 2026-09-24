package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.Punishment;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.StartResult;
import dev.jalikdev.lowCore.antifreecam.AntiModClient;
import dev.jalikdev.lowCore.database.AntiFreecamLogRepository.AntiFreecamLogEntry;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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

import static dev.jalikdev.lowCore.utils.GuiUtil.fill;
import static dev.jalikdev.lowCore.utils.GuiUtil.item;
import static dev.jalikdev.lowCore.utils.GuiUtil.title;

public final class AntiFreecamCommand implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> ACTIONS = List.of("gui", "on", "off", "toggle", "status",
            "punishment", "command", "check", "logs", "allow", "block", "bypass-permission");
    private static final List<String> PUNISHMENTS = List.of("notify", "kick", "ban", "custom");
    private static final List<String> LOG_RESULTS = List.of("ALL", "DETECTED", "ALLOWED", "CLEAN",
            "TIMEOUT", "PROTECTED", "INCONCLUSIVE");
    private static final int[] LOG_PAGE_OPTIONS = {1, 3, 5, 10, 20};
    private static final int[] MANUAL_COOLDOWN_OPTIONS = {0, 5, 10, 30, 60};
    private static final int[] CLIENT_SLOTS = {10, 11, 12, 14, 15};
    private static final int PLAYER_PAGE_SIZE = 45;
    private static final List<String> CLIENT_FILTERS = clientFilters();
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AntiFreecamManager manager;
    private final Map<UUID, LogFilter> logFilters = new HashMap<>();

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
            case "command" -> setCustomCommand(sender, args);
            case "check" -> checkPlayer(sender, args);
            case "logs" -> showLogs(sender, args);
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

    private void setCustomCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            LowCore.sendConfigMessage(sender, "anti-mods.command-usage");
            return;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("list")) {
            LowCore.sendMessage(sender, "&7Configured Anti-Mod punishment commands:");
            List<String> commands = manager.getCustomCommands();
            if (commands.isEmpty()) LowCore.sendMessage(sender, "&8- &7none");
            else commands.forEach(value -> LowCore.sendMessage(sender, "&8- &e" + value));
            return;
        }
        if (operation.equals("clear") && args.length == 2) {
            manager.setCustomCommands(List.of());
            manager.audit(sender, "Cleared Anti-Mod custom punishment commands");
            LowCore.sendConfigMessage(sender, "anti-mods.command-cleared");
            return;
        }
        if ((operation.equals("set") || operation.equals("add")) && args.length >= 3) {
            String configured = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).strip();
            if (configured.startsWith("/")) configured = configured.substring(1);
            if (configured.isBlank()) {
                LowCore.sendConfigMessage(sender, "anti-mods.command-usage");
                return;
            }
            List<String> commands = operation.equals("add")
                    ? new ArrayList<>(manager.getCustomCommands()) : new ArrayList<>();
            commands.add(configured);
            manager.setCustomCommands(commands);
            manager.audit(sender, (operation.equals("add") ? "Added" : "Set")
                    + " Anti-Mod custom punishment command: " + configured);
            LowCore.sendConfigMessage(sender, "anti-mods.command-updated", "command", configured);
            return;
        }
        LowCore.sendConfigMessage(sender, "anti-mods.command-usage");
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
            case COOLDOWN -> LowCore.sendConfigMessage(sender, "anti-mods.check-cooldown",
                    "player", target.getName(), "seconds",
                    Long.toString(manager.getManualCooldownRemainingSeconds(target.getUniqueId())));
            case OFFLINE -> LowCore.sendConfigMessage(sender, "unknown-player");
            case FAILED -> LowCore.sendConfigMessage(sender, "anti-mods.failed", "player", target.getName());
        }
    }

    private void sendStatus(CommandSender sender) {
        LowCore.sendConfigMessage(sender, "anti-mods.status",
                "status", manager.isEnabled() ? "§aenabled" : "§cdisabled",
                "punishment", manager.getPunishment().displayName());
    }

    private void showLogs(CommandSender sender, String[] args) {
        String playerFilter = args.length >= 2 && !args[1].equalsIgnoreCase("all") ? args[1] : null;
        if (sender instanceof Player player) {
            if (args.length >= 2) logFilters.put(player.getUniqueId(),
                    playerFilter == null ? LogFilter.empty() : new LogFilter("ALL", null, playerFilter));
            openLogsGui(player, 0);
            return;
        }
        List<AntiFreecamLogEntry> logs = manager.getRecentLogs(10, 0, null, null, playerFilter);
        LowCore.sendMessage(sender, "&7Recent Anti-Mod logs (&e"
                + manager.getLogCount(null, null, playerFilter) + " matching&7):");
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
            case CUSTOM -> Material.COMMAND_BLOCK;
        };
        inventory.setItem(12, item(punishmentMaterial, "&ePunishment: &f" + punishment.displayName(),
                "&7Only blocked detected clients are punished.",
                punishment == Punishment.CUSTOM ? "&7Commands: &e" + manager.getCustomCommands().size() : "",
                punishment == Punishment.CUSTOM ? "&7Set with: &e/anti-mods command set <command>" : "",
                "", "&eClick to cycle"));
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
        List<AntiModClient> clients = manager.getClients();
        for (int index = 0; index < clients.size() && index < CLIENT_SLOTS.length; index++) {
            AntiModClient client = clients.get(index);
            int slot = CLIENT_SLOTS[index];
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
        int cooldown = manager.getManualCheckCooldownSeconds();
        holder.inventory.setItem(18, item(Material.CLOCK, "&dManual check cooldown: &f"
                        + (cooldown == 0 ? "disabled" : cooldown + "s"),
                "&7Limits repeated manual checks per target.", "&7Automatic join checks are unaffected.", "", "&eClick to cycle"));
        holder.inventory.setItem(22, item(Material.ARROW, "&eBack", "&7Return to Anti-Mods settings."));
        player.openInventory(holder.inventory);
    }

    private void openPlayerGui(Player player, int requestedPage) {
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !manager.isBedrockPlayer(target))
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
        int maximumPage = Math.max(0, (targets.size() - 1) / PLAYER_PAGE_SIZE);
        int selectedPage = Math.max(0, Math.min(requestedPage, maximumPage));
        GuiHolder holder = new GuiHolder(GuiPage.PLAYERS, 54,
                "§8Anti-Mods: Select Player", selectedPage, maximumPage);
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
        holder.inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to Anti-Mods settings."));
        if (selectedPage < maximumPage) holder.inventory.setItem(53, item(Material.ARROW, "&eNext page"));
        player.openInventory(holder.inventory);
    }

    private void openLogsGui(Player player, int requestedPage) {
        LogFilter filter = logFilters.computeIfAbsent(player.getUniqueId(), ignored -> LogFilter.empty());
        int pageSize = manager.getLogPageSize();
        int total = Math.min(manager.getLogCount(filter.resultValue(), filter.client, filter.player),
                pageSize * manager.getMaxLogPages());
        int maximumPage = Math.max(0, (total - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maximumPage));
        GuiHolder holder = new GuiHolder(GuiPage.LOGS, 54,
                "§8Anti-Mod Logs §7(" + (page + 1) + "/" + (maximumPage + 1) + ")", page);
        fill(holder.inventory);
        List<AntiFreecamLogEntry> logs = manager.getRecentLogs(pageSize, page * pageSize,
                filter.resultValue(), filter.client, filter.player);
        for (int slot = 0; slot < logs.size(); slot++) holder.inventory.setItem(slot, logItem(logs.get(slot)));
        if (page > 0) holder.inventory.setItem(45, item(Material.ARROW, "&ePrevious page"));
        holder.inventory.setItem(46, item(Material.HOPPER, "&eResult: &f" + filter.result,
                "&7Click to cycle the result filter."));
        holder.inventory.setItem(47, item(Material.OAK_DOOR, "&eBack", "&7Return to settings."));
        holder.inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + " / " + (maximumPage + 1),
                "&7Stored checks: &e" + total, "&7Configured maximum: &e" + manager.getMaxLogPages() + " pages"));
        holder.inventory.setItem(50, item(Material.COMPASS, "&bClient: &f"
                        + (filter.client == null ? "ALL" : filter.client),
                "&7Click to cycle the client filter."));
        holder.inventory.setItem(51, item(Material.LAVA_BUCKET, "&cClear logs", "&7Delete all Anti-Mod logs.", "", "&cClick to continue"));
        holder.inventory.setItem(52, item(Material.MILK_BUCKET, "&fReset filters",
                "&7Client: &e" + (filter.client == null ? "ALL" : filter.client),
                "&7Player: &e" + (filter.player == null ? "ALL" : filter.player), "", "&eClick to reset"));
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
                } else if (slot == 18) {
                    manager.setManualCheckCooldownSeconds(nextManualCooldown(manager.getManualCheckCooldownSeconds()));
                    manager.audit(player, "Set Anti-Mod manual check cooldown to "
                            + manager.getManualCheckCooldownSeconds() + " seconds");
                    openSettingsGui(player);
                } else if (slot == 22) openMainGui(player);
            }
            case PLAYERS -> {
                if (slot == 45 && holder.pageNumber > 0) openPlayerGui(player, holder.pageNumber - 1);
                else if (slot == 49) openMainGui(player);
                else if (slot == 53 && holder.pageNumber < holder.maximumPage) {
                    openPlayerGui(player, holder.pageNumber + 1);
                }
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
                else if (slot == 46) {
                    LogFilter current = logFilters.computeIfAbsent(player.getUniqueId(), ignored -> LogFilter.empty());
                    logFilters.put(player.getUniqueId(), current.nextResult());
                    openLogsGui(player, 0);
                }
                else if (slot == 47) openMainGui(player);
                else if (slot == 50) {
                    LogFilter current = logFilters.computeIfAbsent(player.getUniqueId(), ignored -> LogFilter.empty());
                    logFilters.put(player.getUniqueId(), current.nextClient());
                    openLogsGui(player, 0);
                }
                else if (slot == 51) openClearLogsGui(player);
                else if (slot == 52) {
                    logFilters.put(player.getUniqueId(), LogFilter.empty());
                    openLogsGui(player, 0);
                }
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
        else if (slot == 29) openPlayerGui(player, 0);
        else if (slot == 31) openLogsGui(player, 0);
        else if (slot == 33) player.performCommand("lowcore");
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        logFilters.remove(event.getPlayer().getUniqueId());
    }

    private int nextLogPageOption(int current) {
        for (int option : LOG_PAGE_OPTIONS) if (option > current) return option;
        return LOG_PAGE_OPTIONS[0];
    }

    private int nextManualCooldown(int current) {
        for (int option : MANUAL_COOLDOWN_OPTIONS) if (option > current) return option;
        return MANUAL_COOLDOWN_OPTIONS[0];
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
        long cooldown = manager.getManualCooldownRemainingSeconds(player.getUniqueId());
        ItemStack item = item(Material.PLAYER_HEAD, "&e" + player.getName(),
                manager.isBypassed(player) ? "&7Status: &eBypassed"
                        : cooldown > 0 ? "&7Cooldown: &e" + cooldown + "s"
                        : "&7Run all translation-key probes.", "", "&eClick to check");
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.antimods.admin")) return List.of();
        if (args.length == 1) return matching(ACTIONS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("punishment")) return matching(PUNISHMENTS, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
            return matching(List.of("set", "add", "list", "clear"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("block"))) {
            return matching(manager.getClients().stream().map(AntiModClient::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("logs")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        return List.of();
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    private static List<String> clientFilters() {
        List<String> values = new ArrayList<>();
        values.add(null);
        for (AntiModClient client : AntiModClient.values()) values.add(client.displayName());
        return java.util.Collections.unmodifiableList(values);
    }

    private enum GuiPage { MAIN, CLIENTS, SETTINGS, PLAYERS, LOGS, CLEAR_LOGS }

    private record LogFilter(String result, String client, String player) {
        private static LogFilter empty() { return new LogFilter("ALL", null, null); }
        private String resultValue() { return result.equals("ALL") ? null : result; }
        private LogFilter nextResult() {
            int index = LOG_RESULTS.indexOf(result);
            return new LogFilter(LOG_RESULTS.get((index + 1) % LOG_RESULTS.size()), client, player);
        }
        private LogFilter nextClient() {
            int index = CLIENT_FILTERS.indexOf(client);
            return new LogFilter(result, CLIENT_FILTERS.get((index + 1) % CLIENT_FILTERS.size()), player);
        }
    }

    private static final class GuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final int pageNumber;
        private final int maximumPage;
        private final Map<Integer, UUID> playersBySlot;
        private final Map<Integer, AntiModClient> clientsBySlot;
        private final Inventory inventory;

        private GuiHolder(GuiPage page, int size, String inventoryTitle, int pageNumber) {
            this(page, size, inventoryTitle, pageNumber, 0);
        }

        private GuiHolder(GuiPage page, int size, String inventoryTitle, int pageNumber, int maximumPage) {
            this.page = page;
            this.pageNumber = pageNumber;
            this.maximumPage = maximumPage;
            this.playersBySlot = page == GuiPage.PLAYERS ? new HashMap<>() : Map.of();
            this.clientsBySlot = page == GuiPage.CLIENTS ? new HashMap<>() : Map.of();
            this.inventory = Bukkit.createInventory(this, size, title(inventoryTitle));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
