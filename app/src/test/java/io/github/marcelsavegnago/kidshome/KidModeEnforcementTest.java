package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KidModeEnforcementTest {
    private static final String OWN = "io.github.marcelsavegnago.kidshome";

    @Test
    public void escapeGateAndDisabledKidModeDoNotRedirect() {
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                true, true, true, true, "com.example.game", false, false, false));
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                false, false, true, true, "com.example.game", false, false, false));
    }

    @Test
    public void blockAndDisallowedPackagesRedirect() {
        assertEquals(KidModeEnforcement.Action.REDIRECT, KidModeEnforcement.enforcementAction(
                true, false, true, false, OWN, true, true, true));
        assertEquals(KidModeEnforcement.Action.REDIRECT, KidModeEnforcement.enforcementAction(
                true, false, false, true, "com.example.game", false, false, false));
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                true, false, false, true, "com.example.game", true, true, true));
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                true, false, false, true, "", false, false, false));
    }

    @Test
    public void allowlistedAppOverItsLimitRedirectsEvenWithoutFullBlocking() {
        assertEquals(KidModeEnforcement.Action.REDIRECT, KidModeEnforcement.enforcementAction(
                true, false, false, false, "com.example.game", true, false, false));
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                true, false, false, false, "com.example.game", true, false, true));
        assertEquals(KidModeEnforcement.Action.ALLOW, KidModeEnforcement.enforcementAction(
                true, false, false, false, "com.other.app", false, false, false));
    }

    @Test
    public void packageAllowListMatchesKidModeRules() {
        assertTrue(KidModeEnforcement.isAllowedInKidMode(
                OWN, OWN, true, "com.kid.app", false, false, false, false));
        assertTrue(KidModeEnforcement.isSystemPackage("com.android.systemui"));
        assertTrue(KidModeEnforcement.isSystemPackage("android"));
        assertTrue(KidModeEnforcement.isAllowedInKidMode(
                "com.android.systemui.panel", OWN, false, null, false, false, false, false));
        assertFalse(KidModeEnforcement.isAllowedInKidMode(
                "com.android.launcher3", OWN, false, null, false, true, false, true));
        assertTrue(KidModeEnforcement.isHijackPackage("com.amazon.firelauncher"));

        assertTrue(KidModeEnforcement.isAllowedInKidMode(
                "com.kid.app", OWN, true, "com.kid.app", true, false, false, false));
        assertFalse(KidModeEnforcement.isAllowedInKidMode(
                "com.other.app", OWN, true, "com.kid.app", false, true, false, true));

        assertTrue(KidModeEnforcement.isAllowedInKidMode(
                "com.example.game", OWN, false, null, false, true, false, true));
        assertFalse(KidModeEnforcement.isAllowedInKidMode(
                "com.example.game", OWN, false, null, false, true, false, false));
        assertTrue(KidModeEnforcement.isAllowedInKidMode(
                "com.browser", OWN, false, null, false, false, true, true));
        assertFalse(KidModeEnforcement.isAllowedInKidMode(
                "com.random.app", OWN, false, null, false, false, false, false));
        assertFalse(KidModeEnforcement.isAllowedInKidMode(
                null, OWN, false, null, false, false, false, false));
    }
}
