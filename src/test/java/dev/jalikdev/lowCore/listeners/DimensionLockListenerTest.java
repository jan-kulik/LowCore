package dev.jalikdev.lowCore.listeners;

import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.PortalCreateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionLockListenerTest {

    @Test
    void blocksEntryIntoLockedNetherButAllowsExit() {
        assertTrue(DimensionLockListener.shouldBlockTransfer(
                World.Environment.NORMAL, World.Environment.NETHER, true, false));
        assertFalse(DimensionLockListener.shouldBlockTransfer(
                World.Environment.NETHER, World.Environment.NORMAL, true, false));
    }

    @Test
    void blocksEntryIntoLockedEndButAllowsExit() {
        assertTrue(DimensionLockListener.shouldBlockTransfer(
                World.Environment.NORMAL, World.Environment.THE_END, false, true));
        assertFalse(DimensionLockListener.shouldBlockTransfer(
                World.Environment.THE_END, World.Environment.NORMAL, false, true));
    }

    @Test
    void allowsUnlockedAndSameDimensionTransfers() {
        assertFalse(DimensionLockListener.shouldBlockTransfer(
                World.Environment.NORMAL, World.Environment.NETHER, false, false));
        assertFalse(DimensionLockListener.shouldBlockTransfer(
                World.Environment.THE_END, World.Environment.THE_END, false, true));
    }

    @Test
    void preventsNetherPortalCreationOnlyWhileLocked() {
        assertTrue(DimensionLockListener.shouldBlockPortalCreation(
                PortalCreateEvent.CreateReason.FIRE, true));
        assertTrue(DimensionLockListener.shouldBlockPortalCreation(
                PortalCreateEvent.CreateReason.NETHER_PAIR, true));
        assertFalse(DimensionLockListener.shouldBlockPortalCreation(
                PortalCreateEvent.CreateReason.END_PLATFORM, true));
        assertFalse(DimensionLockListener.shouldBlockPortalCreation(
                PortalCreateEvent.CreateReason.FIRE, false));
    }

    @Test
    void preventsEnderEyesBeingInsertedWhileEndIsLocked() {
        assertTrue(DimensionLockListener.shouldBlockEndFrameInteraction(
                Action.RIGHT_CLICK_BLOCK, Material.END_PORTAL_FRAME, Material.ENDER_EYE, true));
        assertFalse(DimensionLockListener.shouldBlockEndFrameInteraction(
                Action.RIGHT_CLICK_BLOCK, Material.END_PORTAL_FRAME, Material.ENDER_EYE, false));
        assertFalse(DimensionLockListener.shouldBlockEndFrameInteraction(
                Action.RIGHT_CLICK_BLOCK, Material.STONE, Material.ENDER_EYE, true));
    }
}
