package io.github.marcelsavegnago.kidshome;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Thin NanoHTTPD adapter around {@link RemoteRequestHandler}.
 *
 * <p>POST and PUT bodies are checked and then copied into a small memory buffer.
 * {@link NanoHTTPD.IHTTPSession#parseBody} is never called: NanoHTTPD writes any
 * body of 1024 bytes or more to a temp file and does not enforce an upper bound,
 * so a peer that can reach the port could fill the cache before authentication.
 */
public final class RemoteControlServer extends NanoHTTPD {
    private static final String TAG = "KidsHome.RemoteHttp";

    /** Small enough for login JSON and parent actions, far below a cache-fill payload. */
    static final int MAX_BODY_BYTES = 4096;

    private final RemoteAuth auth;
    private final RemoteRequestHandler handler;

    /**
     * @param bindHost private Wi-Fi/Ethernet IPv4, or any value that
     *                 {@link RemoteNetwork#chooseBindHost} maps to loopback.
     *                 Never bound to every interface.
     */
    public RemoteControlServer(String bindHost, int port, RemoteAuth auth, RemoteRequestHandler handler) {
        super(RemoteNetwork.chooseBindHost(bindHost), port);
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
            Method method = session.getMethod();
            Map<String, String> headers = copyHeaders(session);

            // Peer check before any body byte is read.
            if (!RemoteLanFilter.isAllowedPeer(remote)) {
                return reject(403, "forbidden");
            }

            String body = "";
            if (Method.POST.equals(method) || Method.PUT.equals(method)) {
                BodyLimit limit = BodyLimit.inspect(headers);
                if (!limit.accepted()) {
                    return reject(limit.status, limit.message);
                }
                try {
                    body = readBody(session.getInputStream(), limit.length);
                } catch (IOException e) {
                    return reject(400, "bad request");
                }
            }

            Map<String, String> cookies = parseCookies(headerValue(headers, "cookie"));
            RemoteHttpModels.Request request = new RemoteHttpModels.Request(
                    method != null ? method.name() : "GET",
                    session.getUri(),
                    session.getQueryParameterString(),
                    remote,
                    body,
                    headers,
                    cookies
            );
            return toNano(handler.handle(request));
        } catch (Exception e) {
            Log.e(TAG, "serve failed", e);
            return reject(500, "error");
        }
    }

    /**
     * Outcome of inspecting Content-Length / Transfer-Encoding before the body is read.
     * Status 0 means {@link #length} bytes may be buffered in memory.
     */
    static final class BodyLimit {
        final int status;
        final int length;
        final String message;

        private BodyLimit(int status, int length, String message) {
            this.status = status;
            this.length = length;
            this.message = message;
        }

        boolean accepted() {
            return status == 0;
        }

        static BodyLimit inspect(Map<String, String> headers) {
            if (isChunked(headers)) {
                return new BodyLimit(411, 0, "length required");
            }
            String raw = headerValue(headers, "content-length");
            if (raw == null || raw.trim().isEmpty()) {
                return new BodyLimit(411, 0, "length required");
            }
            long length;
            try {
                length = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                return new BodyLimit(400, 0, "bad request");
            }
            if (length < 0) {
                return new BodyLimit(400, 0, "bad request");
            }
            if (length > MAX_BODY_BYTES) {
                return new BodyLimit(413, 0, "payload too large");
            }
            return new BodyLimit(0, (int) length, "");
        }
    }

    private static boolean isChunked(Map<String, String> headers) {
        String value = headerValue(headers, "transfer-encoding");
        if (value == null || value.isEmpty()) {
            return false;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            String token = part.trim();
            int semi = token.indexOf(';');
            if (semi >= 0) {
                token = token.substring(0, semi).trim();
            }
            if ("chunked".equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies exactly {@code length} bytes into memory. Does not create a file.
     * The string is trimmed the same way NanoHTTPD's old postData path was.
     */
    private static String readBody(InputStream input, int length) throws IOException {
        if (length == 0) {
            return "";
        }
        if (input == null) {
            throw new IOException("missing body");
        }
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buf, offset, length - offset);
            if (read <= 0) {
                throw new IOException("short body");
            }
            offset += read;
        }
        return new String(buf, StandardCharsets.UTF_8).trim();
    }

    /** Rejects the request and asks NanoHTTPD to close the socket so the unread body is discarded. */
    private Response reject(int status, String message) {
        Response response = toNano(RemoteHttpModels.Response.text(status, message));
        response.closeConnection(true);
        return response;
    }

    private static Map<String, String> copyHeaders(IHTTPSession session) {
        Map<String, String> headers = new HashMap<>();
        if (session.getHeaders() != null) {
            headers.putAll(session.getHeaders());
        }
        return headers;
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
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
        for (Map.Entry<String, String> entry : result.headers.entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
        return response;
    }

    private static Response.IStatus statusFromCode(int code) {
        for (Response.Status status : Response.Status.values()) {
            if (status.getRequestStatus() == code) {
                return status;
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
            map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return map;
    }
}
