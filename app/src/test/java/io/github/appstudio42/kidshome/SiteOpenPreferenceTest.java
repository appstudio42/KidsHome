package io.github.appstudio42.kidshome;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Embedded site shortcuts are the default, including for installs that already
 * picked a browser. Saved shortcuts and the browser package are left in place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SiteOpenPreferenceTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void missingPreferenceDefaultsToEmbedded() {
        assertTrue(Prefs.isSitesOpenEmbedded(context));
        assertFalse(Prefs.isExternalBrowserAllowed(context, "com.android.chrome"));
    }

    @Test
    public void freshMigrationDoesNotSeedShortcutsAndOpensEmbedded() {
        Prefs.get(context).edit().putInt(Prefs.KEY_PREFS_VERSION, 0).commit();

        Prefs.migrateIfNeeded(context);

        assertEquals(Prefs.CURRENT_PREFS_VERSION,
                Prefs.get(context).getInt(Prefs.KEY_PREFS_VERSION, -1));
        assertTrue(Prefs.isSitesOpenEmbedded(context));
        assertTrue(Prefs.getSiteShortcuts(context).isEmpty());
        assertEquals("", Prefs.getBrowserPackage(context));
    }

    @Test
    public void existingBrowserMovesToEmbeddedWithoutLosingBrowserOrShortcuts() {
        String json = "[{\"id\":\"poki\",\"name\":\"Poki Kids\",\"url\":\"https://kids.poki.com/\"}]";
        Prefs.get(context).edit()
                .putInt(Prefs.KEY_PREFS_VERSION, 3)
                .putString(Prefs.KEY_BROWSER_PACKAGE, "com.android.chrome")
                .putString(Prefs.KEY_BROWSER_ACTIVITY, "com.android.chrome.Main")
                .putString(Prefs.KEY_SITE_SHORTCUTS, json)
                .commit();

        Prefs.migrateIfNeeded(context);

        assertEquals(4, Prefs.get(context).getInt(Prefs.KEY_PREFS_VERSION, -1));
        assertTrue(Prefs.isSitesOpenEmbedded(context));
        assertEquals("com.android.chrome", Prefs.getBrowserPackage(context));
        assertEquals("com.android.chrome.Main", Prefs.getBrowserActivity(context));
        List<SiteShortcut> sites = Prefs.getSiteShortcuts(context);
        assertEquals(1, sites.size());
        assertEquals("poki", sites.get(0).id);
        assertEquals("Poki Kids", sites.get(0).name);
        assertEquals("https://kids.poki.com/", sites.get(0).url);
        assertFalse(Prefs.isExternalBrowserAllowed(context, "com.android.chrome"));
    }

    @Test
    public void migrationLeavesAnEmptyShortcutListEmpty() {
        Prefs.get(context).edit()
                .putInt(Prefs.KEY_PREFS_VERSION, 3)
                .putString(Prefs.KEY_BROWSER_PACKAGE, "com.android.chrome")
                .putBoolean("siteSuggestionsSeeded", false)
                .commit();

        Prefs.migrateIfNeeded(context);

        assertTrue(Prefs.getSiteShortcuts(context).isEmpty());
        assertEquals("com.android.chrome", Prefs.getBrowserPackage(context));
        assertTrue(Prefs.isSitesOpenEmbedded(context));
    }

    @Test
    public void embeddedCanBeTurnedOffWithoutClearingTheSavedBrowser() {
        Prefs.setBrowser(context, "com.android.chrome", "com.android.chrome.Main");
        Prefs.setSitesOpenEmbedded(context, true);
        assertEquals("com.android.chrome", Prefs.getBrowserPackage(context));
        assertEquals("com.android.chrome.Main", Prefs.getBrowserActivity(context));
        assertFalse(Prefs.isExternalBrowserAllowed(context, "com.android.chrome"));

        Prefs.setSitesOpenEmbedded(context, false);

        assertFalse(Prefs.isSitesOpenEmbedded(context));
        assertEquals("com.android.chrome", Prefs.getBrowserPackage(context));
        assertEquals("com.android.chrome.Main", Prefs.getBrowserActivity(context));
        assertTrue(Prefs.isExternalBrowserAllowed(context, "com.android.chrome"));
        assertFalse(Prefs.isExternalBrowserAllowed(context, "org.mozilla.firefox"));
    }

    @Test
    public void secondMigrationDoesNotForceEmbeddedBackOn() {
        Prefs.get(context).edit()
                .putInt(Prefs.KEY_PREFS_VERSION, 3)
                .putString(Prefs.KEY_BROWSER_PACKAGE, "com.android.chrome")
                .commit();
        Prefs.migrateIfNeeded(context);
        Prefs.setSitesOpenEmbedded(context, false);

        Prefs.migrateIfNeeded(context);

        assertFalse(Prefs.isSitesOpenEmbedded(context));
        assertEquals("com.android.chrome", Prefs.getBrowserPackage(context));
        assertEquals(Prefs.CURRENT_PREFS_VERSION,
                Prefs.get(context).getInt(Prefs.KEY_PREFS_VERSION, -1));
    }
}
