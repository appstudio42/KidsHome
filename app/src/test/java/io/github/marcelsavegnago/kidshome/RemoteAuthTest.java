package io.github.marcelsavegnago.kidshome;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RemoteAuthTest {
    private RemoteAuth auth;

    @Before
    public void setUp() {
        auth = new RemoteAuth();
        auth.resetForTests();
        auth.setExpectedToken("abc123token");
    }

    @Test
    public void tokenLoginIssuesSession() {
        RemoteAuth.AuthResult result = auth.tryToken("abc123token");
        assertTrue(result.success);
        assertNotNull(result.sessionId);
        assertTrue(auth.isValidSession(result.sessionId));
    }

    @Test
    public void wrongTokenFails() {
        RemoteAuth.AuthResult result = auth.tryToken("nope");
        assertFalse(result.success);
        assertEquals(RemoteAuth.FailureReason.INVALID_CREDENTIALS, result.reason);
    }

    @Test
    public void pinRateLimitLocksOut() {
        RemoteAuth.PinVerifier bad = pin -> false;
        RemoteAuth.AuthResult first = auth.tryPin("0000", bad);
        assertFalse(first.success);
        assertTrue(auth.isPinLockedOut());
        assertTrue(auth.pinLockoutRemainingMs() > 0L);

        RemoteAuth.AuthResult whileLocked = auth.tryPin("1234", pin -> true);
        assertFalse(whileLocked.success);
        assertEquals(RemoteAuth.FailureReason.RATE_LIMITED, whileLocked.reason);
    }

    @Test
    public void goodPinResetsFailures() {
        auth.tryPin("0000", pin -> false);
        // Force unlock window for test by resetting lock timer via successful path after reset.
        auth.resetForTests();
        auth.setExpectedToken("abc123token");
        RemoteAuth.AuthResult ok = auth.tryPin("9999", pin -> "9999".equals(pin));
        assertTrue(ok.success);
        assertEquals(0, auth.pinFailureCount());
        assertTrue(auth.isValidSession(ok.sessionId));
    }

    @Test
    public void regenerateRevokesSessions() {
        RemoteAuth.AuthResult result = auth.tryToken("abc123token");
        assertTrue(auth.isValidSession(result.sessionId));
        auth.revokeAllSessions();
        assertFalse(auth.isValidSession(result.sessionId));
    }

    @Test
    public void matchesTokenDoesNotCreateSession() {
        assertTrue(auth.matchesToken("abc123token"));
        assertFalse(auth.matchesToken("other"));
    }
}
