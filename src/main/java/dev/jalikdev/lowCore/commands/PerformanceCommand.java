package dev.jalikdev.lowCore.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.jalikdev.lowCore.LowCore;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PerformanceCommand implements CommandExecutor, TabCompleter {

    private final LowCore plugin;

    public PerformanceCommand(LowCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!plugin.getConfig().getBoolean("performance.enabled", true)) {
            LowCore.sendMessage(sender, "&cThe performance command is disabled in the config.");
            return true;
        }

        if (!sender.hasPermission("lowcore.performance")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        double[] tpsArray;
        try {
            tpsArray = Bukkit.getServer().getTPS();
        } catch (NoSuchMethodError e) {
            tpsArray = new double[]{20.0, 20.0, 20.0};
        }

        double tps1 = tpsArray.length > 0 ? tpsArray[0] : 20.0;
        double tps5 = tpsArray.length > 1 ? tpsArray[1] : tps1;
        double tps15 = tpsArray.length > 2 ? tpsArray[2] : tps5;

        double mspt = -1;
        if (plugin.getConfig().getBoolean("performance.show-mspt", true)) {
            try {
                mspt = Bukkit.getServer().getAverageTickTime();
            } catch (NoSuchMethodError ignored) {
                mspt = -1;
            }
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        int online = Bukkit.getOnlinePlayers().size();

        int chunks = 0;
        if (plugin.getConfig().getBoolean("performance.show-chunks", true)) {
            for (World world : Bukkit.getWorlds()) {
                chunks += world.getChunkCount();
            }
        }

        LowCore.sendMessage(sender, "&aServer performance:");
        LowCore.sendMessage(sender,
                "&7TPS &8(1m/5m/15m)&7: &a" +
                        formatTps(tps1) + " &7/ &a" +
                        formatTps(tps5) + " &7/ &a" +
                        formatTps(tps15));

        if (mspt >= 0 && plugin.getConfig().getBoolean("performance.show-mspt", true)) {
            LowCore.sendMessage(sender, "&7Average MSPT: &a" + String.format(Locale.US, "%.2f", mspt));
        }

        LowCore.sendMessage(sender,
                "&7Memory: &a" + usedMem + "MB &7/ &a" + maxMem + "MB");
        LowCore.sendMessage(sender,
                "&7Players online: &a" + online);

        if (plugin.getConfig().getBoolean("performance.show-chunks", true)) {
            LowCore.sendMessage(sender, "&7Loaded chunks: &a" + chunks);
        }

        return true;
    }

    private String formatTps(double value) {
        double clamped = Math.min(20.0, value);
        return String.format(Locale.US, "%.2f", clamped);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        return Collections.emptyList();
    }
}
