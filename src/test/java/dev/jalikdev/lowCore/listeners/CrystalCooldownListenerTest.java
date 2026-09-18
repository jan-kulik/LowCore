package dev.jalikdev.lowCore.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrystalCooldownListenerTest {

    @Test
    void reportsFullCooldownForPlacementInSameTick() {
        assertEquals(10, CrystalCooldownListener.remainingTicks(100, 100, 10));
    }

    @Test
    void countsDownUsingServerTicks() {
        assertEquals(6, CrystalCooldownListener.remainingTicks(100, 104, 10));
        assertEquals(1, CrystalCooldownListener.remainingTicks(100, 109, 10));
    }

    @Test
    void allowsPlacementWhenCooldownHasElapsed() {
        assertEquals(0, CrystalCooldownListener.remainingTicks(100, 110, 10));
        assertEquals(0, CrystalCooldownListener.remainingTicks(100, 120, 10));
    }

    @Test
    void handlesServerTickIntegerOverflow() {
        assertEquals(3, CrystalCooldownListener.remainingTicks(Integer.MAX_VALUE - 1, Integer.MIN_VALUE, 5));
    }
}
