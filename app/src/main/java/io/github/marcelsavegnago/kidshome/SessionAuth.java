package io.github.marcelsavegnago.kidshome;

/**
 * In-memory unlock flag so the parent does not re-enter the PIN for every screen in one session.
 */
public final class SessionAuth {
    private static volatile boolean unlocked;
    private static volatile long unlockedAtMs;

    private static final long UNLOCK_TTL_MS = 5 * 60 * 1000L;

    private SessionAuth() {
    }

    public static void markUnlocked() {
        unlocked = true;
        unlockedAtMs = System.currentTimeMillis();
    }

    public static void lock() {
        unlocked = false;
        unlockedAtMs = 0L;
    }

    public static boolean isUnlocked() {
        if (!unlocked) {
            return false;
        }
        if (System.currentTimeMillis() - unlockedAtMs > UNLOCK_TTL_MS) {
            lock();
            return false;
        }
        return true;
    }
}
