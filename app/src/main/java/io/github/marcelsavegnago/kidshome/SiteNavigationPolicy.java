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
 * <p>This is a navigation rule. Images, scripts, and other subresources are
 * not passed through here, so a page can still load the assets it needs.
 */
public final class SiteNavigationPolicy {
    private SiteNavigationPolicy() {
    }

    /**
     * @param anchorUrl shortcut URL saved by the parent
     * @param targetUrl URL the WebView is about to navigate to
     */
    public static boolean allows(String anchorUrl, String targetUrl) {
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
