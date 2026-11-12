package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public static final String KEY_ESCAPE_CORNER = "EscapeCornerEnabled";
    public static final String KEY_ESCAPE_BACK_HOLD = "EscapeBackHoldEnabled";

    public static final String KEY_CHILD_NAME = "ChildName";
    public static final String KEY_CHILD_AVATAR = "ChildAvatar";
    public static final String KEY_ALLOWED_PACKAGES = "AllowedPackages";
    public static final String KEY_SITE_SHORTCUTS = "SiteShortcutsJson";
    public static final String KEY_BROWSER_PACKAGE = "BrowserPackage";
    public static final String KEY_BROWSER_ACTIVITY = "BrowserActivity";
    public static final String KEY_ONBOARDING_DONE = "OnboardingDone";
    public static final String KEY_TOUCH_SOUNDS = "KidTouchSounds";
    public static final String KEY_PREFS_VERSION = "PrefsVersion";
    /** Schema version for forward-compatible migrations. Existing installs stay at v0 until migrate. */
    public static final int CURRENT_PREFS_VERSION = 1;

    public static final String KEY_SCREEN_TIME_ENABLED = "ScreenTimeEnabled";
    public static final String KEY_DAILY_LIMIT_MINUTES = "DailyLimitMinutes";
    public static final String KEY_BEDTIME_ENABLED = "BedtimeEnabled";
    public static final String KEY_BEDTIME_START = "BedtimeStartMinutes";
    public static final String KEY_BEDTIME_END = "BedtimeEndMinutes";
    public static final String KEY_USED_MILLIS = "UsedMillisToday";
    public static final String KEY_BONUS_MILLIS = "BonusMillisToday";
    public static final String KEY_USAGE_DAY = "UsageDayKey";
    public static final String KEY_LAST_TICK = "LastScreenTickMs";
    public static final String KEY_WARN_SHOWN_DAY = "ScreenTimeWarnDay";
    public static final String KEY_ST_DISABLED_DAY = "ScreenTimeDisabledDay";
    public static final String KEY_BT_DISABLED_DAY = "BedtimeDisabledDay";

    public static final String KEY_ENFORCEMENT_MODE = "EnforcementMode";
    public static final String KEY_A11Y_EXTRA_BLOCKING = "AccessibilityExtraBlocking";
    public static final String KEY_LOCK_TASK_DISABLE_STATUS_BAR = "LockTaskDisableStatusBar";
    public static final String KEY_LOCK_TASK_DISABLE_KEYGUARD = "LockTaskDisableKeyguard";

    private Prefs() {
    }

    public static EnforcementMode getEnforcementModePreference(Context context) {
        return EnforcementMode.fromPrefsValue(get(context).getString(KEY_ENFORCEMENT_MODE, "auto"));
    }

    public static void setEnforcementModePreference(Context context, EnforcementMode mode) {
        EnforcementMode value = mode == null ? EnforcementMode.AUTO : mode;
        get(context).edit().putString(KEY_ENFORCEMENT_MODE, value.toPrefsValue()).commit();
    }

    /**
     * When true, accessibility still blocks non-allowed apps in Launcher / Device Owner modes.
     * Always implied for Accessibility (Fire) mode.
     */
    public static boolean isAccessibilityExtraBlocking(Context context) {
        return get(context).getBoolean(KEY_A11Y_EXTRA_BLOCKING, true);
    }

    public static void setAccessibilityExtraBlocking(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_A11Y_EXTRA_BLOCKING, enabled).commit();
    }

    public static boolean isLockTaskDisableStatusBar(Context context) {
        return get(context).getBoolean(KEY_LOCK_TASK_DISABLE_STATUS_BAR, false);
    }

    public static void setLockTaskDisableStatusBar(Context context, boolean disable) {
        get(context).edit().putBoolean(KEY_LOCK_TASK_DISABLE_STATUS_BAR, disable).commit();
    }

    public static boolean isLockTaskDisableKeyguard(Context context) {
        return get(context).getBoolean(KEY_LOCK_TASK_DISABLE_KEYGUARD, false);
    }

    public static void setLockTaskDisableKeyguard(Context context, boolean disable) {
        get(context).edit().putBoolean(KEY_LOCK_TASK_DISABLE_KEYGUARD, disable).commit();
    }

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Migrates prefs forward without wiping PIN / settings. Safe to call on every launch.
     */
    public static void migrateIfNeeded(Context context) {
        SharedPreferences prefs = get(context);
        int version = prefs.getInt(KEY_PREFS_VERSION, 0);
        if (version >= CURRENT_PREFS_VERSION) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        // v1: ensure avatar + onboarding defaults exist; keep all prior keys untouched.
        if (!prefs.contains(KEY_CHILD_AVATAR)) {
            editor.putString(KEY_CHILD_AVATAR, ChildAvatars.DEFAULT);
        }
        if (!prefs.contains(KEY_ONBOARDING_DONE)) {
            // Existing installs that already have a PIN or kid mode skip the wizard.
            boolean skip = prefs.getBoolean(KEY_KID_MODE, false) || hasKidPin(context);
            editor.putBoolean(KEY_ONBOARDING_DONE, skip);
        }
        if (!prefs.contains(KEY_TOUCH_SOUNDS)) {
            editor.putBoolean(KEY_TOUCH_SOUNDS, false);
        }
        editor.putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION);
        editor.commit();
    }

    public static boolean isOnboardingDone(Context context) {
        migrateIfNeeded(context);
        return get(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingDone(Context context, boolean done) {
        get(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).commit();
    }

    public static boolean isTouchSoundsEnabled(Context context) {
        return get(context).getBoolean(KEY_TOUCH_SOUNDS, false);
    }

    public static void setTouchSoundsEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_TOUCH_SOUNDS, enabled).commit();
    }

    public static String getChildAvatar(Context context) {
        return ChildAvatars.sanitize(get(context).getString(KEY_CHILD_AVATAR, ChildAvatars.DEFAULT));
    }

    public static void setChildAvatar(Context context, String avatar) {
        get(context).edit().putString(KEY_CHILD_AVATAR, ChildAvatars.sanitize(avatar)).commit();
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
        get(context).edit().putBoolean(KEY_KID_MODE, enabled).commit();
    }

    public static boolean isEscapeCornerEnabled(Context context) {
        return get(context).getBoolean(KEY_ESCAPE_CORNER, true);
    }

    public static boolean isEscapeBackHoldEnabled(Context context) {
        return get(context).getBoolean(KEY_ESCAPE_BACK_HOLD, true);
    }

    public static void setEscapeOptions(Context context, boolean corner, boolean backHold) {
        get(context).edit()
                .putBoolean(KEY_ESCAPE_CORNER, corner)
                .putBoolean(KEY_ESCAPE_BACK_HOLD, backHold)
                .commit();
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
                .commit();
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

    public static boolean isKidLockToApp(Context context) {
        return get(context).getBoolean(KEY_KID_LOCK_TO_APP, false);
    }

    public static void setKidLockToApp(Context context, boolean lockToApp) {
        get(context).edit().putBoolean(KEY_KID_LOCK_TO_APP, lockToApp).commit();
    }

    public static void setKidApp(Context context, String packageName, String activityName) {
        get(context).edit()
                .putString(KEY_KID_APP_PACKAGE, packageName)
                .putString(KEY_KID_APP_ACTIVITY, activityName)
                .commit();
    }

    public static String getKidAppPackage(Context context) {
        return get(context).getString(KEY_KID_APP_PACKAGE, "");
    }

    public static String getKidAppActivity(Context context) {
        return get(context).getString(KEY_KID_APP_ACTIVITY, "");
    }

    public static String getChildName(Context context) {
        return get(context).getString(KEY_CHILD_NAME, "");
    }

    public static void setChildName(Context context, String name) {
        get(context).edit().putString(KEY_CHILD_NAME, name == null ? "" : name.trim()).commit();
    }

    public static void setChildProfile(Context context, String name, String avatar) {
        get(context).edit()
                .putString(KEY_CHILD_NAME, name == null ? "" : name.trim())
                .putString(KEY_CHILD_AVATAR, ChildAvatars.sanitize(avatar))
                .commit();
    }

    public static Set<String> getAllowedPackages(Context context) {
        Set<String> stored = get(context).getStringSet(KEY_ALLOWED_PACKAGES, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public static void setAllowedPackages(Context context, Set<String> packages) {
        get(context).edit().putStringSet(KEY_ALLOWED_PACKAGES, new HashSet<>(packages)).commit();
    }

    public static List<SiteShortcut> getSiteShortcuts(Context context) {
        return SiteShortcut.fromJson(get(context).getString(KEY_SITE_SHORTCUTS, ""));
    }

    public static void setSiteShortcuts(Context context, List<SiteShortcut> shortcuts) {
        get(context).edit().putString(KEY_SITE_SHORTCUTS, SiteShortcut.toJson(shortcuts)).commit();
    }

    public static void ensureDefaultSiteSuggestions(Context context) {
        if (!getSiteShortcuts(context).isEmpty()) {
            return;
        }
        // Only seed once when empty so parents can delete them.
        if (get(context).getBoolean("siteSuggestionsSeeded", false)) {
            return;
        }
        setSiteShortcuts(context, SiteShortcut.defaultSuggestions());
        get(context).edit().putBoolean("siteSuggestionsSeeded", true).commit();
    }

    public static String getBrowserPackage(Context context) {
        return get(context).getString(KEY_BROWSER_PACKAGE, "");
    }

    public static String getBrowserActivity(Context context) {
        return get(context).getString(KEY_BROWSER_ACTIVITY, "");
    }

    public static void setBrowser(Context context, String packageName, String activityName) {
        get(context).edit()
                .putString(KEY_BROWSER_PACKAGE, packageName == null ? "" : packageName)
                .putString(KEY_BROWSER_ACTIVITY, activityName == null ? "" : activityName)
                .commit();
        if (packageName != null && !packageName.isEmpty()) {
            Set<String> allowed = getAllowedPackages(context);
            allowed.add(packageName);
            setAllowedPackages(context, allowed);
        }
        DeviceOwnerHelper.refreshLockTaskPackages(context);
    }

    public static boolean isScreenTimeEnabled(Context context) {
        return get(context).getBoolean(KEY_SCREEN_TIME_ENABLED, false);
    }

    public static void setScreenTimeEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_SCREEN_TIME_ENABLED, enabled).commit();
    }

    public static int getDailyLimitMinutes(Context context) {
        return get(context).getInt(KEY_DAILY_LIMIT_MINUTES, 60);
    }

    public static void setDailyLimitMinutes(Context context, int minutes) {
        get(context).edit().putInt(KEY_DAILY_LIMIT_MINUTES, Math.max(1, minutes)).commit();
    }

    public static boolean isBedtimeEnabled(Context context) {
        return get(context).getBoolean(KEY_BEDTIME_ENABLED, false);
    }

    public static void setBedtimeEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_BEDTIME_ENABLED, enabled).commit();
    }

    public static int getBedtimeStartMinutes(Context context) {
        return get(context).getInt(KEY_BEDTIME_START, 20 * 60);
    }

    public static int getBedtimeEndMinutes(Context context) {
        return get(context).getInt(KEY_BEDTIME_END, 7 * 60);
    }

    public static void setBedtimeWindow(Context context, int startMinutes, int endMinutes) {
        get(context).edit()
                .putInt(KEY_BEDTIME_START, startMinutes)
                .putInt(KEY_BEDTIME_END, endMinutes)
                .commit();
    }

    public static void ensureScreenTimeDay(Context context) {
        String today = ScreenTimeState.dayKeyNow();
        String stored = get(context).getString(KEY_USAGE_DAY, "");
        if (today.equals(stored)) {
            return;
        }
        get(context).edit()
                .putString(KEY_USAGE_DAY, today)
                .putLong(KEY_USED_MILLIS, 0L)
                .putLong(KEY_BONUS_MILLIS, 0L)
                .putString(KEY_WARN_SHOWN_DAY, "")
                .putString(KEY_ST_DISABLED_DAY, "")
                .putString(KEY_BT_DISABLED_DAY, "")
                .putLong(KEY_LAST_TICK, 0L)
                .commit();
    }

    public static long getUsedMillisToday(Context context) {
        ensureScreenTimeDay(context);
        return get(context).getLong(KEY_USED_MILLIS, 0L);
    }

    public static long getBonusMillisToday(Context context) {
        ensureScreenTimeDay(context);
        return get(context).getLong(KEY_BONUS_MILLIS, 0L);
    }

    public static void addUsedMillisToday(Context context, long delta) {
        ensureScreenTimeDay(context);
        long used = get(context).getLong(KEY_USED_MILLIS, 0L) + Math.max(0L, delta);
        get(context).edit().putLong(KEY_USED_MILLIS, used).apply();
    }

    public static void addBonusMinutes(Context context, int minutes) {
        ensureScreenTimeDay(context);
        long bonus = get(context).getLong(KEY_BONUS_MILLIS, 0L) + minutes * 60L * 1000L;
        get(context).edit().putLong(KEY_BONUS_MILLIS, bonus).commit();
    }

    public static long getLastScreenTickMs(Context context) {
        return get(context).getLong(KEY_LAST_TICK, 0L);
    }

    public static void setLastScreenTickMs(Context context, long ms) {
        get(context).edit().putLong(KEY_LAST_TICK, ms).apply();
    }

    public static void flushScreenTime(Context context) {
        // Force a sync write of the latest usage counters.
        SharedPreferences prefs = get(context);
        prefs.edit()
                .putLong(KEY_USED_MILLIS, prefs.getLong(KEY_USED_MILLIS, 0L))
                .putLong(KEY_LAST_TICK, prefs.getLong(KEY_LAST_TICK, 0L))
                .putString(KEY_USAGE_DAY, prefs.getString(KEY_USAGE_DAY, ScreenTimeState.dayKeyNow()))
                .commit();
    }

    public static boolean wasScreenTimeWarningShownToday(Context context) {
        ensureScreenTimeDay(context);
        return ScreenTimeState.dayKeyNow().equals(get(context).getString(KEY_WARN_SHOWN_DAY, ""));
    }

    public static void setScreenTimeWarningShownToday(Context context, boolean shown) {
        get(context).edit()
                .putString(KEY_WARN_SHOWN_DAY, shown ? ScreenTimeState.dayKeyNow() : "")
                .commit();
    }

    public static boolean isScreenTimeDisabledToday(Context context) {
        ensureScreenTimeDay(context);
        return ScreenTimeState.dayKeyNow().equals(get(context).getString(KEY_ST_DISABLED_DAY, ""));
    }

    public static void setScreenTimeDisabledToday(Context context, boolean disabled) {
        get(context).edit()
                .putString(KEY_ST_DISABLED_DAY, disabled ? ScreenTimeState.dayKeyNow() : "")
                .commit();
    }

    public static boolean isBedtimeDisabledToday(Context context) {
        ensureScreenTimeDay(context);
        return ScreenTimeState.dayKeyNow().equals(get(context).getString(KEY_BT_DISABLED_DAY, ""));
    }

    public static void setBedtimeDisabledToday(Context context, boolean disabled) {
        get(context).edit()
                .putString(KEY_BT_DISABLED_DAY, disabled ? ScreenTimeState.dayKeyNow() : "")
                .commit();
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
