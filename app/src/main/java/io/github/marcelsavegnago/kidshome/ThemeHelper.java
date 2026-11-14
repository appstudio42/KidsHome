package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

/**
 * Keeps status / navigation bars and Material toolbars aligned with the active
 * Material theme — including Dynamic Color overlays that otherwise leave a
 * static seed status bar clashing with a remapped purple app bar.
 */
public final class ThemeHelper {
    private ThemeHelper() {
    }

    public static void applySystemBars(@NonNull Activity activity) {
        Window window = activity.getWindow();
        int primary = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorPrimary, Color.BLACK);
        int onPrimary = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
        int surface = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorSurface, Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Bar colors are ignored. Icons must contrast with the window background
            // that shows through the transparent bars (see ImmersiveHelper).
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                boolean lightBackground = ColorUtils.calculateLuminance(surface) > 0.5;
                controller.setAppearanceLightStatusBars(lightBackground);
                controller.setAppearanceLightNavigationBars(lightBackground);
            }
        } else {
            window.setStatusBarColor(primary);
            window.setNavigationBarColor(surface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                boolean lightPrimary = ColorUtils.calculateLuminance(primary) > 0.5;
                int flags = window.getDecorView().getSystemUiVisibility();
                if (lightPrimary) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }
        tintToolbars(activity.findViewById(android.R.id.content), primary, onPrimary);
    }

    private static void tintToolbars(View root, int primary, int onPrimary) {
        if (root == null) {
            return;
        }
        if (root instanceof MaterialToolbar) {
            MaterialToolbar toolbar = (MaterialToolbar) root;
            toolbar.setBackgroundColor(primary);
            toolbar.setTitleTextColor(onPrimary);
            toolbar.setSubtitleTextColor(onPrimary);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setTint(onPrimary);
            }
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintToolbars(group.getChildAt(i), primary, onPrimary);
            }
        }
    }
}
