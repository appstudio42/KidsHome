package io.github.marcelsavegnago.kidshome;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FaviconHelperTest {
    private static final String URL_A = "https://kids.example/games";
    private static final String URL_B = "https://kids.example/stories";

    private ImageView icon;

    @Before
    public void setUp() {
        FaviconHelper.memoryCache().clear();
        icon = new ImageView(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void memoryHitAppliesBitmapAndSkipsAnotherUrl() {
        Bitmap bitmapA = bitmap(4);
        Bitmap bitmapB = bitmap(6);
        FaviconHelper.memoryCache().put(URL_A, bitmapA);
        FaviconHelper.memoryCache().put(URL_B, bitmapB);

        FaviconHelper.loadInto(
                ApplicationProvider.getApplicationContext(),
                URL_A,
                icon,
                R.drawable.ic_web_shortcut);
        assertSame(bitmapA, drawnBitmap(icon));
        assertEquals(URL_A, icon.getTag(R.id.favicon_bind_key));

        FaviconHelper.loadInto(
                ApplicationProvider.getApplicationContext(),
                URL_B,
                icon,
                R.drawable.ic_web_shortcut);
        assertSame(bitmapB, drawnBitmap(icon));
        assertFalse(FaviconHelper.applyIfCurrent(icon, URL_A, bitmapA));
        assertSame(bitmapB, drawnBitmap(icon));
    }

    @Test
    public void negativeCacheKeepsPlaceholder() {
        Bitmap stale = bitmap(3);
        icon.setImageBitmap(stale);
        FaviconHelper.memoryCache().put(URL_A, null);

        FaviconHelper.loadInto(
                ApplicationProvider.getApplicationContext(),
                URL_A,
                icon,
                R.drawable.ic_web_shortcut);

        assertFalse(stale == drawnBitmap(icon));
        assertEquals(URL_A, icon.getTag(R.id.favicon_bind_key));
        FaviconHelper.MemoryCache.Entry entry = FaviconHelper.memoryCache().get(URL_A);
        assertNotNull(entry);
        assertTrue(entry.isNegative());
    }

    @Test
    public void blankUrlClearsTheBindKey() {
        Bitmap bitmap = bitmap(2);
        icon.setTag(R.id.favicon_bind_key, URL_A);
        FaviconHelper.loadInto(
                ApplicationProvider.getApplicationContext(),
                "  ",
                icon,
                R.drawable.ic_web_shortcut);
        assertNull(icon.getTag(R.id.favicon_bind_key));
        assertFalse(FaviconHelper.applyIfCurrent(icon, URL_A, bitmap));
    }

    @Test
    public void failureDoesNotEraseSuccessAndMissIsDistinct() {
        FaviconHelper.MemoryCache cache = FaviconHelper.memoryCache();
        assertNull(cache.get(URL_A));

        cache.put(URL_A, null);
        FaviconHelper.MemoryCache.Entry negative = cache.get(URL_A);
        assertNotNull(negative);
        assertTrue(negative.isNegative());

        Bitmap bitmap = bitmap(5);
        cache.put(URL_A, bitmap);
        assertSame(bitmap, cache.get(URL_A).bitmap);

        cache.put(URL_A, null);
        assertSame(bitmap, cache.get(URL_A).bitmap);
        assertFalse(cache.get(URL_A).isNegative());
    }

    @Test
    public void lruEvictsLeastRecentlyUsedIncludingNegativeEntries() {
        FaviconHelper.MemoryCache cache = new FaviconHelper.MemoryCache(2);
        cache.put("https://kids.example/a", bitmap(1));
        cache.put("https://kids.example/b", null);
        cache.put("https://kids.example/c", bitmap(1));

        assertNull(cache.get("https://kids.example/a"));
        assertNotNull(cache.get("https://kids.example/b"));
        assertTrue(cache.get("https://kids.example/b").isNegative());
        assertNotNull(cache.get("https://kids.example/c").bitmap);
        assertEquals(2, cache.size());
    }

    @Test
    public void ignoresEmptyKeys() {
        FaviconHelper.MemoryCache cache = new FaviconHelper.MemoryCache(2);
        cache.put(null, bitmap(1));
        cache.put("", null);
        assertEquals(0, cache.size());
        assertNull(cache.get(null));
    }

    private static Bitmap bitmap(int size) {
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }

    private static Bitmap drawnBitmap(ImageView view) {
        if (!(view.getDrawable() instanceof BitmapDrawable)) {
            return null;
        }
        return ((BitmapDrawable) view.getDrawable()).getBitmap();
    }
}
