package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Hides system bars on kid-facing screens and applies safe content padding.
 * On Android 12L+ tablets the taskbar/dock is otherwise an easy escape route.
 */
public final class ImmersiveHelper {
    private ImmersiveHelper() {
    }

    public static void enter(@NonNull Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
        // Legacy fallback for devices without WindowInsetsController.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // noinspection deprecation
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    public static void exit(@NonNull Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, true);
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // noinspection deprecation
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    /**
     * Pads {@code content} for status/nav/display-cutout insets so controls stay tappable
     * when bars are transiently shown.
     */
    public static void applyContentInsets(@NonNull View content) {
        final int start = content.getPaddingStart();
        final int top = content.getPaddingTop();
        final int end = content.getPaddingEnd();
        final int bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPaddingRelative(
                    start + bars.left,
                    top + bars.top,
                    end + bars.right,
                    bottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
