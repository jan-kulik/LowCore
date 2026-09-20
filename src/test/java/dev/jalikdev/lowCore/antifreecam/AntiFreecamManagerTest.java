package dev.jalikdev.lowCore.antifreecam;

import dev.jalikdev.lowCore.antifreecam.AntiModClient.ProbeSignature;
import dev.jalikdev.lowCore.antifreecam.AntiModClient.SignatureType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiFreecamManagerTest {
    private static final List<ProbeSignature> BATCH = List.of(
            new ProbeSignature(AntiModClient.FREECAM, "key.freecam.toggle", SignatureType.KEYBIND),
            new ProbeSignature(AntiModClient.METEOR, "key.meteor-client.open-gui", SignatureType.TRANSLATABLE_RAW_SIGNAL),
            new ProbeSignature(AntiModClient.WURST, "description.wurst.hack.clickgui", SignatureType.TRANSLATABLE)
    );
    private static final List<String> FALLBACKS = List.of("fallback-a", "fallback-b", "fallback-c");

    @Test
    void vanillaFallbacksAreClean() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "key.freecam.toggle", "fallback-b", "fallback-c", "W"
        }, BATCH, FALLBACKS);
        assertTrue(result.detectedClients().isEmpty());
        assertFalse(result.protectedResponse());
    }

    @Test
    void multipleClientsCanBeDetectedInOneProbe() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "F4", "Open GUI", "Window-based ClickGUI.", "W"
        }, BATCH, FALLBACKS);
        assertEquals(Set.of(AntiModClient.FREECAM, AntiModClient.METEOR, AntiModClient.WURST),
                result.detectedClients());
    }

    @Test
    void meteorRawKeyIsDetectedButOtherRawKeysAreFailOpen() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "key.freecam.toggle", "key.meteor-client.open-gui", "description.wurst.hack.clickgui", "W"
        }, BATCH, FALLBACKS);
        assertEquals(Set.of(AntiModClient.METEOR), result.detectedClients());
    }

    @Test
    void filteredControlResponseNeverPunishes() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "F4", "Open GUI", "Window-based ClickGUI.", AntiFreecamManager.CONTROL_KEY
        }, BATCH, FALLBACKS);
        assertTrue(result.protectedResponse());
        assertTrue(result.detectedClients().isEmpty());
    }
}
