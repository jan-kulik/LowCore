package dev.jalikdev.lowCore.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import dev.jalikdev.lowCore.LowCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class KillAllCommand implements CommandExecutor, TabCompleter {

    private static final List<String> MOB_TYPES = Arrays.stream(EntityType.values())
            .filter(type -> type.isAlive()
                    && type != EntityType.PLAYER
                    && type != EntityType.ARMOR_STAND
                    && type != EntityType.UNKNOWN)
            .map(type -> type.name().toLowerCase())
            .sorted()
            .collect(Collectors.toList());

    private static final List<String> SPECIAL_GROUPS = Arrays.asList("hostile", "passive");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }
        Player p = (Player) sender;

        if (!p.hasPermission("lowcore.killall")) {
            LowCore.sendConfigMessage(p, "no-permission");
            return true;
        }

        if (args.length == 0) {
            int killed = 0;
            for (Entity e : p.getWorld().getEntities()) {
                if (isKillable(e)) {
                    e.remove();
                    killed++;
                }
            }
            LowCore.sendMessage(p, "&aKilled &e" + killed + " &amobs.");
            return true;
        }

        if (args[0].equalsIgnoreCase("hostile")) {
            double radius = -1;

            if (args.length >= 2) {
                try {
                    radius = Double.parseDouble(args[1]);
                } catch (NumberFormatException ex) {
                    LowCore.sendMessage(p, "&cInvalid radius: &e" + args[1]);
                    return true;
                }
            }

            int killed = 0;
            if (radius > 0) {
                for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Monster && isKillable(e)) {
                        e.remove();
                        killed++;
                    }
                }
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &ahostile mobs within &e" + radius + " &ablocks.");
            } else {
                for (Entity e : p.getWorld().getEntities()) {
                    if (e instanceof Monster && isKillable(e)) {
                        e.remove();
                        killed++;
                    }
                }
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &ahostile mobs in the world.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("passive")) {
            double radius = -1;

            if (args.length >= 2) {
                try {
                    radius = Double.parseDouble(args[1]);
                } catch (NumberFormatException ex) {
                    LowCore.sendMessage(p, "&cInvalid radius: &e" + args[1]);
                    return true;
                }
            }

            int killed = 0;
            if (radius > 0) {
                for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Animals && isKillable(e)) {
                        e.remove();
                        killed++;
                    }
                }
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &apassive mobs within &e" + radius + " &ablocks.");
            } else {
                for (Entity e : p.getWorld().getEntities()) {
                    if (e instanceof Animals && isKillable(e)) {
                        e.remove();
                        killed++;
                    }
                }
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &apassive mobs in the world.");
            }
            return true;
        }

        String typeName = args[0].toUpperCase();
        EntityType type;

        try {
            type = EntityType.valueOf(typeName);
        } catch (Exception e) {
            LowCore.sendMessage(p, "&cUnknown mob type: &e" + args[0]);
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("named")) {
            int killed = killByType(p, type, -1, true);
            LowCore.sendMessage(p, "&aKilled &e" + killed + " &anamed &e" + type.name().toLowerCase() + "&a.");
            return true;
        }

        if (args.length >= 2) {
            double radius;

            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                LowCore.sendMessage(p, "&cInvalid radius: &e" + args[1]);
                return true;
            }

            boolean namedOnly = args.length >= 3 && args[2].equalsIgnoreCase("named");

            int killed = killByType(p, type, radius, namedOnly);
            if (namedOnly) {
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &anamed &e" + type.name().toLowerCase()
                        + " &awithin &e" + radius + " &ablocks.");
            } else {
                LowCore.sendMessage(p, "&aKilled &e" + killed + " &a"
                        + type.name().toLowerCase() + " &awithin &e" + radius + " &ablocks.");
            }
            return true;
        }

        int unnamedKilled = killByType(p, type, -1, false, true);
        int namedLeft = countByType(p, type, -1, true);

        LowCore.sendMessage(p, "&aKilled &e" + unnamedKilled + " &aunnamed &e" + type.name().toLowerCase() + "&a.");

        if (namedLeft > 0) {
            TextComponent base = new TextComponent(
                    LowCore.getInstance().getPrefix() +
                            "§eFound §f" + namedLeft + " §enamed " + type.name().toLowerCase() +
                            ". §7[§cClick here to kill them too§7]"
            );
            base.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/killall " + type.name().toLowerCase() + " named"
            ));
            p.spigot().sendMessage(base);
        }

        return true;
    }

    private boolean isKillable(Entity e) {
        return !(e instanceof Player)
                && e.getType().isAlive()
                && e.getType() != EntityType.ARMOR_STAND
                && e.getType() != EntityType.UNKNOWN;
    }

    private int killByType(Player p, EntityType type, double radius, boolean namedOnly) {
        return killByType(p, type, radius, namedOnly, false);
    }

    private int killByType(Player p, EntityType type, double radius, boolean namedOnly, boolean skipNamed) {
        int killed = 0;

        Collection<Entity> entities;
        if (radius > 0) {
            entities = p.getNearbyEntities(radius, radius, radius);
        } else {
            entities = p.getWorld().getEntities();
        }

        for (Entity e : entities) {
            if (e.getType() != type || !isKillable(e)) continue;

            boolean hasName = e.getCustomName() != null;

            if (skipNamed && hasName) continue;
            if (namedOnly && !hasName) continue;

            e.remove();
            killed++;
        }

        return killed;
    }

    private int countByType(Player p, EntityType type, double radius, boolean namedOnly) {
        int count = 0;

        Collection<Entity> entities;
        if (radius > 0) {
            entities = p.getNearbyEntities(radius, radius, radius);
        } else {
            entities = p.getWorld().getEntities();
        }

        for (Entity e : entities) {
            if (e.getType() != type || !isKillable(e)) continue;
            boolean hasName = e.getCustomName() != null;
            if (namedOnly && hasName) {
                count++;
            }
        }

        return count;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {

        if (!sender.hasPermission("lowcore.killall")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> result = new ArrayList<>();

            for (String s : SPECIAL_GROUPS) {
                if (s.startsWith(current)) result.add(s);
            }
            for (String s : MOB_TYPES) {
                if (s.startsWith(current)) result.add(s);
            }
            return result;
        }

        if (args.length == 2) {
            String current = args[1].toLowerCase();
            List<String> radii = Arrays.asList("10", "25", "50", "100", "200", "named");

            return radii.stream()
                    .filter(s -> s.startsWith(current))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            if ("named".startsWith(args[2].toLowerCase())) {
                return Collections.singletonList("named");
            }
        }

        return Collections.emptyList();
    }
}
