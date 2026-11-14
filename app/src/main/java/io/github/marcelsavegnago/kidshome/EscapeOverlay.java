package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Tiny transparent corner hotspot: 5 quick taps open the emergency PIN UI.
 */
public final class EscapeOverlay {
    private static final String TAG = "KidsHome.EscapeOverlay";
    private static final int TAP_TARGET = 5;
    private static final long TAP_WINDOW_MS = 2500L;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View hotspot;
    private int tapCount;
    private long windowStartMs;

    public EscapeOverlay(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isShowing() {
        return hotspot != null;
    }

    public void refresh() {
        boolean need = Prefs.isKidModeEnabled(appContext) && Prefs.isEscapeCornerEnabled(appContext);
        if (need) {
            show();
        } else {
            hide();
        }
    }

    public void show() {
        if (hotspot != null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; corner escape unavailable (Back-hold still works)");
            return;
        }

        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
        int size = Math.max((int) (56 * metrics.density), 56);

        hotspot = new View(appContext);
        hotspot.setBackgroundColor(Color.TRANSPARENT);
        hotspot.setContentDescription("KidsHome escape");
        hotspot.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                onTap();
            }
            return true;
        });

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 0;
        // Keep the 5-tap target below the status bar and cutout. On API 35 the
        // window is edge-to-edge, so a y of a few dp sits under the clock or camera.
        params.y = topSafeOffset(metrics);

        try {
            windowManager.addView(hotspot, params);
            Log.i(TAG, "Escape corner overlay shown");
        } catch (Exception e) {
            Log.w(TAG, "Unable to add escape overlay", e);
            hotspot = null;
        }
    }

    public void hide() {
        if (hotspot == null) {
            return;
        }
        try {
            if (windowManager != null) {
                windowManager.removeView(hotspot);
            }
        } catch (Exception ignored) {
        }
        hotspot = null;
        tapCount = 0;
        windowStartMs = 0L;
    }

    private int topSafeOffset(DisplayMetrics metrics) {
        int margin = (int) (8 * metrics.density);
        int status = 0;
        int resId = appContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            status = appContext.getResources().getDimensionPixelSize(resId);
        }
        return status + margin;
    }

    private void onTap() {
        long now = System.currentTimeMillis();
        if (windowStartMs == 0L || now - windowStartMs > TAP_WINDOW_MS) {
            windowStartMs = now;
            tapCount = 1;
        } else {
            tapCount++;
        }
        handler.removeCallbacksAndMessages(null);
        if (tapCount >= TAP_TARGET) {
            tapCount = 0;
            windowStartMs = 0L;
            EscapeLauncher.openPin(appContext);
            return;
        }
        // Reset the window if the user stops tapping.
        handler.postDelayed(() -> {
            tapCount = 0;
            windowStartMs = 0L;
        }, TAP_WINDOW_MS);
    }
}
