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
}
