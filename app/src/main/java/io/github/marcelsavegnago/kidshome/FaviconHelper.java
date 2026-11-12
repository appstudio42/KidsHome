package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Lightweight favicon fetch/cache (no image libraries).
 */
public final class FaviconHelper {
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

    private static Bitmap download(Context context, String pageUrl) {
        try {
            Uri uri = Uri.parse(pageUrl);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return null;
            }
            String[] candidates = new String[]{
                    "https://www.google.com/s2/favicons?domain=" + host + "&sz=128",
                    uri.getScheme() + "://" + host + "/favicon.ico"
            };
            for (String candidate : candidates) {
                HttpURLConnection conn = (HttpURLConnection) new URL(candidate).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream in = conn.getInputStream()) {
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        if (bmp != null) {
                            save(cacheFile(context, pageUrl), bmp);
                            return bmp;
                        }
                    }
                }
                conn.disconnect();
            }
        } catch (Exception ignored) {
        }
        return null;
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
