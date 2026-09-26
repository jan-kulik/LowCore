package dev.jalikdev.lowCore.stasis;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StasisManagerTest {

    @Test
    void recognizesAllVanillaPressurePlateFamilies() {
        assertTrue(StasisManager.isPressurePlate(Material.OAK_PRESSURE_PLATE));
        assertTrue(StasisManager.isPressurePlate(Material.STONE_PRESSURE_PLATE));
        assertTrue(StasisManager.isPressurePlate(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE));
        assertTrue(StasisManager.isPressurePlate(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));
        assertTrue(StasisManager.isPressurePlate(Material.HEAVY_WEIGHTED_PRESSURE_PLATE));
        assertFalse(StasisManager.isPressurePlate(Material.OAK_BUTTON));
        assertFalse(StasisManager.isPressurePlate(Material.STONE));
    }
}
