package dev.jalikdev.lowCore.antifreecam;

import dev.jalikdev.lowCore.LowCore;
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
    private final Map<UUID, ProbeSession> active = new HashMap<>();

    public AntiFreecamManager(LowCore plugin) {
        this.plugin = plugin;
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

    public StartResult startManualCheck(Player target, CommandSender initiator) {
        UUID initiatorId = initiator instanceof Player player ? player.getUniqueId() : null;
        return startCheck(target, initiatorId, true, Set.of());
    }

    private StartResult startCheck(Player target, UUID initiatorId, boolean manual, Set<String> firstDetections) {
        if (!target.isOnline()) {
            return StartResult.OFFLINE;
        }
        if (target.hasPermission("lowcore.antifreecam.bypass")) {
            return StartResult.BYPASSED;
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
                Set.copyOf(firstDetections), signLocation, freecamFallback, meteorFallback);
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
        } catch (RuntimeException exception) {
            active.remove(target.getUniqueId());
            restoreClientBlock(target, signLocation);
            plugin.getLogger().warning("Could not start anti-freecam probe for " + target.getName()
                    + ": " + exception.getMessage());
            return StartResult.FAILED;
        }

        long closeDelay = clamp(plugin.getConfig().getLong("anti-freecam.close-delay-ticks", 2L), 1L, 20L);
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
        if (!isEnabled() || event.getPlayer().hasPermission("lowcore.antifreecam.bypass")) {
            return;
        }
        long delay = clamp(plugin.getConfig().getLong("anti-freecam.join-delay-ticks", 60L), 20L, 1200L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && isEnabled()) {
                startCheck(player, null, false, Set.of());
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
            notifyResult(session, "anti-freecam.protected", event.getPlayer(), "");
            return;
        }

        Set<String> detected = evaluation.detectedMods();
        if (detected.isEmpty()) {
            if (session.manual) {
                notifyResult(session, "anti-freecam.clean", event.getPlayer(), "");
            }
            return;
        }

        boolean doubleCheck = plugin.getConfig().getBoolean("anti-freecam.double-check", true);
        if (doubleCheck && !session.confirmation) {
            notifyInitiator(session, "anti-freecam.confirming", event.getPlayer(), joinMods(detected));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(session.playerId);
                if (target != null && target.isOnline()) {
                    startCheck(target, session.initiatorId, session.manual, detected);
                }
            }, 10L);
            return;
        }

        Set<String> confirmed = new HashSet<>(detected);
        if (session.confirmation) {
            confirmed.retainAll(session.firstDetections);
        }
        if (confirmed.isEmpty()) {
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
            if (session.manual) {
                notifyResult(session, "anti-freecam.timeout", player, "");
            }
        }
    }

    private void handleDetection(Player target, ProbeSession session, Set<String> mods) {
        String modNames = joinMods(mods);
        notifyResult(session, "anti-freecam.detected", target, modNames);

        Punishment punishment = getPunishment();
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
        private final Location location;
        private final String freecamFallback;
        private final String meteorFallback;
        private BukkitTask closeTask;
        private BukkitTask timeoutTask;

        private ProbeSession(UUID playerId, UUID initiatorId, boolean manual, boolean confirmation,
                             Set<String> firstDetections, Location location,
                             String freecamFallback, String meteorFallback) {
            this.playerId = playerId;
            this.initiatorId = initiatorId;
            this.manual = manual;
            this.confirmation = confirmation;
            this.firstDetections = firstDetections;
            this.location = location;
            this.freecamFallback = freecamFallback;
            this.meteorFallback = meteorFallback;
        }
    }
}
