package io.github.marcelsavegnago.kidshome;

import android.os.SystemClock;

/**
 * Decides whether the usage day may roll over.
 *
 * <p>The wall clock is not trusted across a jump. Between samples we compare the
 * wall delta with the {@link SystemClock#elapsedRealtime()} delta. A backward or
 * forward jump beyond {@link #JUMP_TOLERANCE_MS}, a reboot (elapsed clock
 * restarted), or a timezone change that does not move the monotonic timeline
 * keeps the stored day key. The key advances only when trusted time, projected
 * with elapsed realtime, reaches the midnight that was scheduled for that day.
 */
public final class ScreenTimeClock {
    /**
     * Wall/elapsed disagreement inside this window is drift (scheduling, NTP
     * step of a few seconds), not a hand edit of the clock.
     */
    public static final long JUMP_TOLERANCE_MS = 180_000L;

    private static volatile Clock clock = new SystemTimeClock();

    private ScreenTimeClock() {
    }

    /** Wall + monotonic sample. Tests install a fake. */
    public interface Clock {
        long currentTimeMillis();

        long elapsedRealtime();
    }

    public static final class Decision {
        public final String dayKey;
        /** True when counters for the previous key must be archived and cleared. */
        public final boolean rollDay;
        public final long anchorWallMs;
        public final long anchorElapsedMs;
        public final long nextMidnightMs;
        /** True when this sample disagreed with the monotonic clock or rebooted. */
        public final boolean discontinuity;

        public Decision(
                String dayKey,
                boolean rollDay,
                long anchorWallMs,
                long anchorElapsedMs,
                long nextMidnightMs,
                boolean discontinuity
        ) {
            this.dayKey = dayKey == null ? "" : dayKey;
            this.rollDay = rollDay;
            this.anchorWallMs = anchorWallMs;
            this.anchorElapsedMs = anchorElapsedMs;
            this.nextMidnightMs = nextMidnightMs;
            this.discontinuity = discontinuity;
        }
    }

    public static Clock clock() {
        return clock;
    }

    static void setClockForTests(Clock replacement) {
        clock = replacement == null ? new SystemTimeClock() : replacement;
    }

    static void resetClockForTests() {
        clock = new SystemTimeClock();
    }

    /**
     * @param storedDayKey persisted usage day, possibly empty on first run
     * @param anchorWallMs last trusted wall instant, or {@code <= 0} if unknown
     * @param anchorElapsedMs elapsed realtime at that anchor, or {@code <= 0} if unknown
     * @param nextMidnightMs absolute millis of the next local midnight for the stored day
     */
    public static Decision resolve(
            String storedDayKey,
            long anchorWallMs,
            long anchorElapsedMs,
            long nextMidnightMs,
            long nowWallMs,
            long nowElapsedMs
    ) {
        boolean anchored = anchorWallMs > 0L
                && anchorElapsedMs > 0L
                && nextMidnightMs > anchorWallMs;
        if (!anchored) {
            String wallKey = ScreenTimeState.dayKey(nowWallMs);
            boolean roll = storedDayKey == null
                    || storedDayKey.isEmpty()
                    || !storedDayKey.equals(wallKey);
            return new Decision(
                    wallKey,
                    roll,
                    nowWallMs,
                    nowElapsedMs,
                    ScreenTimeState.nextLocalMidnightMillis(nowWallMs),
                    false);
        }

        long wallDelta = nowWallMs - anchorWallMs;
        long elapsedDelta = nowElapsedMs - anchorElapsedMs;
        // elapsedRealtime only goes backwards across a reboot.
        boolean reboot = elapsedDelta < 0L;
        boolean wallJump = !reboot && Math.abs(wallDelta - elapsedDelta) > JUMP_TOLERANCE_MS;
        boolean discontinuity = reboot || wallJump;

        long trustedWall;
        if (reboot) {
            // Downtime while powered off is unknown. Do not adopt the new wall
            // clock; continue the previous trusted instant on this boot.
            trustedWall = anchorWallMs;
        } else if (wallJump) {
            trustedWall = anchorWallMs + elapsedDelta;
        } else {
            trustedWall = nowWallMs;
        }

        if (trustedWall >= nextMidnightMs) {
            String newKey = ScreenTimeState.dayKey(trustedWall);
            long newNext = ScreenTimeState.nextLocalMidnightMillis(trustedWall);
            if (storedDayKey != null && storedDayKey.equals(newKey)) {
                // Already on this key (zone glitch at the boundary). Push the
                // deadline forward so we do not spin, and do not clear usage.
                return new Decision(
                        storedDayKey, false, trustedWall, nowElapsedMs, newNext, discontinuity);
            }
            boolean roll = storedDayKey == null
                    || storedDayKey.isEmpty()
                    || !storedDayKey.equals(newKey);
            return new Decision(newKey, roll, trustedWall, nowElapsedMs, newNext, discontinuity);
        }

        String keep = (storedDayKey == null || storedDayKey.isEmpty())
                ? ScreenTimeState.dayKey(trustedWall)
                : storedDayKey;
        // Stay on the monotonic projection. Adopting a jumped wall clock would
        // let a same-day forward set reach midnight early and clear the allowance.
        long anchorOut = discontinuity ? trustedWall : nowWallMs;
        return new Decision(keep, false, anchorOut, nowElapsedMs, nextMidnightMs, discontinuity);
    }

    private static final class SystemTimeClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }
}
