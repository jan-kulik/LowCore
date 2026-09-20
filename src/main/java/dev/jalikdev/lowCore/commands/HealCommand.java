package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.utils.CompletionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import dev.jalikdev.lowCore.LowCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class HealCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            LowCore.sendConfigMessage(sender, "player-only");
            return true;
        }

        if (!sender.hasPermission("lowcore.heal")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        boolean other = args.length == 1;
        Player target = player;

        if (other) {
            if (!sender.hasPermission("lowcore.heal.others")) {
                LowCore.sendConfigMessage(sender, "heal.no-permission-others");
                return true;
            }
            Player t  = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                LowCore.sendConfigMessage(sender, "unknown-player");
                return true;
            }
            target = t;
        } else if (args.length > 1) {
            LowCore.sendMessage(sender, "&cUsage: &e/heal [player]");
            return true;
        }

        target.setFoodLevel(20);
        target.setSaturation(20);

        if (other) {
            LowCore.sendConfigMessage(sender, "heal.other", "player", target.getName());
            LowCore.sendConfigMessage(target, "heal.target",  "healer", sender.getName());
        } else  {
            LowCore.sendConfigMessage(sender, "heal.self");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (args.length == 1 && sender.hasPermission("lowcore.heal.others")) {
            return CompletionUtil.onlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }
}
