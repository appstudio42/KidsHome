package io.github.marcelsavegnago.kidshome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KidModeGateTest {
    private final FakeClock clock = new FakeClock();

    @Before
    public void setUp() {
        clock.elapsed = 10_000L;
        KidModeGate.setClockForTests(clock);
    }

    @After
    public void tearDown() {
        KidModeGate.resetForTests();
    }

    @Test
    public void escapeUiStopsEnforcementUntilClosed() {
        KidModeGate.markEscapeUiOpen();
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());

        KidModeGate.markEscapeUiClosed();
        assertFalse(KidModeGate.isLockOrPinUiVisible());
        assertTrue(KidModeGate.isEscapeUiActive());

        clock.elapsed += KidModeGate.GRACE_AFTER_CLOSE_MS;
        assertFalse(KidModeGate.isEscapeUiActive());
    }

    @Test
    public void escapeFlagExpiresWhenNothingRefreshesIt() {
        KidModeGate.markEscapeUiOpen();
        clock.elapsed += KidModeGate.ESCAPE_UI_MAX_AGE_MS - 1L;
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());

        clock.elapsed += 1L;
        assertFalse(KidModeGate.isEscapeUiActive());
        assertFalse(KidModeGate.isLockOrPinUiVisible());
    }

    @Test
    public void refreshingTheOpenStampExtendsTheWindow() {
        KidModeGate.markEscapeUiOpen();
        clock.elapsed += 50_000L;
        KidModeGate.markEscapeUiOpen();
        clock.elapsed += 50_000L;
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());
    }

    @Test
    public void screenLockStaysUpWithoutTheEscapeTimeout() {
        KidModeGate.markScreenLockUiOpen();
        clock.elapsed += KidModeGate.ESCAPE_UI_MAX_AGE_MS * 4L;
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isScreenLockUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());

        KidModeGate.markScreenLockUiClosed();
        assertFalse(KidModeGate.isLockOrPinUiVisible());
        clock.elapsed += KidModeGate.GRACE_AFTER_CLOSE_MS;
        assertFalse(KidModeGate.isEscapeUiActive());
    }

    @Test
    public void stoppedEscapeDoesNotKeepScreenTimePaused() {
        KidModeGate.markEscapeUiOpen();
        assertFalse(ScreenTimeState.shouldAccrueUsage(
                true, false, KidModeGate.isLockOrPinUiVisible()));

        KidModeGate.markEscapeUiClosed();
        assertFalse(KidModeGate.isLockOrPinUiVisible());
        assertEquals(20_000L, ScreenTimeState.millisToAccrue(
                20_000L, true, false, KidModeGate.isLockOrPinUiVisible()));
    }

    @Test
    public void rebootDropsAStampFromThePreviousBoot() {
        KidModeGate.markEscapeUiOpen();
        clock.elapsed = 500L;
        assertFalse(KidModeGate.isEscapeUiActive());
        assertFalse(KidModeGate.isLockOrPinUiVisible());
    }

    private static final class FakeClock implements KidModeGate.Clock {
        long elapsed;

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }
    }
}
