package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        registerActivityLifecycleCallbacks(new EdgeToEdgeCallbacks());
    }

    /**
     * Applies safe-area padding on API 35 for screens that are not immersive.
     * {@code onActivityCreated} runs from {@code Activity.onCreate} before the
     * subclass has called {@code setContentView} or {@link ImmersiveHelper#enter}.
     * {@code onStart} runs after that, so lock screens have already opted out.
     */
    private static final class EdgeToEdgeCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            ImmersiveHelper.onActivityCreated(activity);
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
