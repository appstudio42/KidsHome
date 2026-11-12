package io.github.marcelsavegnago.kidshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Restarts helper services after boot and resets the chosen launcher if it was uninstalled.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "KidsHome.Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                onBootOrUpdate(context);
                break;

            case Intent.ACTION_PACKAGE_FULLY_REMOVED:
            case Intent.ACTION_PACKAGE_REMOVED:
                onPackageRemoved(context, intent);
                break;

            default:
                break;
        }
    }

    private void onBootOrUpdate(Context context) {
        Log.i(TAG, "Boot/update received; starting services");
        ServiceMan.start(context);
        StatusNotifier.ensureChannel(context);

        if (!AccessibilityHelper.isAccessibilityEnabled(context)) {
            StatusNotifier.notifyAccessibilityDisabled(context);
        }

        // Warm the chosen launcher a few seconds after boot to reduce the first-Home cold start delay.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (Prefs.hasChosenLauncher(context) || Prefs.isKidModeEnabled(context)) {
                HomePress.perform(context, true);
            }
        }, 2500);
    }

    private void onPackageRemoved(Context context, Intent intent) {
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        String removed = data.getSchemeSpecificPart();
        if (removed == null) {
            return;
        }

        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (replacing) {
            return;
        }

        String chosen = Prefs.getChosenLauncherPackage(context);
        if (removed.equals(chosen)) {
            Log.w(TAG, "Chosen launcher uninstalled; falling back to KidsHome");
            Prefs.clearChosenLauncher(context);
        }

        String kidApp = Prefs.getKidAppPackage(context);
        if (removed.equals(kidApp)) {
            Log.w(TAG, "Kid app uninstalled; disabling app-lock, keeping launcher lock if kid mode on");
            Prefs.setKidLockToApp(context, false);
        }
    }
}
