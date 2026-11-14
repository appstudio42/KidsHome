package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.os.PowerManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Screen-time usage must survive a wall-clock edit, and a late watchdog tick
 * must still count while the screen is on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScreenTimeGuardTest {
    private Context context;
    private final MutableClock clock = new MutableClock();
    private final GateClock gateClock = new GateClock();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        clock.wall = atHour(15);
        clock.elapsed = 5_000_000L;
        gateClock.elapsed = clock.elapsed;
        ScreenTimeClock.setClockForTests(clock);
        KidModeGate.setClockForTests(gateClock);
        AccServ.lastApp = "";
        Prefs.setKidModeEnabled(context, true);
        Prefs.setScreenTimeEnabled(context, true);
        Prefs.setDailyLimitMinutes(context, 180);
        setScreenInteractive(true);
    }

    @After
    public void tearDown() {
        ScreenTimeClock.resetClockForTests();
        KidModeGate.resetForTests();
        AccServ.lastApp = "";
    }

    @Test
    public void clockJumpDoesNotResetUsageOrBonus() {
        Prefs.ensureScreenTimeDay(context);
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(Prefs.KEY_USED_MILLIS, 50_000L)
                .putLong(Prefs.KEY_BONUS_MILLIS, 15_000L)
                .commit();
        String key = Prefs.get(context).getString(Prefs.KEY_USAGE_DAY, "");

        clock.wall += 26L * 60L * 60L * 1000L;
        clock.elapsed += 10_000L;
        ScreenTimeTracker.onClockChanged(context);

        assertEquals(key, Prefs.get(context).getString(Prefs.KEY_USAGE_DAY, ""));
        assertEquals(50_000L, Prefs.get(context).getLong(Prefs.KEY_USED_MILLIS, -1L));
        assertEquals(15_000L, Prefs.get(context).getLong(Prefs.KEY_BONUS_MILLIS, -1L));
    }

    @Test
    public void monotonicMidnightStillRollsAndArchives() {
        clock.wall = atHour(23);
        Prefs.ensureScreenTimeDay(context);
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(Prefs.KEY_USED_MILLIS, 40_000L)
                .commit();
        String key = Prefs.get(context).getString(Prefs.KEY_USAGE_DAY, "");

        long passed = 2L * 60L * 60L * 1000L;
        clock.wall += passed;
        clock.elapsed += passed;
        Prefs.ensureScreenTimeDay(context);

        String rolled = Prefs.get(context).getString(Prefs.KEY_USAGE_DAY, "");
        assertNotEquals(key, rolled);
        assertEquals(0L, Prefs.get(context).getLong(Prefs.KEY_USED_MILLIS, -1L));
        UsageHistory.DayUsage archived = Prefs.getUsageHistory(context).get(key);
        assertNotNull(archived);
        assertEquals(40_000L, archived.totalMillis);
    }

    @Test
    public void lateWatchdogCountsInsteadOfDroppingTheGap() {
        Prefs.ensureScreenTimeDay(context);
        Prefs.setScreenTimeTick(context, clock.wall, clock.elapsed);
        clock.wall += 180_000L;
        clock.elapsed += 180_000L;
        gateClock.elapsed = clock.elapsed;

        ScreenTimeTracker.tick(context);

        assertEquals(180_000L, Prefs.getUsedMillisToday(context));
    }

    @Test
    public void sleepSizedGapIsCappedAndScreenOffCountsNothing() {
        Prefs.ensureScreenTimeDay(context);
        Prefs.setScreenTimeTick(context, clock.wall, clock.elapsed);
        clock.wall += 2L * 60L * 60L * 1000L;
        clock.elapsed += 2L * 60L * 60L * 1000L;
        gateClock.elapsed = clock.elapsed;

        ScreenTimeTracker.tick(context);
        assertEquals(ScreenTimeState.MAX_ACCRUAL_PER_TICK_MS, Prefs.getUsedMillisToday(context));

        long used = Prefs.getUsedMillisToday(context);
        setScreenInteractive(false);
        clock.wall += 180_000L;
        clock.elapsed += 180_000L;
        gateClock.elapsed = clock.elapsed;
        ScreenTimeTracker.tick(context);
        assertEquals(used, Prefs.getUsedMillisToday(context));
    }

    @Test
    public void visibleEscapePausesUsageAndAStaleFlagDoesNot() {
        Prefs.ensureScreenTimeDay(context);
        Prefs.setScreenTimeTick(context, clock.wall, clock.elapsed);
        KidModeGate.markEscapeUiOpen();

        clock.wall += 30_000L;
        clock.elapsed += 30_000L;
        gateClock.elapsed = clock.elapsed;
        ScreenTimeTracker.tick(context);
        assertEquals(0L, Prefs.getUsedMillisToday(context));

        clock.wall += 61_000L;
        clock.elapsed += 61_000L;
        gateClock.elapsed = clock.elapsed;
        ScreenTimeTracker.tick(context);
        assertEquals(61_000L, Prefs.getUsedMillisToday(context));
    }

    private void setScreenInteractive(boolean interactive) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Shadows.shadowOf(powerManager).setIsInteractive(interactive);
    }

    private static long atHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static final class MutableClock implements ScreenTimeClock.Clock {
        long wall;
        long elapsed;

        @Override
        public long currentTimeMillis() {
            return wall;
        }

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }
    }

    private static final class GateClock implements KidModeGate.Clock {
        long elapsed;

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }
    }
}
