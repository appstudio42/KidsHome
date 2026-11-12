package io.github.marcelsavegnago.kidshome;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Lightweight motion helpers that respect system reduce-motion / animator scale.
 */
public final class MotionHelper {
    private MotionHelper() {
    }

    public static boolean animationsEnabled(Context context) {
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            );
            return scale > 0.01f;
        } catch (Exception e) {
            return true;
        }
    }

    public static void fadeInUp(View view, Context context) {
        if (view == null) {
            return;
        }
        if (!animationsEnabled(context)) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        Animation anim = AnimationUtils.loadAnimation(context, R.anim.fade_in_up);
        view.startAnimation(anim);
    }

    public static void popIn(View view, Context context, long delayMs) {
        if (view == null) {
            return;
        }
        if (!animationsEnabled(context)) {
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setAlpha(1f);
            return;
        }
        view.setScaleX(0.86f);
        view.setScaleY(0.86f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(Math.max(0L, delayMs))
                .setDuration(220L)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    public static void bouncePress(View view, Context context) {
        if (view == null || !animationsEnabled(context)) {
            return;
        }
        view.animate().cancel();
        view.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(80L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .setInterpolator(new OvershootInterpolator())
                        .start())
                .start();
    }

    public static void shake(View view, Context context) {
        if (view == null) {
            return;
        }
        if (!animationsEnabled(context)) {
            return;
        }
        Animation shake = AnimationUtils.loadAnimation(context, R.anim.shake);
        view.startAnimation(shake);
    }

    public static Animator progressAnimator(View target, String property, float to) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(target, property, to);
        anim.setDuration(280L);
        anim.setInterpolator(new DecelerateInterpolator());
        return anim;
    }
}
