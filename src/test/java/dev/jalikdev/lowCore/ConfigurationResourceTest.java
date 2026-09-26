package dev.jalikdev.lowCore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationResourceTest {

    @Test
    void pluginYamlContainsCanonicalCommandsAndPermissions() {
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/plugin.yml"));

        assertNotNull(plugin.getConfigurationSection("commands.anti-mods"));
        assertNotNull(plugin.getConfigurationSection("commands.lock-dimension"));
        assertEquals("lowcore.god", plugin.getString("commands.god.permission"));
        assertTrue(plugin.contains("permissions.lowcore.invsee.edit"));
        assertTrue(plugin.contains("permissions.lowcore.performance.notify"));
        assertTrue(plugin.contains("permissions.lowcore.update"));
        assertTrue(plugin.getBoolean("permissions.lowcore.stasis.use.default"));
    }

    @Test
    void defaultConfigUsesCorrectedKeys() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config.yml"));

        assertEquals(20, config.getInt("spawnmob.max-amount"));
        assertTrue(config.getBoolean("update-checker.enabled"));
        assertTrue(config.contains("update-checker.notify-console"));
        assertEquals(10, config.getInt("anti-mods.manual-check-cooldown-seconds"));
        assertTrue(config.isList("anti-mods.custom-commands"));
    }
}
