package io.github.marcelsavegnago.kidshome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class ServiceMan {
    private static final String TAG = "KidsHome.ServiceMan";
    private static Intent serviceIntent;

    private ServiceMan() {
    }

    private static boolean isRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean start(Context context) {
        Context app = context.getApplicationContext();
        serviceIntent = new Intent(app, HomeButtonService.class);
        if (isRunning(HomeButtonService.class, app)) {
            return false;
        }
        try {
            app.startService(serviceIntent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to start HomeButtonService", e);
            return false;
        }
    }

    public static void startDelayed(Context context, long delayMs) {
        final Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> start(app), delayMs);
    }

    public static void stop(Context context) {
        if (serviceIntent == null) {
            serviceIntent = new Intent(context.getApplicationContext(), HomeButtonService.class);
        }
        try {
            context.getApplicationContext().stopService(serviceIntent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to stop HomeButtonService", e);
        }
    }
}
