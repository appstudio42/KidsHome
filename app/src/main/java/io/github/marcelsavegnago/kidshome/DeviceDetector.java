package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Runtime device / OS detection for picking the best enforcement mode.
 */
public final class DeviceDetector {
    private DeviceDetector() {
    }

    public static boolean isFireOs(Context context) {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        String brand = Build.BRAND != null ? Build.BRAND : "";
        String model = Build.MODEL != null ? Build.MODEL : "";
        if (manufacturer.equalsIgnoreCase("Amazon") || brand.equalsIgnoreCase("Amazon")) {
            return true;
        }
        String modelLower = model.toLowerCase();
        if (modelLower.contains("fire") || modelLower.contains("kf")) {
            return true;
        }
        PackageManager pm = context.getPackageManager();
        return isPackagePresent(pm, "com.amazon.firelauncher")
                || isPackagePresent(pm, "com.amazon.launcher")
                || isPackagePresent(pm, "com.amazon.device.software.ota");
    }

    public static boolean isDeviceOwner(Context context) {
        return DeviceOwnerHelper.isDeviceOwner(context);
    }

    public static String describeDevice() {
        return Build.MANUFACTURER + " " + Build.MODEL
                + " (SDK " + Build.VERSION.SDK_INT + ")";
    }

    private static boolean isPackagePresent(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
