package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;

/** Soft haptics for PIN errors / key presses. Never throws. */
public final class HapticHelper {
    private HapticHelper() {
    }

    public static void tick(View view) {
        if (view == null) {
            return;
        }
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {
        }
    }

    public static void error(Context context) {
        try {
            Vibrator vibrator = vibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(40);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager != null ? manager.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
