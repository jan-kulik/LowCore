package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager.Dimension;
import dev.jalikdev.lowCore.utils.DurationUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LockDimensionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> DIMENSIONS = List.of("nether", "end");
    private static final List<String> ACTIONS = List.of("lock", "unlock", "status", "30m", "1h", "1d");

    private final DimensionLockManager lockManager;

    public LockDimensionCommand(DimensionLockManager lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lowcore.dimensions")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            LowCore.sendConfigMessage(sender, "dimensions.command-usage");
            return true;
        }

        Optional<Dimension> selected = Dimension.fromInput(args[0]);
        if (selected.isEmpty()) {
            LowCore.sendConfigMessage(sender, "dimensions.command-usage");
            return true;
        }

        Dimension dimension = selected.get();
        if (args.length == 1 || args[1].equalsIgnoreCase("lock")) {
            lockManager.lock(dimension, 0L);
            LowCore.sendConfigMessage(sender, "dimensions.locked-permanent",
                    "dimension", dimension.displayName());
            return true;
        }

        if (args[1].equalsIgnoreCase("unlock")) {
            lockManager.unlock(dimension);
            LowCore.sendConfigMessage(sender, "dimensions.unlocked", "dimension", dimension.displayName());
            return true;
        }

        if (args[1].equalsIgnoreCase("status")) {
            sendStatus(sender, dimension);
            return true;
        }

        try {
            long duration = DurationUtil.parseMillis(args[1]);
            lockManager.lock(dimension, duration);
            LowCore.sendConfigMessage(sender, "dimensions.locked-timed",
                    "dimension", dimension.displayName(), "duration", DurationUtil.formatMillis(duration));
        } catch (IllegalArgumentException exception) {
            LowCore.sendConfigMessage(sender, "dimensions.invalid-duration");
        }
        return true;
    }

    private void sendStatus(CommandSender sender, Dimension dimension) {
        long remaining = lockManager.getRemainingMillis(dimension);
        if (remaining < 0L) {
            LowCore.sendConfigMessage(sender, "dimensions.status-open", "dimension", dimension.displayName());
        } else if (remaining == 0L) {
            LowCore.sendConfigMessage(sender, "dimensions.status-permanent", "dimension", dimension.displayName());
        } else {
            LowCore.sendConfigMessage(sender, "dimensions.status-timed",
                    "dimension", dimension.displayName(), "duration", DurationUtil.formatMillis(remaining));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return matching(DIMENSIONS, args[0]);
        }
        if (args.length == 2) {
            return matching(ACTIONS, args[1]);
        }
        return List.of();
    }

    private List<String> matching(List<String> values, String input) {
        String normalized = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
