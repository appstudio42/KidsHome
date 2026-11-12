package io.github.marcelsavegnago.kidshome;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Thin NanoHTTPD adapter around {@link RemoteRequestHandler}.
 */
public final class RemoteControlServer extends NanoHTTPD {
    private static final String TAG = "KidsHome.RemoteHttp";

    private final RemoteRequestHandler handler;
    private final RemoteAuth auth;

    public RemoteControlServer(int port, RemoteAuth auth, RemoteRequestHandler handler) {
        super(port);
        this.auth = auth;
        this.handler = handler;
    }

    public RemoteAuth getAuth() {
        return auth;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String remote = session.getRemoteIpAddress();
            Map<String, String> files = new HashMap<>();
            Method method = session.getMethod();
            if (Method.POST.equals(method) || Method.PUT.equals(method)) {
                try {
                    session.parseBody(files);
                } catch (Exception e) {
                    Log.w(TAG, "parseBody failed", e);
                }
            }
            String body = files.get("postData");
            if (body == null) {
                body = "";
            }

            Map<String, String> headers = new HashMap<>();
            if (session.getHeaders() != null) {
                headers.putAll(session.getHeaders());
            }
            Map<String, String> cookies = parseCookies(headers.get("cookie"));

            String query = session.getQueryParameterString();
            RemoteHttpModels.Request request = new RemoteHttpModels.Request(
                    method != null ? method.name() : "GET",
                    session.getUri(),
                    query,
                    remote,
                    body,
                    headers,
                    cookies
            );
            RemoteHttpModels.Response result = handler.handle(request);
            return toNano(result);
        } catch (Exception e) {
            Log.e(TAG, "serve failed", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error");
        }
    }

    private static Response toNano(RemoteHttpModels.Response result) {
        Response.IStatus status = statusFromCode(result.status);
        Response response;
        if (result.binaryBody != null) {
            response = newFixedLengthResponse(
                    status,
                    result.mimeType,
                    new ByteArrayInputStream(result.binaryBody),
                    result.binaryBody.length
            );
        } else {
            response = newFixedLengthResponse(status, result.mimeType, result.body);
        }
        for (Map.Entry<String, String> e : result.headers.entrySet()) {
            response.addHeader(e.getKey(), e.getValue());
        }
        return response;
    }

    private static Response.IStatus statusFromCode(int code) {
        for (Response.Status s : Response.Status.values()) {
            if (s.getRequestStatus() == code) {
                return s;
            }
        }
        return Response.Status.lookup(code);
    }

    private static Map<String, String> parseCookies(String header) {
        Map<String, String> map = new HashMap<>();
        if (header == null || header.isEmpty()) {
            return map;
        }
        String[] parts = header.split(";");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            map.put(key, value);
        }
        return map;
    }
}
