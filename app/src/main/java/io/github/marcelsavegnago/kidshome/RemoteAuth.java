package io.github.marcelsavegnago.kidshome;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pairing-token / PIN session auth for the LAN remote control server.
 * Pure JVM logic — no Android dependencies (unit-test friendly).
 */
public final class RemoteAuth {
    /** Parent remote session lifetime. Long enough for an evening at home, not a permanent credential. */
    public static final long SESSION_TTL_MS = 12L * 60L * 60L * 1000L; // 12 hours
    public static final String COOKIE_NAME = "kh_session";

    /**
     * Session cookie for the LAN page.
     * HttpOnly so page scripts cannot read it, SameSite=Strict so other sites cannot send it.
     * Secure is intentionally absent: the page is plain HTTP on the local network, and the
     * Secure flag would make browsers drop the cookie.
     */
    public static String sessionCookieHeader(String sessionId) {
        long maxAge = SESSION_TTL_MS / 1000L;
        return COOKIE_NAME + "=" + sessionId
                + "; Path=/; Max-Age=" + maxAge
                + "; HttpOnly; SameSite=Strict";
    }

    /** Clears the session cookie with the same flags used when it was set. */
    public static String clearSessionCookieHeader() {
        return COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict";
    }

    public enum FailureReason {
        NONE,
        INVALID_CREDENTIALS,
        RATE_LIMITED
    }

    public static final class AuthResult {
        public final boolean success;
        public final FailureReason reason;
        public final String sessionId;
        public final long lockoutRemainingMs;

        private AuthResult(boolean success, FailureReason reason, String sessionId, long lockoutRemainingMs) {
            this.success = success;
            this.reason = reason;
            this.sessionId = sessionId;
            this.lockoutRemainingMs = lockoutRemainingMs;
        }

        public static AuthResult ok(String sessionId) {
            return new AuthResult(true, FailureReason.NONE, sessionId, 0L);
        }

        public static AuthResult fail(FailureReason reason, long lockoutRemainingMs) {
            return new AuthResult(false, reason, null, Math.max(0L, lockoutRemainingMs));
        }
    }

    public interface PinVerifier {
        boolean verify(String pin);
    }

    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private int pinFailures;
    private long pinLockedUntilMs;
    private String expectedToken = "";

    public synchronized void setExpectedToken(String token) {
        expectedToken = token == null ? "" : token;
    }

    public synchronized String getExpectedToken() {
        return expectedToken;
    }

    /** Generates a URL-safe pairing token (32 hex chars). */
    public static String generateToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public synchronized boolean matchesToken(String token) {
        return expectedToken != null
                && !expectedToken.isEmpty()
                && token != null
                && constantTimeEquals(token, expectedToken);
    }

    public synchronized AuthResult tryToken(String token) {
        pruneExpired();
        if (!matchesToken(token)) {
            return AuthResult.fail(FailureReason.INVALID_CREDENTIALS, 0L);
        }
        return AuthResult.ok(issueSessionLocked());
    }

    public synchronized AuthResult tryPin(String pin, PinVerifier verifier) {
        pruneExpired();
        long now = System.currentTimeMillis();
        if (now < pinLockedUntilMs) {
            return AuthResult.fail(FailureReason.RATE_LIMITED, pinLockedUntilMs - now);
        }
        if (verifier == null || pin == null || !verifier.verify(pin)) {
            long delay = registerPinFailureLocked(now);
            return AuthResult.fail(
                    delay > 0L ? FailureReason.RATE_LIMITED : FailureReason.INVALID_CREDENTIALS,
                    delay
            );
        }
        pinFailures = 0;
        pinLockedUntilMs = 0L;
        return AuthResult.ok(issueSessionLocked());
    }

    public synchronized boolean isValidSession(String sessionId) {
        pruneExpired();
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        Long expiry = sessions.get(sessionId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public synchronized void revokeAllSessions() {
        sessions.clear();
    }

    public synchronized void revokeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public synchronized boolean isPinLockedOut() {
        return System.currentTimeMillis() < pinLockedUntilMs;
    }

    public synchronized long pinLockoutRemainingMs() {
        return Math.max(0L, pinLockedUntilMs - System.currentTimeMillis());
    }

    public synchronized int pinFailureCount() {
        return pinFailures;
    }

    /** Visible for unit tests. */
    synchronized void resetForTests() {
        sessions.clear();
        pinFailures = 0;
        pinLockedUntilMs = 0L;
        expectedToken = "";
    }

    private String issueSessionLocked() {
        String id = generateToken();
        sessions.put(id, System.currentTimeMillis() + SESSION_TTL_MS);
        return id;
    }

    private long registerPinFailureLocked(long now) {
        pinFailures++;
        int shift = Math.min(Math.max(pinFailures - 1, 0), 4);
        long delay = Math.min(30_000L, 1_000L * (1L << shift));
        pinLockedUntilMs = now + delay;
        return delay;
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == null || e.getValue() <= now) {
                it.remove();
            }
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
