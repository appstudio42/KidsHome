package io.github.marcelsavegnago.kidshome;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure helpers for daily limits / bedtime windows (device local timezone).
 */
public final class ScreenTimeState {
    private ScreenTimeState() {
    }

    public static String dayKeyNow() {
        return dayKey(System.currentTimeMillis());
    }

    public static String dayKey(long epochMs) {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        cal.setTimeInMillis(epochMs);
        return String.format(Locale.US, "%04d%02d%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    public static int minutesOfDayNow() {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /** True if now is inside [start, end) allowing overnight windows (e.g. 20:00–07:00). */
    public static boolean isInBedtimeWindow(int nowMinutes, int startMinutes, int endMinutes) {
        if (startMinutes == endMinutes) {
            return false;
        }
        if (startMinutes < endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }
        // Overnight: e.g. 20:00 (1200) to 07:00 (420)
        return nowMinutes >= startMinutes || nowMinutes < endMinutes;
    }

    /**
     * Remaining allowance for today. Bonus minutes extend the daily limit rather than
     * being clamped by it — e.g. limit 60, used 25, bonus 30 → 65 remaining (not 60).
     */
    public static long remainingMillis(long usedMillis, long bonusMillis, long dailyLimitMillis) {
        long used = Math.max(0L, usedMillis);
        long bonus = Math.max(0L, bonusMillis);
        long limit = Math.max(0L, dailyLimitMillis);
        return Math.max(0L, limit - used + bonus);
    }

    public static boolean isDailyLimitReached(long usedMillis, long bonusMillis, long dailyLimitMillis) {
        return remainingMillis(usedMillis, bonusMillis, dailyLimitMillis) <= 0L;
    }
}
