package io.github.marcelsavegnago.kidshome;

/**
 * In-process gate so AccServ / HomePress do not yank away PIN / screen-time lock UI.
 */
public final class KidModeGate {
    private static volatile boolean escapeUiVisible;
    private static volatile boolean screenLockUiVisible;
    private static volatile long escapeUiOpenedAtMs;

    private static final long GRACE_AFTER_CLOSE_MS = 750L;
    private static volatile long graceUntilMs;

    private KidModeGate() {
    }

    public static void markEscapeUiOpen() {
        escapeUiVisible = true;
        escapeUiOpenedAtMs = System.currentTimeMillis();
        graceUntilMs = 0L;
    }

    public static void markEscapeUiClosed() {
        escapeUiVisible = false;
        escapeUiOpenedAtMs = 0L;
        graceUntilMs = System.currentTimeMillis() + GRACE_AFTER_CLOSE_MS;
    }

    public static void markScreenLockUiOpen() {
        screenLockUiVisible = true;
        graceUntilMs = 0L;
    }

    public static void markScreenLockUiClosed() {
        screenLockUiVisible = false;
        graceUntilMs = System.currentTimeMillis() + GRACE_AFTER_CLOSE_MS;
    }

    public static boolean isEscapeUiActive() {
        if (escapeUiVisible || screenLockUiVisible) {
            return true;
        }
        return System.currentTimeMillis() < graceUntilMs;
    }

    public static boolean isScreenLockUiActive() {
        return screenLockUiVisible;
    }

    /**
     * True while the screen-time lock or escape PIN activity is actually visible.
     * Unlike {@link #isEscapeUiActive()}, this ignores the short post-close grace window
     * so kid-home time right after unlock can still accrue.
     */
    public static boolean isLockOrPinUiVisible() {
        return escapeUiVisible || screenLockUiVisible;
    }

    public static long openedAtMs() {
        return escapeUiOpenedAtMs;
    }
}
