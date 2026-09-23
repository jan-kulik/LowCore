package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NightVisionCommand implements CommandExecutor, Listener {

    private final LowCore plugin;
    private final Set<UUID> nightVision = new HashSet<>();

    public NightVisionCommand(LowCore plugin) {
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

        if (!sender.hasPermission("lowcore.nightvision")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (nightVision.contains(uuid)) {
            nightVision.remove(uuid);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            LowCore.sendConfigMessage(player, "misc.nv-disabled");
        } else {
            nightVision.add(uuid);
            applyNightVision(player);
            LowCore.sendConfigMessage(player, "misc.nv-enabled");
        }
        return true;
    }

    private void applyNightVision(Player player) {
        UUID uuid = player.getUniqueId();
        if (!nightVision.contains(uuid)) return;

        PotionEffect effect = new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        );

        player.addPotionEffect(effect);
    }

    private void applyNightVisionLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyNightVision(player));
    }

    @EventHandler
    public void onMilk(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!nightVision.contains(p.getUniqueId())) return;

        if (e.getItem().getType() == Material.MILK_BUCKET) {
            applyNightVisionLater(p);
        }
    }

    @EventHandler
    public void onTotem(org.bukkit.event.entity.EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!nightVision.contains(p.getUniqueId())) return;

        applyNightVisionLater(p);
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!nightVision.contains(p.getUniqueId())) return;

        applyNightVisionLater(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (nightVision.remove(player.getUniqueId())) player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    public void shutdown() {
        for (UUID playerId : nightVision) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        nightVision.clear();
    }
}
