package dev.jalikdev.lowCore.commands;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.database.DatabaseManager;
import dev.jalikdev.lowCore.database.OfflineInventoryRepository;
import dev.jalikdev.lowCore.database.OfflineInventoryRepository.OfflineInventoryMeta;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

public class LowCoreAdminCommand implements CommandExecutor, TabCompleter {

    private final LowCore plugin;
    private final OfflineInventoryRepository offlineRepo;

    public LowCoreAdminCommand(LowCore plugin) {
        this.plugin = plugin;
        this.offlineRepo = plugin.getOfflineInventoryRepository();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("lowcore.admin")) {
            LowCore.sendConfigMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            LowCore.sendConfigMessage(sender, "admin.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("offinv")) {
            return handleOffinv(sender, args);
        }

        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebug(sender, args);
        }

        LowCore.sendConfigMessage(sender, "admin.usage");
        return true;
    }

    private boolean handleOffinv(CommandSender sender, String[] args) {
        if (args.length == 1) {
            LowCore.sendConfigMessage(sender, "admin.offinv-usage");
            return true;
        }

        if (args[1].equalsIgnoreCase("clear")) {
            if (args.length < 3) {
                LowCore.sendConfigMessage(sender, "admin.offinv-usage");
                return true;
            }

            OfflinePlayer off = Bukkit.getOfflinePlayer(args[2]);
            if ((off == null || !off.hasPlayedBefore()) && !off.isOnline()) {
                LowCore.sendConfigMessage(sender, "unknown-player");
                return true;
            }

            offlineRepo.deletePlayer(off.getUniqueId());
            LowCore.sendConfigMessage(sender, "admin.offinv-cleared", "target", off.getName());
            return true;
        }

        if (args[1].equalsIgnoreCase("clearall")) {
            int count = offlineRepo.deleteAll();
            LowCore.sendConfigMessage(sender, "admin.offinv-cleared-all", "count", String.valueOf(count));
            return true;
        }

        LowCore.sendConfigMessage(sender, "admin.offinv-usage");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length == 1) {
            LowCore.sendConfigMessage(sender, "admin.debug-usage");
            return true;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("info")) {
            return debugInfo(sender);
        }

        if (sub.equals("dbtest")) {
            return debugDbTest(sender);
        }

        if (sub.equals("dbstats")) {
            return debugDbStats(sender);
        }

        if (sub.equals("dbreconnect")) {
            return debugDbReconnect(sender);
        }

        if (sub.equals("offinv")) {
            return debugOffinv(sender, args);
        }

        if (sub.equals("player")) {
            return debugPlayer(sender, args);
        }

        if (sub.equals("config")) {
            return debugConfig(sender, args);
        }

        if (sub.equals("files")) {
            return debugFiles(sender, args);
        }

        if (sub.equals("status")) {
            return debugStatus(sender);
        }

        LowCore.sendConfigMessage(sender, "admin.debug-usage");
        return true;
    }

    private boolean debugInfo(CommandSender sender) {
        PluginDescriptionFile d = plugin.getDescription();
        String ver = d.getVersion();
        String name = d.getName();
        String api = Bukkit.getBukkitVersion();
        String server = Bukkit.getServer().getVersion();
        int players = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<String> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) worlds.add(w.getName());

        LowCore.sendMessage(sender, "&7=== LowCore Debug Info ===");
        LowCore.sendMessage(sender, "&7Plugin: &a" + name + " &7v&a" + ver);
        LowCore.sendMessage(sender, "&7Server: &a" + server);
        LowCore.sendMessage(sender, "&7Bukkit API: &a" + api);
        LowCore.sendMessage(sender, "&7Online: &a" + players + "&7/&a" + maxPlayers);
        if (!online.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Player p : online) {
                if (sb.length() > 0) sb.append("&7, &f");
                sb.append(p.getName());
            }
            LowCore.sendMessage(sender, "&7Players: &f" + sb);
        } else {
            LowCore.sendMessage(sender, "&7Players: &cnone");
        }
        LowCore.sendMessage(sender, "&7Worlds: &a" + String.join("&7, &a", worlds));
        return true;
    }

    private boolean debugDbTest(CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            LowCore.sendMessage(sender, "&cDatabase manager is null.");
            return true;
        }

        Connection c = db.getConnection();
        if (c == null) {
            LowCore.sendMessage(sender, "&cDatabase connection is null or not initialized.");
            return true;
        }

        long start = System.nanoTime();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            if (rs.next()) {
                long end = System.nanoTime();
                long ms = (end - start) / 1_000_000;
                LowCore.sendMessage(sender, "&aDatabase test OK &7(SELECT 1) &8- &a" + ms + "ms");
            } else {
                LowCore.sendMessage(sender, "&cDatabase test failed: no result.");
            }
        } catch (Exception e) {
            LowCore.sendMessage(sender, "&cDatabase test failed: &4" + e.getClass().getSimpleName() + "&c: " + e.getMessage());
        }
        return true;
    }

    private boolean debugDbStats(CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            LowCore.sendMessage(sender, "&cDatabase manager is null.");
            return true;
        }

        Connection c = db.getConnection();
        if (c == null) {
            LowCore.sendMessage(sender, "&cDatabase connection is null or not initialized.");
            return true;
        }

        LowCore.sendMessage(sender, "&7=== Database Stats ===");
        try (Statement st = c.createStatement()) {

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM last_locations")) {
                if (rs.next()) {
                    LowCore.sendMessage(sender, "&7Table &alast_locations&7: &e" + rs.getInt("cnt") + " &7rows");
                }
            } catch (Exception e) {
                LowCore.sendMessage(sender, "&7Table &alast_locations&7: &cerror");
            }

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM offline_inventories")) {
                if (rs.next()) {
                    LowCore.sendMessage(sender, "&7Table &aoffline_inventories&7: &e" + rs.getInt("cnt") + " &7rows");
                }
            } catch (Exception e) {
                LowCore.sendMessage(sender, "&7Table &aoffline_inventories&7: &cerror");
            }

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM anti_freecam_logs")) {
                if (rs.next()) {
                    LowCore.sendMessage(sender, "&7Anti-Mod logs: &e" + rs.getInt("cnt") + " &7rows");
                }
            } catch (Exception e) {
                LowCore.sendMessage(sender, "&7Anti-Mod logs: &cerror");
            }

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM audit_logs")) {
                if (rs.next()) {
                    LowCore.sendMessage(sender, "&7Admin audit logs: &e" + rs.getInt("cnt") + " &7rows");
                }
            } catch (Exception e) {
                LowCore.sendMessage(sender, "&7Admin audit logs: &cerror");
            }

        } catch (Exception e) {
            LowCore.sendMessage(sender, "&cError while reading DB stats: &4" + e.getClass().getSimpleName());
        }
        return true;
    }

    private boolean debugDbReconnect(CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            LowCore.sendMessage(sender, "&cDatabase manager is null.");
            return true;
        }

        LowCore.sendMessage(sender, "&7Reconnecting SQLite database...");
        boolean ok = db.reconnect();
        if (ok) {
            LowCore.sendMessage(sender, "&aDatabase reconnected successfully.");
        } else {
            LowCore.sendMessage(sender, "&cDatabase reconnect failed. Check console for details.");
        }
        return true;
    }

    private boolean debugOffinv(CommandSender sender, String[] args) {
        if (args.length == 2) {
            LowCore.sendConfigMessage(sender, "admin.debug-offinv-usage");
            return true;
        }

        String action = args[2].toLowerCase();

        if (action.equals("list")) {
            List<UUID> uuids = offlineRepo.getAllWithData();
            if (uuids.isEmpty()) {
                LowCore.sendMessage(sender, "&7No offline inventory data stored.");
                return true;
            }
            List<String> names = new ArrayList<>();
            for (UUID u : uuids) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                String n = off.getName();
                if (n == null) n = u.toString();
                names.add(n);
            }
            LowCore.sendMessage(sender, "&7Offline inventory entries: &a" + names.size());
            LowCore.sendMessage(sender, "&7Players: &f" + String.join("&7, &f", names));
            return true;
        }

        if (action.equals("info")) {
            if (args.length < 4) {
                LowCore.sendConfigMessage(sender, "admin.debug-offinv-usage");
                return true;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(args[3]);
            if ((off == null || !off.hasPlayedBefore()) && !off.isOnline()) {
                LowCore.sendConfigMessage(sender, "unknown-player");
                return true;
            }
            OfflineInventoryMeta meta = offlineRepo.getMeta(off.getUniqueId());
            if (meta == null) {
                LowCore.sendMessage(sender, "&cNo offline inventory entry for &e" + off.getName());
                return true;
            }
            String time = meta.updatedAt > 0
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(meta.updatedAt))
                    : "unknown";
            LowCore.sendMessage(sender, "&7=== Offline Inventory Debug: &a" + off.getName() + " &7===");
            LowCore.sendMessage(sender, "&7Inventory Snapshot: " + (meta.hasInvSnapshot ? "&aYES" : "&cNO"));
            LowCore.sendMessage(sender, "&7EnderChest Snapshot: " + (meta.hasEcSnapshot ? "&aYES" : "&cNO"));
            LowCore.sendMessage(sender, "&7Inventory Pending: " + (meta.hasInvPending ? "&aYES" : "&cNO"));
            LowCore.sendMessage(sender, "&7EnderChest Pending: " + (meta.hasEcPending ? "&aYES" : "&cNO"));
            LowCore.sendMessage(sender, "&7Last Updated: &f" + time);
            return true;
        }

        LowCore.sendConfigMessage(sender, "admin.debug-offinv-usage");
        return true;
    }

    private boolean debugPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            LowCore.sendConfigMessage(sender, "admin.debug-player-usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            LowCore.sendConfigMessage(sender, "unknown-player");
            return true;
        }

        LowCore.sendMessage(sender, "&7=== Player Debug: &a" + target.getName() + " &7===");
        LowCore.sendMessage(sender, "&7UUID: &f" + target.getUniqueId());
        LowCore.sendMessage(sender, "&7World: &a" + target.getWorld().getName());
        LowCore.sendMessage(sender, "&7Location: &f" +
                String.format("x=%.2f y=%.2f z=%.2f", target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ()));
        LowCore.sendMessage(sender, "&7Health: &a" + target.getHealth() + " &7/ &a" + target.getMaxHealth());
        LowCore.sendMessage(sender, "&7Food: &a" + target.getFoodLevel());
        LowCore.sendMessage(sender, "&7Gamemode: &a" + target.getGameMode().name());
        try {
            LowCore.sendMessage(sender, "&7Ping: &a" + target.getPing() + "ms");
        } catch (Throwable ignored) {
            LowCore.sendMessage(sender, "&7Ping: &cnot available on this server version");
        }
        LowCore.sendMessage(sender, "&7Inventory Size: &a" + target.getInventory().getSize());
        return true;
    }

    private boolean debugConfig(CommandSender sender, String[] args) {
        if (args.length < 3) {
            LowCore.sendConfigMessage(sender, "admin.debug-config-usage");
            return true;
        }
        if (args[2].equalsIgnoreCase("reload")) {
            plugin.reloadLowCoreConfig(sender);
            return true;
        }
        LowCore.sendConfigMessage(sender, "admin.debug-config-usage");
        return true;
    }

    private boolean debugFiles(CommandSender sender, String[] args) {
        if (args.length < 3) {
            LowCore.sendConfigMessage(sender, "admin.debug-files-usage");
            return true;
        }

        if (args[2].equalsIgnoreCase("list")) {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) {
                LowCore.sendMessage(sender, "&cData folder does not exist: " + folder.getPath());
                return true;
            }
            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                LowCore.sendMessage(sender, "&7Data folder is empty.");
                return true;
            }
            LowCore.sendMessage(sender, "&7=== Data Folder Files ===");
            for (File f : files) {
                String type = f.isDirectory() ? "DIR " : "FILE";
                long size = f.isDirectory() ? 0L : f.length();
                LowCore.sendMessage(sender, "&7" + type + " &f" + f.getName() + " &8(" + size + " bytes)");
            }
            return true;
        }

        LowCore.sendConfigMessage(sender, "admin.debug-files-usage");
        return true;
    }

    private boolean debugStatus(CommandSender sender) {
        boolean jq = plugin.getConfig().getBoolean("join-quit-messages.enabled", true);
        boolean motd = plugin.getConfig().getBoolean("motd.enabled", true);
        boolean upd = plugin.getConfig().getBoolean("update-checker.enabled", true);
        boolean perf = plugin.getConfig().getBoolean("performance.enabled", true);
        boolean perfMon = plugin.getConfig().getBoolean("performance-monitor.enabled", true);
        boolean lagCleanup = plugin.getConfig().getBoolean("lag-cleanup.enabled", true);
        boolean lastLogout = plugin.getConfig().getBoolean("lastlogout.enabled", true);
        boolean antiMods = plugin.getConfig().getBoolean("anti-mods.enabled", false);

        LowCore.sendMessage(sender, "&7=== LowCore System Status ===");
        LowCore.sendMessage(sender, "&7Join/Quit Messages: " + (jq ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7MOTD: " + (motd ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7Update Checker: " + (upd ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7Performance Command: " + (perf ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7Performance Monitor: " + (perfMon ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7Lag Cleanup: " + (lagCleanup ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7LastLogout: " + (lastLogout ? "&aENABLED" : "&cDISABLED"));
        LowCore.sendMessage(sender, "&7Anti-Mods: " + (antiMods ? "&aENABLED" : "&cDISABLED"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (!sender.hasPermission("lowcore.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String in = args[0].toLowerCase();
            if ("offinv".startsWith(in)) list.add("offinv");
            if ("debug".startsWith(in)) list.add("debug");
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("offinv")) {
            List<String> list = new ArrayList<>();
            String in = args[1].toLowerCase();
            if ("clear".startsWith(in)) list.add("clear");
            if ("clearall".startsWith(in)) list.add("clearall");
            return list;
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("offinv")
                && args[1].equalsIgnoreCase("clear")) {

            String input = args[2].toLowerCase();
            List<String> names = new ArrayList<>();

            for (UUID uuid : offlineRepo.getAllWithData()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                String name = off.getName();
                if (name != null && name.toLowerCase().startsWith(input)) names.add(name);
            }

            return names;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (args.length == 2) {
                List<String> list = new ArrayList<>();
                String in = args[1].toLowerCase();
                if ("info".startsWith(in)) list.add("info");
                if ("dbtest".startsWith(in)) list.add("dbtest");
                if ("dbstats".startsWith(in)) list.add("dbstats");
                if ("dbreconnect".startsWith(in)) list.add("dbreconnect");
                if ("offinv".startsWith(in)) list.add("offinv");
                if ("player".startsWith(in)) list.add("player");
                if ("config".startsWith(in)) list.add("config");
                if ("files".startsWith(in)) list.add("files");
                if ("status".startsWith(in)) list.add("status");
                return list;
            }

            if (args.length == 3) {
                String sub = args[1].toLowerCase();
                String in = args[2].toLowerCase();

                if (sub.equals("offinv")) {
                    List<String> list = new ArrayList<>();
                    if ("list".startsWith(in)) list.add("list");
                    if ("info".startsWith(in)) list.add("info");
                    return list;
                }

                if (sub.equals("player")) {
                    List<String> list = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(in)) list.add(p.getName());
                    }
                    return list;
                }

                if (sub.equals("config")) {
                    List<String> list = new ArrayList<>();
                    if ("reload".startsWith(in)) list.add("reload");
                    return list;
                }

                if (sub.equals("files")) {
                    List<String> list = new ArrayList<>();
                    if ("list".startsWith(in)) list.add("list");
                    return list;
                }
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("offinv") && args[2].equalsIgnoreCase("info")) {
                String in = args[3].toLowerCase();
                List<String> names = new ArrayList<>();
                for (UUID uuid : offlineRepo.getAllWithData()) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                    String name = off.getName();
                    if (name != null && name.toLowerCase().startsWith(in)) names.add(name);
                }
                return names;
            }
        }

        return Collections.emptyList();
    }
}
