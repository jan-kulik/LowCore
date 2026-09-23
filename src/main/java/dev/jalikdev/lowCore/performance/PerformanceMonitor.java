package dev.jalikdev.lowCore.performance;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import dev.jalikdev.lowCore.LowCore;

import java.util.Locale;

public class PerformanceMonitor {

    private final LowCore plugin;

    private boolean enabled;
    private double warnThreshold;
    private double severeThreshold;
    private long checkIntervalTicks;
    private long cooldownMillis;

    private int taskId = -1;
    private long lastWarnMillis = 0L;
    private long lastSevereMillis = 0L;

    public PerformanceMonitor(LowCore plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    private void loadSettings() {
        this.enabled = plugin.getConfig().getBoolean("performance-monitor.enabled", true);
        this.warnThreshold = plugin.getConfig().getDouble("performance-monitor.warn-tps", 18.0);
        this.severeThreshold = plugin.getConfig().getDouble("performance-monitor.severe-tps", 15.0);

        int intervalSeconds = plugin.getConfig().getInt("performance-monitor.check-interval-seconds", 10);
        this.checkIntervalTicks = Math.max(20L, intervalSeconds * 20L);

        int cooldownSeconds = plugin.getConfig().getInt("performance-monitor.cooldown-seconds", 60);
        this.cooldownMillis = Math.max(1000L, cooldownSeconds * 1000L);
    }

    public void reload() {
        stop();
        loadSettings();
        start();
    }

    public void start() {
        if (taskId != -1) return;
        if (!enabled) {
            plugin.getLogger().info("Performance monitor is disabled in config.");
            return;
        }

        this.taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::checkPerformance,
                20L,
                checkIntervalTicks
        );

        plugin.getLogger().info("Performance monitor started. Check interval: "
                + (checkIntervalTicks / 20L) + "s");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void checkPerformance() {
        double currentTps = getCurrentTps();
        if (currentTps <= 0) {
            return;
        }

        long now = System.currentTimeMillis();

        if (currentTps < severeThreshold) {
            if (now - lastSevereMillis > cooldownMillis) {
                lastSevereMillis = now;
                notifyOps("&cSevere lag! &7Current TPS: &c" + formatTps(currentTps));
            }
            return;
        }

        if (currentTps < warnThreshold && now - lastWarnMillis > cooldownMillis) {
            lastWarnMillis = now;
            notifyOps("&eServer performance warning! &7Current TPS: &e" + formatTps(currentTps));
        }
    }

    private double getCurrentTps() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            return tps[0];
        } catch (NoSuchMethodError error) {
            plugin.getLogger().warning("Server TPS API not available. Performance monitor disabled for TPS.");
            stop();
            return -1;
        }
    }

    private String formatTps(double tps) {
        return String.format(Locale.ROOT, "%.2f", tps);
    }

    private void notifyOps(String rawMessage) {
        String full = LowCore.getInstance().getPrefix() + rawMessage;
        String colored = ChatColor.translateAlternateColorCodes('&', full);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("lowcore.performance.notify")) {
                online.sendMessage(colored);
            }
        }

        plugin.getLogger().warning(ChatColor.stripColor(colored));
    }
}
