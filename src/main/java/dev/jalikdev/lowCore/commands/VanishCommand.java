package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class VanishCommand implements CommandExecutor, Listener {

    private final LowCore plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    public void startActionbarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (vanished.isEmpty()) return;
            String msg = colorize(getCfg("vanish.messages.actionbar", "&aYou are currently vanished."));
            BaseComponent[] comp = TextComponent.fromLegacyText(msg);
            for (UUID id : vanished) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, comp);
                }
            }
        }, 0L, 40L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("lowcore.vanish")) {
            LowCore.sendConfigMessage(player, "no-permission");
            return true;
        }

        if (vanished.contains(player.getUniqueId())) {
            vanished.remove(player.getUniqueId());

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) p.showPlayer(plugin, player);
            }

            try {
                player.setSilent(false);
                player.setCollidable(true);
                player.setCanPickupItems(true);
            } catch (Throwable ignored) {}

            player.sendMessage(colorize(plugin.getPrefix() + getCfg("vanish.messages.disabled", "&eYou are now visible.")));
            Bukkit.broadcastMessage(replacePlayer(colorize(getCfg("vanish.messages.fake-join", "+ %player%")), player));
        } else {
            vanished.add(player.getUniqueId());

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) p.hidePlayer(plugin, player);
            }

            try {
                player.setSilent(true);
                player.setCollidable(false);
                player.setCanPickupItems(false);
            } catch (Throwable ignored) {}

            player.sendMessage(colorize(plugin.getPrefix() + getCfg("vanish.messages.enabled", "&aYou are now vanished.")));
            Bukkit.broadcastMessage(replacePlayer(colorize(getCfg("vanish.messages.fake-quit", "- %player%")), player));
        }

        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        if (vanished.isEmpty()) return;

        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && v.isOnline()) {
                joiner.hidePlayer(plugin, v);
            }
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (event.isCancelled()) return;
        if (vanished.isEmpty()) return;

        String buffer = event.getBuffer().toLowerCase(Locale.ROOT);
        if (!buffer.contains(" ")) return;
        if (event.getCompletions().isEmpty()) return;

        event.getCompletions().removeIf(this::isVanishedName);
    }

    private boolean isVanishedName(String name) {
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && v.isOnline() && v.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String getCfg(String path, String def) {
        String raw = plugin.getConfig().getString(path, def);
        return raw != null ? raw : def;
    }

    private String replacePlayer(String msg, Player p) {
        return msg.replace("%player%", p.getName());
    }

    private String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
