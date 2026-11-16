package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

/**
 * Keeps status / navigation bars and Material toolbars aligned with the active
 * Material theme, including night mode.
 *
 * <p>Icon appearance follows the color actually painted behind each bar.
 * Below API 23 (status) and API 27 (navigation) the platform can only draw
 * light icons, so a light bar is replaced with {@code system_bar_dark}.
 * From API 35 the platform ignores bar colors; both icon sets follow the
 * window background that shows through the transparent bars.
 */
public final class ThemeHelper {
    private ThemeHelper() {
    }

    public static void applySystemBars(@NonNull Activity activity) {
        Window window = activity.getWindow();
        // colorPrimary lives on AppCompat. Material 1.13 no longer re-exports it.
        int primary = MaterialColors.getColor(
                activity, androidx.appcompat.R.attr.colorPrimary, Color.BLACK);
        int onPrimary = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
        int surface = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorSurface, Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            int background = windowBackground(activity, surface);
            applyIconContrast(window, background, background);
        } else {
            applyOpaqueBarContrast(window, primary, surface);
        }
        tintToolbars(activity.findViewById(android.R.id.content), primary, onPrimary);
    }

    /**
     * Legacy immersive mode replaces {@code systemUiVisibility} in one shot, which
     * clears {@code SYSTEM_UI_FLAG_LIGHT_*} set by the theme. Fold the contrast
     * bits into {@code immersiveFlags} so a visible Fire OS navigation bar keeps
     * dark icons on a light surface. No-op for the appearance bits at API 35,
     * where {@link #applySystemBars} already set the insets controller.
     */
    static int legacyImmersiveFlags(@NonNull Window window, int immersiveFlags) {
        int flags = immersiveFlags;
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return flags;
        }
        int status = contrastedStatusColor(window, window.getStatusBarColor());
        int nav = contrastedNavigationColor(window, window.getNavigationBarColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLight(status)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && isLight(nav)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        return flags;
    }

    private static void applyOpaqueBarContrast(@NonNull Window window, int statusColor, int navColor) {
        int status = contrastedStatusColor(window, statusColor);
        int nav = contrastedNavigationColor(window, navColor);
        window.setStatusBarColor(status);
        window.setNavigationBarColor(nav);
        applyIconContrast(window, status, nav);
    }

    private static int contrastedStatusColor(@NonNull Window window, int statusColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && isLight(statusColor)) {
            return darkBar(window);
        }
        return statusColor;
    }

    private static int contrastedNavigationColor(@NonNull Window window, int navColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && isLight(navColor)) {
            return darkBar(window);
        }
        return navColor;
    }

    private static void applyIconContrast(@NonNull Window window, int statusColor, int navColor) {
        boolean lightStatus = isLight(statusColor);
        boolean lightNav = isLight(navColor);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                controller.setAppearanceLightStatusBars(lightStatus);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                controller.setAppearanceLightNavigationBars(lightNav);
            }
            return;
        }
        int flags = window.getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (lightStatus) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (lightNav) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    static boolean isLight(int color) {
        return ColorUtils.calculateLuminance(color) > 0.5;
    }

    private static int darkBar(@NonNull Window window) {
        return ContextCompat.getColor(window.getContext(), R.color.system_bar_dark);
    }

    /**
     * Color showing through transparent system bars. Falls back to {@code surface}
     * when the theme background is not a concrete color.
     */
    private static int windowBackground(@NonNull Activity activity, int surface) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return surface;
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(activity, value.resourceId);
        }
        return surface;
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
