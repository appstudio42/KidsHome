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
     * Effective used minutes after subtracting bonus (never negative).
     */
    public static long effectiveUsedMinutes(long usedMillis, long bonusMillis) {
        long effective = Math.max(0L, usedMillis - Math.max(0L, bonusMillis));
        return effective / 60_000L;
    }

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
