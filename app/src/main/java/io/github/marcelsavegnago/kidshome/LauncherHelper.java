package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Helpers for registering / detecting KidsHome as the default HOME launcher.
 */
public final class LauncherHelper {
    private static final String TAG = "KidsHome.Launcher";
    public static final int REQUEST_HOME_ROLE = 4101;

    private LauncherHelper() {
    }

    public static boolean isDefaultHome(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) {
            return false;
        }
        return context.getPackageName().equals(info.activityInfo.packageName);
    }

    public static String getCurrentHomePackage(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) {
            return "";
        }
        return info.activityInfo.packageName;
    }

    /**
     * Opens the system UI to choose the default home app.
     * On Android 10+ prefers {@link RoleManager#ROLE_HOME}.
     */
    public static void openDefaultHomePicker(Activity activity) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager roleManager = activity.getSystemService(RoleManager.class);
                if (roleManager != null
                        && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                        && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Intent roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                    activity.startActivityForResult(roleIntent, REQUEST_HOME_ROLE);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "RoleManager HOME request failed", e);
            }
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            return;
        } catch (Exception e) {
            Log.w(TAG, "ACTION_HOME_SETTINGS failed", e);
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addCategory(Intent.CATEGORY_DEFAULT);
            activity.startActivity(Intent.createChooser(home, activity.getString(R.string.setup_pick_home)));
        } catch (Exception e) {
            Log.e(TAG, "Unable to open home picker", e);
        }
    }
}
