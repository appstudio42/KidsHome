package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

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

    @Test
    public void noUsageAccruesWhileBlockedOrOnLockUi() {
        assertFalse(ScreenTimeState.shouldAccrueUsage(true, true, false));
        assertFalse(ScreenTimeState.shouldAccrueUsage(true, false, true));
        assertFalse(ScreenTimeState.shouldAccrueUsage(true, true, true));
        assertFalse(ScreenTimeState.shouldAccrueUsage(false, false, false));
        assertTrue(ScreenTimeState.shouldAccrueUsage(true, false, false));
    }

    @Test
    public void midnightCrossingDoesNotChargePreviousDayIntoNewDay() {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long midnight = cal.getTimeInMillis();

        long lastBeforeMidnight = midnight - 40_000L; // 23:59:20
        long nowAfterMidnight = midnight + 20_000L;   // 00:00:20
        // Only the 20s after midnight belong to the new day.
        assertEquals(20_000L,
                ScreenTimeState.millisChargedToCurrentDay(lastBeforeMidnight, nowAfterMidnight));

        // Same-day tick still charges the full delta.
        assertEquals(15_000L,
                ScreenTimeState.millisChargedToCurrentDay(midnight + 5_000L, midnight + 20_000L));

        // Invalid / empty last tick charges nothing.
        assertEquals(0L, ScreenTimeState.millisChargedToCurrentDay(0L, nowAfterMidnight));
        assertEquals(0L, ScreenTimeState.millisChargedToCurrentDay(nowAfterMidnight, lastBeforeMidnight));
    }

    @Test
    public void lateWatchdogStillCountsUpToTheCap() {
        long threeMinutes = 180_000L;
        assertTrue(threeMinutes > 90_000L);
        assertEquals(threeMinutes, ScreenTimeState.millisToAccrue(threeMinutes, true, false, false));
        assertEquals(ScreenTimeState.MAX_ACCRUAL_PER_TICK_MS,
                ScreenTimeState.millisToAccrue(2L * 60L * 60L * 1000L, true, false, false));
    }

    @Test
    public void screenOffBlockAndLockUiAccrueNothing() {
        long gap = 180_000L;
        assertEquals(0L, ScreenTimeState.millisToAccrue(gap, false, false, false));
        assertEquals(0L, ScreenTimeState.millisToAccrue(gap, true, true, false));
        assertEquals(0L, ScreenTimeState.millisToAccrue(gap, true, false, true));
        assertEquals(0L, ScreenTimeState.millisToAccrue(0L, true, false, false));
        assertEquals(0L, ScreenTimeState.millisToAccrue(-5_000L, true, false, false));
    }

    @Test
    public void tickDeltaIgnoresFirstSampleAndReboot() {
        assertEquals(0L, ScreenTimeState.tickDeltaMillis(0L, 50_000L));
        assertEquals(0L, ScreenTimeState.tickDeltaMillis(80_000L, 10_000L));
        assertEquals(15_000L, ScreenTimeState.tickDeltaMillis(10_000L, 25_000L));
    }
}
