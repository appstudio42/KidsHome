package io.github.marcelsavegnago.kidshome;

/**
 * Tracks failed emergency-PIN attempts and applies a progressive delay.
 */
public final class PinAttemptTracker {
    private static int failures;
    private static long lockedUntilMs;

    private PinAttemptTracker() {
    }

    public static void reset() {
        failures = 0;
        lockedUntilMs = 0L;
    }

    public static boolean isLockedOut() {
        return System.currentTimeMillis() < lockedUntilMs;
    }

    public static long remainingLockMs() {
        return Math.max(0L, lockedUntilMs - System.currentTimeMillis());
    }

    /**
     * Records a failure and returns how long (ms) the UI should wait before the next try.
     */
    public static long registerFailure() {
        failures++;
        // 1s, 2s, 4s, 8s, 16s, capped at 30s
        int shift = Math.min(Math.max(failures - 1, 0), 4);
        long delay = Math.min(30_000L, 1_000L * (1L << shift));
        lockedUntilMs = System.currentTimeMillis() + delay;
        return delay;
    }

    public static int failureCount() {
        return failures;
    }

    /** Visible for unit tests. */
    static void resetForTests() {
        reset();
    }
}
