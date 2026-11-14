package io.github.marcelsavegnago.kidshome;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteControlServerTest {
    private RemoteAuth auth;
    private RemoteControlServer server;

    @Before
    public void setUp() {
        auth = new RemoteAuth();
        auth.resetForTests();
        auth.setExpectedToken("pair-token");
        server = new RemoteControlServer("127.0.0.1", 8642, auth, new RemoteRequestHandler(auth, new FakeBridge()));
    }

    @Test
    public void rejectsPublicPeerBeforeBodyIsParsedOrRead() {
        FakeSession session = new FakeSession();
        session.method = NanoHTTPD.Method.POST;
        session.uri = "/api/login";
        session.remoteIp = "8.8.8.8";
        session.headers.put("content-length", "50000000");
        session.body = new BombStream();

        NanoHTTPD.Response response = server.serve(session);

        assertEquals(403, status(response));
        assertEquals("forbidden", responseBody(response));
        assertFalse(session.parseBodyCalled);
        assertEquals("close", response.getHeader("connection"));
        assertSecurityHeaders(response, false);
        assertNull(response.getHeader("set-cookie"));
    }

    @Test
    public void rejectsMissingAndChunkedLengthBeforeRead() {
        FakeSession missing = post("192.168.0.8", "/api/login", null, new BombStream());
        NanoHTTPD.Response missingResponse = server.serve(missing);
        assertEquals(411, status(missingResponse));
        assertEquals("length required", responseBody(missingResponse));
        assertFalse(missing.parseBodyCalled);
        assertEquals("close", missingResponse.getHeader("connection"));

        FakeSession chunked = post("10.0.0.4", "/api/login", "8", new BombStream());
        chunked.headers.put("transfer-encoding", "gzip, Chunked");
        NanoHTTPD.Response chunkedResponse = server.serve(chunked);
        assertEquals(411, status(chunkedResponse));
        assertFalse(chunked.parseBodyCalled);
        assertSecurityHeaders(chunkedResponse, false);
    }

    @Test
    public void rejectsOversizedBodyBeforeRead() {
        FakeSession session = post("192.168.1.9", "/api/add-time",
                String.valueOf(RemoteControlServer.MAX_BODY_BYTES + 1), new BombStream());

        NanoHTTPD.Response response = server.serve(session);

        assertEquals(413, status(response));
        assertEquals("payload too large", responseBody(response));
        assertFalse(session.parseBodyCalled);
        assertEquals("close", response.getHeader("connection"));
        assertSecurityHeaders(response, false);
    }

    @Test
    public void bodyLimitBoundaries() {
        assertEquals(0, RemoteControlServer.BodyLimit.inspect(lengthHeader("0")).status);
        assertEquals(0, RemoteControlServer.BodyLimit.inspect(lengthHeader(" 12 ")).status);
        assertEquals(12, RemoteControlServer.BodyLimit.inspect(lengthHeader(" 12 ")).length);
        assertEquals(0, RemoteControlServer.BodyLimit.inspect(
                lengthHeader(String.valueOf(RemoteControlServer.MAX_BODY_BYTES))).status);

        assertEquals(413, RemoteControlServer.BodyLimit.inspect(
                lengthHeader(String.valueOf(RemoteControlServer.MAX_BODY_BYTES + 1))).status);
        assertEquals(413, RemoteControlServer.BodyLimit.inspect(lengthHeader("999999999999")).status);
        assertEquals(411, RemoteControlServer.BodyLimit.inspect(new HashMap<String, String>()).status);
        assertEquals(411, RemoteControlServer.BodyLimit.inspect(lengthHeader("")).status);
        assertEquals(400, RemoteControlServer.BodyLimit.inspect(lengthHeader("-1")).status);
        assertEquals(400, RemoteControlServer.BodyLimit.inspect(lengthHeader("nope")).status);
        assertEquals(400, RemoteControlServer.BodyLimit.inspect(lengthHeader("12, 12")).status);

        Map<String, String> chunked = lengthHeader("4");
        chunked.put("Transfer-Encoding", "chunked");
        assertEquals(411, RemoteControlServer.BodyLimit.inspect(chunked).status);
    }

    @Test
    public void acceptedBodyIsMemoryOnlyAndStillLogsIn() throws Exception {
        String json = "{\"token\":\"pair-token\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        CountingStream stream = new CountingStream(bytes);
        FakeSession session = post("192.168.0.20", "/api/login", String.valueOf(bytes.length), stream);

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "kh-remote-" + System.nanoTime());
        assertTrue(tempDir.mkdirs());
        String previousTmp = System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());
        try {
            NanoHTTPD.Response response = server.serve(session);
            assertEquals(200, status(response));
            assertTrue(responseBody(response).contains("\"ok\":true"));
            String cookie = response.getHeader("set-cookie");
            assertTrue(cookie.startsWith("kh_session="));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Strict"));
            assertFalse(cookie.contains("pair-token"));
            assertNull(response.getHeader("connection"));
            assertSecurityHeaders(response, false);
            assertFalse(session.parseBodyCalled);
            assertEquals(bytes.length, stream.readBytes);
            String[] leftover = tempDir.list();
            assertTrue(leftover == null || leftover.length == 0);
        } finally {
            if (previousTmp != null) {
                System.setProperty("java.io.tmpdir", previousTmp);
            }
            deleteTree(tempDir);
        }
    }

    @Test
    public void largerAcceptedBodyDoesNotSpillToTempFile() {
        byte[] bytes = new byte[3000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = 'a';
        }
        CountingStream stream = new CountingStream(bytes);
        FakeSession session = post("172.16.0.4", "/api/login", String.valueOf(bytes.length), stream);
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "kh-remote-large-" + System.nanoTime());
        assertTrue(tempDir.mkdirs());
        String previousTmp = System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());
        try {
            NanoHTTPD.Response response = server.serve(session);
            assertEquals(400, status(response));
            assertFalse(session.parseBodyCalled);
            assertEquals(3000, stream.readBytes);
            String[] leftover = tempDir.list();
            assertTrue(leftover == null || leftover.length == 0);
        } finally {
            if (previousTmp != null) {
                System.setProperty("java.io.tmpdir", previousTmp);
            }
            deleteTree(tempDir);
        }
    }

    @Test
    public void shortBodyIsRejectedWithoutParse() {
        FakeSession session = post("192.168.0.5", "/api/login", "8", new ByteArrayInputStream(new byte[]{'{', '}'}));
        NanoHTTPD.Response response = server.serve(session);
        assertEquals(400, status(response));
        assertFalse(session.parseBodyCalled);
        assertEquals("close", response.getHeader("connection"));
    }

    @Test
    public void indexAndPairingKeepLoginFlowAndHeaders() {
        FakeSession page = new FakeSession();
        page.method = NanoHTTPD.Method.GET;
        page.uri = "/";
        page.remoteIp = "192.168.0.20";
        page.body = new BombStream();
        NanoHTTPD.Response html = server.serve(page);
        assertEquals(200, status(html));
        assertTrue(responseBody(html).contains("<html>ok</html>"));
        assertSecurityHeaders(html, true);
        assertFalse(page.parseBodyCalled);

        FakeSession pairing = new FakeSession();
        pairing.method = NanoHTTPD.Method.GET;
        pairing.uri = "/";
        pairing.query = "t=pair-token&lang=pt-BR";
        pairing.remoteIp = "192.168.0.20";
        NanoHTTPD.Response redirect = server.serve(pairing);
        assertEquals(302, status(redirect));
        assertEquals("/?lang=pt-BR", redirect.getHeader("location"));
        assertFalse(redirect.getHeader("location").contains("pair-token"));
        String cookie = redirect.getHeader("set-cookie");
        assertTrue(cookie.startsWith("kh_session="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertFalse(cookie.contains("pair-token"));
        assertEquals("no-store", redirect.getHeader("cache-control"));
        assertEquals("no-referrer", redirect.getHeader("referrer-policy"));
    }

    @Test
    public void bindHostIsNeverEveryInterface() {
        assertEquals("127.0.0.1", new RemoteControlServer(null, 9, auth, handler()).getHostname());
        assertEquals("127.0.0.1", new RemoteControlServer("0.0.0.0", 9, auth, handler()).getHostname());
        assertEquals("127.0.0.1", new RemoteControlServer("8.8.8.8", 9, auth, handler()).getHostname());
        assertEquals("127.0.0.1", new RemoteControlServer("100.64.1.8", 9, auth, handler()).getHostname());
        assertEquals("192.168.1.20", new RemoteControlServer("192.168.1.20", 9, auth, handler()).getHostname());
    }

    private RemoteRequestHandler handler() {
        return new RemoteRequestHandler(auth, new FakeBridge());
    }

    private static FakeSession post(String peer, String path, String contentLength, InputStream body) {
        FakeSession session = new FakeSession();
        session.method = NanoHTTPD.Method.POST;
        session.uri = path;
        session.remoteIp = peer;
        session.body = body;
        if (contentLength != null) {
            session.headers.put("content-length", contentLength);
        }
        return session;
    }

    private static Map<String, String> lengthHeader(String value) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Length", value);
        return headers;
    }

    private static int status(NanoHTTPD.Response response) {
        return response.getStatus().getRequestStatus();
    }

    private static String responseBody(NanoHTTPD.Response response) {
        try {
            InputStream data = response.getData();
            if (data == null) {
                return "";
            }
            byte[] buf = new byte[4096];
            int n = data.read(buf);
            if (n < 0) {
                return "";
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void assertSecurityHeaders(NanoHTTPD.Response response, boolean htmlPage) {
        assertEquals("nosniff", response.getHeader("x-content-type-options"));
        assertEquals("DENY", response.getHeader("x-frame-options"));
        assertEquals("no-referrer", response.getHeader("referrer-policy"));
        assertEquals("no-store", response.getHeader("cache-control"));
        if (htmlPage) {
            String policy = response.getHeader("content-security-policy");
            assertEquals(RemoteHttpModels.INDEX_CONTENT_SECURITY_POLICY, policy);
            assertFalse(policy.contains("unsafe-inline"));
        } else {
            assertNull(response.getHeader("content-security-policy"));
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    private static final class FakeBridge implements RemoteRequestHandler.DeviceBridge {
        @Override
        public RemoteDeviceState readState() {
            return new RemoteDeviceState(
                    true, false, false, false, "none",
                    true, 60, 0, 0, 60,
                    0, 0, "Theo", Collections.emptyList());
        }

        @Override
        public boolean verifyPin(String pin) {
            return false;
        }

        @Override
        public void addBonusMinutes(int minutes) {
        }

        @Override
        public void lockNow() {
        }

        @Override
        public void unlockNow() {
        }

        @Override
        public void logAction(String action, int minutes) {
        }

        @Override
        public String readAsset(String relativePath) {
            if ("index.html".equals(relativePath)) {
                return "<html>ok</html>";
            }
            return null;
        }
    }

    private static final class FakeSession implements NanoHTTPD.IHTTPSession {
        NanoHTTPD.Method method = NanoHTTPD.Method.GET;
        String uri = "/";
        String query = "";
        String remoteIp = "192.168.0.2";
        final Map<String, String> headers = new HashMap<>();
        InputStream body = new ByteArrayInputStream(new byte[0]);
        boolean parseBodyCalled;

        @Override
        public void execute() {
        }

        @Override
        public NanoHTTPD.CookieHandler getCookies() {
            return null;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }

        @Override
        public InputStream getInputStream() {
            return body;
        }

        @Override
        public NanoHTTPD.Method getMethod() {
            return method;
        }

        @Override
        public Map<String, String> getParms() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, List<String>> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public String getQueryParameterString() {
            return query;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public void parseBody(Map<String, String> files) {
            parseBodyCalled = true;
            throw new AssertionError("parseBody must not run");
        }

        @Override
        public String getRemoteIpAddress() {
            return remoteIp;
        }

        @Override
        public String getRemoteHostName() {
            return remoteIp;
        }
    }

    private static final class BombStream extends InputStream {
        @Override
        public int read() {
            throw new AssertionError("body was read");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            throw new AssertionError("body was read");
        }
    }

    private static final class CountingStream extends InputStream {
        private final byte[] data;
        private int position;
        int readBytes;

        CountingStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            if (position >= data.length) {
                return -1;
            }
            readBytes++;
            return data[position++] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= data.length) {
                return -1;
            }
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            readBytes += count;
            return count;
        }
    }
}
