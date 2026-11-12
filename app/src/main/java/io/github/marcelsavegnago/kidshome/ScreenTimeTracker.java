package io.github.marcelsavegnago.kidshome;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * Tracks kid-mode screen-on usage, bedtime windows, per-app usage, warnings, and lock screen.
 * Persists frequently so reboot / process death do not lose the count.
 */
public final class ScreenTimeTracker {
    private static final String TAG = "KidsHome.ScreenTime";
    private static final long WARN_BEFORE_MS = 5L * 60L * 1000L;
    private static final long MAX_DELTA_MS = 90_000L;
    private static final int NOTIF_WARN = 2001;

    private ScreenTimeTracker() {
    }

    public static void tick(Context context) {
        Context app = context.getApplicationContext();
        Prefs.migrateIfNeeded(app);
        Prefs.ensureScreenTimeDay(app);

        if (!Prefs.isKidModeEnabled(app)) {
            Prefs.setLastScreenTickMs(app, 0L);
            Prefs.setForegroundTrackPackage(app, "");
            return;
        }

        boolean screenOn = isScreenOn(app);
        long now = System.currentTimeMillis();
        long last = Prefs.getLastScreenTickMs(app);
        // Evaluate block / lock-UI before accruing so overnight lock screens and
        // remote/bedtime blocks never burn today's allowance.
        boolean blocked = isBlocked(app);
        boolean lockOrPinUi = KidModeGate.isLockOrPinUiVisible();

        if (ScreenTimeState.shouldAccrueUsage(screenOn, blocked, lockOrPinUi) && last > 0L) {
            long delta = now - last;
            if (delta > 0L && delta <= MAX_DELTA_MS) {
                // Cap/split at midnight: pre-midnight time must not charge the new day.
                long chargeable = ScreenTimeState.millisChargedToCurrentDay(last, now);
                if (chargeable > 0L) {
                    if (Prefs.isScreenTimeEnabled(app) && !Prefs.isScreenTimeDisabledToday(app)) {
                        Prefs.addUsedMillisToday(app, chargeable);
                    }
                    String fg = resolveForegroundPackage(app);
                    if (fg != null && !fg.isEmpty() && !fg.equals(app.getPackageName())) {
                        Prefs.addAppUsedMillisToday(app, fg, chargeable);
                        Prefs.setForegroundTrackPackage(app, fg);
                    }
                }
            }
        }
        Prefs.setLastScreenTickMs(app, now);
        Prefs.flushScreenTime(app);

        if (isBlocked(app)) {
            ScreenTimeLockActivity.launch(app);
            return;
        }

        maybeWarn(app);
    }

    private static String resolveForegroundPackage(Context context) {
        String tracked = Prefs.getForegroundTrackPackage(context);
        String last = AccServ.lastApp;
        if (last != null && !last.isEmpty()
                && !last.equals(context.getPackageName())
                && !"com.android.systemui".equals(last)
                && !"android".equals(last)) {
            return last;
        }
        return tracked;
    }

    public static boolean isBlocked(Context context) {
        return currentBlockReason(context) != BlockReason.NONE;
    }

    public static long remainingMillis(Context context) {
        Prefs.ensureScreenTimeDay(context);
        if (!Prefs.isScreenTimeEnabled(context) || Prefs.isScreenTimeDisabledToday(context)) {
            return Long.MAX_VALUE;
        }
        long limit = Prefs.getDailyLimitMinutes(context) * 60L * 1000L;
        return ScreenTimeState.remainingMillis(
                Prefs.getUsedMillisToday(context),
                Prefs.getBonusMillisToday(context),
                limit
        );
    }

    public static String formatRemaining(Context context) {
        if (!Prefs.isScreenTimeEnabled(context)) {
            return context.getString(R.string.screen_time_off);
        }
        if (Prefs.isScreenTimeDisabledToday(context)) {
            return context.getString(R.string.screen_time_off_today);
        }
        long rem = remainingMillis(context);
        if (rem == Long.MAX_VALUE) {
            return context.getString(R.string.screen_time_off);
        }
        return formatMillis(context, rem);
    }

    public static String formatMillis(Context context, long rem) {
        if (rem == Long.MAX_VALUE) {
            return context.getString(R.string.app_time_unlimited);
        }
        long totalSec = rem / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        if (h > 0) {
            return context.getString(R.string.screen_time_remaining_hm, h, m);
        }
        return context.getString(R.string.screen_time_remaining_m, m);
    }

    public static String formatAppRemainingShort(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        int limit = Prefs.getAppLimitMinutes(context, packageName);
        if (limit < 0) {
            return "";
        }
        long rem = Prefs.remainingAppMillis(context, packageName);
        if (rem <= 0L) {
            return context.getString(R.string.app_time_zero);
        }
        long minutes = Math.max(1L, (rem + 59_999L) / 60_000L);
        return context.getString(R.string.app_time_left_short, minutes);
    }

    public static BlockReason currentBlockReason(Context context) {
        if (!Prefs.isKidModeEnabled(context)) {
            return BlockReason.NONE;
        }
        // Parent remote "lock now" takes priority so the tablet shows the lock screen immediately.
        if (Prefs.isRemoteForceLocked(context)) {
            return BlockReason.REMOTE;
        }
        RuleEngine.ScheduleDecision schedule = Prefs.currentScheduleDecision(context);
        if (RuleEngine.isAnyBedtimeActive(
                Prefs.isBedtimeEnabled(context),
                Prefs.isBedtimeDisabledToday(context),
                Prefs.getBedtimeStartMinutes(context),
                Prefs.getBedtimeEndMinutes(context),
                ScreenTimeState.minutesOfDayNow(),
                schedule)) {
            return BlockReason.BEDTIME;
        }
        if (Prefs.isScreenTimeEnabled(context)
                && !Prefs.isScreenTimeDisabledToday(context)
                && ScreenTimeState.isDailyLimitReached(
                Prefs.getUsedMillisToday(context),
                Prefs.getBonusMillisToday(context),
                Prefs.getDailyLimitMinutes(context) * 60L * 1000L)) {
            return BlockReason.DAILY_LIMIT;
        }
        return BlockReason.NONE;
    }

    /**
     * Whether a specific allowed package may be launched right now.
     * Considers schedule category filters and per-app limits.
     */
    public static boolean canLaunchApp(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (isBlocked(context)) {
            return false;
        }
        RuleEngine.ScheduleDecision schedule = Prefs.currentScheduleDecision(context);
        AppCategory category = Prefs.getAppCategory(context, packageName);
        if (!RuleEngine.isAllowedBySchedule(schedule, category)) {
            return false;
        }
        return !Prefs.isAppLimitReached(context, packageName);
    }

    private static void maybeWarn(Context context) {
        if (!Prefs.isScreenTimeEnabled(context) || Prefs.isScreenTimeDisabledToday(context)) {
            return;
        }
        long rem = remainingMillis(context);
        if (rem <= 0L || rem > WARN_BEFORE_MS) {
            return;
        }
        if (Prefs.wasScreenTimeWarningShownToday(context)) {
            return;
        }
        Prefs.setScreenTimeWarningShownToday(context, true);
        StatusNotifier.ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        Intent open = new Intent(context, KidHomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, flags);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(context, StatusNotifier.CHANNEL_ID);
        } else {
            b = new Notification.Builder(context);
        }
        nm.notify(NOTIF_WARN, b
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.screen_time_warn_title))
                .setContentText(context.getString(R.string.screen_time_warn_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        Log.i(TAG, "Screen-time warning shown; remainingMs=" + rem);
    }

    @SuppressWarnings("deprecation")
    private static boolean isScreenOn(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return pm.isInteractive();
        }
        return pm.isScreenOn();
    }

    public enum BlockReason {
        NONE,
        DAILY_LIMIT,
        BEDTIME,
        REMOTE
    }
}
