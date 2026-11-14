package io.github.marcelsavegnago.kidshome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Launches the configured home target as quickly as possible.
 */
public final class HomePress {
    private static final String TAG = "KidsHome.HomePress";
    private static final long DEBOUNCE_MS = 120L;
    private static long lastActivateMs = 0L;

    private HomePress() {
    }

    public static Intent getDesiredIntent(Context context) {
        Context app = context.getApplicationContext();

        if (Prefs.isKidModeEnabled(app) && ScreenTimeTracker.isBlocked(app)) {
            Intent lock = new Intent(app, ScreenTimeLockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            return lock;
        }

        String packageName;
        String activityName;

        if (Prefs.isKidModeEnabled(app)) {
            if (Prefs.isKidLockToApp(app)) {
                packageName = Prefs.getKidAppPackage(app);
                activityName = Prefs.getKidAppActivity(app);
                if (!PackageUtils.isActivityAvailable(app, packageName, activityName)) {
                    Log.w(TAG, "Kid app missing; falling back to kid home");
                    Prefs.setKidLockToApp(app, false);
                    packageName = app.getPackageName();
                    activityName = KidHomeActivity.class.getName();
                }
            } else {
                packageName = app.getPackageName();
                activityName = KidHomeActivity.class.getName();
            }
        } else {
            packageName = Prefs.getChosenLauncherPackage(app);
            activityName = Prefs.getChosenLauncherActivity(app);
        }

        if (!PackageUtils.isActivityAvailable(app, packageName, activityName)) {
            Log.w(TAG, "Chosen target missing (" + packageName + "); opening KidsHome");
            Prefs.clearChosenLauncher(app);
            if (Prefs.isKidModeEnabled(app) && Prefs.isKidLockToApp(app)) {
                Prefs.setKidLockToApp(app, false);
            }
            packageName = app.getPackageName();
            activityName = Prefs.isKidModeEnabled(app)
                    ? KidHomeActivity.class.getName()
                    : MainActivity.class.getName();
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(packageName, activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (!packageName.equals(app.getPackageName())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        return intent;
    }

    public static boolean perform(Context context) {
        return perform(context, false);
    }

    public static boolean perform(Context context, boolean force) {
        if (KidModeGate.isEscapeUiActive()) {
            Log.d(TAG, "Skipping HomePress while protected UI is active");
            return false;
        }
        if (Prefs.isKidModeEnabled(context) && ScreenTimeTracker.isBlocked(context)) {
            ScreenTimeLockActivity.launch(context);
            return true;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastActivateMs < DEBOUNCE_MS) {
            return false;
        }
        lastActivateMs = now;

        Intent intent = getDesiredIntent(context);
        return BackgroundActivityStarter.launch(context, intent, BackgroundActivityStarter.REQUEST_HOME);
    }

    public static void warmTarget(Context context) {
        if (KidModeGate.isEscapeUiActive()) {
            return;
        }
        Intent intent = getDesiredIntent(context);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!BackgroundActivityStarter.launch(context, intent, BackgroundActivityStarter.REQUEST_HOME)) {
            Log.w(TAG, "Warm launch failed");
        }
    }
}
