package io.github.marcelsavegnago.kidshome;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.http.SslError;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Full-screen site shortcut. Behaves like an installed app: the site's title
 * and icon, no address bar, and no path out to another domain or to an
 * external browser.
 *
 * <p>Cookies, DOM storage, and the HTTP cache stay in the app WebView profile
 * and are partitioned by origin, so each site keeps its own progress. They
 * are not cleared when this screen closes.
 *
 * <p>Time on this screen counts toward the daily limit because the screen-time
 * watchdog ticks while it is in front. Bedtime, the daily limit, and a remote
 * lock replace it with {@link ScreenTimeLockActivity}. The screen is this
 * app's own package, so {@link AccServ} and {@link HomeWatcher} do not treat
 * it as a foreign app.
 */
public class SiteActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "siteUrl";
    public static final String EXTRA_NAME = "siteName";

    private static final long TICK_MS = 15_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable screenTick = new Runnable() {
        @Override
        public void run() {
            enforceKidSession();
            if (!isFinishing()) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private WebView webView;
    private WebView popupProbe;
    private ImageView siteIcon;
    private TextView siteTitle;
    private String anchorUrl = "";
    private String siteName = "";
    private long lastRecoverElapsedMs;

    public static Intent intent(Context context, SiteShortcut shortcut) {
        Intent intent = new Intent(context, SiteActivity.class);
        intent.putExtra(EXTRA_URL, shortcut.url);
        intent.putExtra(EXTRA_NAME, shortcut.name);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        anchorUrl = getIntent() != null ? getIntent().getStringExtra(EXTRA_URL) : null;
        siteName = getIntent() != null ? getIntent().getStringExtra(EXTRA_NAME) : null;
        if (anchorUrl == null) {
            anchorUrl = "";
        }
        if (siteName == null || siteName.trim().isEmpty()) {
            siteName = anchorUrl;
        } else {
            siteName = siteName.trim();
        }
        if (!canShow(anchorUrl)) {
            finish();
            return;
        }

        LockBack.intercept(this, getOnBackPressedDispatcher(), this::onBack);

        setContentView(R.layout.activity_site);
        ImmersiveHelper.enter(this);
        ImmersiveHelper.applyContentInsets(findViewById(R.id.siteRoot));

        siteIcon = findViewById(R.id.siteIcon);
        siteTitle = findViewById(R.id.siteTitle);
        webView = findViewById(R.id.siteWeb);
        showTitle(siteName, null);
        FaviconHelper.loadInto(this, anchorUrl, siteIcon, R.drawable.ic_web_shortcut);

        WebView.setWebContentsDebuggingEnabled(false);
        configureWebView(webView, true);
        webView.setWebViewClient(createClient());
        webView.setWebChromeClient(new SiteChrome());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            // Downloads stay inside the shortcut. Nothing is handed to another app.
        });

        boolean restored = false;
        if (savedInstanceState != null) {
            restored = webView.restoreState(savedInstanceState) != null;
        }
        if (!restored) {
            webView.loadUrl(anchorUrl);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImmersiveHelper.enter(this);
        if (webView != null) {
            webView.onResume();
        }
        enforceKidSession();
        handler.removeCallbacks(screenTick);
        if (!isFinishing()) {
            handler.postDelayed(screenTick, TICK_MS);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isFinishing() && Prefs.isKidModeEnabled(this)) {
            ImmersiveHelper.enter(this);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isFinishing()) {
            ImmersiveHelper.enter(this);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(screenTick);
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
        Prefs.flushScreenTime(this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        destroyPopup();
        destroyWebView();
        super.onDestroy();
    }

    private void onBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        finish();
    }

    /**
     * Counts this screen like any other kid-mode foreground (daily limit) and
     * leaves it when bedtime, the daily limit, or a remote lock is active.
     * The foreground package is this app, so a previously open game does not
     * keep accruing per-app time while the site is up.
     */
    private void enforceKidSession() {
        if (isFinishing()) {
            return;
        }
        if (!canShow(anchorUrl)) {
            finish();
            return;
        }
        AccServ.lastApp = getPackageName();
        Prefs.setForegroundTrackPackage(this, getPackageName());
        ScreenTimeTracker.tick(this);
        if (ScreenTimeTracker.isBlocked(this)) {
            if (webView != null) {
                webView.onPause();
            }
            ScreenTimeLockActivity.launch(this);
        }
    }

    private boolean canShow(String url) {
        if (!Prefs.isKidModeEnabled(this)) {
            return false;
        }
        if (SiteNavigationPolicy.httpHost(url) == null || !isSavedShortcut(url)) {
            return false;
        }
        return !url.isEmpty();
    }

    private boolean isSavedShortcut(String url) {
        for (SiteShortcut site : Prefs.getSiteShortcuts(this)) {
            if (site.url != null && site.url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true when the WebView must not navigate there
     */
    private boolean blockedNavigation(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        // about:blank is the WebView's empty document, not another site.
        if ("about:blank".equals(url) || url.startsWith("about:blank#") || url.startsWith("about:blank?")) {
            return false;
        }
        return !SiteNavigationPolicy.allows(anchorUrl, url);
    }

    private void recoverFromBlocked(WebView view) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRecoverElapsedMs < 400L) {
            view.stopLoading();
            return;
        }
        lastRecoverElapsedMs = now;
        view.stopLoading();
        if (view.canGoBack()) {
            view.goBack();
        }
    }

    private void showTitle(String title, @Nullable Bitmap icon) {
        String label = title == null ? "" : title.trim();
        if (label.isEmpty() || looksLikeUrl(label)) {
            label = siteName;
        }
        siteTitle.setText(label);
        setTitle(label);
        if (icon != null) {
            siteIcon.setImageBitmap(icon);
        }
        updateTaskDescription(label, icon);
    }

    @SuppressWarnings("deprecation")
    private void updateTaskDescription(String label, @Nullable Bitmap icon) {
        Bitmap copy = copyBitmap(icon);
        try {
            // Bitmap constructor still accepts the site icon on API 22–35.
            setTaskDescription(new ActivityManager.TaskDescription(label, copy));
        } catch (RuntimeException ignored) {
            // A recycled favicon must not take down the shortcut.
        }
    }

    @Nullable
    private static Bitmap copyBitmap(@Nullable Bitmap icon) {
        if (icon == null || icon.isRecycled()) {
            return null;
        }
        try {
            Bitmap.Config config = icon.getConfig() != null ? icon.getConfig() : Bitmap.Config.ARGB_8888;
            return icon.copy(config, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean looksLikeUrl(String title) {
        String value = title.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private void configureWebView(WebView view, boolean enableJavaScript) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(enableJavaScript);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(enableJavaScript);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        SiteWebViewSupport.applyVersionedSettings(getApplicationContext(), settings);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);
        view.removeJavascriptInterface("searchBoxJavaBridge_");
        view.removeJavascriptInterface("accessibility");
        view.removeJavascriptInterface("accessibilityTraversal");
    }

    private void destroyWebView() {
        WebView view = webView;
        webView = null;
        releaseWebView(view);
    }

    private void destroyPopup() {
        WebView view = popupProbe;
        popupProbe = null;
        releaseWebView(view);
    }

    private static void releaseWebView(@Nullable WebView view) {
        if (view == null) {
            return;
        }
        try {
            view.stopLoading();
            view.setWebChromeClient(null);
            view.setDownloadListener(null);
            view.setWebViewClient(new WebViewClient());
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
            }
            view.destroy();
        } catch (RuntimeException ignored) {
        }
    }

    private void deliverPopup(String url) {
        if (url == null || "about:blank".equals(url) || url.startsWith("about:blank")) {
            return;
        }
        WebView host = webView;
        if (host != null && !blockedNavigation(url)) {
            host.loadUrl(url);
        }
        // Destroying the probe inside its own navigation callback crashes some WebViews.
        handler.post(this::destroyPopup);
    }

    private WebViewClient createClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new SiteClient26();
        }
        return new SiteClient();
    }

    private class SiteClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request != null && request.getUrl() != null ? request.getUrl().toString() : null;
            if (blockedNavigation(url)) {
                return true;
            }
            return false;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return blockedNavigation(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (blockedNavigation(url)) {
                recoverFromBlocked(view);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (handler != null) {
                handler.cancel();
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (isFinishing()) {
                return;
            }
            // API 23+ calls this from the main-frame overload only. Older WebViews
            // report subresources here too, so ignore those.
            String current = view != null ? view.getUrl() : null;
            boolean mainFrame = failingUrl == null
                    || failingUrl.equals(anchorUrl)
                    || (current != null && failingUrl.equals(current));
            if (mainFrame) {
                Toast.makeText(SiteActivity.this, R.string.site_load_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Loaded only on API 26+, where {@link RenderProcessGoneDetail} exists.
     * Returning true keeps a dead renderer from crashing the kid home.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private final class SiteClient26 extends SiteClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (view != null) {
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(view);
                }
                view.destroy();
                if (view == webView) {
                    webView = null;
                }
                if (view == popupProbe) {
                    popupProbe = null;
                }
            }
            if (!isFinishing()) {
                finish();
            }
            return true;
        }
    }

    private final class SiteChrome extends WebChromeClient {
        @Override
        public void onReceivedTitle(WebView view, String title) {
            showTitle(title, null);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (icon != null) {
                showTitle(siteTitle.getText() != null ? siteTitle.getText().toString() : siteName, icon);
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
                return false;
            }
            destroyPopup();
            WebView probe = new WebView(view.getContext());
            configureWebView(probe, false);
            probe.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView popup, WebResourceRequest request) {
                    String url = request != null && request.getUrl() != null
                            ? request.getUrl().toString() : null;
                    deliverPopup(url);
                    return true;
                }

                @SuppressWarnings("deprecation")
                @Override
                public boolean shouldOverrideUrlLoading(WebView popup, String url) {
                    deliverPopup(url);
                    return true;
                }

                @Override
                public void onPageStarted(WebView popup, String url, Bitmap favicon) {
                    if (url != null && !url.startsWith("about:blank")) {
                        deliverPopup(url);
                    }
                }
            });
            probe.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            });
            popupProbe = probe;
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(probe);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            destroyPopup();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (callback != null) {
                callback.invoke(origin, false, false);
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (request != null) {
                request.deny();
            }
        }

        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            return true;
        }
    }
}
