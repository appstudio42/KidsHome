package io.github.marcelsavegnago.kidshome;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure HTTP request router for parent remote control.
 * Android side injects {@link DeviceBridge} for Prefs / lock actions.
 */
public final class RemoteRequestHandler {
    public static final int DEFAULT_PORT = 8642;

    public interface DeviceBridge {
        RemoteDeviceState readState();

        boolean verifyPin(String pin);

        void addBonusMinutes(int minutes);

        void lockNow();

        void unlockNow();

        void logAction(String action, int minutes);

        String readAsset(String relativePath);
    }

    private final RemoteAuth auth;
    private final DeviceBridge bridge;

    public RemoteRequestHandler(RemoteAuth auth, DeviceBridge bridge) {
        this.auth = auth;
        this.bridge = bridge;
    }

    public RemoteHttpModels.Response handle(RemoteHttpModels.Request request) {
        if (request == null) {
            return RemoteHttpModels.Response.text(400, "bad request");
        }
        if (!RemoteLanFilter.isAllowedPeer(request.remoteAddr)) {
            return RemoteHttpModels.Response.text(403, "forbidden");
        }

        String path = normalizePath(request.path);
        if ("GET".equals(request.method) && ("/".equals(path) || "/index.html".equals(path))) {
            return serveIndex(request);
        }
        if ("GET".equals(request.method) && path.startsWith("/assets/")) {
            return serveAsset(path.substring("/assets/".length()));
        }
        if ("POST".equals(request.method) && "/api/login".equals(path)) {
            return handleLogin(request);
        }
        if ("POST".equals(request.method) && "/api/logout".equals(path)) {
            return handleLogout(request);
        }
        if (!isAuthenticated(request)) {
            if ("GET".equals(request.method) && "/api/state".equals(path)) {
                return RemoteHttpModels.Response.json(401, "{\"error\":\"unauthorized\"}");
            }
            if ("POST".equals(request.method) && path.startsWith("/api/")) {
                return RemoteHttpModels.Response.json(401, "{\"error\":\"unauthorized\"}");
            }
        }
        if ("GET".equals(request.method) && "/api/state".equals(path)) {
            return RemoteHttpModels.Response.json(200, bridge.readState().toJson());
        }
        if ("POST".equals(request.method) && "/api/add-time".equals(path)) {
            return handleAddTime(request);
        }
        if ("POST".equals(request.method) && "/api/lock".equals(path)) {
            bridge.lockNow();
            bridge.logAction(RemoteActionLog.ACTION_LOCK, 0);
            return RemoteHttpModels.Response.json(200, bridge.readState().toJson());
        }
        if ("POST".equals(request.method) && "/api/unlock".equals(path)) {
            bridge.unlockNow();
            bridge.logAction(RemoteActionLog.ACTION_UNLOCK, 0);
            return RemoteHttpModels.Response.json(200, bridge.readState().toJson());
        }
        return RemoteHttpModels.Response.text(404, "not found");
    }

    private RemoteHttpModels.Response serveIndex(RemoteHttpModels.Request request) {
        String html = bridge.readAsset("index.html");
        if (html == null || html.isEmpty()) {
            return RemoteHttpModels.Response.text(500, "missing page");
        }
        // Auto-login via pairing token in query (?t=...) so scanning the QR is enough.
        String token = queryParam(request.query, "t");
        if (token != null && !token.isEmpty() && !isAuthenticated(request)) {
            RemoteAuth.AuthResult result = auth.tryToken(token);
            if (result.success) {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Cache-Control", "no-store");
                headers.put("Set-Cookie", sessionCookie(result.sessionId));
                return new RemoteHttpModels.Response(200, "text/html; charset=utf-8", html, headers);
            }
        }
        return RemoteHttpModels.Response.html(html);
    }

    private RemoteHttpModels.Response serveAsset(String relative) {
        if (relative.contains("..") || relative.startsWith("/")) {
            return RemoteHttpModels.Response.text(403, "forbidden");
        }
        String body = bridge.readAsset(relative);
        if (body == null) {
            return RemoteHttpModels.Response.text(404, "not found");
        }
        String mime = "text/plain; charset=utf-8";
        String lower = relative.toLowerCase(Locale.US);
        if (lower.endsWith(".css")) {
            mime = "text/css; charset=utf-8";
        } else if (lower.endsWith(".js")) {
            mime = "application/javascript; charset=utf-8";
        } else if (lower.endsWith(".svg")) {
            mime = "image/svg+xml";
        } else if (lower.endsWith(".json")) {
            mime = "application/json; charset=utf-8";
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        return new RemoteHttpModels.Response(200, mime, body, headers);
    }

    private RemoteHttpModels.Response handleLogin(RemoteHttpModels.Request request) {
        JSONObject json = parseJson(request.body);
        String token = json.optString("token", "");
        String pin = json.optString("pin", "");

        RemoteAuth.AuthResult result;
        if (token != null && !token.isEmpty()) {
            result = auth.tryToken(token);
        } else if (pin != null && !pin.isEmpty()) {
            result = auth.tryPin(pin, bridge::verifyPin);
        } else {
            return RemoteHttpModels.Response.json(400, "{\"error\":\"missing_credentials\"}");
        }

        if (!result.success) {
            if (result.reason == RemoteAuth.FailureReason.RATE_LIMITED) {
                return RemoteHttpModels.Response.json(429,
                        "{\"error\":\"rate_limited\",\"retryMs\":" + result.lockoutRemainingMs + "}");
            }
            return RemoteHttpModels.Response.json(401, "{\"error\":\"invalid_credentials\"}");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Set-Cookie", sessionCookie(result.sessionId));
        return new RemoteHttpModels.Response(
                200,
                "application/json; charset=utf-8",
                "{\"ok\":true}",
                headers
        );
    }

    private RemoteHttpModels.Response handleLogout(RemoteHttpModels.Request request) {
        String session = request.cookies.get(RemoteAuth.COOKIE_NAME);
        auth.revokeSession(session);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Set-Cookie", RemoteAuth.COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly");
        return new RemoteHttpModels.Response(200, "application/json; charset=utf-8", "{\"ok\":true}", headers);
    }

    private RemoteHttpModels.Response handleAddTime(RemoteHttpModels.Request request) {
        JSONObject json = parseJson(request.body);
        int minutes = json.optInt("minutes", 0);
        if (!RemoteTimeMath.isPresetOrCustomMinutes(minutes)) {
            return RemoteHttpModels.Response.json(400, "{\"error\":\"invalid_minutes\"}");
        }
        int clamped = (int) RemoteTimeMath.clampMinutes(minutes);
        if (clamped <= 0) {
            return RemoteHttpModels.Response.json(400, "{\"error\":\"invalid_minutes\"}");
        }
        bridge.addBonusMinutes(clamped);
        bridge.logAction(RemoteActionLog.ACTION_ADD_TIME, clamped);
        return RemoteHttpModels.Response.json(200, bridge.readState().toJson());
    }

    private boolean isAuthenticated(RemoteHttpModels.Request request) {
        String session = request.cookies.get(RemoteAuth.COOKIE_NAME);
        if (auth.isValidSession(session)) {
            return true;
        }
        // Accept pairing token as Bearer / query without minting a new session each call.
        String authHeader = header(request, "authorization");
        if (authHeader != null && authHeader.toLowerCase(Locale.US).startsWith("bearer ")) {
            return auth.matchesToken(authHeader.substring(7).trim());
        }
        String qToken = queryParam(request.query, "t");
        return qToken != null && !qToken.isEmpty() && auth.matchesToken(qToken);
    }

    private static String sessionCookie(String sessionId) {
        long maxAge = RemoteAuth.SESSION_TTL_MS / 1000L;
        return RemoteAuth.COOKIE_NAME + "=" + sessionId
                + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=Strict";
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.isEmpty() || key == null) {
            return null;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            String v = eq >= 0 ? part.substring(eq + 1) : "";
            if (key.equals(urlDecode(k))) {
                return urlDecode(v);
            }
        }
        return null;
    }

    private static String header(RemoteHttpModels.Request request, String name) {
        for (Map.Entry<String, String> e : request.headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static JSONObject parseJson(String body) {
        try {
            return new JSONObject(body == null || body.isEmpty() ? "{}" : body);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
