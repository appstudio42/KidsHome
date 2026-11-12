package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteTimeMathTest {
    @Test
    public void clampMinutesCapsAndFloors() {
        assertEquals(0L, RemoteTimeMath.clampMinutes(0));
        assertEquals(0L, RemoteTimeMath.clampMinutes(-5));
        assertEquals(15L, RemoteTimeMath.clampMinutes(15));
        assertEquals(240L, RemoteTimeMath.clampMinutes(999));
    }

    @Test
    public void remainingAfterBonus() {
        long used = 50L * 60_000L;
        long bonus = 20L * 60_000L;
        assertEquals(30L, RemoteTimeMath.remainingMinutes(used, bonus, 60));
        assertEquals(50L, RemoteTimeMath.effectiveUsedMinutes(used, 0L));
    }

    @Test
    public void presetValidation() {
        assertTrue(RemoteTimeMath.isPresetOrCustomMinutes(15));
        assertTrue(RemoteTimeMath.isPresetOrCustomMinutes(30));
        assertTrue(RemoteTimeMath.isPresetOrCustomMinutes(60));
        assertTrue(RemoteTimeMath.isPresetOrCustomMinutes(7));
        assertFalse(RemoteTimeMath.isPresetOrCustomMinutes(0));
        assertFalse(RemoteTimeMath.isPresetOrCustomMinutes(500));
    }
}
