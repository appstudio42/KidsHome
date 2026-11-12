package io.github.marcelsavegnago.kidshome;

/**
 * Pure time-math helpers for remote-control API responses and unit tests.
 */
public final class RemoteTimeMath {
    private RemoteTimeMath() {
    }

    public static long clampMinutes(long minutes) {
        if (minutes < 1L) {
            return 0L;
        }
        // Cap a single remote grant so a typo cannot add days of time.
        return Math.min(minutes, 240L);
    }

    public static long minutesToMillis(long minutes) {
        return clampMinutes(minutes) * 60L * 1000L;
    }

    public static long millisToWholeMinutes(long millis) {
        if (millis <= 0L) {
            return 0L;
        }
        if (millis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return millis / 60_000L;
    }

    /**
     * Raw used minutes (wall-clock usage today), not reduced by bonus.
     */
    public static long usedMinutes(long usedMillis) {
        return millisToWholeMinutes(Math.max(0L, usedMillis));
    }

    /**
     * Today's total allowance shown to parents: configured daily limit plus bonus minutes.
     * Example: limit 60 + bonus 30 → 90.
     */
    public static int effectiveLimitMinutes(int dailyLimitMinutes, long bonusMinutes) {
        if (dailyLimitMinutes <= 0) {
            return 0;
        }
        long total = (long) dailyLimitMinutes + Math.max(0L, bonusMinutes);
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    /**
     * Remaining minutes after usage, with bonus extending past the base daily limit.
     * Example: used 25, bonus 30, limit 60 → 65 left (display total 90).
     */
    public static long remainingMinutes(long usedMillis, long bonusMillis, int dailyLimitMinutes) {
        if (dailyLimitMinutes <= 0) {
            return 0L;
        }
        long rem = ScreenTimeState.remainingMillis(
                usedMillis,
                bonusMillis,
                dailyLimitMinutes * 60L * 1000L
        );
        return millisToWholeMinutes(rem);
    }

    public static boolean isPresetOrCustomMinutes(int minutes) {
        return minutes == 15 || minutes == 30 || minutes == 60 || (minutes >= 1 && minutes <= 240);
    }
}
