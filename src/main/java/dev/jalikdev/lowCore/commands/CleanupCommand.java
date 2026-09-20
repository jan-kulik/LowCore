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
import org.bukkit.inventory.Inventory;
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
        Inventory inv = Bukkit.createInventory(null, 27, "§aLag Cleanup");

        inv.setItem(11, createBtn(Material.BARRIER, "§cRemove Items", "§7Remove all dropped items."));
        inv.setItem(12, createBtn(Material.EXPERIENCE_BOTTLE, "§eRemove XP Orbs", "§7Remove all XP orbs."));
        inv.setItem(13, createBtn(Material.OAK_BOAT, "§bRemove Boats/Minecarts", "§7Remove all riding vehicles."));
        inv.setItem(14, createBtn(Material.ZOMBIE_HEAD, "§cRemove Hostile Mobs", "§7Remove all hostile creatures."));
        inv.setItem(15, createBtn(Material.COW_SPAWN_EGG, "§aRemove Passive Mobs", "§7Remove all passive creatures."));

        fillEmptySlots(inv);

        player.openInventory(inv);
    }

    private void openConfirmGUI(Player player, String type) {
        Inventory inv = Bukkit.createInventory(null, 27, "§cConfirm " + type);

        inv.setItem(11, createBtn(Material.GREEN_CONCRETE, "§aConfirm", "§7Click to confirm removal."));
        inv.setItem(15, createBtn(Material.RED_CONCRETE, "§cCancel", "§7Click to go back."));

        player.setMetadata("cleanup-type", new org.bukkit.metadata.FixedMetadataValue(plugin, type));

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
        if (e.getClickedInventory() == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();

        if (title.equals("§aLag Cleanup")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            String name = e.getCurrentItem().getItemMeta().getDisplayName();

            if (name.contains("Items")) openConfirmGUI(p, "items");
            if (name.contains("XP Orbs")) openConfirmGUI(p, "xp");
            if (name.contains("Boats")) openConfirmGUI(p, "vehicles");
            if (name.contains("Hostile")) openConfirmGUI(p, "hostile");
            if (name.contains("Passive")) openConfirmGUI(p, "passive");
        }

        if (title.startsWith("§cConfirm")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            String display = e.getCurrentItem().getItemMeta().getDisplayName();
            String type = p.getMetadata("cleanup-type").get(0).asString();

            if (display.contains("Confirm")) {
                int removed = performCleanup(type);
                plugin.audit(p, "Cleanup " + type + " removed " + removed + " entities/items");
                p.sendMessage(plugin.getPrefix() + "§aRemoved §e" + removed + " §aentities/items.");
                p.closeInventory();
            }

            if (display.contains("Cancel")) {
                openMainGUI(p);
            }
        }
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
}
