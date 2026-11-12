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

    private static final long BACK_HOLD_ESCAPE_MS = 3000L;

    static volatile boolean homePressCanceled = false;
    static volatile String lastApp = "";
    static volatile String lastClass = "";
    static volatile boolean serviceConnected = false;

    private HomeWatcher homeWatcher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long backDownAtMs;
    private boolean backEscapeFired;
    private final Runnable backHoldRunnable = () -> {
        if (!backEscapeFired && Prefs.isKidModeEnabled(AccServ.this) && Prefs.isEscapeBackHoldEnabled(AccServ.this)) {
            backEscapeFired = true;
            EscapeLauncher.openPin(AccServ.this);
        }
    };

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

        if (KidModeGate.isEscapeUiActive()) {
            return;
        }

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            maybeEnforceKidMode();
            return;
        }

        String packageName = lastApp;

        // Fire / accessibility mode: react as early as possible when a stock launcher appears.
        if (EnforcementController.shouldHijackHome(this)
                && Prefs.applicationOpenDetection(this)
                && shouldHijackPackage(packageName)) {
            HomePress.perform(this);
            return;
        }

        // Remember foreground package for per-app usage accounting.
        if (lastApp != null && !lastApp.isEmpty()
                && !lastApp.equals(getPackageName())
                && !"com.android.systemui".equals(lastApp)
                && !"android".equals(lastApp)) {
            Prefs.setForegroundTrackPackage(this, lastApp);
        }

        maybeEnforceKidMode();
    }

    private void maybeEnforceKidMode() {
        if (!Prefs.isKidModeEnabled(this) || KidModeGate.isEscapeUiActive()) {
            return;
        }
        if (ScreenTimeTracker.isBlocked(this)) {
            HomePress.perform(this);
            return;
        }
        if (!EnforcementController.shouldBlockNonAllowedApps(this)) {
            // Still bounce apps that hit a per-app limit or schedule filter when we can see them.
            String pkg = lastApp;
            if (pkg != null && !pkg.isEmpty()
                    && Prefs.getAllowedPackages(this).contains(pkg)
                    && !ScreenTimeTracker.canLaunchApp(this, pkg)) {
                HomePress.perform(this);
            }
            return;
        }
        String packageName = lastApp;
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        if (isAllowedInKidMode(packageName)) {
            return;
        }
        HomePress.perform(this);
    }

    private boolean isAllowedInKidMode(String packageName) {
        if (packageName.equals(getPackageName())) {
            // KidsHome itself (kid home / PIN / lock screens) is allowed; UI is PIN-gated.
            return true;
        }
        if (packageName.equals("com.android.systemui")
                || packageName.equals("android")
                || packageName.startsWith("com.android.systemui")) {
            return true;
        }
        if (shouldHijackPackage(packageName)) {
            return false;
        }

        if (Prefs.isKidLockToApp(this)) {
            String kidApp = Prefs.getKidAppPackage(this);
            return packageName.equals(kidApp) && ScreenTimeTracker.canLaunchApp(this, packageName);
        }

        // Kid-home mode: allowlisted apps + chosen browser, with schedule / per-app limits.
        boolean allowlisted = Prefs.getAllowedPackages(this).contains(packageName);
        String browser = Prefs.getBrowserPackage(this);
        boolean isBrowser = browser != null && browser.equals(packageName);
        if (!allowlisted && !isBrowser) {
            return false;
        }
        return ScreenTimeTracker.canLaunchApp(this, packageName);
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
        if (event == null) {
            return false;
        }

        // Emergency escape: hold Back ~3s while kid mode is on (independent of hardware Home detection).
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && Prefs.isKidModeEnabled(this)
                && Prefs.isEscapeBackHoldEnabled(this)) {
            return handleBackEscape(event);
        }

        if (!Prefs.hardwareDetection(this) || !EnforcementController.shouldHijackHome(this)) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_HOME:
                if (KidModeGate.isEscapeUiActive()) {
                    return true;
                }
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

    /**
     * Detect a ~3s Back hold without breaking short Back presses.
     * @return true only after the escape fires (consumes the long-press); false otherwise.
     */
    private boolean handleBackEscape(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                backDownAtMs = System.currentTimeMillis();
                backEscapeFired = false;
                handler.removeCallbacks(backHoldRunnable);
                handler.postDelayed(backHoldRunnable, BACK_HOLD_ESCAPE_MS);
            }
            // Do not consume DOWN so short presses still work for the foreground app.
            return backEscapeFired;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(backHoldRunnable);
            long held = System.currentTimeMillis() - backDownAtMs;
            if (backEscapeFired || held >= BACK_HOLD_ESCAPE_MS) {
                if (!backEscapeFired) {
                    backEscapeFired = true;
                    EscapeLauncher.openPin(this);
                }
                return true;
            }
            backDownAtMs = 0L;
            return false;
        }
        return false;
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
                if (KidModeGate.isEscapeUiActive()) {
                    return;
                }
                // In launcher / device-owner modes the system already routes Home to KidHome.
                if (!EnforcementController.shouldHijackHome(AccServ.this)) {
                    return;
                }
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
                if (KidModeGate.isEscapeUiActive()) {
                    return;
                }
                if (!Prefs.isKidModeEnabled(AccServ.this)) {
                    return;
                }
                // Recents: always bounce back when hijacking or when extra a11y blocking is on.
                if (!EnforcementController.shouldHijackHome(AccServ.this)
                        && !EnforcementController.shouldBlockNonAllowedApps(AccServ.this)) {
                    return;
                }
                handler.postDelayed(() -> {
                    if (!KidModeGate.isEscapeUiActive()) {
                        HomePress.perform(AccServ.this);
                    }
                }, 80);
            }
        });
        homeWatcher.startWatch();

        ServiceMan.start(getApplicationContext());
        EscapeOverlayNotifier.requestRefresh(getApplicationContext());
        StatusNotifier.ensureChannel(this);

        Log.i(TAG, "KidsHome accessibility service connected on " + android.os.Build.MODEL
                + " kidMode=" + Prefs.isKidModeEnabled(this)
                + " mode=" + EnforcementController.getEffectiveMode(this));

        // Re-read prefs after connect (kid-mode disable uses commit()) before forcing a launch.
        handler.post(() -> {
            if (KidModeGate.isEscapeUiActive()) {
                return;
            }
            if (!EnforcementController.shouldHijackHome(getApplicationContext())) {
                return;
            }
            if (Prefs.hasChosenLauncher(getApplicationContext()) || Prefs.isKidModeEnabled(getApplicationContext())) {
                HomePress.perform(getApplicationContext(), true);
            }
        });
        handler.postDelayed(() -> {
            if (KidModeGate.isEscapeUiActive()
                    || !EnforcementController.shouldHijackHome(getApplicationContext())) {
                return;
            }
            if (Prefs.hasChosenLauncher(getApplicationContext()) || Prefs.isKidModeEnabled(getApplicationContext())) {
                HomePress.warmTarget(getApplicationContext());
            }
        }, 1500);
    }

    @Override
    public void onDestroy() {
        serviceConnected = false;
        handler.removeCallbacks(backHoldRunnable);
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
