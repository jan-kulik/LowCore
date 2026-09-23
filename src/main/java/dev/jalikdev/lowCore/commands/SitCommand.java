package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class SitCommand implements CommandExecutor, Listener {

    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private final Map<UUID, Location> seatBase = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final BukkitTask tickTask;

    public SitCommand(LowCore plugin) {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private boolean isStandingOnBlock(Player player) {
        return !player.getLocation()
                .subtract(0, 0.1, 0)
                .getBlock()
                .isPassable();
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

        if (!isStandingOnBlock(player)) {
            LowCore.sendMessage(sender, "&cYou must be standing!");
            return true;
        }

        if (seats.containsKey(player.getUniqueId())) {
            standUp(player);
        } else {
            sitDown(player);
        }
        return true;
    }

    private void sitDown(Player player) {
        Location base = player.getLocation().clone();
        base.setPitch(0f);

        ArmorStand seat = player.getWorld().spawn(base, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setCollidable(false);
            s.addScoreboardTag("sit-seat");
        });

        seat.addPassenger(player);
        seats.put(player.getUniqueId(), seat);
        seatBase.put(player.getUniqueId(), base);
        lastYaw.put(player.getUniqueId(), base.getYaw());
        LowCore.sendConfigMessage(player, "sit.down");
    }

    private void standUp(Player player) {
        standUp(player, true, true);
    }

    private void standUp(Player player, boolean teleport, boolean notify) {
        UUID uuid = player.getUniqueId();

        ArmorStand seat = seats.remove(uuid);
        Location base = seatBase.remove(uuid);
        lastYaw.remove(uuid);

        if (player.getVehicle() != null) player.leaveVehicle();
        if (seat != null && !seat.isDead()) seat.remove();

        if (teleport && base != null) {
            Location tp = base.clone().add(0, 0.5, 0);
            tp.setYaw(player.getLocation().getYaw());
            tp.setPitch(player.getLocation().getPitch());
            player.teleport(tp);
        }
        if (notify) LowCore.sendConfigMessage(player, "sit.up");
    }

    private void tick() {
        Iterator<Map.Entry<UUID, ArmorStand>> iterator = seats.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArmorStand> entry = iterator.next();
            UUID uuid = entry.getKey();
            ArmorStand seat = entry.getValue();

            Player p = Bukkit.getPlayer(uuid);
            Location base = seatBase.get(uuid);

            if (p == null || !p.isOnline() || base == null || seat == null || !seat.isValid()) {
                if (seat != null && seat.isValid()) seat.remove();
                iterator.remove();
                seatBase.remove(uuid);
                lastYaw.remove(uuid);
                continue;
            }

            float yaw = smoothYaw(uuid, p.getLocation().getYaw());
            if (Math.abs(normalizeYaw(yaw - seat.getLocation().getYaw())) < 0.5f) continue;

            Location seatLoc = base.clone();
            seatLoc.setYaw(yaw);
            seatLoc.setPitch(0f);

            seat.teleport(seatLoc);
        }
    }

    private float smoothYaw(UUID uuid, float newYaw) {
        Float last = lastYaw.get(uuid);
        if (last == null) {
            lastYaw.put(uuid, newYaw);
            return newYaw;
        }

        float delta = normalizeYaw(newYaw - last);

        float result = normalizeYaw(last + delta);
        lastYaw.put(uuid, result);
        return result;
    }

    private float normalizeYaw(float yaw) {
        while (yaw <= -180f) yaw += 360f;
        while (yaw > 180f) yaw -= 360f;
        return yaw;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!seats.containsKey(player.getUniqueId())) return;
        standUp(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!seats.containsKey(player.getUniqueId())) return;
        standUp(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (seats.containsKey(player.getUniqueId())) standUp(player, false, false);
    }

    public void shutdown() {
        tickTask.cancel();
        for (ArmorStand seat : seats.values()) {
            if (seat != null && seat.isValid()) seat.remove();
        }
        seats.clear();
        seatBase.clear();
        lastYaw.clear();
    }
}
