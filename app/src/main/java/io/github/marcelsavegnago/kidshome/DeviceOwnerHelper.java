package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Device Owner helpers: lock-task (kiosk), persistent HOME, status bar / keyguard.
 */
public final class DeviceOwnerHelper {
    private static final String TAG = "KidsHome.DeviceOwner";

    private DeviceOwnerHelper() {
    }

    public static ComponentName getAdminComponent(Context context) {
        return new ComponentName(context.getApplicationContext(), KidsHomeDeviceAdminReceiver.class);
    }

    public static String getDeviceOwnerAdbCommand(Context context) {
        ComponentName admin = getAdminComponent(context);
        return "adb shell dpm set-device-owner " + admin.flattenToShortString();
    }

    public static DevicePolicyManager dpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager manager = dpm(context);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager manager = dpm(context);
        return manager != null && manager.isAdminActive(getAdminComponent(context));
    }

    /**
     * Whitelist KidsHome + allowed apps + browser for lock task, set preferred HOME,
     * and optionally disable status bar / keyguard.
     */
    public static void applyKidModePolicies(Context context) {
        if (!isDeviceOwner(context)) {
            Log.w(TAG, "applyKidModePolicies skipped — not device owner");
            return;
        }
        DevicePolicyManager manager = dpm(context);
        ComponentName admin = getAdminComponent(context);
        if (manager == null) {
            return;
        }

        String[] packages = buildLockTaskPackages(context);
        try {
            manager.setLockTaskPackages(admin, packages);
            Log.i(TAG, "Lock task packages set (" + packages.length + ")");
        } catch (Exception e) {
            Log.e(TAG, "setLockTaskPackages failed", e);
        }

        // Block Home / Overview / notifications / system info on API 28+ (Fire OS 7 = 28).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                Log.i(TAG, "Lock task features = NONE");
            } catch (Exception e) {
                Log.w(TAG, "setLockTaskFeatures failed", e);
            }
        }

        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_HOME);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            ComponentName home = new ComponentName(context, KidHomeActivity.class);
            manager.addPersistentPreferredActivity(admin, filter, home);
            Log.i(TAG, "Persistent preferred HOME set to KidHomeActivity");
        } catch (Exception e) {
            Log.e(TAG, "addPersistentPreferredActivity failed", e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (Prefs.isLockTaskDisableStatusBar(context)) {
                    manager.setStatusBarDisabled(admin, true);
                } else {
                    manager.setStatusBarDisabled(admin, false);
                }
            } catch (Exception e) {
                Log.w(TAG, "setStatusBarDisabled failed", e);
            }
            try {
                if (Prefs.isLockTaskDisableKeyguard(context)) {
                    manager.setKeyguardDisabled(admin, true);
                } else {
                    manager.setKeyguardDisabled(admin, false);
                }
            } catch (Exception e) {
                Log.w(TAG, "setKeyguardDisabled failed", e);
            }
        }
    }

    public static void clearKidModePolicies(Context context) {
        if (!isDeviceOwner(context)) {
            return;
        }
        DevicePolicyManager manager = dpm(context);
        ComponentName admin = getAdminComponent(context);
        if (manager == null) {
            return;
        }
        try {
            manager.setLockTaskPackages(admin, new String[]{context.getPackageName()});
        } catch (Exception e) {
            Log.w(TAG, "clear lock task packages failed", e);
        }
        try {
            manager.clearPackagePersistentPreferredActivities(admin, context.getPackageName());
        } catch (Exception e) {
            Log.w(TAG, "clearPackagePersistentPreferredActivities failed", e);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                manager.setStatusBarDisabled(admin, false);
            } catch (Exception ignored) {
            }
            try {
                manager.setKeyguardDisabled(admin, false);
            } catch (Exception ignored) {
            }
        }
    }

    public static String[] buildLockTaskPackages(Context context) {
        Set<String> packages = new LinkedHashSet<>();
        packages.add(context.getPackageName());
        if (Prefs.isKidLockToApp(context)) {
            String kid = Prefs.getKidAppPackage(context);
            if (kid != null && !kid.isEmpty()) {
                packages.add(kid);
            }
        } else {
            packages.addAll(Prefs.getAllowedPackages(context));
        }
        String browser = Prefs.getBrowserPackage(context);
        if (browser != null && !browser.isEmpty()) {
            packages.add(browser);
        }
        List<String> list = new ArrayList<>(packages);
        return list.toArray(new String[0]);
    }

    public static void refreshLockTaskPackages(Context context) {
        if (!isDeviceOwner(context)) {
            return;
        }
        DevicePolicyManager manager = dpm(context);
        if (manager == null) {
            return;
        }
        try {
            manager.setLockTaskPackages(getAdminComponent(context), buildLockTaskPackages(context));
        } catch (Exception e) {
            Log.w(TAG, "refreshLockTaskPackages failed", e);
        }
    }

    public static void startLockTaskIfNeeded(Activity activity) {
        if (activity == null || !Prefs.isKidModeEnabled(activity)) {
            return;
        }
        if (EnforcementController.getEffectiveMode(activity) != EnforcementMode.DEVICE_OWNER) {
            return;
        }
        if (!isDeviceOwner(activity)) {
            return;
        }
        applyKidModePolicies(activity);
        try {
            if (isInLockTask(activity)) {
                return;
            }
            activity.startLockTask();
            Log.i(TAG, "startLockTask OK");
        } catch (Exception e) {
            Log.w(TAG, "startLockTask failed", e);
        }
    }

    public static boolean isInLockTask(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int state = am.getLockTaskModeState();
            return state != ActivityManager.LOCK_TASK_MODE_NONE;
        }
        // noinspection deprecation
        return am.isInLockTaskMode();
    }

    public static void stopLockTaskQuietly(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            activity.stopLockTask();
        } catch (Exception e) {
            Log.w(TAG, "stopLockTask failed", e);
        }
    }

    /**
     * Remove device owner so the app can be uninstalled.
     * Must be called while still device owner (from parent settings after PIN).
     */
    public static boolean clearDeviceOwner(Context context) {
        if (!isDeviceOwner(context)) {
            return false;
        }
        DevicePolicyManager manager = dpm(context);
        if (manager == null) {
            return false;
        }
        try {
            clearKidModePolicies(context);
            // Deprecated but still the supported self-clear path for device-owner apps.
            manager.clearDeviceOwnerApp(context.getPackageName());
            Log.i(TAG, "Device owner cleared");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "clearDeviceOwnerApp failed", e);
            return false;
        }
    }

}
