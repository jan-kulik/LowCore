package dev.jalikdev.lowCore.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.jalikdev.lowCore.LowCore;

public class JoinQuitListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final LowCore plugin;

    public JoinQuitListener(LowCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfig().getBoolean("join-quit-messages.enabled", true)) {
            String raw = plugin.getConfig().getString("join-quit-messages.join", "&a+ &7%player%");
            raw = raw.replace("%player%", player.getName());
            event.joinMessage(LEGACY.deserialize(raw));
        }

        if (plugin.getConfig().getBoolean("update-checker.enabled", true)
                && plugin.getConfig().getBoolean("update-checker.notify-players-with-permission", true)
                && plugin.isUpdateAvailable()
                && player.hasPermission("lowcore.update")) {

            String latest = plugin.getLatestVersion();
            String current = plugin.getPluginMeta().getVersion();

            player.sendMessage(LEGACY.deserialize("&8[&aLowCore&8] &7A new &aupdate &7is available!"));
            player.sendMessage(LEGACY.deserialize("&7Current: &c" + current + " &7→ Latest: &a" + latest));
            player.sendMessage(LEGACY.deserialize("&7Download: &ahttps://github.com/jan-kulik/LowCore/releases"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfig().getBoolean("join-quit-messages.enabled", true)) {
            String raw = plugin.getConfig().getString("join-quit-messages.quit", "&c- &7%player%");
            raw = raw.replace("%player%", player.getName());
            event.quitMessage(LEGACY.deserialize(raw));
        }

        plugin.getLastLocationRepository().saveLogoutLocation(player);
    }
}
