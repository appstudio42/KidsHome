package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * Whether the device has a network that claims to reach the internet.
 * A cached PWA can still paint when this is true and the server is down;
 * the offline panel is only for a failed main frame while this is false.
 */
final class SiteConnectivity {
    private SiteConnectivity() {
    }

    static boolean isOffline(Context context) {
        if (context == null) {
            return true;
        }
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Offline23.isOffline(manager);
        }
        return offlineLegacy(manager);
    }

    @SuppressWarnings("deprecation")
    private static boolean offlineLegacy(ConnectivityManager manager) {
        NetworkInfo info = manager.getActiveNetworkInfo();
        return info == null || !info.isConnected();
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static final class Offline23 {
        private Offline23() {
        }

        static boolean isOffline(ConnectivityManager manager) {
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return true;
            }
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            return caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
    }
}
