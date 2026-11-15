package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;

/**
 * WebView calls that do not exist on API 22. Each target lives in its own
 * class so an older device never loads it.
 */
final class SiteWebViewSupport {
    private SiteWebViewSupport() {
    }

    static void applyVersionedSettings(Context context, WebSettings settings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SafeBrowsing26.enable(settings);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SafeBrowsing27.start(context);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForceDark29.disable(settings);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Darkening33.disable(settings);
        }
    }

    private static final class SafeBrowsing26 {
        private SafeBrowsing26() {
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressWarnings("deprecation")
        static void enable(WebSettings settings) {
            settings.setSafeBrowsingEnabled(true);
        }
    }

    private static final class SafeBrowsing27 {
        private SafeBrowsing27() {
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        static void start(Context context) {
            WebView.startSafeBrowsing(context.getApplicationContext(), null);
        }
    }

    private static final class ForceDark29 {
        private ForceDark29() {
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @SuppressWarnings("deprecation")
        static void disable(WebSettings settings) {
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
    }

    private static final class Darkening33 {
        private Darkening33() {
        }

        @RequiresApi(33)
        static void disable(WebSettings settings) {
            settings.setAlgorithmicDarkeningAllowed(false);
        }
    }
}
