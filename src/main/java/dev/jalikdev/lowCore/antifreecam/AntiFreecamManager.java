package dev.jalikdev.lowCore.antifreecam;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.database.AntiFreecamLogRepository;
import dev.jalikdev.lowCore.database.AntiFreecamLogRepository.AntiFreecamLogEntry;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Detects selected client mods through Paper's virtual-sign API. The check is
 * deliberately fail-open: missing, blocked or malformed responses never cause
 * punishment.
 */
public final class AntiFreecamManager implements Listener {

    public static final String FREECAM_KEY = "key.freecam.toggle";
    public static final String FREECAM_GUI_KEY = "freecam.config.gui.title";
    public static final String METEOR_KEY = "key.meteor-client.open-gui";
    public static final String CONTROL_KEY = "key.forward";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LowCore plugin;
    private final AntiFreecamLogRepository logRepository;
    private final BedrockPlayerDetector bedrockDetector;
    private final Map<UUID, ProbeSession> active = new HashMap<>();

    public AntiFreecamManager(LowCore plugin, AntiFreecamLogRepository logRepository) {
        this.plugin = plugin;
        this.logRepository = logRepository;
        this.bedrockDetector = new BedrockPlayerDetector(plugin);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("anti-freecam.enabled", false);
    }

    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("anti-freecam.enabled", enabled);
        plugin.saveConfig();
        if (!enabled) {
            shutdown();
        }
    }

    public Punishment getPunishment() {
        return Punishment.fromConfig(plugin.getConfig().getString("anti-freecam.punishment", "kick"));
    }

    public void setPunishment(Punishment punishment) {
        plugin.getConfig().set("anti-freecam.punishment", punishment.configName());
        plugin.saveConfig();
    }

    public boolean isChecking(UUID playerId) {
        return active.containsKey(playerId);
    }

    public boolean isBedrockPlayer(UUID playerId) {
        return bedrockDetector.isBedrockPlayer(playerId);
    }

    public boolean isBedrockPlayer(Player player) {
        if (isBedrockPlayer(player.getUniqueId())) {
            return true;
        }
        for (String configuredPrefix : plugin.getConfig()
                .getStringList("anti-freecam.bedrock-name-prefixes")) {
            String prefix = configuredPrefix.strip();
            if (!prefix.isEmpty() && player.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public List<AntiFreecamLogEntry> getRecentLogs(int limit, int offset) {
        try {
            return logRepository.findRecent(limit, offset);
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
            return List.of();
        }
    }

    public int getLogCount() {
        try {
            return logRepository.count();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
            return 0;
        }
    }

    public StartResult startManualCheck(Player target, CommandSender initiator) {
        UUID initiatorId = initiator instanceof Player player ? player.getUniqueId() : null;
        return startCheck(target, initiatorId, true, Set.of(), 0);
    }

    private StartResult startCheck(Player target, UUID initiatorId, boolean manual,
                                   Set<String> firstDetections, int automaticAttempt) {
        if (!target.isOnline()) {
            return StartResult.OFFLINE;
        }
        if (target.hasPermission("lowcore.antifreecam.bypass")) {
            return StartResult.BYPASSED;
        }
        if (isBedrockPlayer(target)) {
            return StartResult.BEDROCK;
        }
        if (active.containsKey(target.getUniqueId())) {
            return StartResult.ALREADY_RUNNING;
        }

        String nonce = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
        String freecamFallback = "LCAF" + nonce;
        String meteorFallback = "LCAM" + nonce;
        Location signLocation = findProbeLocation(target);
        ProbeSession session = new ProbeSession(
                target.getUniqueId(), initiatorId, manual, !firstDetections.isEmpty(),
                Set.copyOf(firstDetections), automaticAttempt,
                signLocation, freecamFallback, meteorFallback);
        active.put(target.getUniqueId(), session);

        try {
            target.sendBlockChange(signLocation, Material.OAK_SIGN.createBlockData());
            target.sendSignChange(signLocation, List.of(
                    Component.keybind(FREECAM_KEY),
                    Component.translatable(FREECAM_GUI_KEY, freecamFallback),
                    Component.translatable(METEOR_KEY, meteorFallback),
                    Component.keybind(CONTROL_KEY)
            ));
            target.openVirtualSign(Position.block(signLocation), Side.FRONT);

            // Restore the real client-side block immediately, before a rendered
            // frame can expose the virtual sign. The delayed close packet below
            // remains a compatibility fallback for clients that keep it open.
            restoreClientBlock(target, signLocation);
        } catch (RuntimeException exception) {
            active.remove(target.getUniqueId());
            restoreClientBlock(target, signLocation);
            plugin.getLogger().warning("Could not start anti-freecam probe for " + target.getName()
                    + ": " + exception.getMessage());
            return StartResult.FAILED;
        }

        long closeDelay = clamp(plugin.getConfig().getLong("anti-freecam.close-delay-ticks", 1L), 1L, 20L);
        session.closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.get(target.getUniqueId()) == session && target.isOnline()) {
                target.closeInventory();
            }
        }, closeDelay);

        long timeout = clamp(plugin.getConfig().getLong("anti-freecam.timeout-ticks", 40L), 10L, 200L);
        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(session), timeout);
        return StartResult.STARTED;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        if (!isEnabled() || joiningPlayer.hasPermission("lowcore.antifreecam.bypass")
                || isBedrockPlayer(joiningPlayer)) {
            return;
        }
        // A short delay keeps the probe inside the terrain-loading phase while
        // allowing the client's initial world/chunk packets to settle first.
        long delay = clamp(plugin.getConfig().getLong("anti-freecam.join-delay-ticks", 10L), 1L, 200L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && isEnabled() && !isBedrockPlayer(player)) {
                startCheck(player, null, false, Set.of(), 1);
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUncheckedSignChange(UncheckedSignChangeEvent event) {
        ProbeSession session = active.get(event.getPlayer().getUniqueId());
        if (session == null || !samePosition(session.location, event.getEditedBlockPosition())) {
            return;
        }

        event.setCancelled(true);
        finishTasks(session);
        active.remove(session.playerId);
        restoreClientBlock(event.getPlayer(), session.location);

        List<Component> components = event.lines();
        String[] lines = new String[4];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = index < components.size() ? PLAIN.serialize(components.get(index)).strip() : "";
        }

        ProbeEvaluation evaluation = evaluateResponses(
                lines, session.freecamFallback, session.meteorFallback);
        logResult(event.getPlayer(), evaluation, lines);

        if (evaluation.protectedResponse()) {
            saveLog(session, event.getPlayer(), "PROTECTED", Set.of(), "none",
                    "Client filtered the control key response");
            notifyResult(session, "anti-freecam.protected", event.getPlayer(), "");
            return;
        }

        Set<String> detected = evaluation.detectedMods();
        if (detected.isEmpty()) {
            if (session.confirmation) {
                saveLog(session, event.getPlayer(), "INCONCLUSIVE", session.firstDetections,
                        "none", "First result did not repeat during confirmation");
                notifyResult(session, "anti-freecam.inconclusive", event.getPlayer(), "");
            } else {
                saveLog(session, event.getPlayer(), "CLEAN", Set.of(), "none",
                        "No configured translation keys matched");
            }
            if (session.manual && !session.confirmation) {
                notifyResult(session, "anti-freecam.clean", event.getPlayer(), "");
            }
            return;
        }

        boolean doubleCheck = plugin.getConfig().getBoolean("anti-freecam.double-check", true);
        if (doubleCheck && !session.confirmation) {
            notifyInitiator(session, "anti-freecam.confirming", event.getPlayer(), joinMods(detected));
            long confirmationDelay = clamp(
                    plugin.getConfig().getLong("anti-freecam.confirmation-delay-ticks", 2L), 1L, 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(session.playerId);
                if (target != null && target.isOnline()) {
                    startCheck(target, session.initiatorId, session.manual, detected, session.automaticAttempt);
                }
            }, confirmationDelay);
            return;
        }

        Set<String> confirmed = new HashSet<>(detected);
        if (session.confirmation) {
            confirmed.retainAll(session.firstDetections);
        }
        if (confirmed.isEmpty()) {
            saveLog(session, event.getPlayer(), "INCONCLUSIVE", session.firstDetections,
                    "none", "Detected mods differed between the first and confirmation probes");
            notifyResult(session, "anti-freecam.inconclusive", event.getPlayer(), "");
            return;
        }

        if (!session.manual && !isEnabled()) {
            return;
        }

        handleDetection(event.getPlayer(), session, confirmed);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ProbeSession session = active.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            finishTasks(session);
        }
    }

    private void timeout(ProbeSession session) {
        if (active.remove(session.playerId) != session) {
            return;
        }
        finishTasks(session);
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) {
            restoreClientBlock(player, session.location);
            int maximumAttempts = (int) clamp(
                    plugin.getConfig().getLong("anti-freecam.join-attempts", 2L), 1L, 3L);
            if (!session.manual && !session.confirmation && session.automaticAttempt < maximumAttempts
                    && isEnabled() && !isBedrockPlayer(player)) {
                long retryDelay = clamp(
                        plugin.getConfig().getLong("anti-freecam.join-retry-delay-ticks", 10L), 1L, 100L);
                plugin.getLogger().info("Retrying early anti-freecam join probe for " + player.getName()
                        + " (attempt " + (session.automaticAttempt + 1) + "/" + maximumAttempts + ")");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && isEnabled() && !isBedrockPlayer(player)) {
                        startCheck(player, null, false, Set.of(), session.automaticAttempt + 1);
                    }
                }, retryDelay);
                return;
            }
            saveLog(session, player, session.confirmation ? "INCONCLUSIVE" : "TIMEOUT",
                    session.firstDetections, "none", "The client did not return a sign response in time");
            if (session.manual) {
                notifyResult(session, session.confirmation
                        ? "anti-freecam.inconclusive" : "anti-freecam.timeout", player, "");
            }
        }
    }

    private void handleDetection(Player target, ProbeSession session, Set<String> mods) {
        String modNames = joinMods(mods);
        Punishment punishment = getPunishment();
        saveLog(session, target, "DETECTED", mods, punishment.configName(),
                "Match confirmed by translation-key probes");
        notifyResult(session, "anti-freecam.detected", target, modNames);

        if (punishment == Punishment.NOTIFY) {
            return;
        }

        String reason = color(plugin.getConfig().getString("anti-freecam.messages.kick-reason",
                "&cDisallowed client modification detected: &e%mods%"))
                .replace("%mods%", modNames);
        if (punishment == Punishment.BAN) {
            target.ban(reason, (Instant) null, "LowCore Anti-Freecam", true);
        } else {
            target.kick(LEGACY.deserialize(reason));
        }
    }

    private void notifyResult(ProbeSession session, String messageKey, Player target, String mods) {
        String message = plugin.formatMessage(messageKey,
                "player", target.getName(), "mods", mods);
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lowcore.antifreecam.alerts")) {
                player.sendMessage(message);
            }
        }
        notifyInitiatorRaw(session, message);
    }

    private void notifyInitiator(ProbeSession session, String messageKey, Player target, String mods) {
        notifyInitiatorRaw(session, plugin.formatMessage(messageKey,
                "player", target.getName(), "mods", mods));
    }

    private void notifyInitiatorRaw(ProbeSession session, String message) {
        if (session.initiatorId == null) {
            return;
        }
        Player initiator = Bukkit.getPlayer(session.initiatorId);
        if (initiator != null && initiator.isOnline()
                && !initiator.hasPermission("lowcore.antifreecam.alerts")) {
            initiator.sendMessage(message);
        }
    }

    private void logResult(Player player, ProbeEvaluation evaluation, String[] lines) {
        String[] safeLines = new String[lines.length];
        for (int index = 0; index < lines.length; index++) {
            safeLines[index] = sanitizeForLog(lines[index]);
        }
        plugin.getLogger().info("Anti-freecam probe for " + player.getName()
                + ": detected=" + joinMods(evaluation.detectedMods())
                + ", protected=" + evaluation.protectedResponse()
                + ", responses=[" + String.join(" | ", safeLines) + "]");
    }

    private void saveLog(ProbeSession session, Player player, String result, Set<String> mods,
                         String punishment, String details) {
        try {
            logRepository.save(player.getUniqueId(), player.getName(), result, joinMods(mods),
                    session.manual ? "manual" : "automatic", punishment, details);
            logRepository.trimTo(plugin.getConfig().getInt("anti-freecam.log-max-entries", 5000));
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }

    private Location findProbeLocation(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        int minimum = player.getWorld().getMinHeight() + 1;
        int y = Math.max(minimum, base.getBlockY() - 4);
        return new Location(player.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }

    private void restoreClientBlock(Player player, Location location) {
        if (!player.isOnline() || player.getWorld() != location.getWorld()) {
            return;
        }
        player.sendBlockChange(location, location.getBlock().getBlockData());
        if (location.getBlock().getState() instanceof TileState tileState) {
            player.sendBlockUpdate(location, tileState);
        }
    }

    private void finishTasks(ProbeSession session) {
        if (session.closeTask != null) {
            session.closeTask.cancel();
        }
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }
    }

    private boolean samePosition(Location location, BlockPosition position) {
        return location.getBlockX() == position.blockX()
                && location.getBlockY() == position.blockY()
                && location.getBlockZ() == position.blockZ();
    }

    public static ProbeEvaluation evaluateResponses(String[] lines, String freecamFallback,
                                                     String meteorFallback) {
        String freecamKeybind = response(lines, 0);
        String freecamTitle = response(lines, 1);
        String meteor = response(lines, 2);
        String control = response(lines, 3);

        boolean protectedResponse = control.equalsIgnoreCase(CONTROL_KEY);
        Set<String> detected = new HashSet<>();
        if (!protectedResponse) {
            if (!freecamKeybind.isEmpty() && !freecamKeybind.equalsIgnoreCase(FREECAM_KEY)) {
                detected.add("Freecam");
            }
            if (!freecamTitle.isEmpty()
                    && !freecamTitle.equalsIgnoreCase(freecamFallback)
                    && !freecamTitle.equalsIgnoreCase(FREECAM_GUI_KEY)) {
                detected.add("Freecam");
            }
            if (!meteor.isEmpty() && !meteor.equalsIgnoreCase(meteorFallback)) {
                detected.add("Meteor Client");
            }
        }
        return new ProbeEvaluation(Set.copyOf(detected), protectedResponse);
    }

    private static String response(String[] lines, int index) {
        if (lines == null || index >= lines.length || lines[index] == null) {
            return "";
        }
        return lines[index].strip();
    }

    private static String joinMods(Set<String> mods) {
        return mods.isEmpty() ? "none" : String.join(", ", mods.stream().sorted().toList());
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }

    private static String sanitizeForLog(String value) {
        String safe = value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "?");
        return safe.length() <= 80 ? safe : safe.substring(0, 80) + "...";
    }

    public void shutdown() {
        for (ProbeSession session : active.values()) {
            finishTasks(session);
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null) {
                restoreClientBlock(player, session.location);
            }
        }
        active.clear();
    }

    public enum Punishment {
        NOTIFY("notify", "Notify only"),
        KICK("kick", "Kick"),
        BAN("ban", "Ban");

        private final String configName;
        private final String displayName;

        Punishment(String configName, String displayName) {
            this.configName = configName;
            this.displayName = displayName;
        }

        public String configName() {
            return configName;
        }

        public String displayName() {
            return displayName;
        }

        public Punishment next() {
            Punishment[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Punishment fromConfig(String value) {
            if (value != null) {
                for (Punishment punishment : values()) {
                    if (punishment.configName.equalsIgnoreCase(value)) {
                        return punishment;
                    }
                }
            }
            return KICK;
        }
    }

    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        BYPASSED,
        BEDROCK,
        OFFLINE,
        FAILED
    }

    public record ProbeEvaluation(Set<String> detectedMods, boolean protectedResponse) {
    }

    private static final class ProbeSession {
        private final UUID playerId;
        private final UUID initiatorId;
        private final boolean manual;
        private final boolean confirmation;
        private final Set<String> firstDetections;
        private final int automaticAttempt;
        private final Location location;
        private final String freecamFallback;
        private final String meteorFallback;
        private BukkitTask closeTask;
        private BukkitTask timeoutTask;

        private ProbeSession(UUID playerId, UUID initiatorId, boolean manual, boolean confirmation,
                             Set<String> firstDetections, int automaticAttempt, Location location,
                             String freecamFallback, String meteorFallback) {
            this.playerId = playerId;
            this.initiatorId = initiatorId;
            this.manual = manual;
            this.confirmation = confirmation;
            this.firstDetections = firstDetections;
            this.automaticAttempt = automaticAttempt;
            this.location = location;
            this.freecamFallback = freecamFallback;
            this.meteorFallback = meteorFallback;
        }
    }
}
