package dev.jalikdev.lowCore.listeners;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DimensionLockListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 2_000L;

    private final LowCore plugin;
    private final Map<UUID, Long> lastMessageAt = new HashMap<>();

    public DimensionLockListener(LowCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null) {
            return;
        }

        World.Environment from = event.getFrom().getWorld().getEnvironment();
        World.Environment to = destination.getWorld().getEnvironment();
        if (!shouldBlockTransfer(from, to, isNetherLocked(), isEndLocked())
                || event.getPlayer().hasPermission("lowcore.dimensions.bypass")) {
            return;
        }

        event.setCancelled(true);
        notifyLocked(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null) {
            return;
        }

        Entity entity = event.getEntity();
        World.Environment from = entity.getWorld().getEnvironment();
        World.Environment to = destination.getWorld().getEnvironment();
        if (shouldBlockTransfer(from, to, isNetherLocked(), isEndLocked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (shouldBlockPortalCreation(event.getReason(), isNetherLocked())) {
            event.setCancelled(true);

            if (event.getEntity() instanceof Player player) {
                notifyLocked(player, World.Environment.NETHER);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastMessageAt.remove(event.getPlayer().getUniqueId());
    }

    private boolean isNetherLocked() {
        return plugin.getConfig().getBoolean("dimensions.nether-locked", false);
    }

    private boolean isEndLocked() {
        return plugin.getConfig().getBoolean("dimensions.end-locked", false);
    }

    private void notifyLocked(Player player, World.Environment environment) {
        long now = System.currentTimeMillis();
        Long previous = lastMessageAt.put(player.getUniqueId(), now);
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }

        String dimension = environment == World.Environment.NETHER ? "Nether" : "End";
        LowCore.sendConfigMessage(player, "dimensions.locked", "dimension", dimension);
    }

    static boolean shouldBlockTransfer(World.Environment from, World.Environment to,
                                       boolean netherLocked, boolean endLocked) {
        if (from == to) {
            return false;
        }
        return (to == World.Environment.NETHER && netherLocked)
                || (to == World.Environment.THE_END && endLocked);
    }

    static boolean shouldBlockPortalCreation(PortalCreateEvent.CreateReason reason, boolean netherLocked) {
        return netherLocked && (reason == PortalCreateEvent.CreateReason.FIRE
                || reason == PortalCreateEvent.CreateReason.NETHER_PAIR);
    }
}
