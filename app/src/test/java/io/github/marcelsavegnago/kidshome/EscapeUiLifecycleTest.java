package io.github.marcelsavegnago.kidshome;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The escape / lock gate follows the activity lifecycle, not only onDestroy.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class EscapeUiLifecycleTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        KidModeGate.resetForTests();
        SessionAuth.lock();
        PinAttemptTracker.attach(context);
        PinAttemptTracker.reset();
        Prefs.setOnboardingDone(context, true);
        Prefs.setKidModeEnabled(context, true);
    }

    @After
    public void tearDown() {
        KidModeGate.resetForTests();
    }

    @Test
    public void escapePinStopClearsTheGateAndRestartRestoresIt() {
        ActivityController<EscapePinActivity> controller =
                Robolectric.buildActivity(EscapePinActivity.class).create().start().resume();
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());

        controller.pause().stop();
        assertFalse(KidModeGate.isLockOrPinUiVisible());
        ShadowSystemClock.advanceBy(Duration.ofMillis(KidModeGate.GRACE_AFTER_CLOSE_MS + 1L));
        assertFalse(KidModeGate.isEscapeUiActive());

        controller.restart();
        assertTrue(KidModeGate.isEscapeUiActive());
        assertTrue(KidModeGate.isLockOrPinUiVisible());
    }

    @Test
    public void screenLockStopClearsTheGateAndRestartRestoresIt() {
        Prefs.setRemoteForceLocked(context, true);
        ActivityController<ScreenTimeLockActivity> controller =
                Robolectric.buildActivity(ScreenTimeLockActivity.class).create().start().resume();
        assertTrue(KidModeGate.isScreenLockUiActive());
        assertFalse(controller.get().isFinishing());

        controller.pause().stop();
        assertFalse(KidModeGate.isScreenLockUiActive());
        assertFalse(KidModeGate.isLockOrPinUiVisible());
        ShadowSystemClock.advanceBy(Duration.ofMillis(KidModeGate.GRACE_AFTER_CLOSE_MS + 1L));
        assertFalse(KidModeGate.isEscapeUiActive());

        controller.restart();
        assertTrue(KidModeGate.isScreenLockUiActive());
        assertFalse(controller.get().isFinishing());
    }
}
