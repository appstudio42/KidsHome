package io.github.marcelsavegnago.kidshome;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Device admin component used when KidsHome is provisioned as Device Owner.
 * Provision once via:
 * {@code adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver}
 */
public class KidsHomeDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "KidsHome.DeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin disabled");
    }
}
