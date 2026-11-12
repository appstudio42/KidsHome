package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeResolverTest {

    @Test
    public void autoPrefersDeviceOwner() {
        assertEquals(
                EnforcementMode.DEVICE_OWNER,
                ModeResolver.resolve(EnforcementMode.AUTO, true, true)
        );
        assertEquals(
                EnforcementMode.DEVICE_OWNER,
                ModeResolver.resolve(EnforcementMode.AUTO, true, false)
        );
    }

    @Test
    public void autoPrefersAccessibilityOnFire() {
        assertEquals(
                EnforcementMode.ACCESSIBILITY,
                ModeResolver.resolve(EnforcementMode.AUTO, false, true)
        );
    }

    @Test
    public void autoPrefersLauncherOnRegularAndroid() {
        assertEquals(
                EnforcementMode.LAUNCHER,
                ModeResolver.resolve(EnforcementMode.AUTO, false, false)
        );
    }

    @Test
    public void manualOverrideHonored() {
        assertEquals(
                EnforcementMode.LAUNCHER,
                ModeResolver.resolve(EnforcementMode.LAUNCHER, false, true)
        );
        assertEquals(
                EnforcementMode.ACCESSIBILITY,
                ModeResolver.resolve(EnforcementMode.ACCESSIBILITY, false, false)
        );
        assertEquals(
                EnforcementMode.DEVICE_OWNER,
                ModeResolver.resolve(EnforcementMode.DEVICE_OWNER, false, false)
        );
    }

    @Test
    public void prefsRoundTrip() {
        assertEquals(EnforcementMode.AUTO, EnforcementMode.fromPrefsValue("auto"));
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.fromPrefsValue("accessibility"));
        assertEquals(EnforcementMode.LAUNCHER, EnforcementMode.fromPrefsValue("launcher"));
        assertEquals(EnforcementMode.DEVICE_OWNER, EnforcementMode.fromPrefsValue("device_owner"));
        assertEquals(EnforcementMode.AUTO, EnforcementMode.fromPrefsValue(null));
        assertEquals("launcher", EnforcementMode.LAUNCHER.toPrefsValue());
    }

    @Test
    public void hijackOnlyInAccessibilityMode() {
        assertTrue(ModeResolver.shouldHijackHome(EnforcementMode.ACCESSIBILITY));
        assertFalse(ModeResolver.shouldHijackHome(EnforcementMode.LAUNCHER));
        assertFalse(ModeResolver.shouldHijackHome(EnforcementMode.DEVICE_OWNER));
    }

    @Test
    public void blockingRules() {
        assertTrue(ModeResolver.shouldBlockNonAllowed(EnforcementMode.ACCESSIBILITY, false));
        assertFalse(ModeResolver.shouldBlockNonAllowed(EnforcementMode.LAUNCHER, false));
        assertTrue(ModeResolver.shouldBlockNonAllowed(EnforcementMode.LAUNCHER, true));
        assertTrue(ModeResolver.shouldBlockNonAllowed(EnforcementMode.DEVICE_OWNER, true));
    }
}
