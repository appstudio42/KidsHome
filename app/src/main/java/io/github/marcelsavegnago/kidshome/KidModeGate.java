package io.github.marcelsavegnago.kidshome;

import android.os.SystemClock;

/**
 * In-process gate so AccServ / HomePress do not yank away PIN / screen-time lock UI.
 *
 * <p>Visibility is cleared when those screens stop, and the escape flag expires if
 * nothing refreshes it. Timestamps use {@link SystemClock#elapsedRealtime()} so a
 * wall-clock edit cannot keep enforcement paused.
 */
public final class KidModeGate {
    /** Short window after a real close so the transition is not immediately yanked. */
    public static final long GRACE_AFTER_CLOSE_MS = 750L;
    /**
     * Escape UI is trusted only this long after the last open/refresh.
     * A stuck flag (activity stopped without {@code onDestroy}, or marked open
     * before a screen that never appeared) must not suspend enforcement forever.
     */
    public static final long ESCAPE_UI_MAX_AGE_MS = 60_000L;

    private static volatile boolean escapeUiVisible;
    private static volatile boolean screenLockUiVisible;
    private static volatile long escapeUiOpenedElapsedMs;
    private static volatile long graceUntilElapsedMs;
    private static volatile Clock clock = new SystemGateClock();

    private KidModeGate() {
    }

    /** Elapsed-realtime source. Tests install a fake; production uses {@link SystemClock}. */
    interface Clock {
        long elapsedRealtime();
    }

    public static void markEscapeUiOpen() {
        escapeUiVisible = true;
        long now = clock.elapsedRealtime();
        // openedAtMs() is treated as "was really opened" when > 0. A zero reading
        // at boot would look closed to ScreenTimeLockActivity.
        escapeUiOpenedElapsedMs = now > 0L ? now : 1L;
        graceUntilElapsedMs = 0L;
    }

    public static void markEscapeUiClosed() {
        escapeUiVisible = false;
        escapeUiOpenedElapsedMs = 0L;
        graceUntilElapsedMs = clock.elapsedRealtime() + GRACE_AFTER_CLOSE_MS;
    }

    public static void markScreenLockUiOpen() {
        screenLockUiVisible = true;
        graceUntilElapsedMs = 0L;
    }

    public static void markScreenLockUiClosed() {
        screenLockUiVisible = false;
        graceUntilElapsedMs = clock.elapsedRealtime() + GRACE_AFTER_CLOSE_MS;
    }

    public static boolean isEscapeUiActive() {
        long now = clock.elapsedRealtime();
        if (isEscapeUiFresh(now) || screenLockUiVisible) {
            return true;
        }
        return now < graceUntilElapsedMs;
    }

    public static boolean isScreenLockUiActive() {
        return screenLockUiVisible;
    }

    /**
     * True while the screen-time lock or escape PIN activity is actually visible.
     * Unlike {@link #isEscapeUiActive()}, this ignores the short post-close grace window
     * so kid-home time right after unlock can still accrue. A stale escape flag
     * (older than {@link #ESCAPE_UI_MAX_AGE_MS}) does not count.
     */
    public static boolean isLockOrPinUiVisible() {
        return isEscapeUiFresh(clock.elapsedRealtime()) || screenLockUiVisible;
    }

    /**
     * Elapsed-realtime stamp of the last escape-UI open, or {@code 0} when closed.
     * Callers only check {@code > 0}.
     */
    public static long openedAtMs() {
        return escapeUiOpenedElapsedMs;
    }

    private static boolean isEscapeUiFresh(long nowElapsed) {
        if (!escapeUiVisible || escapeUiOpenedElapsedMs <= 0L) {
            return false;
        }
        long age = nowElapsed - escapeUiOpenedElapsedMs;
        // elapsedRealtime restarts on reboot; a stamp from the previous boot is stale.
        if (age < 0L) {
            return false;
        }
        return age < ESCAPE_UI_MAX_AGE_MS;
    }

    static void setClockForTests(Clock replacement) {
        clock = replacement == null ? new SystemGateClock() : replacement;
        escapeUiVisible = false;
        screenLockUiVisible = false;
        escapeUiOpenedElapsedMs = 0L;
        graceUntilElapsedMs = 0L;
    }

    static void resetForTests() {
        setClockForTests(null);
    }

    private static final class SystemGateClock implements Clock {
        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }
}
