package io.github.appstudio42.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Navigation stays on the shortcut host and its subdomains.
 * A lookalike such as evil-poki.com is a different site.
 * A WebAPK scope also limits the path, so another project on the same host
 * (gamestudio42.github.io) cannot be opened from GS Kids.
 */

public class SiteNavigationPolicyTest {
    private static final String POKI = "https://kids.poki.com/";
    private static final String APEX = "https://poki.com/games";

    @Test
    public void sameHostIsAllowed() {
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com/"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com/games/1"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "http://kids.poki.com/play"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "HTTPS://Kids.Poki.COM/Play"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com./path"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com:8443/game"));
    }

    @Test
    public void subdomainIsAllowed() {
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://cdn.kids.poki.com/app.js"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "https://a.b.kids.poki.com/level"));
        assertTrue(SiteNavigationPolicy.allows(APEX, "https://kids.poki.com/"));
        assertTrue(SiteNavigationPolicy.allows(APEX, "https://www.poki.com/"));
    }

    @Test
    public void differentDomainIsBlocked() {
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://example.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://www.poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com.evil.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://evil.com/kids.poki.com"));
        assertFalse(SiteNavigationPolicy.allows(APEX, "https://notpoki.com/"));
    }

    @Test
    public void nonHttpSchemesAreBlocked() {
        assertFalse(SiteNavigationPolicy.allows(POKI, "intent://scan/#Intent;scheme=https;end"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "market://details?id=com.android.chrome"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "tel:123"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "mailto:a@example.com"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "file:///sdcard/secret"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "content://settings/system"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "javascript:alert(1)"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "data:text/html,hi"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "blob:https://kids.poki.com/uuid"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "about:blank"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "ftp://kids.poki.com/file"));
    }

    @Test
    public void suffixAttackLikeEvilPokiIsBlocked() {
        assertFalse(SiteNavigationPolicy.allows(APEX, "https://evil-poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(APEX, "https://evil-poki.com/login"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://evil-poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://notkids.poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(APEX, "https://poki.com.evil-poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(
                "https://www.coolmathgames.com/",
                "https://coolmathgames.com.evil.com/"));
    }

    @Test
    public void userinfoAndGarbageAreBlocked() {
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://kids.poki.com@evil.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://evil.com@kids.poki.com/"));
        assertFalse(SiteNavigationPolicy.allows(POKI, null));
        assertFalse(SiteNavigationPolicy.allows(POKI, ""));
        assertFalse(SiteNavigationPolicy.allows(POKI, "   "));
        assertFalse(SiteNavigationPolicy.allows(null, POKI));
        assertFalse(SiteNavigationPolicy.allows("not a url", POKI));
        assertFalse(SiteNavigationPolicy.allows(POKI, "https://10.0.0.8/"));
        assertTrue(SiteNavigationPolicy.allows("http://10.0.0.8/game", "http://10.0.0.8/play"));
        assertFalse(SiteNavigationPolicy.allows("http://10.0.0.8/game", "http://10.0.0.80/"));
        assertFalse(SiteNavigationPolicy.allows("http://10.0.0.8/game", "http://evil.10.0.0.8/"));
    }

    @Test
    public void missingScopeKeepsHostAndSubdomainRules() {
        assertTrue(SiteNavigationPolicy.allows(POKI, null, "https://kids.poki.com/games/1"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "  ", "https://cdn.kids.poki.com/app.js"));
        assertTrue(SiteNavigationPolicy.allows(POKI, "not a scope", "https://kids.poki.com/play"));
    }

    @Test
    public void gsKidsScopeStaysOnThatPath() {
        String start = "https://gamestudio42.github.io/gamestudio-kids/";
        String scope = "https://gamestudio42.github.io/gamestudio-kids/";
        assertTrue(SiteNavigationPolicy.allows(start, scope, start));
        assertTrue(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids"));
        assertTrue(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids/play"));
        assertTrue(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids/index.html?x=1"));
        assertTrue(SiteNavigationPolicy.allows(start, scope, "https://GameStudio42.GitHub.io/gamestudio-kids/level"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/another-game/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids-evil"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids-evil/play"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://cdn.gamestudio42.github.io/gamestudio-kids/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "http://gamestudio42.github.io/gamestudio-kids/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids/../other"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/gamestudio-kids/%2e%2e/secret"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "intent://gamestudio42.github.io/gamestudio-kids/#Intent;scheme=https;end"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "market://details?id=com.android.chrome"));
    }

    @Test
    public void hostWideScopeAllowsEveryPathOnThatHost() {
        String start = "https://gamestudio42.github.io/gamestudio-kids/";
        String scope = "https://gamestudio42.github.io/";
        assertTrue(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/other/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://example.com/gamestudio-kids/"));
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://www.gamestudio42.github.io/gamestudio-kids/"));
    }

    @Test
    public void serviceWorkerSameScopeIsNotBlocked() {
        String start = "https://gamestudio42.github.io/gamestudio-kids/";
        String scope = "https://gamestudio42.github.io/gamestudio-kids/";
        assertTrue(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "https://gamestudio42.github.io/gamestudio-kids/sw.js"));
        assertTrue(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "https://gamestudio42.github.io/gamestudio-kids/"));
        // Navigation to a sibling project is blocked. A worker fetch is not:
        // Cache Storage may hold a font or script from another https host.
        assertFalse(SiteNavigationPolicy.allows(start, scope, "https://gamestudio42.github.io/another-game/"));
        assertTrue(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "https://cdn.example.com/app.js"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "http://gamestudio42.github.io/gamestudio-kids/sw.js"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "intent://scan/#Intent;scheme=https;end"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(
                scope, start, "market://details?id=com.android.chrome"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(scope, start, "file:///sdcard/secret"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(scope, start, null));
        assertTrue(SiteNavigationPolicy.allowsServiceWorkerRequest(
                null, null, "https://gamestudio42.github.io/gamestudio-kids/sw.js"));
        assertFalse(SiteNavigationPolicy.allowsServiceWorkerRequest(null, null, "javascript:alert(1)"));
    }
}
