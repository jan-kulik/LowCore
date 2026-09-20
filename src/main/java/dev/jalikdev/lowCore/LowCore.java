package dev.jalikdev.lowCore;

import dev.jalikdev.lowCore.antifreecam.AntiFreecamManager;
import dev.jalikdev.lowCore.trialdrops.TrialDropManager;
import dev.jalikdev.lowCore.commands.*;
import dev.jalikdev.lowCore.dimensions.DimensionLockManager;
import dev.jalikdev.lowCore.performance.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.jalikdev.lowCore.listeners.JoinQuitListener;
import dev.jalikdev.lowCore.listeners.DimensionLockListener;
import dev.jalikdev.lowCore.listeners.CrystalCooldownListener;
import dev.jalikdev.lowCore.listeners.MotdListener;
import dev.jalikdev.lowCore.world.WorldInventoryManager;

import dev.jalikdev.lowCore.database.DatabaseManager;
import dev.jalikdev.lowCore.database.AntiFreecamLogRepository;
import dev.jalikdev.lowCore.database.AdminAuditLogRepository;
import dev.jalikdev.lowCore.database.LastLocationRepository;

import dev.jalikdev.lowCore.database.OfflineInventoryRepository;
import dev.jalikdev.lowCore.listeners.OfflineInventoryListener;

import java.sql.SQLException;
import java.io.File;
import java.util.Objects;

public class LowCore extends JavaPlugin {

    public static final String DEFAULT_PREFIX = "&8[&aLowCore&8] &7";

    private WorldInventoryManager worldInventoryManager;

    private static LowCore instance;
    private String prefix;

    private boolean updateAvailable = false;
    private String latestVersion = null;

    private PerformanceMonitor performanceMonitor;
    private DimensionLockManager dimensionLockManager;
    private AntiFreecamManager antiFreecamManager;

    private DatabaseManager databaseManager;
    private LastLocationRepository lastLocationRepository;
    private AntiFreecamLogRepository antiFreecamLogRepository;
    private LogCommand auditLog;

    public static LowCore getInstance() {
        return instance;
    }

    private OfflineInventoryRepository offlineInventoryRepository;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();

        loadPrefix();

        getLogger().info("LowCore plugin enabled!");
        getLogger().info("Configuration loaded.");

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
            getLogger().info("SQLite database connected.");
        } catch (SQLException e) {
            getLogger().severe("Could not connect to SQLite database!");
            e.printStackTrace();
        }

        worldInventoryManager = new WorldInventoryManager(this);
        lastLocationRepository = new LastLocationRepository(databaseManager);
        offlineInventoryRepository = new OfflineInventoryRepository(databaseManager);
        dimensionLockManager = new DimensionLockManager(this);
        antiFreecamLogRepository = new AntiFreecamLogRepository(databaseManager);
        antiFreecamManager = new AntiFreecamManager(this, antiFreecamLogRepository);

        LowcoreCommand lowcoreCommand = new LowcoreCommand(this);
        Objects.requireNonNull(getCommand("lowcore")).setExecutor(lowcoreCommand);
        Objects.requireNonNull(getCommand("lowcore")).setTabCompleter(lowcoreCommand);
        getServer().getPluginManager().registerEvents(lowcoreCommand, this);

        LockDimensionCommand lockDimensionCommand = new LockDimensionCommand(this, dimensionLockManager);
        Objects.requireNonNull(getCommand("lock-dimension")).setExecutor(lockDimensionCommand);
        Objects.requireNonNull(getCommand("lock-dimension")).setTabCompleter(lockDimensionCommand);
        getServer().getPluginManager().registerEvents(lockDimensionCommand, this);

        CrystalCooldownListener crystalCooldownListener = new CrystalCooldownListener(this);
        CrystalCooldownCommand crystalCooldownCommand = new CrystalCooldownCommand(this, crystalCooldownListener);
        Objects.requireNonNull(getCommand("crystal-cooldown")).setExecutor(crystalCooldownCommand);
        Objects.requireNonNull(getCommand("crystal-cooldown")).setTabCompleter(crystalCooldownCommand);
        getServer().getPluginManager().registerEvents(crystalCooldownListener, this);

        AntiFreecamCommand antiFreecamCommand = new AntiFreecamCommand(antiFreecamManager);
        Objects.requireNonNull(getCommand("anti-mods")).setExecutor(antiFreecamCommand);
        Objects.requireNonNull(getCommand("anti-mods")).setTabCompleter(antiFreecamCommand);
        getServer().getPluginManager().registerEvents(antiFreecamCommand, this);
        getServer().getPluginManager().registerEvents(antiFreecamManager, this);

        TrialDropManager trialDropManager = new TrialDropManager(this);
        TrialDropsCommand trialDropsCommand = new TrialDropsCommand(trialDropManager);
        Objects.requireNonNull(getCommand("trial-drops")).setExecutor(trialDropsCommand);
        Objects.requireNonNull(getCommand("trial-drops")).setTabCompleter(trialDropsCommand);
        getServer().getPluginManager().registerEvents(trialDropManager, this);
        getServer().getPluginManager().registerEvents(trialDropsCommand, this);

        InvseeCommand invseeCommand = new InvseeCommand(this);
        Objects.requireNonNull(getCommand("invsee")).setExecutor(invseeCommand);
        Objects.requireNonNull(getCommand("invsee")).setTabCompleter(invseeCommand);
        getServer().getPluginManager().registerEvents(invseeCommand, this);

        GmCommand gmCommand = new GmCommand();
        Objects.requireNonNull(getCommand("gm")).setExecutor(gmCommand);
        Objects.requireNonNull(getCommand("gm")).setTabCompleter(gmCommand);

        EcCommand ecCommand = new EcCommand(this);
        Objects.requireNonNull(getCommand("ec")).setExecutor(ecCommand);
        Objects.requireNonNull(getCommand("ec")).setTabCompleter(ecCommand);
        getServer().getPluginManager().registerEvents(ecCommand, this);

        HatCommand hatCommand = new HatCommand();
        Objects.requireNonNull(getCommand("hat")).setExecutor(hatCommand);
        Objects.requireNonNull(getCommand("hat")).setTabCompleter(hatCommand);

        FlyCommand flyCommand = new FlyCommand();
        Objects.requireNonNull(getCommand("fly")).setExecutor(flyCommand);

        HealCommand healCommand = new HealCommand();
        Objects.requireNonNull(getCommand("heal")).setExecutor(healCommand);
        Objects.requireNonNull(getCommand("heal")).setTabCompleter(healCommand);

        FeedCommand feedCommand = new FeedCommand();
        Objects.requireNonNull(getCommand("feed")).setExecutor(feedCommand);
        Objects.requireNonNull(getCommand("feed")).setTabCompleter(feedCommand);


        SpawnMobCommand spawnMobCommand = new SpawnMobCommand(this);
        Objects.requireNonNull(getCommand("spawnmob")).setExecutor(spawnMobCommand);
        Objects.requireNonNull(getCommand("spawnmob")).setTabCompleter(spawnMobCommand);

        EnchantCommand enchantCommand = new EnchantCommand(this);
        Objects.requireNonNull(getCommand("enchant")).setExecutor(enchantCommand);
        Objects.requireNonNull(getCommand("enchant")).setTabCompleter(enchantCommand);

        AnvilCommand anvilCommand = new AnvilCommand();
        Objects.requireNonNull(getCommand("anvil")).setExecutor(anvilCommand);
        Objects.requireNonNull(getCommand("anvil")).setTabCompleter(anvilCommand);

        RepairCommand repairCommand = new RepairCommand();
        Objects.requireNonNull(getCommand("repair")).setExecutor(repairCommand);
        Objects.requireNonNull(getCommand("repair")).setTabCompleter(repairCommand);

        CraftCommand craftCommand = new CraftCommand();
        Objects.requireNonNull(getCommand("craft")).setExecutor(craftCommand);
        Objects.requireNonNull(getCommand("workbench")).setExecutor(craftCommand);
        Objects.requireNonNull(getCommand("craft")).setTabCompleter(craftCommand);
        Objects.requireNonNull(getCommand("workbench")).setTabCompleter(craftCommand);

        VanishCommand vanishCommand = new VanishCommand(this);
        getCommand("vanish").setExecutor(vanishCommand);
        Bukkit.getPluginManager().registerEvents(vanishCommand, this);
        vanishCommand.startActionbarTask();

        SpeedCommand speedCommand = new SpeedCommand();
        Objects.requireNonNull(getCommand("speed")).setExecutor(speedCommand);
        Objects.requireNonNull(getCommand("speed")).setTabCompleter(speedCommand);

        GodCommand godCommand = new GodCommand();
        Objects.requireNonNull(getCommand("god")).setExecutor(godCommand);
        Objects.requireNonNull(getCommand("god")).setTabCompleter(godCommand);


        KillAllCommand killAllCommand = new KillAllCommand();
        Objects.requireNonNull(getCommand("killall")).setExecutor(killAllCommand);
        Objects.requireNonNull(getCommand("killall")).setTabCompleter(killAllCommand);

        auditLog = new LogCommand(this, new AdminAuditLogRepository(databaseManager));
        Objects.requireNonNull(getCommand("log")).setExecutor(auditLog);
        Objects.requireNonNull(getCommand("log")).setTabCompleter(auditLog);
        getServer().getPluginManager().registerEvents(auditLog, this);

        getServer().getPluginManager().registerEvents(new MotdListener(this), this);

        PerformanceCommand performanceCommand = new PerformanceCommand(this);
        Objects.requireNonNull(getCommand("performance")).setExecutor(performanceCommand);
        Objects.requireNonNull(getCommand("performance")).setTabCompleter(performanceCommand);

        CleanupCommand cleanupCommand = new CleanupCommand(this);
        Objects.requireNonNull(getCommand("cleanup")).setExecutor(cleanupCommand);
        Objects.requireNonNull(getCommand("cleanup")).setTabCompleter(cleanupCommand);
        getServer().getPluginManager().registerEvents(cleanupCommand, this);

        SudoCommand sudoCommand = new SudoCommand(this);
        Objects.requireNonNull(getCommand("sudo")).setExecutor(sudoCommand);
        Objects.requireNonNull(getCommand("sudo")).setTabCompleter(sudoCommand);

        LastLogoutCommand lastLogoutCommand = new LastLogoutCommand(this);
        Objects.requireNonNull(getCommand("lastlogout")).setExecutor(lastLogoutCommand);
        Objects.requireNonNull(getCommand("lastlogout")).setTabCompleter(lastLogoutCommand);

        LowCoreAdminCommand adminCommand = new LowCoreAdminCommand(this);
        Objects.requireNonNull(getCommand("lowcoreadmin")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("lowcoreadmin")).setTabCompleter(adminCommand);

        NightVisionCommand nightVisionCommand = new NightVisionCommand(this);
        Objects.requireNonNull(getCommand("nightvision")).setExecutor(nightVisionCommand);
        getServer().getPluginManager().registerEvents(nightVisionCommand, this);

        SitCommand sit = new SitCommand(this);
        getCommand("sit").setExecutor(sit);
        getServer().getPluginManager().registerEvents(sit, this);


        if (getConfig().getBoolean("update-checker.enabled", true)) checkForUpdatesNow();

        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new OfflineInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionLockListener(this, dimensionLockManager), this);
        dimensionLockManager.start();

        performanceMonitor = new PerformanceMonitor(this);
        performanceMonitor.start();
    }

    @Override
    public void onDisable() {
        if (antiFreecamManager != null) {
            antiFreecamManager.shutdown();
        }

        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }

        if (dimensionLockManager != null) {
            dimensionLockManager.stop();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("LowCore plugin disabled.");
    }

    private void loadPrefix() {
        String raw = getConfig().getString("prefix", DEFAULT_PREFIX);
        this.prefix = ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void migrateConfig() {
        // 2.4.0 used a three-second delay. Move that untouched default into
        // the early terrain-loading phase while preserving custom values.
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;

        if (diskConfig.contains("anti-freecam")
                && !diskConfig.getBoolean("anti-freecam.loading-screen-migrated", false)) {
            if (diskConfig.getLong("anti-freecam.join-delay-ticks", 60L) == 60L) {
                getConfig().set("anti-freecam.join-delay-ticks", 1L);
            }
            getConfig().set("anti-freecam.loading-screen-migrated", true);
            changed = true;
        }

        // 2.5.0 started after one tick, which some clients lost among their
        // initial chunk packets. Give login ten ticks, retry once, and erase
        // the client-only sign immediately so neither automatic attempt flashes.
        if (diskConfig.contains("anti-freecam")
                && !diskConfig.getBoolean("anti-freecam.invisible-probe-migrated", false)) {
            if (getConfig().getLong("anti-freecam.join-delay-ticks", 1L) == 1L) {
                getConfig().set("anti-freecam.join-delay-ticks", 10L);
            }
            if (getConfig().getLong("anti-freecam.close-delay-ticks", 2L) == 2L) {
                getConfig().set("anti-freecam.close-delay-ticks", 1L);
            }
            if (!diskConfig.contains("anti-freecam.join-attempts")) {
                getConfig().set("anti-freecam.join-attempts", 2);
            }
            if (!diskConfig.contains("anti-freecam.join-retry-delay-ticks")) {
                getConfig().set("anti-freecam.join-retry-delay-ticks", 10L);
            }
            getConfig().set("anti-freecam.invisible-probe-migrated", true);
            changed = true;
        }

        // 3.0 renames the public feature and preserves existing Anti-Freecam
        // timing, punishment and enabled-state settings on upgraded servers.
        if (!diskConfig.getBoolean("anti-mods.migrated", false)) {
            org.bukkit.configuration.ConfigurationSection oldSection =
                    diskConfig.getConfigurationSection("anti-freecam");
            if (oldSection != null) {
                for (String path : oldSection.getKeys(true)) {
                    if (!oldSection.isConfigurationSection(path)) {
                        getConfig().set("anti-mods." + path, oldSection.get(path));
                    }
                }
            }
            getConfig().set("anti-mods.migrated", true);
            changed = true;
        }

        if (!diskConfig.contains("update-checker.enabled") && diskConfig.contains("update-checker.enebled")) {
            getConfig().set("update-checker.enabled", diskConfig.getBoolean("update-checker.enebled", true));
            getConfig().set("update-checker.enebled", null);
            changed = true;
        }
        if (!diskConfig.contains("update-checker.notify-console") && diskConfig.contains("update-checker.notify")) {
            getConfig().set("update-checker.notify-console", diskConfig.getBoolean("update-checker.notify", true));
            getConfig().set("update-checker.notify", null);
            changed = true;
        }
        if (!diskConfig.contains("spawnmob.max-amount") && diskConfig.contains("spawnmob.max-ammount")) {
            getConfig().set("spawnmob.max-amount", diskConfig.getInt("spawnmob.max-ammount", 20));
            getConfig().set("spawnmob.max-ammount", null);
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }

    public String getPrefix() {
        return prefix != null ? prefix : ChatColor.translateAlternateColorCodes('&', DEFAULT_PREFIX);
    }

    public String getMessageRaw(String key) {
        String raw;

        if (getConfig().contains("messages." + key)) {
            raw = getConfig().getString("messages." + key);
        }

        else if (key.contains(".")) {
            String[] parts = key.split("\\.");
            if (parts.length == 2 && getConfig().contains(parts[0] + ".messages." + parts[1])) {
                raw = getConfig().getString(parts[0] + ".messages." + parts[1]);
            } else {
                raw = "&cMissing message: " + key;
            }
        }

        else {
            raw = "&cMissing message: " + key;
        }

        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String getMessage(String key) {
        return getPrefix() + getMessageRaw(key);
    }

    public String formatMessage(String key, String... placeholders) {
        String msg = getMessage(key);

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            msg = msg.replace(placeholder, placeholders[i + 1]);
        }

        return msg;
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;

        if (instance == null) {
            String full = DEFAULT_PREFIX + message;
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', full));
            return;
        }

        String full = instance.getPrefix() + message;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', full));
    }

    public static void sendConfigMessage(CommandSender sender, String key, String... placeholders) {
        if (sender == null || instance == null) return;

        String msg = instance.formatMessage(key, placeholders);
        sender.sendMessage(msg);
    }

    public void reloadLowCoreConfig(CommandSender sender) {
        reloadConfig();
        loadPrefix();
        if (antiFreecamManager != null && !antiFreecamManager.isEnabled()) {
            antiFreecamManager.shutdown();
        }
        reloadPerformanceMonitor();
        sendConfigMessage(sender, "reload");
        getLogger().info("Configuration reloaded by " + sender.getName());
        audit(sender, "Reloaded LowCore configuration");
    }

    public void audit(CommandSender actor, String action) {
        if (auditLog != null) {
            auditLog.logAction(actor, action);
        }
    }

    public void reloadPerformanceMonitor() {
        if (performanceMonitor != null) performanceMonitor.reload();
    }

    public void checkForUpdatesNow() {
        new UpdateChecker(this).checkForUpdates();
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public void setUpdateAvailable(boolean updateAvailable) {
        this.updateAvailable = updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }


    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LastLocationRepository getLastLocationRepository() {
        return lastLocationRepository;
    }

    public OfflineInventoryRepository getOfflineInventoryRepository() {
        return offlineInventoryRepository;
    }

    public WorldInventoryManager getWorldInventoryManager() {
        return worldInventoryManager;
    }

    public DimensionLockManager getDimensionLockManager() {
        return dimensionLockManager;
    }

    public AntiFreecamManager getAntiFreecamManager() {
        return antiFreecamManager;
    }

    public AntiFreecamLogRepository getAntiFreecamLogRepository() {
        return antiFreecamLogRepository;
    }
}
