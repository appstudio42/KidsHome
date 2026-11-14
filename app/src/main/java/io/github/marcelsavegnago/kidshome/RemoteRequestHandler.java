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
                return unauthorized();
            }
            if ("POST".equals(request.method) && path.startsWith("/api/")) {
                return unauthorized();
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
        // One-time exchange only: ?t= becomes a session cookie, then a clean URL.
        // The token is never left on the page that loads scripts or calls the API.
        if (rawQueryHasKey(request.query, "t")) {
            return exchangePairingToken(request);
        }
        String html = bridge.readAsset("index.html");
        if (html == null || html.isEmpty()) {
            return RemoteHttpModels.Response.text(500, "missing page");
        }
        return RemoteHttpModels.Response.html(html);
    }

    private RemoteHttpModels.Response exchangePairingToken(RemoteHttpModels.Request request) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Referrer-Policy", "no-referrer");
        headers.put("Location", locationWithoutPairingToken(request.query));
        String token = queryParam(request.query, "t");
        if (token != null && !token.isEmpty() && !hasValidSession(request)) {
            RemoteAuth.AuthResult result = auth.tryToken(token);
            if (result.success) {
                headers.put("Set-Cookie", RemoteAuth.sessionCookieHeader(result.sessionId));
            }
        }
        return new RemoteHttpModels.Response(302, "text/plain; charset=utf-8", "", headers);
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
        headers.put("Set-Cookie", RemoteAuth.sessionCookieHeader(result.sessionId));
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
        headers.put("Set-Cookie", RemoteAuth.clearSessionCookieHeader());
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
        if (hasValidSession(request)) {
            return true;
        }
        // Query-string tokens are not credentials. A non-browser client that already
        // sends Authorization: Bearer keeps working; that header is not a URL.
        String authHeader = header(request, "authorization");
        if (authHeader != null && authHeader.toLowerCase(Locale.US).startsWith("bearer ")) {
            return auth.matchesToken(authHeader.substring(7).trim());
        }
        return false;
    }

    private boolean hasValidSession(RemoteHttpModels.Request request) {
        String session = request.cookies.get(RemoteAuth.COOKIE_NAME);
        return auth.isValidSession(session);
    }

    /** Session missing. Clients re-pair with POST /api/login (token or PIN) or by opening the QR link. */
    private static RemoteHttpModels.Response unauthorized() {
        return RemoteHttpModels.Response.json(401, "{\"error\":\"unauthorized\",\"reauth\":\"/api/login\"}");
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

    /** True when the key is present even if its value is empty. */
    private static boolean rawQueryHasKey(String query, String key) {
        if (query == null || query.isEmpty() || key == null) {
            return false;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            if (containsCrlf(k)) {
                continue;
            }
            if (key.equals(urlDecode(k))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Landing redirect that drops the pairing token and keeps other parameters (for example lang).
     * Always a same-origin path. Parts with CR/LF are dropped so the Location header cannot be split.
     */
    static String locationWithoutPairingToken(String query) {
        if (query == null || query.isEmpty()) {
            return "/";
        }
        StringBuilder kept = new StringBuilder();
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.isEmpty() || containsCrlf(part)) {
                continue;
            }
            int eq = part.indexOf('=');
            String rawKey = eq >= 0 ? part.substring(0, eq) : part;
            String rawVal = eq >= 0 ? part.substring(eq + 1) : null;
            String key = urlDecode(rawKey);
            if (key.isEmpty() || "t".equals(key) || containsCrlf(key)) {
                continue;
            }
            String value = rawVal == null ? null : urlDecode(rawVal);
            if (value != null && containsCrlf(value)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(urlEncode(key));
            if (value != null) {
                kept.append('=').append(urlEncode(value));
            }
        }
        if (kept.length() == 0) {
            return "/";
        }
        String location = "/?" + kept;
        if (containsCrlf(location) || !location.startsWith("/")) {
            return "/";
        }
        return location;
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.isEmpty() || key == null) {
            return null;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.isEmpty() || containsCrlf(part)) {
                continue;
            }
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            String v = eq >= 0 ? part.substring(eq + 1) : "";
            if (key.equals(urlDecode(k))) {
                String decoded = urlDecode(v);
                if (!decoded.isEmpty()) {
                    return decoded;
                }
            }
        }
        return null;
    }

    private static boolean containsCrlf(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
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

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
