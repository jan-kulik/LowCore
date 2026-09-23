package dev.jalikdev.lowCore.antifreecam;

import dev.jalikdev.lowCore.LowCore;

import java.lang.reflect.Method;
import java.util.UUID;

public final class BedrockPlayerDetector {

    private final LowCore plugin;
    private boolean initialized;
    private Method geyserApi;
    private Method geyserIsBedrockPlayer;
    private Method floodgateGetInstance;
    private Method floodgateIsPlayer;

    public BedrockPlayerDetector(LowCore plugin) {
        this.plugin = plugin;
    }

    public boolean isBedrockPlayer(UUID playerId) {
        initialize();
        return invokeFloodgate(playerId) || invokeGeyser(playerId);
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApi = apiClass.getMethod("api");
            geyserIsBedrockPlayer = apiClass.getMethod("isBedrockPlayer", UUID.class);
            plugin.getLogger().info("Anti-freecam hooked into Geyser; Bedrock players will be skipped.");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            geyserApi = null;
            geyserIsBedrockPlayer = null;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateGetInstance = apiClass.getMethod("getInstance");
            floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            plugin.getLogger().info("Anti-freecam hooked into Floodgate; Bedrock players will be skipped.");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            floodgateGetInstance = null;
            floodgateIsPlayer = null;
        }
    }

    private boolean invokeGeyser(UUID playerId) {
        if (geyserApi == null || geyserIsBedrockPlayer == null) {
            return false;
        }
        try {
            Object api = geyserApi.invoke(null);
            return api != null && Boolean.TRUE.equals(geyserIsBedrockPlayer.invoke(api, playerId));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private boolean invokeFloodgate(UUID playerId) {
        if (floodgateGetInstance == null || floodgateIsPlayer == null) {
            return false;
        }
        try {
            Object api = floodgateGetInstance.invoke(null);
            return api != null && Boolean.TRUE.equals(floodgateIsPlayer.invoke(api, playerId));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
