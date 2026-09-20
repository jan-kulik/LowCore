package dev.jalikdev.lowCore.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
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
import dev.jalikdev.lowCore.LowCore;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CleanupCommand implements CommandExecutor, TabCompleter, Listener {

    private final LowCore plugin;

    public CleanupCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.hasPermission("lowcore.cleanup")) {
            LowCore.sendConfigMessage(p, "no-permission");
            return true;
        }

        if (!plugin.getConfig().getBoolean("lag-cleanup.enabled", true)) {
            LowCore.sendConfigMessage(p, "lag-cleanup.disabled");
            return true;
        }

        openMainGUI(p);
        return true;
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private void fillEmptySlots(Inventory inv) {
        ItemStack filler = createFiller();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }


    private void openMainGUI(Player player) {
        CleanupHolder holder = new CleanupHolder(null, false, "§8Lag Cleanup");
        Inventory inv = holder.inventory;

        inv.setItem(11, createBtn(Material.BARRIER, "§cRemove Items", "§7Remove all dropped items."));
        inv.setItem(12, createBtn(Material.EXPERIENCE_BOTTLE, "§eRemove XP Orbs", "§7Remove all XP orbs."));
        inv.setItem(13, createBtn(Material.OAK_BOAT, "§bRemove Boats/Minecarts", "§7Remove all riding vehicles."));
        inv.setItem(14, createBtn(Material.ZOMBIE_HEAD, "§cRemove Hostile Mobs", "§7Remove all hostile creatures."));
        inv.setItem(15, createBtn(Material.COW_SPAWN_EGG, "§aRemove Passive Mobs", "§7Remove all passive creatures."));
        inv.setItem(22, createBtn(Material.ARROW, "§eLowCore menu", "§7Return to the control center."));

        fillEmptySlots(inv);

        player.openInventory(inv);
    }

    private void openConfirmGUI(Player player, String type) {
        CleanupHolder holder = new CleanupHolder(type, true, "§cConfirm " + type);
        Inventory inv = holder.inventory;

        inv.setItem(11, createBtn(Material.GREEN_CONCRETE, "§aConfirm", "§7Click to confirm removal."));
        inv.setItem(15, createBtn(Material.RED_CONCRETE, "§cCancel", "§7Click to go back."));

        fillEmptySlots(inv);

        player.openInventory(inv);
    }

    private ItemStack createBtn(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof CleanupHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != top) return;
        int slot = e.getSlot();
        if (holder.confirmation) {
            if (slot == 11) runCleanup(p, holder.type);
            else if (slot == 15) openMainGUI(p);
            return;
        }
        if (slot == 22) {
            p.performCommand("lowcore");
            return;
        }
        String type = switch (slot) {
            case 11 -> "items";
            case 12 -> "xp";
            case 13 -> "vehicles";
            case 14 -> "hostile";
            case 15 -> "passive";
            default -> null;
        };
        if (type == null) return;
        if (plugin.getConfig().getBoolean("lag-cleanup.confirm-required", true)) openConfirmGUI(p, type);
        else runCleanup(p, type);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CleanupHolder) event.setCancelled(true);
    }

    private void runCleanup(Player player, String type) {
        int removed = performCleanup(type);
        plugin.audit(player, "Cleanup " + type + " removed " + removed + " entities/items");
        player.sendMessage(plugin.getPrefix() + "§aRemoved §e" + removed + " §aentities/items.");
        openMainGUI(player);
    }

    private boolean isProtectedEntity(Entity ent) {
        if (ent instanceof LivingEntity living) {
            String name = living.getCustomName();
            if (name != null && !name.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int performCleanup(String type) {
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity ent : world.getEntities()) {

                switch (type) {

                    case "items" -> {
                        if (ent instanceof Item) {
                            ent.remove();
                            removed++;
                        }
                    }

                    case "xp" -> {
                        if (ent instanceof ExperienceOrb) {
                            ent.remove();
                            removed++;
                        }
                    }

                    case "vehicles" -> {
                        if (ent instanceof Boat || ent instanceof Minecart) {
                            ent.remove();
                            removed++;
                        }
                    }

                    case "hostile" -> {
                        if (ent instanceof Monster) {
                            if (isProtectedEntity(ent)) continue;
                            ent.remove();
                            removed++;
                        }
                    }

                    case "passive" -> {
                        if (ent instanceof Animals) {
                            if (isProtectedEntity(ent)) continue;
                            ent.remove();
                            removed++;
                        }
                    }
                }
            }
        }

        return removed;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command cmd,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        return Collections.emptyList();
    }

    private static final class CleanupHolder implements InventoryHolder {
        private final String type;
        private final boolean confirmation;
        private final Inventory inventory;

        private CleanupHolder(String type, boolean confirmation, String title) {
            this.type = type;
            this.confirmation = confirmation;
            this.inventory = Bukkit.createInventory(this, 27, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
