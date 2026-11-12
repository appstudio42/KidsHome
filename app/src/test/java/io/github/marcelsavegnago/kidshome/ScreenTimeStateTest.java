package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenTimeStateTest {
    @Test
    public void bedtimeSameDayWindow() {
        assertTrue(ScreenTimeState.isInBedtimeWindow(14 * 60, 13 * 60, 15 * 60));
        assertFalse(ScreenTimeState.isInBedtimeWindow(12 * 60, 13 * 60, 15 * 60));
    }

    @Test
    public void bedtimeOvernightWindow() {
        // 20:00 – 07:00
        assertTrue(ScreenTimeState.isInBedtimeWindow(21 * 60, 20 * 60, 7 * 60));
        assertTrue(ScreenTimeState.isInBedtimeWindow(6 * 60, 20 * 60, 7 * 60));
        assertFalse(ScreenTimeState.isInBedtimeWindow(12 * 60, 20 * 60, 7 * 60));
        assertFalse(ScreenTimeState.isInBedtimeWindow(7 * 60, 20 * 60, 7 * 60));
    }

    @Test
    public void remainingAccountsForBonus() {
        long rem = ScreenTimeState.remainingMillis(50 * 60_000L, 15 * 60_000L, 60 * 60_000L);
        assertEquals(25 * 60_000L, rem);
        assertTrue(ScreenTimeState.isDailyLimitReached(70 * 60_000L, 0, 60 * 60_000L));
        assertFalse(ScreenTimeState.isDailyLimitReached(70 * 60_000L, 20 * 60_000L, 60 * 60_000L));
    }

    @Test
    public void bonusExtendsPastDailyLimitWhenUnused() {
        // limit 60, used 25, bonus 30 → 65 left (not capped at 60).
        long rem = ScreenTimeState.remainingMillis(25 * 60_000L, 30 * 60_000L, 60 * 60_000L);
        assertEquals(65 * 60_000L, rem);
        assertFalse(ScreenTimeState.isDailyLimitReached(25 * 60_000L, 30 * 60_000L, 60 * 60_000L));
    }
}
