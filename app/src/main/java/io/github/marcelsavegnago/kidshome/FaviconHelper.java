package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight favicon fetch/cache (no image libraries).
 */
public final class FaviconHelper {
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_HTML_BYTES = 48 * 1024;
    private static final int MAX_IMAGE_BYTES = 256 * 1024;
    private static final String USER_AGENT = "KidsHome";

    public interface Callback {
        void onResult(Bitmap bitmap);
    }

    private FaviconHelper() {
    }

    public static void loadInto(Context context, String pageUrl, ImageView target, int placeholderRes) {
        target.setImageResource(placeholderRes);
        File cached = cacheFile(context, pageUrl);
        if (cached.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bmp != null) {
                target.setImageBitmap(bmp);
                return;
            }
        }
        fetchAsync(context, pageUrl, bmp -> {
            if (bmp != null) {
                target.setImageBitmap(bmp);
            }
        });
    }

    public static void fetchAsync(Context context, String pageUrl, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Bitmap bmp = download(context, pageUrl);
            main.post(() -> callback.onResult(bmp));
        }, "favicon").start();
    }

    /**
     * Loads a favicon from the site itself. Never calls a third-party favicon host.
     */
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
            byte[] dig = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
