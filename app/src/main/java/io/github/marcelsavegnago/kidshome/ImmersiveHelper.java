package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Hides system bars on kid-facing screens and keeps controls out of the status bar,
 * navigation bar, display cutout, and IME.
 *
 * <p>Target SDK 35 enforces edge-to-edge and ignores
 * {@code Window.setDecorFitsSystemWindows(true)} and status-bar colors. Insets are applied
 * with AndroidX so the same padding path runs on API 22–34 when a screen is immersive
 * (Fire OS draws under the bars once the legacy fullscreen flags are set). Non-immersive
 * screens keep the platform's fitted windows below API 35.
 */
public final class ImmersiveHelper {
    private static final int SAFE_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime();

    private ImmersiveHelper() {
    }

    /**
     * Tells {@link #onActivityCreated(Activity)} that this window applies its own insets.
     * Call before {@code onCreate} returns when the real content root is installed later
     * (PIN gate), so the content frame is not padded as well.
     */
    public static void markEdgeToEdgeHandled(@NonNull Activity activity) {
        activity.getWindow().getDecorView().setTag(R.id.kh_immersive_window, Boolean.TRUE);
    }

    public static boolean handlesOwnInsets(@NonNull Activity activity) {
        return Boolean.TRUE.equals(
                activity.getWindow().getDecorView().getTag(R.id.kh_immersive_window));
    }

    public static void enter(@NonNull Activity activity) {
        markEdgeToEdgeHandled(activity);
        Window window = activity.getWindow();
        // Ignored at targetSdk 35 (edge-to-edge is already enforced). Required on older
        // APIs so LAYOUT_FULLSCREEN / hide() actually draw behind the bars.
        if (Build.VERSION.SDK_INT < 35) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }
        applyCutoutMode(window);
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
        // Legacy fallback for devices without WindowInsetsController (Fire OS 5–7).
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
        ViewCompat.requestApplyInsets(decor);
    }

    public static void exit(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT < 35) {
            WindowCompat.setDecorFitsSystemWindows(window, true);
        }
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
     * Pads {@code content} for the visible status/nav bars, display cutout, and IME.
     * Hidden immersive bars report zero insets, so the screen stays full-bleed until a
     * swipe reveals them. Safe to call again: the original padding is stored once.
     */
    public static void applyContentInsets(@NonNull View content) {
        final int[] base = basePadding(content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(SAFE_INSET_TYPES);
            v.setPaddingRelative(
                    base[0] + bars.left,
                    base[1] + bars.top,
                    base[2] + bars.right,
                    base[3] + bars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
        requestApply(content);
    }

    /**
     * Non-immersive activities on API 35. Older releases still fit system windows,
     * so adding padding there would double the inset on Fire OS.
     */
    public static void onActivityCreated(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < 35 || handlesOwnInsets(activity)) {
            return;
        }
        Window window = activity.getWindow();
        applyCutoutMode(window);
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            applyContentInsets(content);
        }
        applyTransparentBarContrast(activity);
    }

    private static void applyCutoutMode(@NonNull Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        WindowManager.LayoutParams attrs = window.getAttributes();
        // API 35 treats SHORT_EDGES / NEVER / DEFAULT as ALWAYS for non-floating windows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else {
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(attrs);
    }

    private static void applyTransparentBarContrast(@NonNull Activity activity) {
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller == null) {
            return;
        }
        TypedValue value = new TypedValue();
        int background = Color.WHITE;
        if (activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                background = value.data;
            } else if (value.resourceId != 0) {
                background = ContextCompat.getColor(activity, value.resourceId);
            }
        }
        boolean lightBackground = ColorUtils.calculateLuminance(background) > 0.5;
        controller.setAppearanceLightStatusBars(lightBackground);
        controller.setAppearanceLightNavigationBars(lightBackground);
    }

    private static int[] basePadding(@NonNull View content) {
        Object tag = content.getTag(R.id.kh_inset_padding_base);
        if (tag instanceof int[] && ((int[]) tag).length == 4) {
            return (int[]) tag;
        }
        int[] base = new int[] {
                content.getPaddingStart(),
                content.getPaddingTop(),
                content.getPaddingEnd(),
                content.getPaddingBottom()
        };
        content.setTag(R.id.kh_inset_padding_base, base);
        return base;
    }

    private static void requestApply(@NonNull View content) {
        if (content.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(content);
            return;
        }
        if (Boolean.TRUE.equals(content.getTag(R.id.kh_insets_attach_hook))) {
            return;
        }
        content.setTag(R.id.kh_insets_attach_hook, Boolean.TRUE);
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                v.removeOnAttachStateChangeListener(this);
                ViewCompat.requestApplyInsets(v);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        });
    }
}
