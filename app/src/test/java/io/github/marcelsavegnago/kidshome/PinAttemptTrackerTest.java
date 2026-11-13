package io.github.marcelsavegnago.kidshome;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinAttemptTrackerTest {
    @Before
    public void setUp() {
        PinAttemptTracker.resetForTests();
    }

    @Test
    public void progressiveDelayIncreases() {
        long d1 = PinAttemptTracker.registerFailure();
        long d2 = PinAttemptTracker.registerFailure();
        long d3 = PinAttemptTracker.registerFailure();
        assertEquals(1000L, d1);
        assertEquals(2000L, d2);
        assertEquals(4000L, d3);
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(3, PinAttemptTracker.failureCount());
    }

    @Test
    public void resetClearsLockout() {
        PinAttemptTracker.registerFailure();
        PinAttemptTracker.reset();
        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(0, PinAttemptTracker.failureCount());
    }

    @Test
    public void delayEscalatesThenCaps() {
        assertEquals(30_000L, PinAttemptTracker.delayForFailures(6));
        assertEquals(60_000L, PinAttemptTracker.delayForFailures(7));
        assertEquals(900_000L, PinAttemptTracker.delayForFailures(10));
        assertEquals(900_000L, PinAttemptTracker.delayForFailures(40));

        FakeClock clock = new FakeClock(5_000_000L, 1_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        PinAttemptTracker.installForTests(store, clock);
        long last = 0L;
        for (int i = 0; i < PinAttemptTracker.DELAYS_MS.length; i++) {
            clock.elapsed += last;
            last = PinAttemptTracker.registerFailure();
            assertEquals(PinAttemptTracker.DELAYS_MS[i], last);
        }
        clock.elapsed += last;
        assertEquals(900_000L, PinAttemptTracker.registerFailure());
    }

    @Test
    public void expiredLockKeepsFailureCount() {
        FakeClock clock = new FakeClock(5_000_000L, 1_000_000L);
        PinAttemptTracker.installForTests(new PinAttemptTracker.MemoryAttemptStore(), clock);
        PinAttemptTracker.registerFailure();
        clock.elapsed += 1_000L;
        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(1, PinAttemptTracker.failureCount());
        assertEquals(2_000L, PinAttemptTracker.registerFailure());
    }

    @Test
    public void processDeathKeepsCountAndRemainingLock() {
        FakeClock clock = new FakeClock(5_000_000L, 1_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        PinAttemptTracker.installForTests(store, clock);
        PinAttemptTracker.registerFailure();
        PinAttemptTracker.registerFailure();
        clock.elapsed += 500L;

        PinAttemptTracker.installForTests(store, clock);

        assertEquals(2, PinAttemptTracker.failureCount());
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(1_500L, PinAttemptTracker.remainingLockMs());
    }

    @Test
    public void resetSurvivesReload() {
        FakeClock clock = new FakeClock(5_000_000L, 1_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        PinAttemptTracker.installForTests(store, clock);
        PinAttemptTracker.registerFailure();
        PinAttemptTracker.reset();

        PinAttemptTracker.installForTests(store, clock);

        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(0, PinAttemptTracker.failureCount());
    }

    @Test
    public void wallClockJumpDoesNotChangeLockout() {
        FakeClock clock = new FakeClock(60_000L, 1_700_000_000_000L);
        PinAttemptTracker.installForTests(new PinAttemptTracker.MemoryAttemptStore(), clock);
        PinAttemptTracker.registerFailure();
        long before = PinAttemptTracker.remainingLockMs();

        clock.wall += 86_400_000L;
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(before, PinAttemptTracker.remainingLockMs());

        clock.wall = 0L;
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(before, PinAttemptTracker.remainingLockMs());
        assertEquals(1, PinAttemptTracker.failureCount());
    }

    @Test
    public void rebootRearmsActiveLockAndIgnoresWallClock() {
        FakeClock clock = new FakeClock(800_000L, 1_700_000_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        PinAttemptTracker.installForTests(store, clock);
        assertEquals(1_000L, PinAttemptTracker.registerFailure());

        clock.elapsed = 1_000L;
        clock.wall += 3_600_000L;

        assertEquals(1, PinAttemptTracker.failureCount());
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(1_000L, PinAttemptTracker.remainingLockMs());
        PinAttemptTracker.Snapshot saved = store.load();
        assertTrue(saved.lockActive);
        assertEquals(1_000L, saved.anchorElapsed);
        assertEquals(2_000L, saved.deadlineElapsed);
    }

    @Test
    public void rebootAfterObservedExpiryDoesNotRelock() {
        FakeClock clock = new FakeClock(500_000L, 1_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        PinAttemptTracker.installForTests(store, clock);
        PinAttemptTracker.registerFailure();
        PinAttemptTracker.registerFailure();
        clock.elapsed += 2_000L;
        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(2, PinAttemptTracker.failureCount());

        clock.elapsed = 40L;
        clock.wall += 86_400_000L;

        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(0L, PinAttemptTracker.remainingLockMs());
        assertEquals(2, PinAttemptTracker.failureCount());
        assertFalse(store.load().lockActive);
    }

    @Test
    public void corruptLongDeadlineIsCapped() {
        FakeClock clock = new FakeClock(50_000L, 1_000_000L);
        PinAttemptTracker.MemoryAttemptStore store = new PinAttemptTracker.MemoryAttemptStore();
        store.save(new PinAttemptTracker.Snapshot(4, true, Long.MAX_VALUE / 4, 10_000L));
        PinAttemptTracker.installForTests(store, clock);

        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(900_000L, PinAttemptTracker.remainingLockMs());
        assertEquals(4, PinAttemptTracker.failureCount());
    }

    private static final class FakeClock implements PinAttemptTracker.AttemptClock {
        long elapsed;
        long wall;

        FakeClock(long elapsed, long wall) {
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
}
