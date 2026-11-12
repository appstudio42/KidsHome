package io.github.marcelsavegnago.kidshome;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * Applies Material You dynamic color on Android 12+ when the system supports it.
 * Older devices (including Fire OS 7) keep the cheerful static seed palette.
 */
public class KidsHomeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.migrateIfNeeded(this);
        try {
            DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable ignored) {
            // Dynamic color is best-effort; static theme remains.
        }
    }
}
