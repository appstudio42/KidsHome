package io.github.marcelsavegnago.kidshome;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Favicon URLs taken only from the website the parent added.
 * Third-party favicon services are not used.
 */
final class FaviconUrls {
    private static final int MAX_ICONS = 4;
    private static final Pattern LINK = Pattern.compile(
            "<link\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL = Pattern.compile(
            "\\brel\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile(
            "\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
            Pattern.CASE_INSENSITIVE);

    private FaviconUrls() {
    }

    static List<String> candidates(String pageUrl, String html) {
        URI page = httpUri(pageUrl);
        if (page == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (html != null && !html.isEmpty()) {
            Matcher links = LINK.matcher(html);
            while (links.find() && out.size() < MAX_ICONS) {
                String attrs = links.group(1);
                String rel = firstGroup(REL, attrs);
                String href = firstGroup(HREF, attrs);
                if (rel == null || href == null) {
                    continue;
                }
                if (!rel.toLowerCase(Locale.US).contains("icon")) {
                    continue;
                }
                String resolved = resolve(page, unescape(href.trim()));
                if (resolved != null) {
                    out.add(resolved);
                }
            }
        }
        String fallback = origin(page);
        if (fallback != null) {
            out.add(fallback + "/favicon.ico");
        }
        return new ArrayList<>(out);
    }

    private static String firstGroup(Pattern pattern, String attrs) {
        Matcher matcher = pattern.matcher(attrs);
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String resolve(URI page, String href) {
        if (href.isEmpty() || href.charAt(0) == '#'
                || href.regionMatches(true, 0, "data:", 0, 5)
                || href.regionMatches(true, 0, "javascript:", 0, 11)) {
            return null;
        }
        if (href.length() > 2000) {
            return null;
        }
        final URI resolved;
        try {
            resolved = page.resolve(href);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return accept(page, resolved);
    }

    private static String accept(URI page, URI resolved) {
        if (resolved == null || resolved.getUserInfo() != null || resolved.getHost() == null) {
            return null;
        }
        String scheme = resolved.getScheme();
        if (scheme == null) {
            return null;
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return null;
        }
        if (!sameSite(page, resolved)) {
            return null;
        }
        String path = resolved.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        int port = resolved.getPort();
        if (("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80)) {
            port = -1;
        }
        try {
            URI clean = new URI(
                    scheme.toLowerCase(Locale.US),
                    null,
                    resolved.getHost(),
                    port,
                    path,
                    resolved.getQuery(),
                    null);
            return clean.toASCIIString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static boolean sameSite(URI page, URI icon) {
        return page.getHost().equalsIgnoreCase(icon.getHost())
                && page.getScheme().equalsIgnoreCase(icon.getScheme())
                && port(page) == port(icon);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        return -1;
    }

    private static URI httpUri(String pageUrl) {
        if (pageUrl == null) {
            return null;
        }
        String trimmed = pageUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(trimmed);
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getHost().isEmpty()) {
                return null;
            }
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                return null;
            }
            return uri;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String origin(URI page) {
        try {
            URI clean = new URI(
                    page.getScheme().toLowerCase(Locale.US),
                    null,
                    page.getHost(),
                    page.getPort(),
                    null,
                    null,
                    null);
            return clean.toASCIIString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String unescape(String href) {
        return href.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
