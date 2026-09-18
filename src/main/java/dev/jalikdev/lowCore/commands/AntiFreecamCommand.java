package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.Punishment;
import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager.StartResult;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AntiFreecamCommand implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> ACTIONS = List.of("gui", "on", "off", "toggle", "status", "punishment", "check");
    private static final List<String> PUNISHMENTS = List.of("notify", "kick", "ban");

    private final AntiFreecamManager manager;

    public AntiFreecamCommand(AntiFreecamManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.antifreecam.admin")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                openMainGui(player);
            } else {
                sendStatus(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "toggle" -> setEnabled(sender, !manager.isEnabled());
            case "status" -> sendStatus(sender);
            case "punishment" -> setPunishment(sender, args);
            case "check" -> checkPlayer(sender, args);
            default -> LowCore.sendConfigMessage(sender, "anti-freecam.usage");
        }
        return true;
    }

    private void setEnabled(CommandSender sender, boolean enabled) {
        manager.setEnabled(enabled);
        LowCore.sendConfigMessage(sender, enabled ? "anti-freecam.enabled" : "anti-freecam.disabled");
    }

    private void setPunishment(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-freecam.punishment-usage");
            return;
        }
        Punishment punishment = Punishment.fromConfig(args[1]);
        if (!punishment.configName().equalsIgnoreCase(args[1])) {
            LowCore.sendConfigMessage(sender, "anti-freecam.punishment-usage");
            return;
        }
        manager.setPunishment(punishment);
        LowCore.sendConfigMessage(sender, "anti-freecam.punishment-updated",
                "punishment", punishment.displayName());
    }

    private void checkPlayer(CommandSender sender, String[] args) {
        if (args.length != 2) {
            LowCore.sendConfigMessage(sender, "anti-freecam.check-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            LowCore.sendConfigMessage(sender, "unknown-player");
            return;
        }
        startCheck(sender, target);
    }

    private void startCheck(CommandSender sender, Player target) {
        StartResult result = manager.startManualCheck(target, sender);
        switch (result) {
            case STARTED -> LowCore.sendConfigMessage(sender, "anti-freecam.check-started",
                    "player", target.getName());
            case ALREADY_RUNNING -> LowCore.sendConfigMessage(sender, "anti-freecam.already-running",
                    "player", target.getName());
            case BYPASSED -> LowCore.sendConfigMessage(sender, "anti-freecam.bypassed",
                    "player", target.getName());
            case OFFLINE -> LowCore.sendConfigMessage(sender, "unknown-player");
            case FAILED -> LowCore.sendConfigMessage(sender, "anti-freecam.failed",
                    "player", target.getName());
        }
    }

    private void sendStatus(CommandSender sender) {
        LowCore.sendConfigMessage(sender, "anti-freecam.status",
                "status", manager.isEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled",
                "punishment", manager.getPunishment().displayName());
    }

    private void openMainGui(Player player) {
        AntiFreecamGuiHolder holder = new AntiFreecamGuiHolder(GuiPage.MAIN, 27, "§8Anti-Freecam Settings");
        Inventory inventory = holder.getInventory();
        fill(inventory);

        boolean enabled = manager.isEnabled();
        inventory.setItem(11, item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&aDetection enabled" : "&cDetection disabled",
                "&7Automatically checks joining players.",
                "&7Timeouts never trigger punishment.", "", "&eClick to toggle"));

        Punishment punishment = manager.getPunishment();
        Material punishmentMaterial = switch (punishment) {
            case NOTIFY -> Material.BELL;
            case KICK -> Material.LEATHER_BOOTS;
            case BAN -> Material.IRON_BARS;
        };
        inventory.setItem(13, item(punishmentMaterial, "&ePunishment: &f" + punishment.displayName(),
                "&7Notify: alert staff only", "&7Kick: remove the player", "&7Ban: permanently ban the player",
                "", "&eClick to cycle"));

        inventory.setItem(15, item(Material.SPYGLASS, "&bCheck a player",
                "&7Run a manual Freecam/Meteor check.", "", "&eClick to select"));
        inventory.setItem(22, item(Material.BARRIER, "&cClose", "&7Close this menu."));
        player.openInventory(inventory);
    }

    private void openPlayerGui(Player player) {
        AntiFreecamGuiHolder holder = new AntiFreecamGuiHolder(GuiPage.PLAYERS, 54, "§8Select player to check");
        Inventory inventory = holder.getInventory();
        fill(inventory);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName())).toList()) {
            if (slot >= 45) {
                break;
            }
            holder.playersBySlot.put(slot, target.getUniqueId());
            inventory.setItem(slot, playerHead(target));
            slot++;
        }
        inventory.setItem(49, item(Material.ARROW, "&eBack", "&7Return to settings."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AntiFreecamGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        if (holder.page == GuiPage.MAIN) {
            if (slot == 11) {
                manager.setEnabled(!manager.isEnabled());
                LowCore.sendConfigMessage(player,
                        manager.isEnabled() ? "anti-freecam.enabled" : "anti-freecam.disabled");
                openMainGui(player);
            } else if (slot == 13) {
                Punishment next = manager.getPunishment().next();
                manager.setPunishment(next);
                LowCore.sendConfigMessage(player, "anti-freecam.punishment-updated",
                        "punishment", next.displayName());
                openMainGui(player);
            } else if (slot == 15) {
                openPlayerGui(player);
            } else if (slot == 22) {
                player.closeInventory();
            }
            return;
        }

        if (slot == 49) {
            openMainGui(player);
            return;
        }
        UUID targetId = holder.playersBySlot.get(slot);
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            player.closeInventory();
            if (target == null) {
                LowCore.sendConfigMessage(player, "unknown-player");
            } else {
                startCheck(player, target);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AntiFreecamGuiHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = item(Material.PLAYER_HEAD, "&e" + player.getName(),
                "&7Run the translation-key probe.", "", "&eClick to check");
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
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(color(line));
        }
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.antifreecam.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("punishment")) {
            return matching(PUNISHMENTS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
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

    private enum GuiPage {
        MAIN,
        PLAYERS
    }

    private static final class AntiFreecamGuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final Map<Integer, UUID> playersBySlot = new HashMap<>();
        private final Inventory inventory;

        private AntiFreecamGuiHolder(GuiPage page, int size, String title) {
            this.page = page;
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
