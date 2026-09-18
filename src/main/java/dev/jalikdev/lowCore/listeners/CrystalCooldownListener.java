package dev.jalikdev.lowCore.listeners;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrystalCooldownListener implements Listener {

    private final LowCore plugin;
    private final Map<UUID, Integer> lastPlacementTick = new HashMap<>();

    public CrystalCooldownListener(LowCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) {
            return;
        }

        Player player = event.getPlayer();
        int cooldown = Math.max(0, plugin.getConfig().getInt("crystal-cooldown.ticks", 0));
        if (player == null || cooldown == 0
                || player.hasPermission("lowcore.crystal-cooldown.bypass")) {
            return;
        }

        int currentTick = plugin.getServer().getCurrentTick();
        Integer previousTick = lastPlacementTick.get(player.getUniqueId());
        if (previousTick != null) {
            int remaining = remainingTicks(previousTick, currentTick, cooldown);
            if (remaining > 0) {
                event.setCancelled(true);
                player.setCooldown(Material.END_CRYSTAL, remaining);
                LowCore.sendConfigMessage(player, "crystal-cooldown.wait",
                        "ticks", Integer.toString(remaining));
                return;
            }
        }

        lastPlacementTick.put(player.getUniqueId(), currentTick);
        player.setCooldown(Material.END_CRYSTAL, cooldown);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPlacementTick.remove(event.getPlayer().getUniqueId());
    }

    public void reset() {
        lastPlacementTick.clear();
    }

    static int remainingTicks(int previousTick, int currentTick, int cooldown) {
        return Math.max(0, cooldown - (currentTick - previousTick));
    }
}
