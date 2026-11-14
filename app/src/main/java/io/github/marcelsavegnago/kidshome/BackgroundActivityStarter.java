package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Starts an activity while KidsHome is in the background (accessibility service,
 * boot receiver, or the home-button watcher).
 *
 * <p>From API 34 the sender of a {@link PendingIntent} must opt in with
 * {@link ActivityOptions#setPendingIntentBackgroundActivityStartMode(int)}. From
 * targetSdk 35 the creator blocks that privilege by default and must opt in with
 * {@link ActivityOptions#setPendingIntentCreatorBackgroundActivityStartMode(int)}
 * when the {@link PendingIntent} is created. Both calls are version-gated. A direct
 * {@code startActivity} remains the fallback for API 22–33 and for OEMs that reject
 * the pending intent. A silent background-activity block (logcat only, no exception)
 * still has to be confirmed on a device.
 */
public final class BackgroundActivityStarter {
    private static final String TAG = "KidsHome.BgStart";

    public static final int REQUEST_HOME = 4101;
    public static final int REQUEST_ESCAPE_PIN = 4102;
    public static final int REQUEST_SCREEN_LOCK = 4103;

    /** Notification content intents. Distinct from the home/lock relaunch codes. */
    public static final int REQUEST_NOTIFICATION = 4200;

    private BackgroundActivityStarter() {
    }

    /** Last failure from {@link #launch}, for tests. Cleared on success. */
    @Nullable
    @VisibleForTesting
    static Throwable lastError;

    /**
     * @return true when the system accepted the pending intent or the direct fallback
     */
    public static boolean launch(@NonNull Context context, @NonNull Intent intent, int requestCode) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            PendingIntent pendingIntent = activityPendingIntent(context, requestCode, intent);
            sendAllowingBackgroundStart(context, pendingIntent);
            lastError = null;
            return true;
        } catch (Exception pendingError) {
            Log.w(TAG, "PendingIntent launch failed; trying startActivity", pendingError);
            try {
                context.startActivity(intent);
                lastError = null;
                return true;
            } catch (Exception startError) {
                lastError = startError;
                startError.addSuppressed(pendingError);
                Log.e(TAG, "Unable to start activity from background", startError);
                return false;
            }
        }
    }

    /**
     * Activity pending intent that opts the creator into background starts on API 34+.
     * Notification taps are delivered by the system; the creator opt-in is what targetSdk 35 requires.
     */
    @NonNull
    public static PendingIntent activityPendingIntent(
            @NonNull Context context, int requestCode, @NonNull Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Bundle creatorOptions = creatorOptionsBundle();
        if (creatorOptions != null) {
            return PendingIntent.getActivity(context, requestCode, intent, flags, creatorOptions);
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    static void sendAllowingBackgroundStart(
            @NonNull Context context, @NonNull PendingIntent pendingIntent)
            throws PendingIntent.CanceledException {
        // The 7-arg send() is API 23. The options bundle itself is only created on API 34+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Bundle senderOptions = senderOptionsBundle();
            pendingIntent.send(context, 0, null, null, null, null, senderOptions);
        } else {
            pendingIntent.send();
        }
    }

    @Nullable
    @VisibleForTesting
    static Bundle creatorOptionsBundle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return options.toBundle();
    }

    @Nullable
    @VisibleForTesting
    static Bundle senderOptionsBundle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return options.toBundle();
    }
}
