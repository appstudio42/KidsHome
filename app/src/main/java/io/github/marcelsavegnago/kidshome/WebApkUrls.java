package io.github.marcelsavegnago.kidshome;

import java.util.List;

/**
 * Pure checks for a Chrome WebAPK ({@code org.chromium.webapk.*}).
 *
 * <p>The shell delegates to Chrome. KidsHome never needs that. It reads the
 * start URL ({@code org.chromium.webapk.shell_apk.startUrl}) and, when
 * present, the scope ({@code org.chromium.webapk.shell_apk.scope}). Only
 * {@code https} URLs are accepted. When neither the metadata nor an https
 * intent-filter host yields a URL, the caller launches the package as a
 * normal app.
 *
 * <p>A scope with a path is a prefix, not just a host. On a shared host such
 * as {@code gamestudio42.github.io}, {@code /gamestudio-kids/} must not allow
 * navigation to another project on the same host. A missing scope, or a scope
 * whose path is {@code /}, keeps the whole host (no sibling-path block).
 * Subdomains are outside a PWA scope and are not allowed while a scope is set.
 */
public final class WebApkUrls {
    public static final String PACKAGE_PREFIX = "org.chromium.webapk.";
    public static final String META_START_URL = "org.chromium.webapk.shell_apk.startUrl";
    public static final String META_SCOPE = "org.chromium.webapk.shell_apk.scope";

    private WebApkUrls() {
    }

    /** Installed WebAPK package name. The id after the prefix is required. */
    public static boolean isWebApkPackage(String packageName) {
        return packageName != null
                && packageName.startsWith(PACKAGE_PREFIX)
                && packageName.length() > PACKAGE_PREFIX.length();
    }

    /**
     * Prefix match, or the shell metadata keys when a build does not use the
     * Chromium package prefix.
     */
    public static boolean isWebApk(String packageName, boolean hasStartUrlMeta, boolean hasScopeMeta) {
        return isWebApkPackage(packageName) || hasStartUrlMeta || hasScopeMeta;
    }

    /**
     * https URL with a host and no userinfo, or null.
     * The original string is kept so the path and query survive.
     */
    public static String httpsUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < "https://".length()
                || !trimmed.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return null;
        }
        if (SiteNavigationPolicy.httpHost(trimmed) == null) {
            return null;
        }
        return trimmed;
    }

    /**
     * Builds an https URL from an intent-filter host and optional path.
     * Wildcard hosts and glob paths are rejected: they are not a start URL.
     */
    public static String httpsUrlFromFilter(String host, String path) {
        if (host == null) {
            return null;
        }
        String trimmedHost = host.trim();
        if (trimmedHost.isEmpty()
                || trimmedHost.indexOf('*') >= 0
                || trimmedHost.indexOf('/') >= 0
                || trimmedHost.indexOf(' ') >= 0
                || trimmedHost.indexOf('\\') >= 0) {
            return null;
        }
        String pathPart = "/";
        if (path != null && !path.trim().isEmpty()) {
            String trimmedPath = path.trim();
            if (trimmedPath.indexOf('*') >= 0
                    || trimmedPath.indexOf(' ') >= 0
                    || trimmedPath.indexOf('\\') >= 0) {
                return null;
            }
            pathPart = trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
        }
        return httpsUrl("https://" + trimmedHost + pathPart);
    }

    /**
     * @param filterUrls https URLs gathered from the launcher intent-filter, may be null
     * @return null when the shell should be launched as a normal package
     */
    public static Target resolve(
            String packageName,
            boolean hasStartUrlMeta,
            boolean hasScopeMeta,
            String startUrlMeta,
            String scopeMeta,
            List<String> filterUrls
    ) {
        if (!isWebApk(packageName, hasStartUrlMeta, hasScopeMeta)) {
            return null;
        }
        String scope = httpsUrl(scopeMeta);
        String start = httpsUrl(startUrlMeta);
        if (start == null) {
            String fromFilter = firstFilterUrl(filterUrls, scope);
            if (scope != null && (fromFilter == null
                    || !SiteNavigationPolicy.allows(fromFilter, scope, fromFilter))) {
                // The scope is itself an https URL. Prefer it to a host-only
                // filter that would land on a different project, and to giving
                // up when the startUrl key is missing.
                start = scope;
            } else {
                start = fromFilter;
            }
        }
        if (start == null) {
            return null;
        }
        if (scope != null && !SiteNavigationPolicy.allows(start, scope, start)) {
            // A scope on another host, or a path that does not contain the
            // start URL, would block the first load. Fall back to the host.
            scope = null;
        }
        return new Target(start, scope);
    }

    private static String firstFilterUrl(List<String> filterUrls, String scopeOrNull) {
        if (filterUrls == null || filterUrls.isEmpty()) {
            return null;
        }
        String scopeHost = scopeOrNull == null ? null : SiteNavigationPolicy.httpHost(scopeOrNull);
        String fallback = null;
        for (String candidate : filterUrls) {
            String https = httpsUrl(candidate);
            if (https == null) {
                continue;
            }
            if (scopeHost != null && scopeHost.equals(SiteNavigationPolicy.httpHost(https))) {
                return https;
            }
            if (fallback == null) {
                fallback = https;
            }
        }
        return fallback;
    }

    /** https page to open, plus an optional scope that also limits the path. */
    public static final class Target {
        public final String startUrl;
        /** Null when navigation is limited to the start URL host only. */
        public final String scopeUrl;

        public Target(String startUrl, String scopeUrl) {
            this.startUrl = startUrl;
            this.scopeUrl = scopeUrl;
        }
    }
}
