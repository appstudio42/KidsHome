package io.github.marcelsavegnago.kidshome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple request/response models for the remote HTTP handler (pure JVM).
 */
public final class RemoteHttpModels {
    private RemoteHttpModels() {
    }

    public static final class Request {
        public final String method;
        public final String path;
        public final String query;
        public final String remoteAddr;
        public final String body;
        public final Map<String, String> headers;
        public final Map<String, String> cookies;

        public Request(
                String method,
                String path,
                String query,
                String remoteAddr,
                String body,
                Map<String, String> headers,
                Map<String, String> cookies
        ) {
            this.method = method == null ? "GET" : method.toUpperCase();
            this.path = path == null ? "/" : path;
            this.query = query == null ? "" : query;
            this.remoteAddr = remoteAddr == null ? "" : remoteAddr;
            this.body = body == null ? "" : body;
            this.headers = headers == null
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.cookies = cookies == null
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(cookies));
        }
    }

    public static final class Response {
        public final int status;
        public final String mimeType;
        public final String body;
        public final Map<String, String> headers;
        public final byte[] binaryBody;

        public Response(int status, String mimeType, String body, Map<String, String> headers) {
            this(status, mimeType, body, headers, null);
        }

        public Response(int status, String mimeType, String body, Map<String, String> headers, byte[] binaryBody) {
            this.status = status;
            this.mimeType = mimeType == null ? "text/plain" : mimeType;
            this.body = body == null ? "" : body;
            this.headers = headers == null
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.binaryBody = binaryBody;
        }

        public static Response json(int status, String json) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Cache-Control", "no-store");
            return new Response(status, "application/json; charset=utf-8", json, headers);
        }

        public static Response html(String html) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Cache-Control", "no-store");
            return new Response(200, "text/html; charset=utf-8", html, headers);
        }

        public static Response text(int status, String text) {
            return new Response(status, "text/plain; charset=utf-8", text, new LinkedHashMap<String, String>());
        }
    }
}
