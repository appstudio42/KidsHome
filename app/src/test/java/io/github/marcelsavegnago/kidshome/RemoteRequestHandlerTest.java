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
        assertTrue(cookie.contains(RemoteAuth.COOKIE_NAME + "="));

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
    public void lockAndUnlock() {
        auth.tryToken("pair-token");
        // Authenticate via matchesToken query
        Map<String, String> empty = Collections.emptyMap();
        RemoteHttpModels.Request lockReq = new RemoteHttpModels.Request(
                "POST", "/api/lock", "t=pair-token", "192.168.1.5", "{}",
                Collections.emptyMap(), empty);
        assertEquals(200, handler.handle(lockReq).status);
        assertTrue(bridge.locked);

        RemoteHttpModels.Request unlockReq = new RemoteHttpModels.Request(
                "POST", "/api/unlock", "t=pair-token", "192.168.1.5", "{}",
                Collections.emptyMap(), empty);
        assertEquals(200, handler.handle(unlockReq).status);
        assertFalse(bridge.locked);
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
