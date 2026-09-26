package dev.jalikdev.lowCore.stasis;

import dev.jalikdev.lowCore.LowCore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class StasisManager implements Listener {

    private static final String HOLDER_TAG = "lowcore_stasis_holder";
    private static final int TRACKING_TIMEOUT_TICKS = 400;
    private static final int REQUIRED_STABLE_TICKS = 2;

    private final LowCore plugin;
    private final File storageFile;
    private final NamespacedKey rodKey;
    private final NamespacedKey pendingRodKey;
    private final NamespacedKey holderKey;
    private final Map<UUID, StasisBinding> bindings = new HashMap<>();
    private final Map<UUID, PendingCast> pendingCasts = new HashMap<>();
    private final Set<UUID> suppressedHolderRemovals = new HashSet<>();
    private BukkitTask trackingTask;

    public StasisManager(LowCore plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "stasis.yml");
        this.rodKey = new NamespacedKey(plugin, "stasis_id");
        this.pendingRodKey = new NamespacedKey(plugin, "stasis_pending_id");
        this.holderKey = new NamespacedKey(plugin, "stasis_holder_id");
        loadBindings();
    }

    public void initializeLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isEntitiesLoaded()) reconcileChunk(chunk);
            }
        }
    }

    public void shutdown() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
        for (PendingCast pending : pendingCasts.values()) {
            clearPendingRod(pending.ownerId(), pending.pendingId());
        }
        pendingCasts.clear();
        saveBindings();
    }

    public Collection<StasisBinding> getBindings() {
        return Collections.unmodifiableCollection(new ArrayList<>(bindings.values()));
    }

    public UUID resolveBindingId(String input) {
        try {
            UUID exact = UUID.fromString(input);
            return bindings.containsKey(exact) ? exact : null;
        } catch (IllegalArgumentException ignored) {
            UUID match = null;
            String prefix = input.toLowerCase(Locale.ROOT);
            for (UUID id : bindings.keySet()) {
                if (!id.toString().startsWith(prefix)) continue;
                if (match != null) return null;
                match = id;
            }
            return match;
        }
    }

    public TriggerResult removeBinding(UUID id) {
        StasisBinding binding = bindings.get(id);
        if (binding == null) return TriggerResult.NOT_FOUND;
        return releaseBinding(binding);
    }

    public CleanupResult cleanupLoadedChunks() {
        int removed = 0;
        int recreated = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!chunk.isEntitiesLoaded()) continue;
                CleanupResult result = reconcileChunk(chunk);
                removed += result.removed();
                recreated += result.recreated();
            }
        }
        return new CleanupResult(removed, recreated);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack rod = player.getInventory().getItem(hand);
        if (rod.getType() != Material.FISHING_ROD) return;

        UUID stasisId = getUuid(rod, rodKey);
        if (stasisId != null) {
            event.setCancelled(true);
            event.getHook().remove();
            if (!player.hasPermission("lowcore.stasis.use")) {
                LowCore.sendConfigMessage(player, "no-permission");
                return;
            }

            TriggerResult result = removeBinding(stasisId);
            removeUuid(rod, rodKey);
            player.getInventory().setItem(hand, rod);
            if (result == TriggerResult.TRIGGERED) {
                LowCore.sendConfigMessage(player, "stasis.triggered");
            } else {
                LowCore.sendConfigMessage(player, "stasis.unavailable");
            }
            return;
        }

        if (!player.hasPermission("lowcore.stasis.use")) return;

        UUID pendingId = UUID.randomUUID();
        setUuid(rod, pendingRodKey, pendingId);
        player.getInventory().setItem(hand, rod);
        pendingCasts.put(event.getHook().getUniqueId(), new PendingCast(
                event.getHook(), player.getUniqueId(), pendingId, 0, null, 0));
        startTrackingTask();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Iterator<Map.Entry<UUID, PendingCast>> iterator = pendingCasts.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingCast pending = iterator.next().getValue();
            if (!pending.ownerId().equals(event.getPlayer().getUniqueId())) continue;
            clearPendingRod(pending.ownerId(), pending.pendingId());
            iterator.remove();
        }
        stopTrackingTaskIfIdle();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded() && chunk.isEntitiesLoaded()) reconcileChunk(chunk);
        });
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        reconcileChunk(event.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) {
            if (chunk.isEntitiesLoaded()) reconcileChunk(chunk);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderDamage(EntityDamageEvent event) {
        if (isHolder(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderManipulate(PlayerArmorStandManipulateEvent event) {
        if (isHolder(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderInteract(PlayerInteractEntityEvent event) {
        if (isHolder(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderLeash(PlayerLeashEntityEvent event) {
        if (isHolder(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHolderDeath(EntityDeathEvent event) {
        if (!isHolder(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setShouldPlayDeathSound(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHolderTeleport(EntityTeleportEvent event) {
        if (isHolder(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onHolderRemoved(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (!isHolder(entity) || event.getCause() == EntityRemoveEvent.Cause.UNLOAD) return;
        if (suppressedHolderRemovals.remove(entity.getUniqueId())) return;

        UUID bindingId = getUuid(entity.getPersistentDataContainer(), holderKey);
        StasisBinding binding = bindingId == null ? null : bindings.get(bindingId);
        if (binding == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(binding.worldId());
            if (world != null && world.isChunkLoaded(binding.chunkX(), binding.chunkZ())) {
                ensureHolder(binding);
            }
        });
    }

    private void startTrackingTask() {
        if (trackingTask != null) return;
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPendingCasts, 1L, 1L);
    }

    private void stopTrackingTaskIfIdle() {
        if (!pendingCasts.isEmpty() || trackingTask == null) return;
        trackingTask.cancel();
        trackingTask = null;
    }

    private void tickPendingCasts() {
        Iterator<Map.Entry<UUID, PendingCast>> iterator = pendingCasts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCast> entry = iterator.next();
            PendingCast pending = entry.getValue();
            FishHook hook = pending.hook();
            int age = pending.age() + 1;

            if (!hook.isValid() || age > TRACKING_TIMEOUT_TICKS) {
                clearPendingRod(pending.ownerId(), pending.pendingId());
                iterator.remove();
                continue;
            }

            Block plate = findPressurePlate(hook);
            BlockPosition candidate = plate == null ? null : new BlockPosition(
                    plate.getWorld().getUID(), plate.getX(), plate.getY(), plate.getZ());
            int stableTicks = candidate != null && candidate.equals(pending.candidate())
                    ? pending.stableTicks() + 1 : candidate == null ? 0 : 1;
            pending = new PendingCast(hook, pending.ownerId(), pending.pendingId(), age, candidate, stableTicks);
            entry.setValue(pending);

            if (plate == null || stableTicks < REQUIRED_STABLE_TICKS || isPlateBound(plate)) continue;
            if (!hook.isOnGround() && !isNearlyStationary(hook.getVelocity())) continue;

            ItemLocation rodLocation = findPendingRod(pending.ownerId(), pending.pendingId());
            if (rodLocation == null) {
                iterator.remove();
                continue;
            }

            UUID bindingId = UUID.randomUUID();
            ItemStack rod = rodLocation.item();
            removeUuid(rod, pendingRodKey);
            setUuid(rod, rodKey, bindingId);
            rodLocation.inventory().setItem(rodLocation.slot(), rod);

            StasisBinding binding = new StasisBinding(bindingId, pending.ownerId(), plate.getWorld().getUID(),
                    plate.getWorld().getName(), plate.getX(), plate.getY(), plate.getZ(), null);
            bindings.put(bindingId, binding);
            ensureHolder(binding);
            saveBindings();

            hook.remove();
            Player player = Bukkit.getPlayer(pending.ownerId());
            if (player != null) LowCore.sendConfigMessage(player, "stasis.linked");
            iterator.remove();
        }
        stopTrackingTaskIfIdle();
    }

    private TriggerResult releaseBinding(StasisBinding binding) {
        World world = Bukkit.getWorld(binding.worldId());
        if (world == null) {
            bindings.remove(binding.id());
            saveBindings();
            return TriggerResult.UNAVAILABLE;
        }

        boolean wasLoaded = world.isChunkLoaded(binding.chunkX(), binding.chunkZ());
        boolean ticketAdded = false;
        if (!wasLoaded) {
            ticketAdded = world.addPluginChunkTicket(binding.chunkX(), binding.chunkZ(), plugin);
            if (!world.isChunkLoaded(binding.chunkX(), binding.chunkZ())) {
                world.loadChunk(binding.chunkX(), binding.chunkZ(), true);
            }
        }

        Block plate = world.getBlockAt(binding.x(), binding.y(), binding.z());
        removeHolders(binding, world.getChunkAt(binding.chunkX(), binding.chunkZ()));
        bindings.remove(binding.id());
        saveBindings();

        boolean available = isPressurePlate(plate.getType());
        if (available) {
            plate.tick();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isPressurePlate(plate.getType())) plate.tick();
            });
        }

        if (!wasLoaded) {
            boolean removeTicket = ticketAdded;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (removeTicket) {
                    world.removePluginChunkTicket(binding.chunkX(), binding.chunkZ(), plugin);
                }
            }, 2L);
        }
        return available ? TriggerResult.TRIGGERED : TriggerResult.UNAVAILABLE;
    }

    private CleanupResult reconcileChunk(Chunk chunk) {
        int removed = 0;
        int recreated = 0;

        for (Entity entity : chunk.getEntities()) {
            if (!isHolder(entity)) continue;
            UUID id = getUuid(entity.getPersistentDataContainer(), holderKey);
            StasisBinding binding = id == null ? null : bindings.get(id);
            if (binding == null || !matchesChunk(binding, chunk) || !matchesPlate(binding, entity)
                    || !(entity instanceof Rabbit)) {
                removeHolder(entity);
                removed++;
            }
        }

        List<StasisBinding> inChunk = bindings.values().stream()
                .filter(binding -> matchesChunk(binding, chunk))
                .toList();
        for (StasisBinding binding : inChunk) {
            if (!isPressurePlate(chunk.getWorld().getBlockAt(binding.x(), binding.y(), binding.z()).getType())) {
                removed += removeHolders(binding, chunk);
                continue;
            }
            HolderResult result = ensureHolder(binding);
            removed += result.duplicatesRemoved();
            if (result.created()) recreated++;
        }
        return new CleanupResult(removed, recreated);
    }

    private HolderResult ensureHolder(StasisBinding original) {
        StasisBinding binding = bindings.get(original.id());
        if (binding == null) return new HolderResult(false, 0);

        World world = Bukkit.getWorld(binding.worldId());
        if (world == null || !world.isChunkLoaded(binding.chunkX(), binding.chunkZ())) {
            return new HolderResult(false, 0);
        }
        Block plate = world.getBlockAt(binding.x(), binding.y(), binding.z());
        if (!isPressurePlate(plate.getType())) return new HolderResult(false, 0);

        Chunk chunk = world.getChunkAt(binding.chunkX(), binding.chunkZ());
        Rabbit keeper = null;
        int duplicates = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Rabbit rabbit)) continue;
            UUID entityBindingId = getUuid(entity.getPersistentDataContainer(), holderKey);
            if (!binding.id().equals(entityBindingId)) continue;
            if (keeper == null && matchesPlate(binding, entity)) {
                keeper = rabbit;
            } else {
                removeHolder(entity);
                duplicates++;
            }
        }

        boolean created = false;
        if (keeper == null) {
            Location location = new Location(world, binding.x() + 0.5, binding.y() + 0.0625, binding.z() + 0.5);
            keeper = world.spawn(location, Rabbit.class, rabbit -> configureHolder(rabbit, binding.id()));
            plate.tick();
            created = true;
        } else {
            configureHolder(keeper, binding.id());
        }

        if (!keeper.getUniqueId().equals(binding.holderId())) {
            bindings.put(binding.id(), binding.withHolder(keeper.getUniqueId()));
            saveBindings();
        }
        return new HolderResult(created, duplicates);
    }

    private void configureHolder(Rabbit holder, UUID bindingId) {
        holder.setAdult();
        holder.setAgeLock(true);
        holder.setAI(false);
        holder.setAware(false);
        holder.setTarget(null);
        holder.setCanPickupItems(false);
        holder.setGravity(false);
        holder.setInvulnerable(true);
        holder.setSilent(true);
        holder.setPersistent(true);
        holder.setRemoveWhenFarAway(false);
        holder.setCollidable(false);
        holder.setFireTicks(0);
        holder.setGlowing(false);
        holder.setCustomNameVisible(false);
        holder.clearLootTable();
        holder.getEquipment().clear();
        holder.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));
        holder.addScoreboardTag(HOLDER_TAG);
        setUuid(holder.getPersistentDataContainer(), holderKey, bindingId);
    }

    private int removeHolders(StasisBinding binding, Chunk chunk) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            UUID entityBindingId = getUuid(entity.getPersistentDataContainer(), holderKey);
            if (!binding.id().equals(entityBindingId)) continue;
            removeHolder(entity);
            removed++;
        }
        return removed;
    }

    private void removeHolder(Entity entity) {
        suppressedHolderRemovals.add(entity.getUniqueId());
        entity.remove();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> suppressedHolderRemovals.remove(entity.getUniqueId()), 2L);
    }

    private boolean isPlateBound(Block block) {
        return bindings.values().stream().anyMatch(binding ->
                binding.worldId().equals(block.getWorld().getUID())
                        && binding.x() == block.getX()
                        && binding.y() == block.getY()
                        && binding.z() == block.getZ());
    }

    private Block findPressurePlate(FishHook hook) {
        Location location = hook.getLocation();
        Block at = location.getBlock();
        if (isPressurePlate(at.getType())) return at;
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        if (isPressurePlate(below.getType()) && location.getY() - below.getY() <= 1.25) return below;
        return null;
    }

    static boolean isPressurePlate(Material material) {
        return material.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean isNearlyStationary(Vector velocity) {
        return velocity.lengthSquared() < 0.01;
    }

    private boolean matchesChunk(StasisBinding binding, Chunk chunk) {
        return binding.worldId().equals(chunk.getWorld().getUID())
                && binding.chunkX() == chunk.getX()
                && binding.chunkZ() == chunk.getZ();
    }

    private boolean matchesPlate(StasisBinding binding, Entity entity) {
        Location location = entity.getLocation();
        return location.getWorld() != null
                && binding.worldId().equals(location.getWorld().getUID())
                && location.getBlockX() == binding.x()
                && location.getBlockZ() == binding.z()
                && location.getY() >= binding.y()
                && location.getY() < binding.y() + 1.5;
    }

    private boolean isHolder(Entity entity) {
        return entity.getScoreboardTags().contains(HOLDER_TAG)
                || entity.getPersistentDataContainer().has(holderKey, PersistentDataType.STRING);
    }

    private ItemLocation findPendingRod(UUID ownerId, UUID pendingId) {
        Player player = Bukkit.getPlayer(ownerId);
        if (player == null) return null;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && pendingId.equals(getUuid(item, pendingRodKey))) {
                return new ItemLocation(inventory, slot, item);
            }
        }
        return null;
    }

    private void clearPendingRod(UUID ownerId, UUID pendingId) {
        ItemLocation location = findPendingRod(ownerId, pendingId);
        if (location == null) return;
        removeUuid(location.item(), pendingRodKey);
        location.inventory().setItem(location.slot(), location.item());
    }

    private UUID getUuid(ItemStack item, NamespacedKey key) {
        if (!item.hasItemMeta()) return null;
        return getUuid(item.getItemMeta().getPersistentDataContainer(), key);
    }

    private UUID getUuid(PersistentDataContainer container, NamespacedKey key) {
        String value = container.get(key, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setUuid(ItemStack item, NamespacedKey key, UUID value) {
        ItemMeta meta = item.getItemMeta();
        setUuid(meta.getPersistentDataContainer(), key, value);
        item.setItemMeta(meta);
    }

    private void setUuid(PersistentDataContainer container, NamespacedKey key, UUID value) {
        container.set(key, PersistentDataType.STRING, value.toString());
    }

    private void removeUuid(ItemStack item, NamespacedKey key) {
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
    }

    private void loadBindings() {
        if (!storageFile.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(storageFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load stasis.yml; no stasis bindings were loaded.", e);
            return;
        }

        ConfigurationSection section = yaml.getConfigurationSection("bindings");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "bindings." + key;
            try {
                UUID id = UUID.fromString(key);
                UUID owner = UUID.fromString(yaml.getString(path + ".owner", ""));
                UUID world = UUID.fromString(yaml.getString(path + ".world-uuid", ""));
                String worldName = yaml.getString(path + ".world-name", "unknown");
                int x = requireInteger(yaml, path + ".x");
                int y = requireInteger(yaml, path + ".y");
                int z = requireInteger(yaml, path + ".z");
                String holderValue = yaml.getString(path + ".holder-uuid");
                UUID holder = holderValue == null || holderValue.isBlank() ? null : UUID.fromString(holderValue);
                bindings.put(id, new StasisBinding(id, owner, world, worldName, x, y, z, holder));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid stasis binding '" + key + "': " + e.getMessage());
            }
        }
    }

    private int requireInteger(YamlConfiguration yaml, String path) {
        if (!yaml.isInt(path)) throw new IllegalArgumentException("missing or invalid " + path);
        return yaml.getInt(path);
    }

    private void saveBindings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        for (StasisBinding binding : bindings.values()) {
            String path = "bindings." + binding.id();
            yaml.set(path + ".owner", binding.ownerId().toString());
            yaml.set(path + ".world-uuid", binding.worldId().toString());
            yaml.set(path + ".world-name", binding.worldName());
            yaml.set(path + ".x", binding.x());
            yaml.set(path + ".y", binding.y());
            yaml.set(path + ".z", binding.z());
            if (binding.holderId() != null) yaml.set(path + ".holder-uuid", binding.holderId().toString());
        }
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder for stasis.yml.");
                return;
            }
            yaml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save stasis.yml.", e);
        }
    }

    public enum TriggerResult {
        TRIGGERED,
        UNAVAILABLE,
        NOT_FOUND
    }

    public record CleanupResult(int removed, int recreated) {
    }

    private record HolderResult(boolean created, int duplicatesRemoved) {
    }

    private record BlockPosition(UUID worldId, int x, int y, int z) {
    }

    private record PendingCast(
            FishHook hook,
            UUID ownerId,
            UUID pendingId,
            int age,
            BlockPosition candidate,
            int stableTicks
    ) {
    }

    private record ItemLocation(PlayerInventory inventory, int slot, ItemStack item) {
    }
}
