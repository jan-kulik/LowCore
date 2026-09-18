package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.listeners.CrystalCooldownListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CrystalCooldownCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_COOLDOWN_TICKS = 72_000;
    private static final List<String> SUGGESTIONS = List.of("0", "1", "2", "5", "10", "20", "off", "status");

    private final LowCore plugin;
    private final CrystalCooldownListener listener;

    public CrystalCooldownCommand(LowCore plugin, CrystalCooldownListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.crystal-cooldown")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("status"))) {
            sendStatus(sender);
            return true;
        }

        if (args.length != 1) {
            LowCore.sendConfigMessage(sender, "crystal-cooldown.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            updateCooldown(sender, 0);
            return true;
        }

        try {
            int ticks = Integer.parseInt(args[0]);
            if (ticks < 0 || ticks > MAX_COOLDOWN_TICKS) {
                throw new NumberFormatException("Cooldown out of range");
            }
            updateCooldown(sender, ticks);
        } catch (NumberFormatException exception) {
            LowCore.sendConfigMessage(sender, "crystal-cooldown.invalid");
        }
        return true;
    }

    private void updateCooldown(CommandSender sender, int ticks) {
        plugin.getConfig().set("crystal-cooldown.ticks", ticks);
        plugin.saveConfig();
        listener.reset();

        if (ticks == 0) {
            Bukkit.getOnlinePlayers().forEach(player -> player.setCooldown(Material.END_CRYSTAL, 0));
            LowCore.sendConfigMessage(sender, "crystal-cooldown.disabled");
        } else {
            LowCore.sendConfigMessage(sender, "crystal-cooldown.updated", "ticks", Integer.toString(ticks));
        }
    }

    private void sendStatus(CommandSender sender) {
        int ticks = Math.max(0, plugin.getConfig().getInt("crystal-cooldown.ticks", 0));
        String seconds = ticks % 20 == 0
                ? Integer.toString(ticks / 20)
                : String.format(java.util.Locale.ROOT, "%.2f", ticks / 20.0);
        LowCore.sendConfigMessage(sender, "crystal-cooldown.status",
                "ticks", Integer.toString(ticks), "seconds", seconds);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String suggestion : SUGGESTIONS) {
            if (suggestion.startsWith(input)) {
                matches.add(suggestion);
            }
        }
        return matches;
    }
}
