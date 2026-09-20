package dev.jalikdev.lowCore.antifreecam;

import dev.jalikdev.lowCore.LowCore;
import dev.jalikdev.lowCore.antifreecam.AntiModClient.ProbeSignature;
import dev.jalikdev.lowCore.antifreecam.AntiModClient.SignatureType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Translation-key based mod detection. All failures are deliberately fail-open. */
public final class AntiFreecamManager implements Listener {

    public static final String CONTROL_KEY = "key.forward";
    private static final int SIGNATURES_PER_PROBE = 3;
    private static final List<ProbeSignature> SIGNATURES = AntiModClient.allSignatures();
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
        return plugin.getConfig().getBoolean("anti-mods.enabled", false);
    }

    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("anti-mods.enabled", enabled);
        plugin.saveConfig();
        if (!enabled) shutdown();
    }

    public Punishment getPunishment() {
        return Punishment.fromConfig(plugin.getConfig().getString("anti-mods.punishment", "kick"));
    }

    public void setPunishment(Punishment punishment) {
        plugin.getConfig().set("anti-mods.punishment", punishment.configName());
        plugin.saveConfig();
    }

    public List<AntiModClient> getClients() {
        return List.of(AntiModClient.values());
    }

    public boolean isClientBlocked(AntiModClient client) {
        return plugin.getConfig().getBoolean("anti-mods.clients." + client.id() + ".blocked", true);
    }

    public void setClientBlocked(AntiModClient client, boolean blocked) {
        plugin.getConfig().set("anti-mods.clients." + client.id() + ".blocked", blocked);
        plugin.saveConfig();
    }

    public boolean isDoubleCheckEnabled() {
        return plugin.getConfig().getBoolean("anti-mods.double-check", true);
    }

    public void setDoubleCheckEnabled(boolean enabled) {
        plugin.getConfig().set("anti-mods.double-check", enabled);
        plugin.saveConfig();
    }

    public boolean isOpBypassEnabled() {
        return plugin.getConfig().getBoolean("anti-mods.bypass.ops", true);
    }

    public void setOpBypassEnabled(boolean enabled) {
        plugin.getConfig().set("anti-mods.bypass.ops", enabled);
        plugin.saveConfig();
    }

    public String getBypassPermission() {
        return plugin.getConfig().getString("anti-mods.bypass.permission", "lowcore.antimods.bypass");
    }

    public void setBypassPermission(String permission) {
        plugin.getConfig().set("anti-mods.bypass.permission", permission);
        plugin.saveConfig();
    }

    public int getMaxLogPages() {
        return (int) clamp(plugin.getConfig().getLong("anti-mods.logs.max-pages", 5L), 1L, 20L);
    }

    public void setMaxLogPages(int pages) {
        plugin.getConfig().set("anti-mods.logs.max-pages", (int) clamp(pages, 1, 20));
        plugin.saveConfig();
        trimLogs();
    }

    public int getLogPageSize() {
        return 36;
    }

    public boolean isChecking(UUID playerId) {
        return active.containsKey(playerId);
    }

    public boolean isBedrockPlayer(UUID playerId) {
        return bedrockDetector.isBedrockPlayer(playerId);
    }

    public boolean isBedrockPlayer(Player player) {
        if (isBedrockPlayer(player.getUniqueId())) return true;
        for (String configuredPrefix : plugin.getConfig().getStringList("anti-mods.bedrock-name-prefixes")) {
            String prefix = configuredPrefix.strip();
            if (!prefix.isEmpty() && player.getName().startsWith(prefix)) return true;
        }
        return false;
    }

    public boolean isBypassed(Player player) {
        if (isOpBypassEnabled() && player.isOp()) return true;
        String permission = getBypassPermission().strip();
        return (!permission.isEmpty() && player.hasPermission(permission))
                || player.hasPermission("lowcore.antifreecam.bypass");
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

    public int clearLogs() {
        try {
            return logRepository.clear();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
            return 0;
        }
    }

    public void audit(CommandSender actor, String action) {
        plugin.audit(actor, action);
    }

    public StartResult startManualCheck(Player target, CommandSender initiator) {
        UUID initiatorId = initiator instanceof Player player ? player.getUniqueId() : null;
        return startProbe(target, initiatorId, true, false, Set.of(), Set.of(), 0, 0);
    }

    private StartResult startProbe(Player target, UUID initiatorId, boolean manual, boolean confirmation,
                                   Set<AntiModClient> firstDetections, Set<AntiModClient> roundDetections,
                                   int automaticAttempt, int batchIndex) {
        if (!target.isOnline()) return StartResult.OFFLINE;
        if (isBypassed(target)) return StartResult.BYPASSED;
        if (isBedrockPlayer(target)) return StartResult.BEDROCK;
        if (active.containsKey(target.getUniqueId())) return StartResult.ALREADY_RUNNING;

        int from = batchIndex * SIGNATURES_PER_PROBE;
        int to = Math.min(from + SIGNATURES_PER_PROBE, SIGNATURES.size());
        if (from >= to) return StartResult.FAILED;
        List<ProbeSignature> batch = List.copyOf(SIGNATURES.subList(from, to));
        String nonce = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
        List<String> fallbacks = new ArrayList<>(batch.size());
        for (int index = 0; index < batch.size(); index++) fallbacks.add("LCM" + index + nonce);

        Location signLocation = findProbeLocation(target);
        ProbeSession session = new ProbeSession(target.getUniqueId(), initiatorId, manual, confirmation,
                Set.copyOf(firstDetections), Set.copyOf(roundDetections), automaticAttempt, batchIndex,
                batch, List.copyOf(fallbacks), signLocation);
        active.put(target.getUniqueId(), session);

        try {
            target.sendBlockChange(signLocation, Material.OAK_SIGN.createBlockData());
            List<Component> lines = new ArrayList<>(4);
            for (int index = 0; index < SIGNATURES_PER_PROBE; index++) {
                if (index >= batch.size()) {
                    lines.add(Component.empty());
                } else {
                    ProbeSignature signature = batch.get(index);
                    lines.add(signature.type() == SignatureType.KEYBIND
                            ? Component.keybind(signature.key())
                            : Component.translatable(signature.key(), fallbacks.get(index)));
                }
            }
            lines.add(Component.keybind(CONTROL_KEY));
            target.sendSignChange(signLocation, lines);
            target.openVirtualSign(Position.block(signLocation), Side.FRONT);
            restoreClientBlock(target, signLocation);
        } catch (RuntimeException exception) {
            active.remove(target.getUniqueId());
            restoreClientBlock(target, signLocation);
            plugin.getLogger().warning("Could not start anti-mod probe for " + target.getName()
                    + ": " + exception.getMessage());
            return StartResult.FAILED;
        }

        long closeDelay = clamp(plugin.getConfig().getLong("anti-mods.close-delay-ticks", 1L), 1L, 20L);
        session.closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.get(target.getUniqueId()) == session && target.isOnline()) target.closeInventory();
        }, closeDelay);
        long timeout = clamp(plugin.getConfig().getLong("anti-mods.timeout-ticks", 40L), 10L, 200L);
        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(session), timeout);
        return StartResult.STARTED;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled() || isBypassed(player) || isBedrockPlayer(player)) return;
        long delay = clamp(plugin.getConfig().getLong("anti-mods.join-delay-ticks", 10L), 1L, 200L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isEnabled() && !isBypassed(player) && !isBedrockPlayer(player)) {
                startProbe(player, null, false, false, Set.of(), Set.of(), 1, 0);
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUncheckedSignChange(UncheckedSignChangeEvent event) {
        ProbeSession session = active.get(event.getPlayer().getUniqueId());
        if (session == null || !samePosition(session.location, event.getEditedBlockPosition())) return;

        event.setCancelled(true);
        finishTasks(session);
        active.remove(session.playerId);
        restoreClientBlock(event.getPlayer(), session.location);

        String[] lines = new String[4];
        List<Component> components = event.lines();
        for (int index = 0; index < lines.length; index++) {
            lines[index] = index < components.size() ? PLAIN.serialize(components.get(index)).strip() : "";
        }

        ProbeEvaluation evaluation = evaluateResponses(lines, session.batch, session.fallbacks);
        logProbe(event.getPlayer(), evaluation, lines, session.batchIndex);
        if (evaluation.protectedResponse()) {
            saveLog(session, event.getPlayer(), "PROTECTED", Set.of(), "none",
                    "Client filtered the control key response");
            notifyResult(session, "anti-mods.protected", event.getPlayer(), "");
            return;
        }

        Set<AntiModClient> combined = new HashSet<>(session.roundDetections);
        combined.addAll(evaluation.detectedClients());
        int nextBatch = session.batchIndex + 1;
        if (nextBatch * SIGNATURES_PER_PROBE < SIGNATURES.size()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(session.playerId);
                if (target != null && target.isOnline()) {
                    startProbe(target, session.initiatorId, session.manual, session.confirmation,
                            session.firstDetections, combined, session.automaticAttempt, nextBatch);
                }
            }, 1L);
            return;
        }
        finishRound(event.getPlayer(), session, combined);
    }

    private void finishRound(Player player, ProbeSession session, Set<AntiModClient> detected) {
        if (detected.isEmpty()) {
            if (session.confirmation) {
                saveLog(session, player, "INCONCLUSIVE", session.firstDetections, "none",
                        "First result did not repeat during confirmation");
                notifyResult(session, "anti-mods.inconclusive", player, "");
            } else {
                saveLog(session, player, "CLEAN", Set.of(), "none", "No configured translation keys matched");
                if (session.manual) notifyResult(session, "anti-mods.clean", player, "");
            }
            return;
        }

        if (isDoubleCheckEnabled() && !session.confirmation) {
            notifyInitiator(session, "anti-mods.confirming", player, joinClients(detected));
            long delay = clamp(plugin.getConfig().getLong("anti-mods.confirmation-delay-ticks", 2L), 1L, 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player target = Bukkit.getPlayer(session.playerId);
                if (target != null && target.isOnline()) {
                    startProbe(target, session.initiatorId, session.manual, true,
                            detected, Set.of(), session.automaticAttempt, 0);
                }
            }, delay);
            return;
        }

        Set<AntiModClient> confirmed = new HashSet<>(detected);
        if (session.confirmation) confirmed.retainAll(session.firstDetections);
        if (confirmed.isEmpty()) {
            saveLog(session, player, "INCONCLUSIVE", session.firstDetections, "none",
                    "Detected clients differed between the first and confirmation rounds");
            notifyResult(session, "anti-mods.inconclusive", player, "");
            return;
        }
        if (!session.manual && !isEnabled()) return;
        handleDetection(player, session, confirmed);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ProbeSession session = active.remove(event.getPlayer().getUniqueId());
        if (session != null) finishTasks(session);
    }

    private void timeout(ProbeSession session) {
        if (active.remove(session.playerId) != session) return;
        finishTasks(session);
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null) return;
        restoreClientBlock(player, session.location);
        int attempts = (int) clamp(plugin.getConfig().getLong("anti-mods.join-attempts", 2L), 1L, 3L);
        if (!session.manual && !session.confirmation && session.automaticAttempt < attempts
                && isEnabled() && !isBypassed(player) && !isBedrockPlayer(player)) {
            long delay = clamp(plugin.getConfig().getLong("anti-mods.join-retry-delay-ticks", 10L), 1L, 100L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isEnabled() && !isBypassed(player) && !isBedrockPlayer(player)) {
                    startProbe(player, null, false, false, Set.of(), Set.of(),
                            session.automaticAttempt + 1, 0);
                }
            }, delay);
            return;
        }
        saveLog(session, player, session.confirmation ? "INCONCLUSIVE" : "TIMEOUT",
                session.firstDetections, "none", "The client did not return a sign response in time");
        if (session.manual) {
            notifyResult(session, session.confirmation ? "anti-mods.inconclusive" : "anti-mods.timeout", player, "");
        }
    }

    private void handleDetection(Player target, ProbeSession session, Set<AntiModClient> detected) {
        Set<AntiModClient> blocked = detected.stream().filter(this::isClientBlocked)
                .collect(java.util.stream.Collectors.toSet());
        if (blocked.isEmpty()) {
            saveLog(session, target, "ALLOWED", detected, "none", "Detected clients are allowed by configuration");
            notifyResult(session, "anti-mods.allowed-detected", target, joinClients(detected));
            return;
        }

        String blockedNames = joinClients(blocked);
        Punishment punishment = getPunishment();
        saveLog(session, target, "DETECTED", detected, punishment.configName(), "Blocked clients: " + blockedNames);
        notifyResult(session, "anti-mods.detected", target, blockedNames);
        if (punishment == Punishment.NOTIFY) return;

        String reason = color(plugin.getConfig().getString("anti-mods.messages.kick-reason",
                "&cDisallowed client modification detected: &e%mods%")).replace("%mods%", blockedNames);
        if (punishment == Punishment.BAN) {
            target.ban(reason, (Instant) null, "LowCore Anti-Mods", true);
        } else {
            target.kick(LEGACY.deserialize(reason));
        }
    }

    private void notifyResult(ProbeSession session, String key, Player target, String mods) {
        String message = plugin.formatMessage(key, "player", target.getName(), "mods", mods);
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lowcore.antimods.alerts")) player.sendMessage(message);
        }
        notifyInitiatorRaw(session, message);
    }

    private void notifyInitiator(ProbeSession session, String key, Player target, String mods) {
        notifyInitiatorRaw(session, plugin.formatMessage(key, "player", target.getName(), "mods", mods));
    }

    private void notifyInitiatorRaw(ProbeSession session, String message) {
        if (session.initiatorId == null) return;
        Player initiator = Bukkit.getPlayer(session.initiatorId);
        if (initiator != null && initiator.isOnline() && !initiator.hasPermission("lowcore.antimods.alerts")) {
            initiator.sendMessage(message);
        }
    }

    private void saveLog(ProbeSession session, Player player, String result, Set<AntiModClient> clients,
                         String punishment, String details) {
        try {
            logRepository.save(player.getUniqueId(), player.getName(), result, joinClients(clients),
                    session.manual ? "manual" : "automatic", punishment, details);
            trimLogs();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }

    private void trimLogs() {
        try {
            logRepository.trimTo(getLogPageSize() * getMaxLogPages());
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }

    private void logProbe(Player player, ProbeEvaluation evaluation, String[] lines, int batch) {
        String[] safe = new String[lines.length];
        for (int index = 0; index < lines.length; index++) safe[index] = sanitizeForLog(lines[index]);
        plugin.getLogger().info("Anti-mod probe " + (batch + 1) + " for " + player.getName()
                + ": detected=" + joinClients(evaluation.detectedClients())
                + ", protected=" + evaluation.protectedResponse() + ", lines=" + String.join(" | ", safe));
    }

    private Location findProbeLocation(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        int y = Math.max(player.getWorld().getMinHeight() + 1, base.getBlockY() - 4);
        return new Location(player.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }

    private void restoreClientBlock(Player player, Location location) {
        if (!player.isOnline() || player.getWorld() != location.getWorld()) return;
        player.sendBlockChange(location, location.getBlock().getBlockData());
        if (location.getBlock().getState() instanceof TileState tileState) player.sendBlockUpdate(location, tileState);
    }

    private void finishTasks(ProbeSession session) {
        if (session.closeTask != null) session.closeTask.cancel();
        if (session.timeoutTask != null) session.timeoutTask.cancel();
    }

    private boolean samePosition(Location location, BlockPosition position) {
        return location.getBlockX() == position.blockX()
                && location.getBlockY() == position.blockY()
                && location.getBlockZ() == position.blockZ();
    }

    public static ProbeEvaluation evaluateResponses(String[] lines, List<ProbeSignature> signatures,
                                                     List<String> fallbacks) {
        boolean protectedResponse = response(lines, 3).equalsIgnoreCase(CONTROL_KEY);
        Set<AntiModClient> detected = new HashSet<>();
        if (!protectedResponse) {
            for (int index = 0; index < signatures.size(); index++) {
                ProbeSignature signature = signatures.get(index);
                String value = response(lines, index);
                if (value.isEmpty()) continue;
                boolean matched = switch (signature.type()) {
                    case KEYBIND -> !value.equalsIgnoreCase(signature.key());
                    case TRANSLATABLE -> !value.equalsIgnoreCase(fallbacks.get(index))
                            && !value.equalsIgnoreCase(signature.key());
                    case TRANSLATABLE_RAW_SIGNAL -> !value.equalsIgnoreCase(fallbacks.get(index));
                };
                if (matched) detected.add(signature.client());
            }
        }
        return new ProbeEvaluation(Set.copyOf(detected), protectedResponse);
    }

    private static String response(String[] lines, int index) {
        if (lines == null || index >= lines.length || lines[index] == null) return "";
        return lines[index].strip();
    }

    private static String joinClients(Set<AntiModClient> clients) {
        return clients.isEmpty() ? "none" : String.join(", ", clients.stream()
                .map(AntiModClient::displayName).sorted().toList());
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
            if (player != null) restoreClientBlock(player, session.location);
        }
        active.clear();
    }

    public enum Punishment {
        NOTIFY("notify", "Notify only"), KICK("kick", "Kick"), BAN("ban", "Ban");
        private final String configName;
        private final String displayName;
        Punishment(String configName, String displayName) {
            this.configName = configName;
            this.displayName = displayName;
        }
        public String configName() { return configName; }
        public String displayName() { return displayName; }
        public Punishment next() { return values()[(ordinal() + 1) % values().length]; }
        public static Punishment fromConfig(String value) {
            if (value != null) {
                for (Punishment punishment : values()) {
                    if (punishment.configName.equalsIgnoreCase(value)) return punishment;
                }
            }
            return KICK;
        }
    }

    public enum StartResult { STARTED, ALREADY_RUNNING, BYPASSED, BEDROCK, OFFLINE, FAILED }

    public record ProbeEvaluation(Set<AntiModClient> detectedClients, boolean protectedResponse) {}

    private static final class ProbeSession {
        private final UUID playerId;
        private final UUID initiatorId;
        private final boolean manual;
        private final boolean confirmation;
        private final Set<AntiModClient> firstDetections;
        private final Set<AntiModClient> roundDetections;
        private final int automaticAttempt;
        private final int batchIndex;
        private final List<ProbeSignature> batch;
        private final List<String> fallbacks;
        private final Location location;
        private BukkitTask closeTask;
        private BukkitTask timeoutTask;

        private ProbeSession(UUID playerId, UUID initiatorId, boolean manual, boolean confirmation,
                             Set<AntiModClient> firstDetections, Set<AntiModClient> roundDetections,
                             int automaticAttempt, int batchIndex, List<ProbeSignature> batch,
                             List<String> fallbacks, Location location) {
            this.playerId = playerId;
            this.initiatorId = initiatorId;
            this.manual = manual;
            this.confirmation = confirmation;
            this.firstDetections = firstDetections;
            this.roundDetections = roundDetections;
            this.automaticAttempt = automaticAttempt;
            this.batchIndex = batchIndex;
            this.batch = batch;
            this.fallbacks = fallbacks;
            this.location = location;
        }
    }
}
