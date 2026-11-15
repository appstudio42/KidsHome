package io.github.marcelsavegnago.kidshome;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Decides which navigations a site shortcut may follow.
 *
 * <p>The anchor is the parent-configured shortcut URL. A navigation is allowed
 * only for {@code http} and {@code https} on that exact host or a subdomain of
 * it ({@code cdn.kids.example} when the shortcut is {@code kids.example}).
 * A different registrable name, a host that merely ends with the anchor
 * ({@code evil-poki.com} vs {@code poki.com}, {@code notkids.poki.com} vs
 * {@code kids.poki.com}), non-http schemes, and userinfo are rejected.
 *
 * <p>When a WebAPK scope is passed, the rule is tighter: {@code https} only,
 * the scope host exactly (no subdomain), and the scope path as a prefix.
 * {@code https://gamestudio42.github.io/gamestudio-kids/} does not allow
 * {@code https://gamestudio42.github.io/another-project/}. A scope whose path
 * is {@code /} keeps every path on that host. An absent or non-https scope
 * falls back to the host rule above.
 *
 * <p>This is a navigation rule. Images, scripts, and other subresources are
 * not passed through here, so a page can still load the assets it needs.
 * Service-worker fetches are not navigations: {@link #allowsServiceWorkerRequest}
 * allows same-scope requests and other http(s) fetches the worker uses to
 * fill Cache Storage. {@code intent:}, {@code market:}, and the other
 * non-http schemes stay blocked.
 */
public final class SiteNavigationPolicy {
    private SiteNavigationPolicy() {
    }

    /**
     * @param anchorUrl shortcut URL saved by the parent
     * @param targetUrl URL the WebView is about to navigate to
     */
    public static boolean allows(String anchorUrl, String targetUrl) {
        return allows(anchorUrl, null, targetUrl);
    }

    /**
     * @param scopeUrl WebAPK scope, or null / blank for host-and-subdomain rules
     */
    public static boolean allows(String anchorUrl, String scopeUrl, String targetUrl) {
        if (scopeUrl == null || scopeUrl.trim().isEmpty()) {
            return hostAllows(anchorUrl, targetUrl);
        }
        HttpUrl scope = parse(scopeUrl);
        if (scope == null || !"https".equals(scope.scheme)) {
            return hostAllows(anchorUrl, targetUrl);
        }
        HttpUrl target = parse(targetUrl);
        if (target == null || !"https".equals(target.scheme)) {
            return false;
        }
        if (!scope.host.equals(target.host)) {
            return false;
        }
        return pathWithin(scope.path, target.path);
    }

    /**
     * Whether a service-worker fetch may proceed.
     * Same-scope URLs are allowed. Other http(s) URLs are allowed too, so the
     * worker can cache fonts and scripts that live on another host. Non-http
     * schemes are rejected. An https scope or anchor does not allow an http fetch.
     */
    public static boolean allowsServiceWorkerRequest(String scopeUrl, String anchorUrl, String requestUrl) {
        HttpUrl request = parse(requestUrl);
        if (request == null) {
            return false;
        }
        if (scopeUrl != null && !scopeUrl.trim().isEmpty()
                && allows(anchorUrl != null ? anchorUrl : scopeUrl, scopeUrl, requestUrl)) {
            return true;
        }
        HttpUrl boundary = parse(scopeUrl);
        if (boundary == null) {
            boundary = parse(anchorUrl);
        }
        if (boundary != null && "https".equals(boundary.scheme) && !"https".equals(request.scheme)) {
            return false;
        }
        return true;
    }

    private static boolean hostAllows(String anchorUrl, String targetUrl) {
        String anchor = httpHost(anchorUrl);
        String target = httpHost(targetUrl);
        if (anchor == null || target == null) {
            return false;
        }
        if (anchor.equals(target)) {
            return true;
        }
        // IP literals have no subdomain tree. 10.0.0.1 must not allow 10.0.0.10
        // or evil.10.0.0.1 via a suffix check.
        if (isIpLiteral(anchor) || isIpLiteral(target)) {
            return false;
        }
        return target.endsWith("." + anchor);
    }

    /**
     * Prefix match on a path boundary. {@code /gamestudio-kids} allows that
     * path and {@code /gamestudio-kids/play}, not {@code /gamestudio-kids-evil}.
     */
    private static boolean pathWithin(String scopePath, String targetPath) {
        String scope = scopePath == null || scopePath.isEmpty() ? "/" : scopePath;
        String target = targetPath == null || targetPath.isEmpty() ? "/" : targetPath;
        if (!scope.startsWith("/")) {
            scope = "/" + scope;
        }
        if (!target.startsWith("/")) {
            target = "/" + target;
        }
        if ("/".equals(scope)) {
            return true;
        }
        String prefix = scope.endsWith("/") ? scope : scope + "/";
        if (target.equals(prefix.substring(0, prefix.length() - 1))) {
            return true;
        }
        return target.startsWith(prefix);
    }

    private static HttpUrl parse(String url) {
        String host = httpHost(url);
        if (host == null || url == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        // URI.normalize() leaves percent-encoded ".." in place, and getPath()
        // then decodes it. A second pass on the decoded path closes that gap
        // so /gamestudio-kids/%2e%2e/secret does not stay inside the scope.
        String path = collapsePath(uri.getPath());
        if (path == null) {
            return null;
        }
        return new HttpUrl(scheme.toLowerCase(Locale.ROOT), host, path);
    }

    private static String collapsePath(String decodedPath) {
        String path = decodedPath == null || decodedPath.isEmpty() ? "/" : decodedPath;
        if (path.indexOf('\\') >= 0 || path.indexOf('\u0000') >= 0) {
            return null;
        }
        try {
            URI normalized = new URI(null, null, path, null).normalize();
            path = normalized.getPath();
        } catch (URISyntaxException e) {
            return null;
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '/') {
                int length = i - start;
                boolean dot = length == 1 && path.charAt(start) == '.';
                boolean dotDot = length == 2
                        && path.charAt(start) == '.'
                        && path.charAt(start + 1) == '.';
                if (dot || dotDot) {
                    return null;
                }
                start = i + 1;
            }
        }
        return path;
    }

    private static final class HttpUrl {
        final String scheme;
        final String host;
        final String path;

        HttpUrl(String scheme, String host, String path) {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
        }
    }

    /**
     * Host of an http(s) URL without userinfo, or null when the URL must not
     * be used as a shortcut anchor.
     */
    public static String httpHost(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()
                || trimmed.indexOf(' ') >= 0
                || trimmed.indexOf('\\') >= 0
                || trimmed.indexOf('\n') >= 0
                || trimmed.indexOf('\r') >= 0) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        // userinfo hides the real destination (https://kids.example@evil.com).
        if (uri.getUserInfo() != null) {
            return null;
        }
        return normalizeHost(uri.getHost());
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return null;
        }
        // IPv6 literals are compared as-is. IDN.toASCII rejects them.
        if (value.indexOf(':') >= 0) {
            return value;
        }
        try {
            return IDN.toASCII(value).toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
                value = value * 10 + (c - '0');
            }
            if (value > 255) {
                return false;
            }
        }
        return true;
    }
}
