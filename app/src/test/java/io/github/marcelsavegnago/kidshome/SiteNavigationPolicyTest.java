package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Navigation stays on the shortcut host and its subdomains.
 * A lookalike such as evil-poki.com is a different site.
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
}
