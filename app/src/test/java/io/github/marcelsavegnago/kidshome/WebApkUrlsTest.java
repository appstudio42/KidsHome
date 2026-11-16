package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GS Kids on a Fire 7: package org.chromium.webapk.a4aaa2f3deb113437_v2,
 * start URL https://gamestudio42.github.io/gamestudio-kids/.
 * The host also serves other projects, so a path scope must stick.
 */
public class WebApkUrlsTest {
    private static final String GS_KIDS = "org.chromium.webapk.a4aaa2f3deb113437_v2";
    private static final String START = "https://gamestudio42.github.io/gamestudio-kids/";
    private static final String SCOPE = "https://gamestudio42.github.io/gamestudio-kids/";

    @Test
    public void detectsChromiumWebApkPrefixAndOptionalMetadata() {
        assertTrue(WebApkUrls.isWebApkPackage(GS_KIDS));
        assertTrue(WebApkUrls.isWebApkPackage("org.chromium.webapk.a4aaa2f3deb113437"));
        assertFalse(WebApkUrls.isWebApkPackage("org.chromium.webapk."));
        assertFalse(WebApkUrls.isWebApkPackage("com.android.chrome"));
        assertFalse(WebApkUrls.isWebApkPackage(null));
        assertTrue(WebApkUrls.isWebApk("com.example.shell", true, false));
        assertTrue(WebApkUrls.isWebApk("com.example.shell", false, true));
        assertFalse(WebApkUrls.isWebApk("com.example.game", false, false));
    }

    @Test
    public void gsKidsMetadataResolvesStartUrlAndScope() {
        WebApkUrls.Target target = WebApkUrls.resolve(GS_KIDS, true, true, START, SCOPE, null);
        assertEquals(START, target.startUrl);
        assertEquals(SCOPE, target.scopeUrl);
        assertTrue(SiteNavigationPolicy.allows(target.startUrl, target.scopeUrl, START + "play"));
        assertFalse(SiteNavigationPolicy.allows(
                target.startUrl, target.scopeUrl, "https://gamestudio42.github.io/another-game/"));
    }

    @Test
    public void onlyHttpsStartUrlsAreAccepted() {
        assertNull(WebApkUrls.httpsUrl("http://gamestudio42.github.io/gamestudio-kids/"));
        assertNull(WebApkUrls.httpsUrl("intent://gamestudio42.github.io/#Intent;scheme=https;end"));
        assertNull(WebApkUrls.httpsUrl("market://details?id=com.android.chrome"));
        assertNull(WebApkUrls.httpsUrl("javascript:alert(1)"));
        assertNull(WebApkUrls.httpsUrl("file:///sdcard/secret"));
        assertNull(WebApkUrls.httpsUrl("data:text/html,hi"));
        assertNull(WebApkUrls.httpsUrl("https://user:pass@gamestudio42.github.io/gamestudio-kids/"));
        assertNull(WebApkUrls.httpsUrl("https://gamestudio42.github.io/gamestudio-kids/ extra"));
        assertNull(WebApkUrls.httpsUrl(null));
        assertNull(WebApkUrls.httpsUrl(""));
        assertEquals(START, WebApkUrls.httpsUrl("  " + START + "  "));
    }

    @Test
    public void httpMetadataFallsBackToHttpsIntentFilter() {
        WebApkUrls.Target target = WebApkUrls.resolve(
                GS_KIDS,
                true,
                false,
                "http://gamestudio42.github.io/gamestudio-kids/",
                null,
                Collections.singletonList(START));
        assertEquals(START, target.startUrl);
        assertNull(target.scopeUrl);
    }

    @Test
    public void missingMetadataUsesFilterHostAndGivesUpWhenNothingIsHttps() {
        assertEquals(
                "https://gamestudio42.github.io/gamestudio-kids",
                WebApkUrls.httpsUrlFromFilter("gamestudio42.github.io", "/gamestudio-kids"));
        assertEquals(
                "https://gamestudio42.github.io/",
                WebApkUrls.httpsUrlFromFilter("gamestudio42.github.io", null));
        assertNull(WebApkUrls.httpsUrlFromFilter("*.github.io", "/gamestudio-kids"));
        assertNull(WebApkUrls.httpsUrlFromFilter("gamestudio42.github.io", "/gamestudio-kids/*"));

        WebApkUrls.Target fromFilter = WebApkUrls.resolve(
                GS_KIDS,
                false,
                false,
                null,
                null,
                Collections.singletonList("https://gamestudio42.github.io/gamestudio-kids"));
        assertEquals("https://gamestudio42.github.io/gamestudio-kids", fromFilter.startUrl);
        assertNull(fromFilter.scopeUrl);

        assertNull(WebApkUrls.resolve(
                GS_KIDS,
                false,
                false,
                null,
                null,
                Collections.singletonList("http://gamestudio42.github.io/gamestudio-kids/")));
        assertNull(WebApkUrls.resolve(GS_KIDS, false, false, "", "", Collections.<String>emptyList()));
        assertNull(WebApkUrls.resolve(
                "com.android.chrome",
                false,
                false,
                null,
                null,
                Collections.singletonList(START)));
    }

    @Test
    public void missingStartUrlOpensTheScopeItself() {
        WebApkUrls.Target target = WebApkUrls.resolve(GS_KIDS, false, true, null, SCOPE, null);
        assertEquals(SCOPE, target.startUrl);
        assertEquals(SCOPE, target.scopeUrl);

        WebApkUrls.Target insteadOfHostRoot = WebApkUrls.resolve(
                GS_KIDS,
                false,
                true,
                null,
                SCOPE,
                Collections.singletonList("https://gamestudio42.github.io/"));
        assertEquals(SCOPE, insteadOfHostRoot.startUrl);
        assertEquals(SCOPE, insteadOfHostRoot.scopeUrl);
    }

    @Test
    public void scopeOnAnotherHostOrOutsideTheStartUrlIsDropped() {
        WebApkUrls.Target otherHost = WebApkUrls.resolve(
                GS_KIDS, true, true, START, "https://evil.example/gamestudio-kids/", null);
        assertEquals(START, otherHost.startUrl);
        assertNull(otherHost.scopeUrl);

        WebApkUrls.Target narrower = WebApkUrls.resolve(
                GS_KIDS,
                true,
                true,
                START,
                "https://gamestudio42.github.io/gamestudio-kids/only-this/",
                null);
        assertEquals(START, narrower.startUrl);
        assertNull(narrower.scopeUrl);
    }

    @Test
    public void filterUrlMatchingTheScopeHostWins() {
        WebApkUrls.Target target = WebApkUrls.resolve(
                GS_KIDS,
                false,
                true,
                "not a url",
                SCOPE,
                Arrays.asList(
                        "https://example.com/",
                        "http://gamestudio42.github.io/gamestudio-kids/",
                        START));
        assertEquals(START, target.startUrl);
        assertEquals(SCOPE, target.scopeUrl);
    }
}
