package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PackageUtils {
    private PackageUtils() {
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isActivityAvailable(Context context, String packageName, String activityName) {
        if (!isPackageInstalled(context, packageName)) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(packageName, activityName);
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    public static List<ResolveInfo> getInstalledApplications(Context context, boolean launchersOnly, boolean hideSystemApps) {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        if (launchersOnly) {
            main.addCategory(Intent.CATEGORY_HOME);
        }
        List<ResolveInfo> launchables = pm.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(pm));

        if (!hideSystemApps) {
            return launchables;
        }

        List<ResolveInfo> results = new ArrayList<>();
        for (ResolveInfo info : launchables) {
            if ((info.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                results.add(info);
            }
        }
        return results;
    }

    public static List<ResolveInfo> getLaunchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        return apps;
    }
}
