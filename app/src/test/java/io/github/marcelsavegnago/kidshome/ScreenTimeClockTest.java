package io.github.marcelsavegnago.kidshome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ScreenTimeClockTest {
    private TimeZone previousZone;

    @Before
    public void setUp() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(previousZone);
        ScreenTimeClock.resetClockForTests();
    }

    @Test
    public void forwardOrBackwardJumpKeepsTheStoredDay() {
        long wall = utc(2026, 11, 14, 15, 0);
        long elapsed = 5_000_000L;
        String key = ScreenTimeState.dayKey(wall);
        long next = ScreenTimeState.nextLocalMidnightMillis(wall);

        ScreenTimeClock.Decision forward = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall + 26L * 60L * 60L * 1000L,
                elapsed + 10_000L);
        assertFalse(forward.rollDay);
        assertTrue(forward.discontinuity);
        assertEquals(key, forward.dayKey);

        ScreenTimeClock.Decision backward = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall - 26L * 60L * 60L * 1000L,
                elapsed + 10_000L);
        assertFalse(backward.rollDay);
        assertTrue(backward.discontinuity);
        assertEquals(key, backward.dayKey);
    }

    @Test
    public void monotonicMidnightRollsTheDay() {
        long wall = utc(2026, 11, 14, 23, 0);
        long elapsed = 5_000_000L;
        String key = ScreenTimeState.dayKey(wall);
        long next = ScreenTimeState.nextLocalMidnightMillis(wall);
        long passed = 2L * 60L * 60L * 1000L;

        ScreenTimeClock.Decision decision = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall + passed,
                elapsed + passed);
        assertTrue(decision.rollDay);
        assertFalse(decision.discontinuity);
        assertNotEquals(key, decision.dayKey);
        assertEquals(ScreenTimeState.dayKey(wall + passed), decision.dayKey);
    }

    @Test
    public void timezoneChangeDoesNotRollBeforeTrustedMidnight() {
        long wall = utc(2026, 11, 14, 12, 0);
        long elapsed = 8_000_000L;
        String key = ScreenTimeState.dayKey(wall);
        long next = ScreenTimeState.nextLocalMidnightMillis(wall);

        TimeZone.setDefault(TimeZone.getTimeZone("GMT+14"));
        assertNotEquals(key, ScreenTimeState.dayKey(wall + 10_000L));

        ScreenTimeClock.Decision decision = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall + 10_000L,
                elapsed + 10_000L);
        assertFalse(decision.rollDay);
        assertEquals(key, decision.dayKey);
    }

    @Test
    public void rebootDoesNotAdoptANewWallDate() {
        long wall = utc(2026, 11, 14, 21, 0);
        long elapsed = 9_000_000L;
        String key = ScreenTimeState.dayKey(wall);
        long next = ScreenTimeState.nextLocalMidnightMillis(wall);

        ScreenTimeClock.Decision decision = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall + 12L * 60L * 60L * 1000L,
                30_000L);
        assertTrue(decision.discontinuity);
        assertFalse(decision.rollDay);
        assertEquals(key, decision.dayKey);
        assertEquals(30_000L, decision.anchorElapsedMs);
    }

    @Test
    public void driftInsideToleranceStillFollowsTheWallClock() {
        long wall = utc(2026, 11, 14, 10, 0);
        long elapsed = 4_000_000L;
        String key = ScreenTimeState.dayKey(wall);
        long next = ScreenTimeState.nextLocalMidnightMillis(wall);
        long step = 60_000L;

        ScreenTimeClock.Decision decision = ScreenTimeClock.resolve(
                key, wall, elapsed, next,
                wall + step + 30_000L,
                elapsed + step);
        assertFalse(decision.discontinuity);
        assertFalse(decision.rollDay);
        assertEquals(key, decision.dayKey);
    }

    @Test
    public void missingAnchorTrustsTheWallDay() {
        long today = utc(2026, 11, 14, 8, 0);
        long yesterday = today - 24L * 60L * 60L * 1000L;
        ScreenTimeClock.Decision decision = ScreenTimeClock.resolve(
                ScreenTimeState.dayKey(yesterday),
                0L, 0L, 0L,
                today,
                1_000L);
        assertTrue(decision.rollDay);
        assertEquals(ScreenTimeState.dayKey(today), decision.dayKey);
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
