package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpawnMobCommand implements CommandExecutor, TabCompleter {

    private final LowCore plugin;

    public SpawnMobCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    private @Nullable Location getSpawnLocation(Player player, int maxDistance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location check = eye.clone();
        boolean foundAir = false;

        for (int i = 1; i <= maxDistance; i++) {
            check.add(direction);
            Block block = check.getBlock();

            if (block.getType().isSolid()) {
                if (!foundAir) return null;
                Location spawn = check.clone().subtract(direction);
                spawn.setYaw(player.getLocation().getYaw());
                spawn.setPitch(0);
                return spawn;
            }
            foundAir = true;
        }

        if (foundAir) {
            Location spawn = check.clone();
            spawn.setYaw(player.getLocation().getYaw());
            spawn.setPitch(0);
            return spawn;
        }

        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.hasPermission("lowcore.spawnmob")) {
            LowCore.sendConfigMessage(player, "spawnmob.no-permission");
            return true;
        }

        if (args.length < 1) {
            LowCore.sendConfigMessage(player, "spawnmob.usage");
            return true;
        }

        String typeName = args[0].toUpperCase(Locale.ROOT);

        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            LowCore.sendConfigMessage(player, "spawnmob.invalid-entity", "type", args[0]);
            return true;
        }

        if (!type.isAlive() || !type.isSpawnable()) {
            LowCore.sendConfigMessage(player, "spawnmob.invalid-entity", "type", args[0]);
            return true;
        }

        int maxAmount = Math.max(1, Math.min(1000, plugin.getConfig().getInt("spawnmob.max-amount", 50)));
        int maxDistance = Math.max(1, Math.min(100, plugin.getConfig().getInt("spawnmob.max-distance", 30)));

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                String msg = plugin.formatMessage("spawnmob.invalid-amount", "max", String.valueOf(maxAmount));
                player.sendMessage(msg);
                return true;
            }
        }

        if (amount < 1 || amount > maxAmount) {
            String msg = plugin.formatMessage("spawnmob.invalid-amount", "max", String.valueOf(maxAmount));
            player.sendMessage(msg);
            return true;
        }

        List<String> restrictedList = plugin.getConfig().getStringList("spawnmob.restricted-mobs");
        String restrictedPermission = plugin.getConfig().getString("spawnmob.restricted-permission", "lowcore.spawnmob.restricted");

        Set<String> restricted = new HashSet<>();
        for (String s : restrictedList) restricted.add(s.toUpperCase(Locale.ROOT));

        if (restricted.contains(type.name())) {
            if (!player.hasPermission(restrictedPermission)) {
                LowCore.sendConfigMessage(player, "spawnmob.restricted", "type", type.name().toLowerCase(Locale.ROOT));
                return true;
            }
        }

        Location loc = getSpawnLocation(player, maxDistance);
        if (loc == null) {
            LowCore.sendConfigMessage(player, "spawnmob.no-target");
            return true;
        }

        World world = player.getWorld();
        int spawned = 0;

        for (int i = 0; i < amount; i++) {
            LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);
            if (entity != null) {
                spawned++;
            }
        }

        if (spawned > 0) {
            String msg = plugin.formatMessage(
                    "spawnmob.success",
                    "amount", String.valueOf(spawned),
                    "type", type.name().toLowerCase(Locale.ROOT)
            );
            player.sendMessage(msg);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (!sender.hasPermission("lowcore.spawnmob")) {
            return Collections.emptyList();
        }

        List<String> restrictedList = plugin.getConfig().getStringList("spawnmob.restricted-mobs");
        String restrictedPermission = plugin.getConfig().getString("spawnmob.restricted-permission", "lowcore.spawnmob.restricted");

        Set<String> restricted = new HashSet<>();
        for (String s : restrictedList) restricted.add(s.toUpperCase(Locale.ROOT));

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();

            for (EntityType type : EntityType.values()) {
                if (!type.isAlive() || !type.isSpawnable()) continue;

                if (!sender.hasPermission(restrictedPermission)
                        && restricted.contains(type.name())) {
                    continue;
                }

                String name = type.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            return Arrays.asList("1", "5", "10", "20");
        }

        return Collections.emptyList();
    }
}
