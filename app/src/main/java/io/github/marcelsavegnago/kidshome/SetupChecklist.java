package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes which setup steps are still missing for the effective enforcement mode.
 */
public final class SetupChecklist {
    public enum StepId {
        ACCESSIBILITY,
        OVERLAY,
        BATTERY,
        DEFAULT_HOME,
        DEVICE_OWNER,
        NOTIFICATIONS
    }

    public static final class Step {
        public final StepId id;
        public final boolean required;
        public final boolean done;
        public final String title;
        public final String detail;

        public Step(StepId id, boolean required, boolean done, String title, String detail) {
            this.id = id;
            this.required = required;
            this.done = done;
            this.title = title;
            this.detail = detail;
        }
    }

    private SetupChecklist() {
    }

    public static List<Step> build(Context context) {
        List<Step> steps = new ArrayList<>();
        EnforcementMode preference = Prefs.getEnforcementModePreference(context);
        EnforcementMode effective = EnforcementController.getEffectiveMode(context);
        boolean fire = DeviceDetector.isFireOs(context);
        boolean owner = DeviceOwnerHelper.isDeviceOwner(context);

        boolean a11yRequired = effective == EnforcementMode.ACCESSIBILITY
                || (Prefs.isAccessibilityExtraBlocking(context) && Prefs.isKidModeEnabled(context))
                || preference == EnforcementMode.ACCESSIBILITY;
        boolean a11yDone = AccessibilityHelper.isAccessibilityEnabled(context);
        steps.add(new Step(
                StepId.ACCESSIBILITY,
                a11yRequired,
                a11yDone,
                context.getString(R.string.setup_step_a11y_title),
                a11yDone
                        ? context.getString(R.string.setup_step_done)
                        : context.getString(R.string.setup_step_a11y_detail)
        ));

        boolean overlayDone = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
        boolean overlayRequired = Prefs.isEscapeCornerEnabled(context) || Prefs.overlayDetection(context);
        steps.add(new Step(
                StepId.OVERLAY,
                overlayRequired,
                overlayDone,
                context.getString(R.string.setup_step_overlay_title),
                overlayDone
                        ? context.getString(R.string.setup_step_done)
                        : context.getString(R.string.setup_step_overlay_detail)
        ));

        boolean batteryDone = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context);
        steps.add(new Step(
                StepId.BATTERY,
                true,
                batteryDone,
                context.getString(R.string.setup_step_battery_title),
                batteryDone
                        ? context.getString(R.string.setup_step_done)
                        : context.getString(R.string.setup_step_battery_detail)
        ));

        boolean homeRequired = effective == EnforcementMode.LAUNCHER
                || preference == EnforcementMode.LAUNCHER
                || preference == EnforcementMode.DEVICE_OWNER
                || (preference == EnforcementMode.AUTO && !fire);
        // On Fire with accessibility mode, default Home is not required.
        if (effective == EnforcementMode.ACCESSIBILITY && preference != EnforcementMode.LAUNCHER
                && preference != EnforcementMode.DEVICE_OWNER) {
            homeRequired = false;
        }
        boolean homeDone = LauncherHelper.isDefaultHome(context) || owner;
        steps.add(new Step(
                StepId.DEFAULT_HOME,
                homeRequired,
                homeDone,
                context.getString(R.string.setup_step_home_title),
                homeDone
                        ? context.getString(R.string.setup_step_done)
                        : context.getString(R.string.setup_step_home_detail)
        ));

        boolean ownerRequired = preference == EnforcementMode.DEVICE_OWNER;
        steps.add(new Step(
                StepId.DEVICE_OWNER,
                ownerRequired,
                owner,
                context.getString(R.string.setup_step_owner_title),
                owner
                        ? context.getString(R.string.setup_step_owner_done)
                        : context.getString(R.string.setup_step_owner_detail,
                        DeviceOwnerHelper.getDeviceOwnerAdbCommand(context))
        ));

        if (Build.VERSION.SDK_INT >= 33) {
            boolean notifDone = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            steps.add(new Step(
                    StepId.NOTIFICATIONS,
                    false,
                    notifDone,
                    context.getString(R.string.setup_step_notif_title),
                    notifDone
                            ? context.getString(R.string.setup_step_done)
                            : context.getString(R.string.setup_step_notif_detail)
            ));
        }

        return steps;
    }

    public static int countMissingRequired(Context context) {
        int missing = 0;
        for (Step step : build(context)) {
            if (step.required && !step.done) {
                missing++;
            }
        }
        return missing;
    }
}
