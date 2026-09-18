package dev.jalikdev.lowCore.dimensions;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Optional;

public class DimensionLockManager {

    private final LowCore plugin;
    private BukkitTask expiryTask;

    public DimensionLockManager(LowCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        checkExpiredLocks();
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredLocks, 20L, 20L);
    }

    public void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    public void lock(Dimension dimension, long durationMillis) {
        plugin.getConfig().set(dimension.lockedPath(), true);
        plugin.getConfig().set(dimension.unlockAtPath(),
                durationMillis > 0L ? System.currentTimeMillis() + durationMillis : 0L);
        plugin.saveConfig();
    }

    public void unlock(Dimension dimension) {
        setUnlocked(dimension, false);
    }

    public boolean isLocked(Dimension dimension) {
        if (!plugin.getConfig().getBoolean(dimension.lockedPath(), false)) {
            return false;
        }

        long unlockAt = plugin.getConfig().getLong(dimension.unlockAtPath(), 0L);
        if (unlockAt > 0L && unlockAt <= System.currentTimeMillis()) {
            setUnlocked(dimension, true);
            return false;
        }
        return true;
    }

    public long getRemainingMillis(Dimension dimension) {
        if (!isLocked(dimension)) {
            return -1L;
        }

        long unlockAt = plugin.getConfig().getLong(dimension.unlockAtPath(), 0L);
        return unlockAt == 0L ? 0L : Math.max(1L, unlockAt - System.currentTimeMillis());
    }

    private void checkExpiredLocks() {
        for (Dimension dimension : Dimension.values()) {
            isLocked(dimension);
        }
    }

    private void setUnlocked(Dimension dimension, boolean announce) {
        boolean wasLocked = plugin.getConfig().getBoolean(dimension.lockedPath(), false);
        plugin.getConfig().set(dimension.lockedPath(), false);
        plugin.getConfig().set(dimension.unlockAtPath(), 0L);
        plugin.saveConfig();

        if (announce && wasLocked) {
            String message = plugin.formatMessage("dimensions.auto-unlocked", "dimension", dimension.displayName());
            Bukkit.getConsoleSender().sendMessage(message);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
        }
    }

    public enum Dimension {
        NETHER("nether", "Nether"),
        END("end", "End");

        private final String configName;
        private final String displayName;

        Dimension(String configName, String displayName) {
            this.configName = configName;
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        String lockedPath() {
            return "dimensions." + configName + "-locked";
        }

        String unlockAtPath() {
            return "dimensions." + configName + "-unlock-at";
        }

        public static Optional<Dimension> fromInput(String input) {
            if (input == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(input.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }
}
