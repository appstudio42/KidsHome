package io.github.marcelsavegnago.kidshome;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Sticky helper: overlay home detection, a11y watchdog, escape corner, screen-time ticks.
 */
public class HomeButtonService extends Service {
    private static final String TAG = "KidsHome.HomeButton";
    private static final long WATCHDOG_INTERVAL_MS = 15_000L;

    private LinearLayout layout;
    private WindowManager windowManager;
    private EscapeOverlay escapeOverlay;
    private BroadcastReceiver screenReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            checkAccessibility();
            if (escapeOverlay != null) {
                escapeOverlay.refresh();
            }
            ScreenTimeTracker.tick(HomeButtonService.this);
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StatusNotifier.ensureChannel(this);
        escapeOverlay = new EscapeOverlay(this);

        if (Prefs.overlayDetection(this)) {
            createOverlayWatcher();
        }

        escapeOverlay.refresh();
        registerScreenReceiver();
        handler.post(watchdog);
        ScreenTimeTracker.tick(this);
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Prefs.flushScreenTime(context);
                    Prefs.setLastScreenTickMs(context, 0L);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                    ScreenTimeTracker.tick(context);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(screenReceiver, filter);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to register screen receiver", e);
        }
    }

    private void createOverlayWatcher() {
        layout = new LinearLayout(getApplicationContext()) {
            public void onCloseSystemDialogs(String reason) {
                if (reason != null && reason.contains("homekey") && !KidModeGate.isEscapeUiActive()) {
                    HomePress.perform(getApplicationContext());
                }
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                return false;
            }
        };
        layout.setFocusable(false);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                0,
                0,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        try {
            windowManager.addView(layout, params);
        } catch (Exception e) {
            Log.w(TAG, "Overlay permission missing; overlay detection disabled for this session", e);
            layout = null;
        }
    }

    private void checkAccessibility() {
        if (!EnforcementController.needsAccessibilityService(this)) {
            StatusNotifier.cancelAccessibilityDisabled(this);
            return;
        }
        boolean enabled = AccessibilityHelper.isAccessibilityEnabled(this);
        if (!enabled) {
            StatusNotifier.notifyAccessibilityDisabled(this);
        } else if (AccServ.isRunning()) {
            StatusNotifier.cancelAccessibilityDisabled(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && EscapeOverlayNotifier.ACTION_REFRESH_ESCAPE_OVERLAY.equals(intent.getAction())) {
            if (escapeOverlay != null) {
                escapeOverlay.refresh();
            }
        } else if (escapeOverlay != null) {
            escapeOverlay.refresh();
        }
        ScreenTimeTracker.tick(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(watchdog);
        Prefs.flushScreenTime(this);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
        }
        if (escapeOverlay != null) {
            escapeOverlay.hide();
            escapeOverlay = null;
        }
        if (windowManager != null && layout != null) {
            try {
                windowManager.removeView(layout);
            } catch (Exception ignored) {
            }
        }
        ServiceMan.startDelayed(getApplicationContext(), 500);
        super.onDestroy();
    }
}
