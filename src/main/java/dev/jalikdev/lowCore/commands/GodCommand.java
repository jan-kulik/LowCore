package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.utils.CompletionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import dev.jalikdev.lowCore.LowCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GodCommand implements CommandExecutor, TabCompleter, Listener {

    private final Set<UUID> godMode = new HashSet<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        if (!sender.hasPermission("lowcore.god")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        Player target = player;

        if (args.length == 1) {
            if (!sender.hasPermission("lowcore.god.others")) {
                LowCore.sendConfigMessage(sender, "godmode.permission-others");
                return true;
            }

            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                LowCore.sendMessage(player, "&cPlayer not found!");
                return true;
            }
            target = t;
        } else if (args.length > 1) {
            LowCore.sendMessage(player, "&cUsage: &e/god [player]");
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean other = args.length == 1;


        if (godMode.contains(uuid)) {
            godMode.remove(uuid);
            target.setInvulnerable(false);
            if (other) {
                LowCore.sendConfigMessage(target, "godmode.disabled-by", "player", sender.getName());
                LowCore.sendConfigMessage(sender, "godmode.disabled-for", "target", target.getName());
            } else {
                LowCore.sendConfigMessage(target, "godmode.disabled");
            }
        } else {
            godMode.add(uuid);
            target.setInvulnerable(true);
            if (other) {
                LowCore.sendConfigMessage(target, "godmode.enabled-by", "player", sender.getName());
                LowCore.sendConfigMessage(sender, "godmode.enabled-for", "target", target.getName());
            } else {
                LowCore.sendConfigMessage(target, "godmode.enabled");
            }
        }

        return true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (godMode.remove(player.getUniqueId())) player.setInvulnerable(false);
    }

    public void shutdown() {
        for (UUID playerId : godMode) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.setInvulnerable(false);
        }
        godMode.clear();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (args.length == 1 && sender.hasPermission("lowcore.god.others")) {
            return CompletionUtil.onlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }
}
