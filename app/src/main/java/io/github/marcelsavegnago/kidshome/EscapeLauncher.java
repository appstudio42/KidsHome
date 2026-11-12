package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Opens the emergency PIN screen above the locked app.
 */
public final class EscapeLauncher {
    private static final String TAG = "KidsHome.Escape";
    private static long lastLaunchMs;

    private EscapeLauncher() {
    }

    public static void openPin(Context context) {
        if (!Prefs.isKidModeEnabled(context)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLaunchMs < 800L && KidModeGate.isEscapeUiActive()) {
            return;
        }
        lastLaunchMs = now;

        // Mark before starting so AccServ does not immediately pull back to the kid app.
        KidModeGate.markEscapeUiOpen();
        Intent intent = new Intent(context.getApplicationContext(), EscapePinActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        try {
            context.getApplicationContext().startActivity(intent);
            Log.i(TAG, "Emergency PIN UI launched");
        } catch (Exception e) {
            KidModeGate.markEscapeUiClosed();
            Log.e(TAG, "Unable to launch emergency PIN UI", e);
        }
    }
}
