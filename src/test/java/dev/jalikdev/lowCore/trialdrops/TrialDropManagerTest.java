package dev.jalikdev.lowCore.trialdrops;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialDropManagerTest {

    @Test
    void recognizesVaultRewardTables() {
        assertTrue(TrialDropManager.isTrialChamberLootTable(
                NamespacedKey.minecraft("chests/trial_chambers/reward_ominous")));
        assertTrue(TrialDropManager.isTrialChamberLootTable(
                NamespacedKey.minecraft("chests/trial_chambers/reward_ominous_unique")));
    }

    @Test
    void recognizesCurrentAndLegacyTrialSpawnerTables() {
        assertTrue(TrialDropManager.isTrialChamberLootTable(
                NamespacedKey.minecraft("spawners/ominous/trial_chamber/key")));
        assertTrue(TrialDropManager.isTrialChamberLootTable(
                NamespacedKey.minecraft("chests/spawner_trial_chamber/ominous_key")));
    }

    @Test
    void ignoresLootOutsideTrialChambers() {
        assertFalse(TrialDropManager.isTrialChamberLootTable(
                NamespacedKey.minecraft("chests/ancient_city")));
        assertFalse(TrialDropManager.isTrialChamberLootTable(
                new NamespacedKey("custom", "chests/trial_chambers/reward_ominous")));
    }

    @Test
    void parsesFriendlyMaterialNames() {
        assertEquals(Material.HEAVY_CORE, TrialDropManager.parseMaterial("heavy-core"));
        assertEquals(Material.HEAVY_CORE, TrialDropManager.parseMaterial("heavy core"));
        assertNull(TrialDropManager.parseMaterial("definitely_not_an_item"));
    }
}
