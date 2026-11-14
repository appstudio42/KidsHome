package io.github.marcelsavegnago.kidshome;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

/**
 * Keeps a kid lock screen from being closed by Back.
 *
 * <p>Predictive back does not call {@code Activity.onBackPressed()} once the
 * screen opts in with {@code android:enableOnBackInvokedCallback}, and that
 * path is the default for apps targeting Android 16 (API 36). An enabled
 * {@link OnBackPressedCallback} runs on both that path and the legacy key
 * path, and only while {@code owner} is at least started.
 */
public final class LockBack {
    private LockBack() {
    }

    /**
     * Consume Back and leave the activity in front.
     */
    @NonNull
    public static OnBackPressedCallback consume(
            @NonNull LifecycleOwner owner,
            @NonNull OnBackPressedDispatcher dispatcher) {
        return intercept(owner, dispatcher, null);
    }

    /**
     * Handle Back with {@code onBack} while this screen is the lock.
     * A null action consumes the event. Disable the returned callback when
     * the screen is no longer locking the child out.
     */
    @NonNull
    public static OnBackPressedCallback intercept(
            @NonNull LifecycleOwner owner,
            @NonNull OnBackPressedDispatcher dispatcher,
            @Nullable Runnable onBack) {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (onBack != null) {
                    onBack.run();
                }
            }
        };
        dispatcher.addCallback(owner, callback);
        return callback;
    }
}
