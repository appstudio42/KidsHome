package io.github.marcelsavegnago.kidshome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Failed parent-PIN attempts and the lockout that follows them.
 *
 * <p>State is written to a private preferences file on every change so a
 * force-stop, low-memory kill, or reboot cannot reset the counter. The
 * deadline uses {@link SystemClock#elapsedRealtime()}, which ignores wall-clock
 * edits. After a reboot that monotonic clock starts over, so an active lockout
 * is armed again for the full current step instead of trusting
 * {@code currentTimeMillis}.
 */
public final class PinAttemptTracker {
    static final long[] DELAYS_MS = {
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            30_000L,
            60_000L,
            120_000L,
            300_000L,
            900_000L
    };

    private static final long MAX_DELAY_MS = DELAYS_MS[DELAYS_MS.length - 1];
    private static final String PREFS_NAME = "pin_attempt_lockout";
    private static final String KEY_FAILURES = "failures";
    private static final String KEY_ACTIVE = "lock_active";
    private static final String KEY_DEADLINE = "deadline_elapsed_realtime";
    private static final String KEY_ANCHOR = "anchor_elapsed_realtime";

    private static AttemptStore store;
    private static AttemptClock clock;
    private static boolean testMode;

    private static int failures;
    private static boolean lockActive;
    private static long deadlineElapsed;
    private static long anchorElapsed;

    private PinAttemptTracker() {
    }

    /** Loads any lockout saved by a previous process. Safe to call more than once. */
    public static void attach(Context context) {
        if (context == null) {
            return;
        }
        synchronized (PinAttemptTracker.class) {
            if (testMode) {
                return;
            }
            if (!(store instanceof PreferencesAttemptStore)) {
                store = new PreferencesAttemptStore(context.getApplicationContext());
                clock = new SystemAttemptClock();
                loadFromStore();
            }
            reconcile(clock.elapsedRealtime());
        }
    }

    public static synchronized void reset() {
        ensureReady();
        failures = 0;
        lockActive = false;
        deadlineElapsed = 0L;
        anchorElapsed = clock.elapsedRealtime();
        save();
    }

    public static synchronized boolean isLockedOut() {
        return remainingLockMs() > 0L;
    }

    public static synchronized long remainingLockMs() {
        ensureReady();
        long now = clock.elapsedRealtime();
        reconcile(now);
        if (!lockActive) {
            return 0L;
        }
        long remaining = deadlineElapsed - now;
        if (remaining < 0L) {
            return 0L;
        }
        return Math.min(remaining, MAX_DELAY_MS);
    }

    /**
     * Records a failure and returns how long (ms) the UI should wait before the next try.
     */
    public static synchronized long registerFailure() {
        ensureReady();
        long now = clock.elapsedRealtime();
        reconcile(now);
        now = clock.elapsedRealtime();
        failures++;
        long delay = delayForFailures(failures);
        lockActive = true;
        anchorElapsed = now;
        deadlineElapsed = now + delay;
        save();
        return delay;
    }

    public static synchronized int failureCount() {
        ensureReady();
        return failures;
    }

    /** Visible for unit tests. */
    static synchronized void resetForTests() {
        testMode = true;
        store = new MemoryAttemptStore();
        clock = new FixedClock(1_000_000L, 1_700_000_000_000L);
        failures = 0;
        lockActive = false;
        deadlineElapsed = 0L;
        anchorElapsed = 0L;
        save();
    }

    /** Visible for unit tests. Replaces the process-local cache with {@code attemptStore}. */
    static synchronized void installForTests(AttemptStore attemptStore, AttemptClock attemptClock) {
        if (attemptStore == null || attemptClock == null) {
            throw new IllegalArgumentException("store and clock are required");
        }
        testMode = true;
        store = attemptStore;
        clock = attemptClock;
        loadFromStore();
        reconcile(clock.elapsedRealtime());
    }

    static long delayForFailures(int failureCount) {
        int index = failureCount - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= DELAYS_MS.length) {
            index = DELAYS_MS.length - 1;
        }
        return DELAYS_MS[index];
    }

    private static void ensureReady() {
        if (store == null || clock == null) {
            throw new IllegalStateException("PinAttemptTracker.attach(Context) was not called");
        }
    }

    private static void loadFromStore() {
        Snapshot snapshot = store.load();
        if (snapshot == null) {
            snapshot = new Snapshot(0, false, 0L, 0L);
        }
        failures = Math.max(0, snapshot.failures);
        lockActive = snapshot.lockActive;
        deadlineElapsed = snapshot.deadlineElapsed;
        anchorElapsed = snapshot.anchorElapsed;
    }

    private static void save() {
        store.save(new Snapshot(failures, lockActive, deadlineElapsed, anchorElapsed));
    }

    /**
     * Same boot: honor the elapsedRealtime deadline and ignore the wall clock.
     * New boot: elapsedRealtime dropped below the saved anchor, so re-arm the
     * step that was still active. A finished lockout stays finished.
     */
    private static void reconcile(long now) {
        if (anchorElapsed > 0L && now < anchorElapsed) {
            long leftover = 0L;
            if (lockActive) {
                leftover = deadlineElapsed - anchorElapsed;
                if (leftover < 0L) {
                    leftover = 0L;
                } else if (leftover > MAX_DELAY_MS) {
                    leftover = MAX_DELAY_MS;
                }
            }
            anchorElapsed = now;
            if (leftover > 0L) {
                lockActive = true;
                deadlineElapsed = now + leftover;
            } else {
                lockActive = false;
                deadlineElapsed = 0L;
            }
            save();
            return;
        }
        if (!lockActive) {
            return;
        }
        long remaining = deadlineElapsed - now;
        if (remaining <= 0L) {
            lockActive = false;
            deadlineElapsed = 0L;
            anchorElapsed = now;
            save();
        } else if (remaining > MAX_DELAY_MS) {
            deadlineElapsed = now + MAX_DELAY_MS;
            anchorElapsed = now;
            save();
        }
    }

    interface AttemptStore {
        Snapshot load();

        void save(Snapshot snapshot);
    }

    interface AttemptClock {
        long elapsedRealtime();

        /** Wall clock. Lockout math must not use this as the deadline. */
        long wallTimeMillis();
    }

    static final class Snapshot {
        final int failures;
        final boolean lockActive;
        final long deadlineElapsed;
        final long anchorElapsed;

        Snapshot(int failures, boolean lockActive, long deadlineElapsed, long anchorElapsed) {
            this.failures = failures;
            this.lockActive = lockActive;
            this.deadlineElapsed = deadlineElapsed;
            this.anchorElapsed = anchorElapsed;
        }
    }

    static final class MemoryAttemptStore implements AttemptStore {
        private Snapshot snapshot = new Snapshot(0, false, 0L, 0L);

        @Override
        public Snapshot load() {
            return snapshot;
        }

        @Override
        public void save(Snapshot snapshot) {
            this.snapshot = snapshot == null
                    ? new Snapshot(0, false, 0L, 0L)
                    : snapshot;
        }
    }

    private static final class FixedClock implements AttemptClock {
        private final long elapsed;
        private final long wall;

        FixedClock(long elapsed, long wall) {
            this.elapsed = elapsed;
            this.wall = wall;
        }

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }

        @Override
        public long wallTimeMillis() {
            return wall;
        }
    }

    private static final class SystemAttemptClock implements AttemptClock {
        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public long wallTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    private static final class PreferencesAttemptStore implements AttemptStore {
        private final SharedPreferences prefs;

        PreferencesAttemptStore(Context context) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public Snapshot load() {
            return new Snapshot(
                    prefs.getInt(KEY_FAILURES, 0),
                    prefs.getBoolean(KEY_ACTIVE, false),
                    prefs.getLong(KEY_DEADLINE, 0L),
                    prefs.getLong(KEY_ANCHOR, 0L)
            );
        }

        @Override
        @SuppressLint("ApplySharedPref")
        public void save(Snapshot snapshot) {
            Snapshot value = snapshot == null ? new Snapshot(0, false, 0L, 0L) : snapshot;
            // commit() returns only after the write is flushed, so a force-stop
            // immediately after a wrong PIN cannot drop the new count.
            prefs.edit()
                    .putInt(KEY_FAILURES, value.failures)
                    .putBoolean(KEY_ACTIVE, value.lockActive)
                    .putLong(KEY_DEADLINE, value.deadlineElapsed)
                    .putLong(KEY_ANCHOR, value.anchorElapsed)
                    .commit();
        }
    }
}
