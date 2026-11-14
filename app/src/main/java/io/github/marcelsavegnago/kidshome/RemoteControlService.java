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
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;

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
    private String lastKnownBind = "";

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
        promoteToForeground();
        lastKnownBind = RemoteNetwork.bindHost(this);
        startOrRestartServer();
        registerNetworkReceiver();
        handler.postDelayed(ipWatchdog, 30_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.isRemoteControlEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        promoteToForeground();
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
            String bindHost = RemoteNetwork.bindHost(this);
            try {
                if (server == null
                        || !server.isAlive()
                        || server.getListeningPort() != port
                        || !bindHost.equals(server.getHostname())) {
                    stopListeningLocked();
                    RemoteRequestHandler requestHandler =
                            new RemoteRequestHandler(AUTH, new RemoteControlBridge(this));
                    server = listen(bindHost, port, requestHandler);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start remote server", e);
                stopListeningLocked();
            }
        }
        updateNotification();
    }

    /**
     * Binds to the current private Wi-Fi/Ethernet address. If that address cannot
     * be claimed, falls back to loopback so the port is not opened on every NIC.
     */
    private RemoteControlServer listen(String bindHost, int port, RemoteRequestHandler requestHandler)
            throws IOException {
        RemoteControlServer created = new RemoteControlServer(bindHost, port, AUTH, requestHandler);
        try {
            created.start(NanoHTTPD_SOCKET_READ_TIMEOUT, false);
            Log.i(TAG, "Remote server listening on " + created.getHostname() + ":" + port);
            return created;
        } catch (IOException primary) {
            try {
                created.stop();
            } catch (Exception ignored) {
            }
            if (RemoteNetwork.LOOPBACK_BIND_HOST.equals(created.getHostname())) {
                throw primary;
            }
            RemoteControlServer fallback = new RemoteControlServer(
                    RemoteNetwork.LOOPBACK_BIND_HOST, port, AUTH, requestHandler);
            try {
                fallback.start(NanoHTTPD_SOCKET_READ_TIMEOUT, false);
            } catch (IOException fallbackFailure) {
                try {
                    fallback.stop();
                } catch (Exception ignored) {
                }
                primary.addSuppressed(fallbackFailure);
                throw primary;
            }
            Log.w(TAG, "LAN bind failed; listening on loopback only", primary);
            return fallback;
        }
    }

    private static void stopListeningLocked() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
            server = null;
        }
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
        String desired = RemoteNetwork.bindHost(this);
        if (!desired.equals(lastKnownBind)) {
            Log.i(TAG, "Bind address changed: " + lastKnownBind + " -> " + desired);
            lastKnownBind = desired;
            startOrRestartServer();
            return;
        }
        String actual = boundHost();
        if ((actual.isEmpty() || !desired.equals(actual)) && Prefs.isRemoteControlEnabled(this)) {
            startOrRestartServer();
        }
    }

    private static String boundHost() {
        synchronized (LOCK) {
            if (server == null || server.getHostname() == null) {
                return "";
            }
            return server.getHostname();
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
        PendingIntent pi = BackgroundActivityStarter.activityPendingIntent(
                this, BackgroundActivityStarter.REQUEST_NOTIFICATION + 2, open);

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

    /**
     * {@code specialUse} is not in the API 35 {@code BOOT_COMPLETED} block list
     * ({@code dataSync}, camera, media, phone, projection, microphone), so boot may
     * still start this service. The {@code SYSTEM_ALERT_WINDOW} exemption now also
     * requires a visible overlay; this service is started from the parent UI or from
     * boot, not from the zero-size home watcher.
     */
    private void promoteToForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

}
