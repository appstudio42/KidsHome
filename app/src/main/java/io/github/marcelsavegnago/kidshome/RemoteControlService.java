package io.github.marcelsavegnago.kidshome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Foreground service that keeps the LAN remote HTTP server alive while enabled.
 */
public class RemoteControlService extends Service {
    private static final String TAG = "KidsHome.RemoteSvc";
    public static final String CHANNEL_ID = "kidshome_remote";
    private static final int NOTIF_ID = 3101;

    public static final String ACTION_REFRESH = "io.github.marcelsavegnago.kidshome.REMOTE_REFRESH";

    private static RemoteControlServer server;
    private static final RemoteAuth AUTH = new RemoteAuth();
    private static final Object LOCK = new Object();

    private BroadcastReceiver networkReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ipWatchdog = new Runnable() {
        @Override
        public void run() {
            maybeRestartForIpChange();
            handler.postDelayed(this, 30_000L);
        }
    };
    private String lastKnownIp = "";

    public static void start(Context context) {
        if (!Prefs.isRemoteControlEnabled(context)) {
            stop(context);
            return;
        }
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, RemoteControlService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to start RemoteControlService", e);
        }
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        try {
            app.stopService(new Intent(app, RemoteControlService.class));
        } catch (Exception e) {
            Log.w(TAG, "Unable to stop RemoteControlService", e);
        }
        stopServer();
    }

    public static void refreshAuthToken(Context context) {
        synchronized (LOCK) {
            AUTH.setExpectedToken(Prefs.getOrCreateRemoteToken(context));
            AUTH.revokeAllSessions();
        }
    }

    public static boolean isServerRunning() {
        synchronized (LOCK) {
            return server != null && server.isAlive();
        }
    }

    public static String currentBaseUrl(Context context) {
        return RemoteNetwork.buildBaseUrl(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        startForeground(NOTIF_ID, buildNotification());
        startOrRestartServer();
        registerNetworkReceiver();
        lastKnownIp = nullToEmpty(RemoteNetwork.getLanIpv4(this));
        handler.postDelayed(ipWatchdog, 30_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.isRemoteControlEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            refreshAuthToken(this);
        }
        startOrRestartServer();
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ipWatchdog);
        unregisterNetworkReceiver();
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startOrRestartServer() {
        synchronized (LOCK) {
            AUTH.setExpectedToken(Prefs.getOrCreateRemoteToken(this));
            int port = Prefs.getRemoteControlPort(this);
            try {
                if (server != null) {
                    if (server.getListeningPort() == port && server.isAlive()) {
                        return;
                    }
                    server.stop();
                    server = null;
                }
                RemoteControlBridge bridge = new RemoteControlBridge(this);
                RemoteRequestHandler handler = new RemoteRequestHandler(AUTH, bridge);
                server = new RemoteControlServer(port, AUTH, handler);
                server.start(NanoHTTPD_SOCKET_READ_TIMEOUT, false);
                Log.i(TAG, "Remote server listening on port " + port);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start remote server", e);
                server = null;
            }
        }
        updateNotification();
    }

    private static final int NanoHTTPD_SOCKET_READ_TIMEOUT = 5_000;

    private static void stopServer() {
        synchronized (LOCK) {
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception ignored) {
                }
                server = null;
            }
            AUTH.revokeAllSessions();
        }
    }

    private void maybeRestartForIpChange() {
        String ip = nullToEmpty(RemoteNetwork.getLanIpv4(this));
        if (!ip.equals(lastKnownIp)) {
            Log.i(TAG, "LAN IP changed: " + lastKnownIp + " -> " + ip);
            lastKnownIp = ip;
            // Server stays bound to all interfaces; refresh notification URL only.
            updateNotification();
        }
        if (!isServerRunning() && Prefs.isRemoteControlEnabled(this)) {
            startOrRestartServer();
        }
    }

    private void registerNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                maybeRestartForIpChange();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(networkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(networkReceiver, filter);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to register network receiver", e);
        }
    }

    private void unregisterNetworkReceiver() {
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
            } catch (Exception ignored) {
            }
            networkReceiver = null;
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.remote_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.remote_notification_channel_desc));
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, RemoteControlActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        String url = RemoteNetwork.buildBaseUrl(this);
        String text = url != null
                ? getString(R.string.remote_notification_text, url)
                : getString(R.string.remote_notification_no_wifi);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.remote_notification_title))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
