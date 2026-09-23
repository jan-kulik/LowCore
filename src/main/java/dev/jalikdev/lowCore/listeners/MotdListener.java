package dev.jalikdev.lowCore.listeners;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import dev.jalikdev.lowCore.LowCore;

public class MotdListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final LowCore plugin;

    public MotdListener(LowCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }

        String line1 = plugin.getConfig().getString("motd.line-1", "&aLowCore &7Server");
        String line2 = plugin.getConfig().getString("motd.line-2", "&7Have fun!");

        String version = plugin.getPluginMeta().getVersion();
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String max = String.valueOf(Bukkit.getMaxPlayers());

        line1 = line1
                .replace("%version%", version)
                .replace("%online%", online)
                .replace("%max%", max);

        line2 = line2
                .replace("%version%", version)
                .replace("%online%", online)
                .replace("%max%", max);

        String motd = line1 + "\n" + line2;
        event.motd(LEGACY.deserialize(motd));
    }
}
