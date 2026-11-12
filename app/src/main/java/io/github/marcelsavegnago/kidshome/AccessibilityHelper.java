package io.github.marcelsavegnago.kidshome;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public final class AccessibilityHelper {
    private AccessibilityHelper() {
    }

    public static String getServiceFlatComponent(Context context) {
        ComponentName componentName = new ComponentName(context, AccServ.class);
        return componentName.flattenToString();
    }

    public static String getServiceId(Context context) {
        // AccessibilityManager reports ids like package/.AccServ
        return context.getPackageName() + "/." + AccServ.class.getSimpleName();
    }

    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        String expectedId = getServiceId(context);
        String expectedFlat = getServiceFlatComponent(context);
        List<AccessibilityServiceInfo> running = am.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
        for (AccessibilityServiceInfo service : running) {
            String id = service.getId();
            if (expectedId.equals(id) || expectedFlat.equals(id)) {
                return true;
            }
            // Some firmwares report the fully qualified class name.
            if (id != null && id.endsWith("/" + AccServ.class.getName())) {
                return true;
            }
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
