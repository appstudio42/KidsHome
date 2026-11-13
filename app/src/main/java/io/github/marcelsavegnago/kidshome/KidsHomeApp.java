package io.github.marcelsavegnago.kidshome;

import android.app.Application;

/**
 * Application entry. Uses the cheerful static teal/coral Material 3 seed palette.
 * Dynamic Color is intentionally not applied: on tablets it remaps the app bar to
 * wallpaper purple while the status bar often keeps the seed teal, causing a clash.
 * Fire OS devices also lack Dynamic Color, so the static seed stays consistent.
 */
public class KidsHomeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PinAttemptTracker.attach(this);
        Prefs.migrateIfNeeded(this);
    }
}
