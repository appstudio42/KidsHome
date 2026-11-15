package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerWebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.util.Collections;

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
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                ServiceWorker24.enable();
            } catch (RuntimeException ignored) {
                // A few OEM WebViews reject the service-worker controller.
                // The page still loads; offline reuse then depends on the HTTP cache.
            }
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

    /**
     * Service workers, and the Cache Storage they use, exist on API 24+.
     * The controller is process-wide. Same-scope and other http(s) fetches
     * proceed (return null) so an offline reload can be served from cache.
     * {@code intent:}, {@code market:}, and other non-http schemes are blocked.
     * File and content access stay off. The cache mode stays {@code LOAD_DEFAULT}
     * and network loads stay allowed, so the first online visit can fill the
     * cache. This profile is not Chrome's.
     */
    @RequiresApi(24)
    private static final class ServiceWorker24 {
        private ServiceWorker24() {
        }

        static void enable() {
            ServiceWorkerController controller = ServiceWorkerController.getInstance();
            ServiceWorkerWebSettings worker = controller.getServiceWorkerWebSettings();
            worker.setAllowContentAccess(false);
            worker.setAllowFileAccess(false);
            worker.setBlockNetworkLoads(false);
            worker.setCacheMode(WebSettings.LOAD_DEFAULT);
            controller.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    Uri uri = request != null ? request.getUrl() : null;
                    String url = uri != null ? uri.toString() : null;
                    if (url == null || url.isEmpty()) {
                        return null;
                    }
                    if (SiteNavigationPolicy.allowsServiceWorkerRequest(null, null, url)) {
                        return null;
                    }
                    return new WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            403,
                            "Blocked",
                            Collections.<String, String>emptyMap(),
                            new ByteArrayInputStream(new byte[0]));
                }
            });
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
