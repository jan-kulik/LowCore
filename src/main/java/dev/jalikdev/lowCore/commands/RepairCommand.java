package dev.jalikdev.lowCore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import dev.jalikdev.lowCore.LowCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RepairCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("lowcore.repair")) {
            LowCore.sendConfigMessage(player, "no-permission");
            return true;
        }

        PlayerInventory inv = player.getInventory();

        if (args.length == 0) {
            ItemStack item = inv.getItemInMainHand();

            if (!repairItem(item)) {
                LowCore.sendMessage(player, "&cYou must hold a repairable item in your hand.");
                return true;
            }

            LowCore.sendMessage(player, "&aYour held item has been repaired.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            int repaired = 0;

            for (ItemStack item : inv.getContents()) {
                if (repairItem(item)) {
                    repaired++;
                }
            }

            LowCore.sendMessage(player, "&aRepaired &e" + repaired + " &aitems in your inventory.");
            return true;
        }

        LowCore.sendMessage(player, "&cUsage: &e/repair &7or &e/repair all");
        return true;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) return false;

        Damageable damageable = (Damageable) meta;
        if (damageable.getDamage() <= 0) return false;

        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {

        if (!sender.hasPermission("lowcore.repair")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            if ("all".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("all");
            }
        }

        return Collections.emptyList();
    }
}
