package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

/**
 * Facade over mode detection and mode-specific enable/disable actions.
 */
public final class EnforcementController {
    private static final String TAG = "KidsHome.Enforce";

    private EnforcementController() {
    }

    public static EnforcementMode getPreferredMode(Context context) {
        return Prefs.getEnforcementModePreference(context);
    }

    public static EnforcementMode getEffectiveMode(Context context) {
        EnforcementMode preference = getPreferredMode(context);
        boolean owner = DeviceDetector.isDeviceOwner(context);
        boolean fire = DeviceDetector.isFireOs(context);
        EnforcementMode resolved = ModeResolver.resolve(preference, owner, fire);
        // If parent forced DEVICE_OWNER but we are not owner yet, fall back for runtime
        // so the app still works via launcher/accessibility until provisioned.
        if (preference == EnforcementMode.DEVICE_OWNER && !owner) {
            if (fire) {
                return EnforcementMode.ACCESSIBILITY;
            }
            return EnforcementMode.LAUNCHER;
        }
        if (preference == EnforcementMode.LAUNCHER && fire) {
            // Manual override honored even on Fire (parent choice).
            return EnforcementMode.LAUNCHER;
        }
        if (preference == EnforcementMode.ACCESSIBILITY) {
            return EnforcementMode.ACCESSIBILITY;
        }
        return resolved;
    }

    public static boolean shouldHijackHome(Context context) {
        return ModeResolver.shouldHijackHome(getEffectiveMode(context));
    }

    public static boolean shouldBlockNonAllowedApps(Context context) {
        return ModeResolver.shouldBlockNonAllowed(
                getEffectiveMode(context),
                Prefs.isAccessibilityExtraBlocking(context)
        );
    }

    public static boolean needsAccessibilityService(Context context) {
        EnforcementMode mode = getEffectiveMode(context);
        if (mode == EnforcementMode.ACCESSIBILITY) {
            return true;
        }
        return Prefs.isAccessibilityExtraBlocking(context)
                && Prefs.isKidModeEnabled(context);
    }

    /** Apply policies when kid mode is turned on or settings change. */
    public static void onKidModeEnabled(Context context) {
        EnforcementMode mode = getEffectiveMode(context);
        Log.i(TAG, "Kid mode enabled; effectiveMode=" + mode
                + " device=" + DeviceDetector.describeDevice());
        if (mode == EnforcementMode.DEVICE_OWNER) {
            DeviceOwnerHelper.applyKidModePolicies(context);
        }
        // Keep lock-task package list fresh if owner even when preference is AUTO.
        if (DeviceOwnerHelper.isDeviceOwner(context)) {
            DeviceOwnerHelper.refreshLockTaskPackages(context);
        }
    }

    /** Tear down mode-specific locks when kid mode is turned off. */
    public static void onKidModeDisabled(Activity activity) {
        Context context = activity != null ? activity : null;
        if (context == null) {
            return;
        }
        Log.i(TAG, "Kid mode disabled; stopping lock task if needed");
        DeviceOwnerHelper.stopLockTaskQuietly(activity);
        if (DeviceOwnerHelper.isDeviceOwner(context)) {
            DeviceOwnerHelper.clearKidModePolicies(context);
        }
    }

    public static void onKidModeDisabled(Context context) {
        if (context instanceof Activity) {
            onKidModeDisabled((Activity) context);
            return;
        }
        if (DeviceOwnerHelper.isDeviceOwner(context)) {
            DeviceOwnerHelper.clearKidModePolicies(context);
        }
    }
}
