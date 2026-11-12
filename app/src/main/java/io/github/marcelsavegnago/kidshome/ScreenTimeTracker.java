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
 * Tracks kid-mode screen-on usage, bedtime windows, warnings, and lock screen.
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
        Prefs.ensureScreenTimeDay(app);

        if (!Prefs.isKidModeEnabled(app)) {
            Prefs.setLastScreenTickMs(app, 0L);
            return;
        }

        boolean screenOn = isScreenOn(app);
        long now = System.currentTimeMillis();
        long last = Prefs.getLastScreenTickMs(app);

        if (screenOn && Prefs.isScreenTimeEnabled(app) && !Prefs.isScreenTimeDisabledToday(app)) {
            if (last > 0L) {
                long delta = now - last;
                if (delta > 0L && delta <= MAX_DELTA_MS) {
                    Prefs.addUsedMillisToday(app, delta);
                }
            }
        }
        Prefs.setLastScreenTickMs(app, now);
        // Persist often so kills lose at most ~one tick.
        Prefs.flushScreenTime(app);

        if (isBlocked(app)) {
            ScreenTimeLockActivity.launch(app);
            return;
        }

        maybeWarn(app);
    }

    public static boolean isBlocked(Context context) {
        if (!Prefs.isKidModeEnabled(context)) {
            return false;
        }
        if (Prefs.isBedtimeEnabled(context)
                && !Prefs.isBedtimeDisabledToday(context)
                && ScreenTimeState.isInBedtimeWindow(
                ScreenTimeState.minutesOfDayNow(),
                Prefs.getBedtimeStartMinutes(context),
                Prefs.getBedtimeEndMinutes(context))) {
            return true;
        }
        if (Prefs.isScreenTimeEnabled(context) && !Prefs.isScreenTimeDisabledToday(context)) {
            long limit = Prefs.getDailyLimitMinutes(context) * 60L * 1000L;
            return ScreenTimeState.isDailyLimitReached(
                    Prefs.getUsedMillisToday(context),
                    Prefs.getBonusMillisToday(context),
                    limit
            );
        }
        return false;
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
        long totalSec = rem / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        if (h > 0) {
            return context.getString(R.string.screen_time_remaining_hm, h, m);
        }
        return context.getString(R.string.screen_time_remaining_m, m);
    }

    public static BlockReason currentBlockReason(Context context) {
        if (!Prefs.isKidModeEnabled(context)) {
            return BlockReason.NONE;
        }
        if (Prefs.isBedtimeEnabled(context)
                && !Prefs.isBedtimeDisabledToday(context)
                && ScreenTimeState.isInBedtimeWindow(
                ScreenTimeState.minutesOfDayNow(),
                Prefs.getBedtimeStartMinutes(context),
                Prefs.getBedtimeEndMinutes(context))) {
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
        BEDTIME
    }
}
