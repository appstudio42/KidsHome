package io.github.marcelsavegnago.kidshome;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteRequestHandlerTest {
    private RemoteAuth auth;
    private FakeBridge bridge;
    private RemoteRequestHandler handler;

    @Before
    public void setUp() {
        auth = new RemoteAuth();
        auth.resetForTests();
        auth.setExpectedToken("pair-token");
        bridge = new FakeBridge();
        handler = new RemoteRequestHandler(auth, bridge);
    }

    @Test
    public void rejectsPublicPeer() {
        RemoteHttpModels.Response res = handler.handle(req("GET", "/api/state", "8.8.8.8", null, null));
        assertEquals(403, res.status);
    }

    @Test
    public void requiresAuthForState() {
        RemoteHttpModels.Response res = handler.handle(req("GET", "/api/state", "192.168.0.2", null, null));
        assertEquals(401, res.status);
    }

    @Test
    public void tokenLoginThenAddTime() {
        RemoteHttpModels.Response login = handler.handle(req(
                "POST", "/api/login", "192.168.0.2", "{\"token\":\"pair-token\"}", null));
        assertEquals(200, login.status);
        String cookie = login.headers.get("Set-Cookie");
        assertHardenedSessionCookie(cookie);
        assertFalse(cookie.contains("pair-token"));

        Map<String, String> cookies = cookieMap(cookie);
        RemoteHttpModels.Response add = handler.handle(req(
                "POST", "/api/add-time", "192.168.0.2", "{\"minutes\":15}", cookies));
        assertEquals(200, add.status);
        assertEquals(15, bridge.addedMinutes.get());
        assertEquals(1, bridge.logged.size());
        assertEquals(RemoteActionLog.ACTION_ADD_TIME, bridge.logged.get(0));
    }

    @Test
    public void pinRateLimitReturns429() {
        handler.handle(req("POST", "/api/login", "10.0.0.8", "{\"pin\":\"0000\"}", null));
        RemoteHttpModels.Response second = handler.handle(req(
                "POST", "/api/login", "10.0.0.8", "{\"pin\":\"0000\"}", null));
        assertEquals(429, second.status);
        assertTrue(second.body.contains("rate_limited"));
    }

    @Test
    public void lockAndUnlockWithSessionCookie() {
        Map<String, String> cookies = sessionCookies();
        RemoteHttpModels.Request lockReq = new RemoteHttpModels.Request(
                "POST", "/api/lock", "", "192.168.1.5", "{}",
                Collections.emptyMap(), cookies);
        assertEquals(200, handler.handle(lockReq).status);
        assertTrue(bridge.locked);

        RemoteHttpModels.Request unlockReq = new RemoteHttpModels.Request(
                "POST", "/api/unlock", "", "192.168.1.5", "{}",
                Collections.emptyMap(), cookies);
        assertEquals(200, handler.handle(unlockReq).status);
        assertFalse(bridge.locked);
    }

    @Test
    public void queryTokenDoesNotAuthenticateApiCalls() {
        RemoteHttpModels.Response state = handler.handle(new RemoteHttpModels.Request(
                "GET", "/api/state", "t=pair-token", "192.168.1.5", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(401, state.status);
        assertTrue(state.body.contains("\"reauth\":\"/api/login\""));
        assertNull(state.headers.get("Set-Cookie"));
        assertFalse(state.body.contains("pair-token"));

        RemoteHttpModels.Response lock = handler.handle(new RemoteHttpModels.Request(
                "POST", "/api/lock", "t=pair-token", "192.168.1.5", "{}",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(401, lock.status);
        assertFalse(bridge.locked);

        RemoteHttpModels.Response add = handler.handle(new RemoteHttpModels.Request(
                "POST", "/api/add-time", "t=pair-token", "192.168.1.5", "{\"minutes\":15}",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(401, add.status);
        assertEquals(0, bridge.addedMinutes.get());
    }

    @Test
    public void sessionCookieStillWorksWhenQueryTokenIsAlsoPresent() {
        Map<String, String> cookies = sessionCookies();
        RemoteHttpModels.Response state = handler.handle(new RemoteHttpModels.Request(
                "GET", "/api/state", "t=pair-token", "192.168.0.2", "",
                Collections.emptyMap(), cookies));
        assertEquals(200, state.status);
    }

    @Test
    public void bearerTokenStillAuthenticatesWithoutQuery() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer pair-token");
        RemoteHttpModels.Response state = handler.handle(new RemoteHttpModels.Request(
                "GET", "/api/state", "", "192.168.0.2", "",
                headers, Collections.emptyMap()));
        assertEquals(200, state.status);

        headers.put("Authorization", "Bearer nope");
        RemoteHttpModels.Response denied = handler.handle(new RemoteHttpModels.Request(
                "GET", "/api/state", "t=pair-token", "192.168.0.2", "",
                headers, Collections.emptyMap()));
        assertEquals(401, denied.status);
    }

    @Test
    public void pairingLinkExchangesTokenForCookieAndRedirects() {
        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token&lang=pt-BR", "192.168.0.20", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, landing.status);
        assertEquals("/?lang=pt-BR", landing.headers.get("Location"));
        assertEquals("no-store", landing.headers.get("Cache-Control"));
        assertEquals("no-referrer", landing.headers.get("Referrer-Policy"));
        assertFalse(landing.headers.get("Location").contains("pair-token"));
        assertFalse(landing.body.contains("pair-token"));
        String cookie = landing.headers.get("Set-Cookie");
        assertHardenedSessionCookie(cookie);
        assertFalse(cookie.contains("pair-token"));

        RemoteHttpModels.Response page = handler.handle(req(
                "GET", "/", "192.168.0.20", null, cookieMap(cookie)));
        assertEquals(200, page.status);
        assertTrue(page.body.contains("<html>ok</html>"));
        assertEquals("no-referrer", page.headers.get("Referrer-Policy"));

        RemoteHttpModels.Response state = handler.handle(req(
                "GET", "/api/state", "192.168.0.20", null, cookieMap(cookie)));
        assertEquals(200, state.status);
    }

    @Test
    public void indexHtmlPairingLinkAlsoRedirectsClean() {
        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/index.html", "t=pair-token", "10.1.1.8", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, landing.status);
        assertEquals("/", landing.headers.get("Location"));
        assertHardenedSessionCookie(landing.headers.get("Set-Cookie"));
    }

    @Test
    public void invalidOrEmptyPairingTokenRedirectsWithoutSession() {
        RemoteHttpModels.Response bad = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=wrong-token", "192.168.0.20", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, bad.status);
        assertEquals("/", bad.headers.get("Location"));
        assertNull(bad.headers.get("Set-Cookie"));

        RemoteHttpModels.Response empty = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=", "192.168.0.20", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, empty.status);
        assertEquals("/", empty.headers.get("Location"));
        assertNull(empty.headers.get("Set-Cookie"));
    }

    @Test
    public void pairingRedirectDropsCrlfAndDoesNotOpenRedirect() {
        String location = RemoteRequestHandler.locationWithoutPairingToken(
                "t=pair-token&next=https://evil.example&lang=en\r\nSet-Cookie: x=1");
        assertEquals("/?next=https%3A%2F%2Fevil.example", location);
        assertFalse(location.contains("\r"));
        assertFalse(location.contains("\n"));
        assertFalse(location.contains("pair-token"));
        assertTrue(location.startsWith("/"));

        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token&lang=en\r\nLocation: http://evil.example", "192.168.0.9", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, landing.status);
        assertEquals("/", landing.headers.get("Location"));
        for (Map.Entry<String, String> header : landing.headers.entrySet()) {
            assertFalse(header.getValue().contains("\r"));
            assertFalse(header.getValue().contains("\n"));
            assertFalse(header.getValue().contains("evil.example"));
        }
    }

    @Test
    public void alreadyPairedSessionIsNotReissuedOnPairingUrl() {
        Map<String, String> cookies = sessionCookies();
        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token", "192.168.0.20", "",
                Collections.emptyMap(), cookies));
        assertEquals(302, landing.status);
        assertEquals("/", landing.headers.get("Location"));
        assertNull(landing.headers.get("Set-Cookie"));
    }

    @Test
    public void staleSessionIsReplacedWhenPairingLinkIsOpened() {
        Map<String, String> cookies = new HashMap<>();
        cookies.put(RemoteAuth.COOKIE_NAME, "stale-session");
        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token", "192.168.0.20", "",
                Collections.emptyMap(), cookies));
        assertEquals(302, landing.status);
        assertHardenedSessionCookie(landing.headers.get("Set-Cookie"));
        assertFalse(landing.headers.get("Set-Cookie").contains("stale-session"));
    }

    @Test
    public void publicPeerCannotExchangePairingToken() {
        RemoteHttpModels.Response landing = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token", "8.8.8.8", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(403, landing.status);
        assertNull(landing.headers.get("Set-Cookie"));
        assertNull(landing.headers.get("Location"));
    }

    @Test
    public void assetUrlDoesNotAcceptOrEchoPairingToken() {
        RemoteHttpModels.Response asset = handler.handle(new RemoteHttpModels.Request(
                "GET", "/assets/app.js", "t=pair-token", "192.168.0.2", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(404, asset.status);
        assertNull(asset.headers.get("Set-Cookie"));
        assertFalse(asset.body.contains("pair-token"));
    }

    @Test
    public void logoutClearsHardenedCookie() {
        Map<String, String> cookies = sessionCookies();
        RemoteHttpModels.Response logout = handler.handle(req(
                "POST", "/api/logout", "192.168.0.2", "{}", cookies));
        assertEquals(200, logout.status);
        String cookie = logout.headers.get("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertFalse(auth.isValidSession(cookies.get(RemoteAuth.COOKIE_NAME)));
    }

    @Test
    public void securityHeadersOnPageApiAndRedirect() {
        RemoteHttpModels.Response page = handler.handle(req("GET", "/", "192.168.0.20", null, null));
        assertEquals(200, page.status);
        assertSecurityHeaders(page, true);

        RemoteHttpModels.Response redirect = handler.handle(new RemoteHttpModels.Request(
                "GET", "/", "t=pair-token", "192.168.0.20", "",
                Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(302, redirect.status);
        assertEquals("/", redirect.headers.get("Location"));
        assertHardenedSessionCookie(redirect.headers.get("Set-Cookie"));
        assertSecurityHeaders(redirect, false);

        Map<String, String> cookies = sessionCookies();
        RemoteHttpModels.Response state = handler.handle(req(
                "GET", "/api/state", "192.168.0.20", null, cookies));
        assertEquals(200, state.status);
        assertSecurityHeaders(state, false);

        RemoteHttpModels.Response login = handler.handle(req(
                "POST", "/api/login", "192.168.0.20", "{\"token\":\"pair-token\"}", null));
        assertEquals(200, login.status);
        assertHardenedSessionCookie(login.headers.get("Set-Cookie"));
        assertSecurityHeaders(login, false);

        RemoteHttpModels.Response denied = handler.handle(req("GET", "/api/state", "8.8.8.8", null, null));
        assertEquals(403, denied.status);
        assertSecurityHeaders(denied, false);
    }

    @Test
    public void invalidMinutesRejected() {
        Map<String, String> cookies = new HashMap<>();
        RemoteAuth.AuthResult session = auth.tryToken("pair-token");
        cookies.put(RemoteAuth.COOKIE_NAME, session.sessionId);
        RemoteHttpModels.Response res = handler.handle(req(
                "POST", "/api/add-time", "192.168.0.2", "{\"minutes\":0}", cookies));
        assertEquals(400, res.status);
    }

    private static RemoteHttpModels.Request req(
            String method, String path, String peer, String body, Map<String, String> cookies) {
        return new RemoteHttpModels.Request(
                method, path, "", peer, body,
                Collections.emptyMap(),
                cookies == null ? Collections.emptyMap() : cookies
        );
    }

    private Map<String, String> sessionCookies() {
        RemoteAuth.AuthResult session = auth.tryToken("pair-token");
        Map<String, String> cookies = new HashMap<>();
        cookies.put(RemoteAuth.COOKIE_NAME, session.sessionId);
        return cookies;
    }

    private static void assertSecurityHeaders(RemoteHttpModels.Response response, boolean htmlPage) {
        assertEquals("nosniff", response.headers.get("X-Content-Type-Options"));
        assertEquals("DENY", response.headers.get("X-Frame-Options"));
        assertEquals("no-referrer", response.headers.get("Referrer-Policy"));
        assertEquals("no-store", response.headers.get("Cache-Control"));
        if (htmlPage) {
            String policy = response.headers.get("Content-Security-Policy");
            assertEquals(RemoteHttpModels.INDEX_CONTENT_SECURITY_POLICY, policy);
            assertFalse(policy.contains("unsafe-inline"));
            assertFalse(policy.contains("unsafe-eval"));
            assertTrue(policy.contains("script-src 'self'"));
            assertTrue(policy.contains("style-src 'self'"));
            assertTrue(policy.contains("connect-src 'self'"));
        } else {
            assertNull(response.headers.get("Content-Security-Policy"));
        }
    }

    private static void assertHardenedSessionCookie(String cookie) {
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(RemoteAuth.COOKIE_NAME + "="));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Max-Age=" + (RemoteAuth.SESSION_TTL_MS / 1000L)));
        assertFalse(cookie.toLowerCase().contains("secure"));
    }

    private static Map<String, String> cookieMap(String setCookie) {
        Map<String, String> map = new HashMap<>();
        String first = setCookie.split(";", 2)[0];
        int eq = first.indexOf('=');
        map.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
        return map;
    }

    private static final class FakeBridge implements RemoteRequestHandler.DeviceBridge {
        final AtomicInteger addedMinutes = new AtomicInteger();
        final List<String> logged = new ArrayList<>();
        boolean locked;

        @Override
        public RemoteDeviceState readState() {
            return new RemoteDeviceState(
                    true, locked, locked, false,
                    locked ? "remote" : "none",
                    true, 60, 10, 0, 50,
                    2, 1, "Theo", Collections.emptyList()
            );
        }

        @Override
        public boolean verifyPin(String pin) {
            return "4242".equals(pin);
        }

        @Override
        public void addBonusMinutes(int minutes) {
            addedMinutes.addAndGet(minutes);
        }

        @Override
        public void lockNow() {
            locked = true;
        }

        @Override
        public void unlockNow() {
            locked = false;
        }

        @Override
        public void logAction(String action, int minutes) {
            logged.add(action);
        }

        @Override
        public String readAsset(String relativePath) {
            if ("index.html".equals(relativePath)) {
                return "<html>ok</html>";
            }
            return null;
        }
    }
}
