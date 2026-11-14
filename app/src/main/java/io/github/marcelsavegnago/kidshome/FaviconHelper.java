package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight favicon fetch/cache (no image libraries, no third-party favicon hosts).
 * Decodes off the main thread and only binds a bitmap when the view still shows that URL.
 */
public final class FaviconHelper {
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_HTML_BYTES = 48 * 1024;
    private static final int MAX_IMAGE_BYTES = 256 * 1024;
    private static final int EXECUTOR_THREADS = 2;
    private static final String USER_AGENT = "KidsHome";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            EXECUTOR_THREADS, new FaviconThreadFactory());
    private static final MemoryCache MEMORY = new MemoryCache();

    public interface Callback {
        void onResult(Bitmap bitmap);
    }

    private FaviconHelper() {
    }

    public static void loadInto(Context context, String pageUrl, ImageView target, int placeholderRes) {
        if (target == null) {
            return;
        }
        target.setImageResource(placeholderRes);
        String key = normalize(pageUrl);
        // Drop a result that belongs to the previous row when this view is recycled.
        target.setTag(R.id.favicon_bind_key, key);
        if (key == null || context == null) {
            return;
        }
        MemoryCache.Entry cached = MEMORY.get(key);
        if (cached != null) {
            if (cached.bitmap != null) {
                target.setImageBitmap(cached.bitmap);
            }
            return;
        }
        Context app = applicationContext(context);
        WeakReference<ImageView> viewRef = new WeakReference<>(target);
        submit(() -> {
            Bitmap bitmap = loadBitmap(app, key);
            MEMORY.put(key, bitmap);
            postToMain(() -> {
                ImageView view = viewRef.get();
                if (view == null) {
                    return;
                }
                MemoryCache.Entry latest = MEMORY.get(key);
                Bitmap resolved = latest != null ? latest.bitmap : bitmap;
                applyIfCurrent(view, key, resolved);
            });
        });
    }

    public static void fetchAsync(Context context, String pageUrl, Callback callback) {
        if (callback == null) {
            return;
        }
        String key = normalize(pageUrl);
        if (key == null || context == null) {
            postToMain(() -> callback.onResult(null));
            return;
        }
        MemoryCache.Entry cached = MEMORY.get(key);
        if (cached != null) {
            Bitmap bitmap = cached.bitmap;
            postToMain(() -> callback.onResult(bitmap));
            return;
        }
        Context app = applicationContext(context);
        submit(() -> {
            Bitmap bitmap = loadBitmap(app, key);
            MEMORY.put(key, bitmap);
            MemoryCache.Entry latest = MEMORY.get(key);
            Bitmap resolved = latest != null ? latest.bitmap : bitmap;
            postToMain(() -> callback.onResult(resolved));
        });
    }

    /**
     * Binds {@code bitmap} only when {@code target} is still waiting for {@code requestKey}.
     * A null bitmap leaves the placeholder in place (negative cache / failed fetch).
     *
     * @return true when the bitmap was applied
     */
    static boolean applyIfCurrent(ImageView target, String requestKey, Bitmap bitmap) {
        if (target == null || requestKey == null || bitmap == null) {
            return false;
        }
        Object tag = target.getTag(R.id.favicon_bind_key);
        if (!requestKey.equals(tag)) {
            return false;
        }
        target.setImageBitmap(bitmap);
        return true;
    }

    static MemoryCache memoryCache() {
        return MEMORY;
    }

    private static void submit(Runnable work) {
        try {
            EXECUTOR.execute(work);
        } catch (RuntimeException ignored) {
            // The shared pool rejected the task. Leave the placeholder / skip the callback body.
        }
    }

    private static void postToMain(Runnable action) {
        Looper main = Looper.getMainLooper();
        if (main == null || action == null) {
            return;
        }
        if (Looper.myLooper() == main) {
            action.run();
        } else {
            new Handler(main).post(action);
        }
    }

    private static Context applicationContext(Context context) {
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }

    private static String normalize(String pageUrl) {
        if (pageUrl == null) {
            return null;
        }
        String trimmed = pageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Reads the disk cache, then the site itself. Never calls a third-party favicon host.
     * Must not run on the main thread: decoding happens here.
     */
    private static Bitmap loadBitmap(Context context, String pageUrl) {
        try {
            File cached = cacheFile(context, pageUrl);
            if (cached.exists()) {
                Bitmap fromDisk = BitmapFactory.decodeFile(cached.getAbsolutePath());
                if (fromDisk != null) {
                    return fromDisk;
                }
            }
            return download(context, pageUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap download(Context context, String pageUrl) {
        try {
            Fetched page = fetch(pageUrl, true);
            if (page.image != null) {
                save(cacheFile(context, pageUrl), page.image);
                return page.image;
            }
            List<String> candidates = FaviconUrls.candidates(pageUrl, page.html);
            for (String candidate : candidates) {
                if (candidate.equals(pageUrl)) {
                    continue;
                }
                Fetched icon = fetch(candidate, false);
                if (icon.image != null) {
                    save(cacheFile(context, pageUrl), icon.image);
                    return icon.image;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final class Fetched {
        final Bitmap image;
        final String html;

        Fetched(Bitmap image, String html) {
            this.image = image;
            this.html = html;
        }
    }

    private static Fetched fetch(String url, boolean captureHtml) throws Exception {
        HttpURLConnection conn = open(url);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return new Fetched(null, null);
            }
            String type = conn.getContentType();
            boolean image = type != null && type.toLowerCase(Locale.US).startsWith("image/");
            try (InputStream in = conn.getInputStream()) {
                if (image || !captureHtml) {
                    byte[] bytes = readLimited(in, MAX_IMAGE_BYTES);
                    if (bytes.length == 0) {
                        return new Fetched(null, null);
                    }
                    return new Fetched(BitmapFactory.decodeByteArray(bytes, 0, bytes.length), null);
                }
                byte[] bytes = readLimited(in, MAX_HTML_BYTES);
                return new Fetched(null, new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        return conn;
    }

    private static byte[] readLimited(InputStream in, int max) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1 && total < max) {
            int keep = Math.min(n, max - total);
            out.write(buf, 0, keep);
            total += keep;
        }
        return out.toByteArray();
    }

    private static void save(File file, Bitmap bmp) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
        } catch (Exception ignored) {
        }
    }

    private static File cacheFile(Context context, String pageUrl) {
        String hash = sha1(pageUrl);
        return new File(new File(context.getFilesDir(), "favicons"), hash + ".png");
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * In-memory favicon cache. A stored entry with a null bitmap is a negative hit:
     * the URL was tried and failed, so callers must not fetch it again until eviction.
     */
    static final class MemoryCache {
        static final int MAX_ENTRIES = 48;

        private final LruCache<String, Entry> entries;

        MemoryCache() {
            this(MAX_ENTRIES);
        }

        MemoryCache(int maxEntries) {
            entries = new LruCache<>(Math.max(1, maxEntries));
        }

        Entry get(String key) {
            if (key == null || key.isEmpty()) {
                return null;
            }
            return entries.get(key);
        }

        /**
         * Stores a bitmap, or a negative entry when {@code bitmap} is null.
         * A failure does not replace a bitmap that is already cached.
         */
        void put(String key, Bitmap bitmap) {
            if (key == null || key.isEmpty()) {
                return;
            }
            synchronized (entries) {
                if (bitmap == null) {
                    Entry existing = entries.get(key);
                    if (existing != null && existing.bitmap != null) {
                        return;
                    }
                }
                entries.put(key, new Entry(bitmap));
            }
        }

        int size() {
            return entries.size();
        }

        void clear() {
            entries.evictAll();
        }

        static final class Entry {
            final Bitmap bitmap;

            Entry(Bitmap bitmap) {
                this.bitmap = bitmap;
            }

            boolean isNegative() {
                return bitmap == null;
            }
        }
    }

    private static final class FaviconThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "favicon-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
