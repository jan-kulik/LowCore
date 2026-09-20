package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.trialdrops.TrialDropManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrialDropsCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int PAGE_SIZE = 45;
    private static final List<String> ACTIONS = List.of("on", "off", "status", "enable", "disable", "toggle");

    private final TrialDropManager manager;

    public TrialDropsCommand(TrialDropManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.trial-drops.admin")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                openGui(player, 0);
            } else {
                LowCore.sendConfigMessage(sender, "trial-drops.player-only");
            }
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("on") || action.equals("off")) {
            boolean enabled = action.equals("on");
            manager.setEnabled(enabled);
            LowCore.sendConfigMessage(sender, enabled ? "trial-drops.enabled" : "trial-drops.disabled");
            return true;
        }
        if (action.equals("status")) {
            LowCore.sendConfigMessage(sender, "trial-drops.status",
                    "status", manager.isEnabled() ? "&aenabled" : "&cdisabled",
                    "count", String.valueOf(manager.getBlockedMaterials().size()));
            return true;
        }

        if ((action.equals("enable") || action.equals("disable") || action.equals("toggle"))
                && args.length == 2) {
            Material material = TrialDropManager.parseMaterial(args[1]);
            if (material == null || !material.isItem()) {
                LowCore.sendConfigMessage(sender, "trial-drops.invalid-item", "item", args[1]);
                return true;
            }
            boolean blocked = action.equals("disable")
                    || (action.equals("toggle") && !manager.isBlocked(material));
            manager.setBlocked(material, blocked);
            sendItemState(sender, material, blocked);
            return true;
        }

        LowCore.sendConfigMessage(sender, "trial-drops.usage");
        return true;
    }

    private void openGui(Player player, int requestedPage) {
        List<Material> materials = manager.getGuiMaterials();
        int maximumPage = Math.max(0, (materials.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maximumPage));
        TrialDropsGuiHolder holder = new TrialDropsGuiHolder(page, maximumPage,
                "§8Secret Trial Drops §7(" + (page + 1) + "/" + (maximumPage + 1) + ")");
        Inventory inventory = holder.getInventory();

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, materials.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Material material = materials.get(index);
            holder.materialsBySlot.put(slot, material);
            inventory.setItem(slot, materialItem(material));
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "&ePrevious page", "&7Page " + page));
        }
        inventory.setItem(47, item(manager.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                manager.isEnabled() ? "&aFilter enabled" : "&cFilter disabled",
                "&7Blocked items: &e" + manager.getBlockedMaterials().size(), "", "&eClick to toggle"));
        inventory.setItem(49, item(Material.PAPER, "&fHow it works",
                "&7Only Trial Chamber loot is filtered.",
                "&7The same items still drop everywhere else.", "", "&7Click an item above to change it."));
        inventory.setItem(51, item(Material.BARRIER, "&cClose", "&7Close this secret menu."));
        if (page < maximumPage) {
            inventory.setItem(53, item(Material.ARROW, "&eNext page", "&7Page " + (page + 2)));
        }
        player.openInventory(inventory);
    }

    private ItemStack materialItem(Material material) {
        boolean blocked = manager.isBlocked(material);
        return item(material,
                (blocked ? "&c" : "&a") + TrialDropManager.displayName(material),
                "&7Trial Chamber drop: " + (blocked ? "&cBLOCKED" : "&aALLOWED"),
                blocked ? "&7New drops are removed before spawning." : "&7Vanilla loot generation is unchanged.",
                "", "&eClick to toggle");
    }

    private void sendItemState(CommandSender sender, Material material, boolean blocked) {
        LowCore.sendConfigMessage(sender, blocked ? "trial-drops.item-blocked" : "trial-drops.item-allowed",
                "item", TrialDropManager.displayName(material));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TrialDropsGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        Material material = holder.materialsBySlot.get(slot);
        if (material != null) {
            boolean blocked = !manager.isBlocked(material);
            manager.setBlocked(material, blocked);
            sendItemState(player, material, blocked);
            openGui(player, holder.page);
        } else if (slot == 45 && holder.page > 0) {
            openGui(player, holder.page - 1);
        } else if (slot == 47) {
            manager.setEnabled(!manager.isEnabled());
            LowCore.sendConfigMessage(player,
                    manager.isEnabled() ? "trial-drops.enabled" : "trial-drops.disabled");
            openGui(player, holder.page);
        } else if (slot == 51) {
            player.closeInventory();
        } else if (slot == 53 && holder.page < holder.maximumPage) {
            openGui(player, holder.page + 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TrialDropsGuiHolder) {
            event.setCancelled(true);
        }
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

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.trial-drops.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(ACTIONS, args[0]);
        }
        if (args.length == 2 && List.of("enable", "disable", "toggle")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            String input = args[1].toUpperCase(Locale.ROOT);
            return manager.getGuiMaterials().stream()
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    private static final class TrialDropsGuiHolder implements InventoryHolder {
        private final int page;
        private final int maximumPage;
        private final Map<Integer, Material> materialsBySlot = new HashMap<>();
        private final Inventory inventory;

        private TrialDropsGuiHolder(int page, int maximumPage, String title) {
            this.page = page;
            this.maximumPage = maximumPage;
            this.inventory = Bukkit.createInventory(this, 54, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
