package io.github.marcelsavegnago.kidshome;

/**
 * In-process gate so AccServ / HomePress do not yank away the emergency PIN UI.
 */
public final class KidModeGate {
    private static volatile boolean escapeUiVisible;
    private static volatile long escapeUiOpenedAtMs;

    /** Keep the gate open briefly after the activity finishes so Home redirects don't race. */
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

    public static boolean isEscapeUiActive() {
        if (escapeUiVisible) {
            return true;
        }
        return System.currentTimeMillis() < graceUntilMs;
    }

    public static long openedAtMs() {
        return escapeUiOpenedAtMs;
    }
}
