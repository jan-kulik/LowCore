package dev.jalikdev.lowCore.stasis;

import java.util.UUID;

public record StasisBinding(
        UUID id,
        UUID ownerId,
        UUID worldId,
        String worldName,
        int x,
        int y,
        int z,
        UUID holderId
) {

    public StasisBinding withHolder(UUID newHolderId) {
        return new StasisBinding(id, ownerId, worldId, worldName, x, y, z, newHolderId);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
