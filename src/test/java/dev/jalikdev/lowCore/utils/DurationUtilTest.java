package dev.jalikdev.lowCore.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationUtilTest {

    @Test
    void parsesSingleAndCombinedDurations() {
        assertEquals(30L * 60L * 1_000L, DurationUtil.parseMillis("30m"));
        assertEquals(90L * 60L * 1_000L, DurationUtil.parseMillis("1h30m"));
        assertEquals((24L + 12L) * 60L * 60L * 1_000L, DurationUtil.parseMillis("1d12h"));
    }

    @Test
    void rejectsInvalidAndExcessiveDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationUtil.parseMillis("30"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtil.parseMillis("1hour"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtil.parseMillis("366d"));
    }

    @Test
    void formatsDurationsForStatusMessages() {
        assertEquals("1d 2h 3m 4s", DurationUtil.formatMillis(93_784_000L));
        assertEquals("1s", DurationUtil.formatMillis(1L));
    }
}
