package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Preference writes must stay visible in-process immediately, including the
 * ones that now use {@code apply()} instead of a blocking {@code commit()}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PrefsPersistenceTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void pinChangeReplacesThePreviousPin() {
        Prefs.setKidPin(context, "1234");
        assertTrue(Prefs.hasKidPin(context));
        assertTrue(storedPin().startsWith("v1:" + PinHasher.DEFAULT_ITERATIONS + ":"));
        assertFalse(Prefs.get(context).contains(Prefs.KEY_KID_PIN_SALT));
        assertTrue(Prefs.verifyKidPin(context, "1234"));

        Prefs.setKidPin(context, "9876");
        assertFalse(Prefs.verifyKidPin(context, "1234"));
        assertTrue(Prefs.verifyKidPin(context, "9876"));
        assertTrue(storedPin().startsWith("v1:" + PinHasher.DEFAULT_ITERATIONS + ":"));
    }

    @Test
    public void legacyPinMigratesWithoutTouchingAttemptTracker() {
        PinAttemptTracker.resetForTests();
        String salt = "00112233445566778899aabbccddeeff";
        String legacy = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
        Prefs.get(context).edit()
                .putString(Prefs.KEY_KID_PIN_SALT, salt)
                .putString(Prefs.KEY_KID_PIN_HASH, legacy)
                .commit();

        assertEquals(1_000L, PinAttemptTracker.registerFailure());
        assertFalse(Prefs.verifyKidPin(context, "0000"));
        assertEquals(legacy, storedPin());
        assertEquals(salt, Prefs.get(context).getString(Prefs.KEY_KID_PIN_SALT, ""));
        assertEquals(1, PinAttemptTracker.failureCount());
        assertTrue(PinAttemptTracker.isLockedOut());

        assertTrue(Prefs.verifyKidPin(context, "1234"));
        String upgraded = storedPin();
        assertTrue(upgraded.startsWith("v1:" + PinHasher.DEFAULT_ITERATIONS + ":"));
        assertNotEquals(legacy, upgraded);
        assertFalse(Prefs.get(context).contains(Prefs.KEY_KID_PIN_SALT));
        assertEquals(1, PinAttemptTracker.failureCount());
        assertTrue(PinAttemptTracker.isLockedOut());

        assertTrue(Prefs.verifyKidPin(context, "1234"));
        assertEquals(upgraded, storedPin());
        assertFalse(Prefs.verifyKidPin(context, "1235"));
        PinAttemptTracker.registerFailure();
        assertEquals(2, PinAttemptTracker.failureCount());
        PinAttemptTracker.reset();
        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(0, PinAttemptTracker.failureCount());
    }

    @Test
    @Config(sdk = 35)
    public void backupIsDisabled() {
        ApplicationInfo info = context.getApplicationInfo();
        assertEquals(0, info.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        assertNotNull(context.getResources().getXml(R.xml.backup_rules));
        assertNotNull(context.getResources().getXml(R.xml.data_extraction_rules));
    }

    @Test
    public void kidModeAndRemoteLockRoundTrip() {
        Prefs.setKidModeEnabled(context, true);
        assertTrue(Prefs.isKidModeEnabled(context));
        Prefs.setKidModeEnabled(context, false);
        assertFalse(Prefs.isKidModeEnabled(context));

        Prefs.setRemoteForceLocked(context, true);
        assertTrue(Prefs.isRemoteForceLocked(context));
        Prefs.setRemoteForceLocked(context, false);
        assertFalse(Prefs.isRemoteForceLocked(context));
    }

    @Test
    public void asyncSettingsAreVisibleImmediately() {
        Prefs.setChildName(context, "  Ana ");
        assertEquals("Ana", Prefs.getChildName(context));

        Prefs.setDailyLimitMinutes(context, 45);
        assertEquals(45, Prefs.getDailyLimitMinutes(context));

        Prefs.setAllowedPackages(context, new HashSet<>(Collections.singleton("com.example.game")));
        assertTrue(Prefs.getAllowedPackages(context).contains("com.example.game"));
        assertEquals(1, Prefs.getAllowedPackages(context).size());
    }

    @Test
    public void dayRolloverArchivesYesterdayAndResetsCounters() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = ScreenTimeState.dayKey(calendar.getTimeInMillis());
        Prefs.get(context).edit()
                .putString(Prefs.KEY_USAGE_DAY, yesterday)
                .putLong(Prefs.KEY_USED_MILLIS, 12_345L)
                .putLong(Prefs.KEY_BONUS_MILLIS, 1_000L)
                .putInt(Prefs.KEY_STARS_TODAY, 3)
                .putString(Prefs.KEY_APP_USED_JSON, "{\"com.example.game\":4000}")
                .commit();

        Prefs.ensureScreenTimeDay(context);

        assertEquals(ScreenTimeState.dayKeyNow(),
                Prefs.get(context).getString(Prefs.KEY_USAGE_DAY, ""));
        assertEquals(0L, Prefs.get(context).getLong(Prefs.KEY_USED_MILLIS, -1L));
        assertEquals(0L, Prefs.get(context).getLong(Prefs.KEY_BONUS_MILLIS, -1L));
        assertEquals(0, Prefs.get(context).getInt(Prefs.KEY_STARS_TODAY, -1));
        assertEquals("{}", Prefs.get(context).getString(Prefs.KEY_APP_USED_JSON, ""));

        UsageHistory.DayUsage archived = Prefs.getUsageHistory(context).get(yesterday);
        assertNotNull(archived);
        assertEquals(12_345L, archived.totalMillis);
        assertEquals(3, archived.starsEarned);
        assertEquals(Long.valueOf(4000L), archived.appMillis.get("com.example.game"));
    }

    @Test
    public void flushKeepsUsageWrittenWithApply() {
        Prefs.addUsedMillisToday(context, 5_000L);
        Prefs.flushScreenTime(context);
        assertEquals(5_000L, Prefs.getUsedMillisToday(context));
    }

    @Test
    public void earnTaskGrantIsVisibleAndNotRepeatable() {
        List<EarnTask> tasks = new ArrayList<>();
        tasks.add(new EarnTask("brush", "Brush teeth", 10, EarnTask.TARGET_TOTAL, true));
        Prefs.setEarnTasks(context, tasks);

        assertEquals(10, Prefs.completeEarnTask(context, "brush"));
        assertTrue(Prefs.isEarnTaskDoneToday(context, "brush"));
        assertEquals(10L * 60L * 1000L, Prefs.getBonusMillisToday(context));
        assertEquals(1, Prefs.getStarsTotal(context));
        assertEquals(1, Prefs.getStarsToday(context));
        assertEquals(0, Prefs.completeEarnTask(context, "brush"));
    }

    @Test
    public void migrateIsIdempotent() {
        Prefs.migrateIfNeeded(context);
        assertEquals(Prefs.CURRENT_PREFS_VERSION,
                Prefs.get(context).getInt(Prefs.KEY_PREFS_VERSION, -1));
        assertFalse(Prefs.get(context).getBoolean(Prefs.KEY_REMOTE_ENABLED, true));

        Prefs.migrateIfNeeded(context);
        assertEquals(Prefs.CURRENT_PREFS_VERSION,
                Prefs.get(context).getInt(Prefs.KEY_PREFS_VERSION, -1));
    }

    @Test
    public void createdRemoteTokenIsStableUntilRegenerated() {
        String created = Prefs.getOrCreateRemoteToken(context);
        assertNotNull(created);
        assertFalse(created.isEmpty());
        assertEquals(created, Prefs.getOrCreateRemoteToken(context));

        Prefs.setRemoteForceLocked(context, true);
        String rotated = Prefs.regenerateRemoteToken(context);
        assertNotEquals(created, rotated);
        assertEquals(rotated, Prefs.getRemoteToken(context));
        assertFalse(Prefs.isRemoteForceLocked(context));
    }

    private String storedPin() {
        return Prefs.get(context).getString(Prefs.KEY_KID_PIN_HASH, "");
    }
}
