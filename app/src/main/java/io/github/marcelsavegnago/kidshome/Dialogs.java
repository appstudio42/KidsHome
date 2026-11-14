package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * Builds {@link android.app.AlertDialog}s without migrating them to AndroidX.
 *
 * <p>At targetSdk 35 a floating dialog is edge-to-edge and
 * {@code Window.setDecorFitsSystemWindows} has no effect. Padding the dialog with the
 * full-screen system-bar and IME insets shrinks the PIN fields and can push the
 * buttons off the card. {@code android:windowOptOutEdgeToEdgeEnforcement} on the dialog
 * theme is the supported way to keep the platform fitting the window above the bars
 * and resizing it for the keyboard. API 22–34 keep the activity dialog theme, so Fire OS
 * dialogs do not change.
 */
public final class Dialogs {
    private Dialogs() {
    }

    @NonNull
    public static AlertDialog.Builder builder(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return new AlertDialog.Builder(context, R.style.Theme_KidsHome_Dialog);
        }
        return new AlertDialog.Builder(context);
    }
}
