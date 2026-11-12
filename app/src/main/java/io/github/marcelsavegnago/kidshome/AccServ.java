package io.github.marcelsavegnago.kidshome;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service that detects Home / Fire launcher and redirects to the chosen target.
 */
public class AccServ extends AccessibilityService {
    static final String TAG = "KidsHome.AccServ";

    /** Known Fire OS / Android stock launcher packages that should be hijacked. */
    private static final String[] HIJACK_PACKAGES = {
            "com.amazon.firelauncher",
            "com.amazon.launcher",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3"
    };

    static volatile boolean homePressCanceled = false;
    static volatile String lastApp = "";
    static volatile String lastClass = "";
    static volatile boolean serviceConnected = false;

    private HomeWatcher homeWatcher;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        CharSequence packageCs = event.getPackageName();
        CharSequence classCs = event.getClassName();
        if (packageCs != null) {
            lastApp = packageCs.toString();
        }
        if (classCs != null) {
            lastClass = classCs.toString();
        }

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            maybeEnforceKidMode();
            return;
        }

        String packageName = lastApp;

        // React as early as possible when Fire launcher (or another stock launcher) appears.
        if (Prefs.applicationOpenDetection(this) && shouldHijackPackage(packageName)) {
            HomePress.perform(this);
            return;
        }

        maybeEnforceKidMode();
    }

    private void maybeEnforceKidMode() {
        if (!Prefs.isKidModeEnabled(this)) {
            return;
        }
        String packageName = lastApp;
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        if (isAllowedInKidMode(packageName)) {
            return;
        }
        // Redirect away from non-allowed apps (settings, play store, etc.).
        HomePress.perform(this);
    }

    private boolean isAllowedInKidMode(String packageName) {
        if (packageName.equals(getPackageName())) {
            // KidsHome itself is gated by PIN in the UI.
            return true;
        }
        if (packageName.equals("com.android.systemui")
                || packageName.equals("android")
                || packageName.startsWith("com.android.systemui")) {
            return true;
        }
        if (shouldHijackPackage(packageName)) {
            // Stock launcher is never "allowed"; AccServ will redirect.
            return false;
        }

        if (Prefs.isKidLockToApp(this)) {
            String kidApp = Prefs.getKidAppPackage(this);
            return packageName.equals(kidApp);
        }
        String launcher = Prefs.getChosenLauncherPackage(this);
        return packageName.equals(launcher);
    }

    private static boolean shouldHijackPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String hijack : HIJACK_PACKAGES) {
            if (hijack.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        Log.v(TAG, "onInterrupt");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (!Prefs.hardwareDetection(this) || event == null) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_HOME:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    homePressCanceled = false;
                } else if (event.getAction() == KeyEvent.ACTION_DOWN && !homePressCanceled) {
                    HomePress.perform(this);
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_MENU:
                if (Prefs.menuButtonOverride(this) && event.getAction() == KeyEvent.ACTION_DOWN) {
                    homePressCanceled = true;
                }
                return false;

            default:
                return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceConnected = true;
        StatusNotifier.cancelAccessibilityDisabled(this);

        lastClass = "";
        lastApp = "";

        if (homeWatcher != null) {
            homeWatcher.stopWatch();
        }
        homeWatcher = new HomeWatcher(getApplicationContext());
        homeWatcher.setOnHomePressedListener(new OnHomePressedListener() {
            @Override
            public void onHomePressed() {
                if (!Prefs.broadcastDetection(AccServ.this) || homePressCanceled) {
                    return;
                }
                if (Prefs.recentAppOverride(AccServ.this)
                        && "com.android.systemui".equals(lastApp)
                        && lastClass != null
                        && lastClass.contains("Recents")) {
                    return;
                }
                HomePress.perform(AccServ.this);
            }

            @Override
            public void onRecentAppPressed() {
                if (Prefs.isKidModeEnabled(AccServ.this)) {
                    // In kid mode, Recents should also bounce back to the locked target.
                    handler.postDelayed(() -> HomePress.perform(AccServ.this), 80);
                }
            }
        });
        homeWatcher.startWatch();

        ServiceMan.start(getApplicationContext());
        StatusNotifier.ensureChannel(this);

        Log.i(TAG, "KidsHome accessibility service connected on " + android.os.Build.MODEL);
        // Force launch after reconnect to recover from the known cold-start / ~10s delay case.
        handler.post(() -> HomePress.perform(getApplicationContext(), true));
        handler.postDelayed(() -> HomePress.warmTarget(getApplicationContext()), 1500);
    }

    @Override
    public void onDestroy() {
        serviceConnected = false;
        if (homeWatcher != null) {
            homeWatcher.stopWatch();
            homeWatcher = null;
        }
        StatusNotifier.notifyAccessibilityDisabled(getApplicationContext());
        super.onDestroy();
    }

    public static boolean isRunning() {
        return serviceConnected;
    }
}
