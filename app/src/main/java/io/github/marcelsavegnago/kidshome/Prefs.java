package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Central preference store for KidsHome.
 */
public final class Prefs {
    public static final String PREFS_NAME = "KidsHome";

    public static final String KEY_CHOSEN_LAUNCHER = "ChosenLauncher";
    public static final String KEY_CHOSEN_LAUNCHER_NAME = "ChosenLauncherName";
    public static final String KEY_HARDWARE_DETECTION = "HardwareDetection";
    public static final String KEY_APPLICATION_OPEN_DETECTION = "ApplicationOpenDetection";
    public static final String KEY_BROADCAST_DETECTION = "BroadcastRecieverDetection";
    public static final String KEY_OVERLAY_DETECTION = "OverlayApplicationDetection";
    public static final String KEY_MENU_BUTTON_OVERRIDE = "MenuButtonOverride";
    public static final String KEY_RECENT_APP_OVERRIDE = "RecentAppOverride";
    public static final String KEY_DEFAULTS_LOADED = "defaultsLoaded";

    public static final String KEY_KID_MODE = "KidModeEnabled";
    public static final String KEY_KID_PIN_HASH = "KidPinHash";
    public static final String KEY_KID_PIN_SALT = "KidPinSalt";
    public static final String KEY_KID_LOCK_TO_APP = "KidLockToApp";
    public static final String KEY_KID_APP_PACKAGE = "KidAppPackage";
    public static final String KEY_KID_APP_ACTIVITY = "KidAppActivity";

    /** Lock target: use the chosen home launcher. */
    public static final int LOCK_TARGET_LAUNCHER = 0;
    /** Lock target: use a single chosen app. */
    public static final int LOCK_TARGET_APP = 1;

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isDefaultsLoaded(Context context) {
        return get(context).getBoolean(KEY_DEFAULTS_LOADED, false);
    }

    public static void loadDetectionDefaults(Context context) {
        boolean tv = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean androidNougatMr1Plus = Build.VERSION.SDK_INT >= 25;

        SharedPreferences.Editor editor = get(context).edit();
        editor.putBoolean(KEY_HARDWARE_DETECTION, tv);
        editor.putBoolean(KEY_MENU_BUTTON_OVERRIDE, tv);
        editor.putBoolean(KEY_OVERLAY_DETECTION, !androidNougatMr1Plus);
        editor.putBoolean(KEY_RECENT_APP_OVERRIDE, androidNougatMr1Plus && !tv);
        editor.putBoolean(KEY_BROADCAST_DETECTION, true);
        editor.putBoolean(KEY_APPLICATION_OPEN_DETECTION, !tv);
        editor.putBoolean(KEY_DEFAULTS_LOADED, true);
        editor.apply();
    }

    public static String getChosenLauncherPackage(Context context) {
        return get(context).getString(KEY_CHOSEN_LAUNCHER, context.getPackageName());
    }

    public static String getChosenLauncherActivity(Context context) {
        return get(context).getString(KEY_CHOSEN_LAUNCHER_NAME, MainActivity.class.getName());
    }

    public static void setChosenLauncher(Context context, String packageName, String activityName) {
        get(context).edit()
                .putString(KEY_CHOSEN_LAUNCHER, packageName)
                .putString(KEY_CHOSEN_LAUNCHER_NAME, activityName)
                .apply();
    }

    public static void clearChosenLauncher(Context context) {
        setChosenLauncher(context, context.getPackageName(), MainActivity.class.getName());
    }

    public static boolean hasChosenLauncher(Context context) {
        String pkg = getChosenLauncherPackage(context);
        return pkg != null && !pkg.equals(context.getPackageName());
    }

    public static boolean isKidModeEnabled(Context context) {
        return get(context).getBoolean(KEY_KID_MODE, false);
    }

    public static void setKidModeEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_KID_MODE, enabled).apply();
    }

    public static boolean hasKidPin(Context context) {
        String hash = get(context).getString(KEY_KID_PIN_HASH, null);
        return hash != null && !hash.isEmpty();
    }

    public static void setKidPin(Context context, String pin) {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash(pin, salt);
        get(context).edit()
                .putString(KEY_KID_PIN_SALT, salt)
                .putString(KEY_KID_PIN_HASH, hash)
                .apply();
    }

    public static boolean verifyKidPin(Context context, String pin) {
        SharedPreferences prefs = get(context);
        String salt = prefs.getString(KEY_KID_PIN_SALT, "");
        String expected = prefs.getString(KEY_KID_PIN_HASH, "");
        if (salt.isEmpty() || expected.isEmpty()) {
            return false;
        }
        return PinHasher.verify(pin, salt, expected);
    }

    public static void clearKidPin(Context context) {
        get(context).edit()
                .remove(KEY_KID_PIN_HASH)
                .remove(KEY_KID_PIN_SALT)
                .apply();
    }

    public static boolean isKidLockToApp(Context context) {
        return get(context).getBoolean(KEY_KID_LOCK_TO_APP, false);
    }

    public static void setKidLockToApp(Context context, boolean lockToApp) {
        get(context).edit().putBoolean(KEY_KID_LOCK_TO_APP, lockToApp).apply();
    }

    public static void setKidApp(Context context, String packageName, String activityName) {
        get(context).edit()
                .putString(KEY_KID_APP_PACKAGE, packageName)
                .putString(KEY_KID_APP_ACTIVITY, activityName)
                .apply();
    }

    public static String getKidAppPackage(Context context) {
        return get(context).getString(KEY_KID_APP_PACKAGE, "");
    }

    public static String getKidAppActivity(Context context) {
        return get(context).getString(KEY_KID_APP_ACTIVITY, "");
    }

    public static boolean hardwareDetection(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_HARDWARE_DETECTION, false);
    }

    public static boolean applicationOpenDetection(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_APPLICATION_OPEN_DETECTION, true);
    }

    public static boolean broadcastDetection(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_BROADCAST_DETECTION, true);
    }

    public static boolean overlayDetection(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_OVERLAY_DETECTION, false);
    }

    public static boolean menuButtonOverride(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_MENU_BUTTON_OVERRIDE, false);
    }

    public static boolean recentAppOverride(Context context) {
        ensureDefaults(context);
        return get(context).getBoolean(KEY_RECENT_APP_OVERRIDE, false);
    }

    public static void saveDetectionSettings(
            Context context,
            boolean hardware,
            boolean applicationOpen,
            boolean broadcast,
            boolean overlay,
            boolean menuOverride,
            boolean recentOverride
    ) {
        get(context).edit()
                .putBoolean(KEY_HARDWARE_DETECTION, hardware)
                .putBoolean(KEY_APPLICATION_OPEN_DETECTION, applicationOpen)
                .putBoolean(KEY_BROADCAST_DETECTION, broadcast)
                .putBoolean(KEY_OVERLAY_DETECTION, overlay)
                .putBoolean(KEY_MENU_BUTTON_OVERRIDE, menuOverride)
                .putBoolean(KEY_RECENT_APP_OVERRIDE, recentOverride)
                .putBoolean(KEY_DEFAULTS_LOADED, true)
                .apply();
    }

    private static void ensureDefaults(Context context) {
        if (!isDefaultsLoaded(context)) {
            loadDetectionDefaults(context);
        }
    }
}
