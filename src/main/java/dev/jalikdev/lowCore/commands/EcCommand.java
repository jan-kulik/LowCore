package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import dev.jalikdev.lowCore.utils.CompletionUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EcCommand implements CommandExecutor, TabCompleter, Listener {

    private final LowCore plugin;
    private final Map<Inventory, UUID> offlineEcViews = new HashMap<>();

    public EcCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            if (!sender.hasPermission("lowcore.ec")) {
                LowCore.sendConfigMessage(sender, "no-permission");
                return true;
            }

            player.openInventory(player.getEnderChest());
            LowCore.sendConfigMessage(sender, "ec.self-open");
            return true;
        }

        if (!sender.hasPermission("lowcore.ec.others")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        Player viewer = player;

        String targetName = args[0];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            viewer.openInventory(target.getEnderChest());
            LowCore.sendConfigMessage(sender, "ec.open-other", "target", target.getName());
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        if ((!offline.hasPlayedBefore()) && !offline.isOnline()) {
            LowCore.sendConfigMessage(sender, "unknown-player");
            return true;
        }

        ItemStack[] data = plugin.getOfflineInventoryRepository().loadEffectiveEnderChest(offline.getUniqueId());
        if (data == null) {
            LowCore.sendConfigMessage(sender, "ec.offline-no-data", "target", offline.getName());
            return true;
        }

        Inventory inv = Bukkit.createInventory(
                viewer,
                27,
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&8EnderChest &7- &a" + offline.getName() + " &7(offline)")
        );

        inv.setContents(data);

        offlineEcViews.put(inv, offline.getUniqueId());
        viewer.openInventory(inv);

        LowCore.sendConfigMessage(sender, "ec.offline-open", "target", offline.getName());
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        UUID uuid = offlineEcViews.remove(top);
        if (uuid == null) return;

        saveOfflineView(top, uuid);
    }

    public void shutdown() {
        for (Map.Entry<Inventory, UUID> entry : offlineEcViews.entrySet()) {
            saveOfflineView(entry.getKey(), entry.getValue());
        }
        offlineEcViews.clear();
    }

    private void saveOfflineView(Inventory inventory, UUID playerId) {
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int i = 0; i < inventory.getSize(); i++) {
            contents[i] = inventory.getItem(i);
        }
        plugin.getOfflineInventoryRepository().savePendingEnderChest(playerId, contents);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (args.length == 1 && sender.hasPermission("lowcore.ec.others")) {
            return CompletionUtil.onlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }
}
