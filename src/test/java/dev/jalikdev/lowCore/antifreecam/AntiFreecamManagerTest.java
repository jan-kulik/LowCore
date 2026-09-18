package dev.jalikdev.lowCore.antifreecam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiFreecamManagerTest {

    private static final String FREECAM_FALLBACK = "LCAFtest";
    private static final String METEOR_FALLBACK = "LCAMtest";

    @Test
    void vanillaFallbacksAreClean() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                AntiFreecamManager.FREECAM_KEY,
                FREECAM_FALLBACK,
                METEOR_FALLBACK,
                "W"
        }, FREECAM_FALLBACK, METEOR_FALLBACK);

        assertTrue(result.detectedMods().isEmpty());
        assertFalse(result.protectedResponse());
    }

    @Test
    void freecamKeybindAndTranslationAreDetected() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "F4",
                "Freecam Options",
                METEOR_FALLBACK,
                "W"
        }, FREECAM_FALLBACK, METEOR_FALLBACK);

        assertEquals(java.util.Set.of("Freecam"), result.detectedMods());
    }

    @Test
    void meteorRawKeyIsDetected() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                AntiFreecamManager.FREECAM_KEY,
                FREECAM_FALLBACK,
                AntiFreecamManager.METEOR_KEY,
                "W"
        }, FREECAM_FALLBACK, METEOR_FALLBACK);

        assertEquals(java.util.Set.of("Meteor Client"), result.detectedMods());
    }

    @Test
    void filteredControlResponseNeverPunishes() {
        var result = AntiFreecamManager.evaluateResponses(new String[]{
                "F4",
                "Freecam Options",
                AntiFreecamManager.METEOR_KEY,
                AntiFreecamManager.CONTROL_KEY
        }, FREECAM_FALLBACK, METEOR_FALLBACK);

        assertTrue(result.protectedResponse());
        assertTrue(result.detectedMods().isEmpty());
    }
}
