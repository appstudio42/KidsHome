package io.github.marcelsavegnago.kidshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

/**
 * Listens for ACTION_CLOSE_SYSTEM_DIALOGS (home / recents).
 * Based on Evan Gillogly's react-native-home-pressed approach.
 */
public class HomeWatcher {
    private static final String TAG = "KidsHome.HomeWatcher";

    private final Context context;
    private final IntentFilter filter;
    private OnHomePressedListener listener;
    private InnerReceiver receiver;
    private boolean registered;

    public HomeWatcher(Context context) {
        this.context = context.getApplicationContext();
        filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    }

    public void setOnHomePressedListener(OnHomePressedListener listener) {
        this.listener = listener;
        receiver = new InnerReceiver();
    }

    public void startWatch() {
        if (receiver == null || registered) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to register home watcher", e);
        }
    }

    public void stopWatch() {
        if (receiver == null || !registered) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.w(TAG, "Unable to unregister home watcher", e);
        }
        registered = false;
    }

    private class InnerReceiver extends BroadcastReceiver {
        private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
        private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (listener == null || intent == null || intent.getAction() == null) {
                return;
            }
            if (!Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                return;
            }
            String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
            if (reason == null) {
                return;
            }
            if (SYSTEM_DIALOG_REASON_HOME_KEY.equals(reason)) {
                listener.onHomePressed();
            } else if (SYSTEM_DIALOG_REASON_RECENT_APPS.equals(reason)) {
                listener.onRecentAppPressed();
            }
        }
    }
}
