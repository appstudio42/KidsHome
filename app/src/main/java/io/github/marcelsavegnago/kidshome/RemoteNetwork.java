package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Resolves the tablet's current LAN IPv4 for the parent remote URL.
 */
public final class RemoteNetwork {
    private RemoteNetwork() {
    }

    public static String getLanIpv4(Context context) {
        String fromWifi = ipv4FromWifiManager(context);
        if (fromWifi != null) {
            return fromWifi;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String fromCm = ipv4FromConnectivityManager(context);
            if (fromCm != null) {
                return fromCm;
            }
        }
        return ipv4FromInterfaces();
    }

    public static String buildBaseUrl(Context context) {
        String ip = getLanIpv4(context);
        int port = Prefs.getRemoteControlPort(context);
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        return String.format(Locale.US, "http://%s:%d", ip, port);
    }

    public static String buildPairingUrl(Context context) {
        String base = buildBaseUrl(context);
        if (base == null) {
            return null;
        }
        String token = Prefs.getOrCreateRemoteToken(context);
        return base + "/?t=" + token;
    }

    @SuppressWarnings("deprecation")
    private static String ipv4FromWifiManager(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) {
                return null;
            }
            int ip = wifi.getConnectionInfo() != null ? wifi.getConnectionInfo().getIpAddress() : 0;
            if (ip == 0) {
                return null;
            }
            return String.format(Locale.US, "%d.%d.%d.%d",
                    ip & 0xff,
                    (ip >> 8) & 0xff,
                    (ip >> 16) & 0xff,
                    (ip >> 24) & 0xff);
        } catch (Exception e) {
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static String ipv4FromConnectivityManager(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return null;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return null;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null || (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                // Still allow other transports that expose a private IPv4 (emulator).
            }
            LinkProperties props = cm.getLinkProperties(active);
            if (props == null) {
                return null;
            }
            for (LinkAddress addr : props.getLinkAddresses()) {
                InetAddress inet = addr.getAddress();
                if (inet instanceof Inet4Address && RemoteLanFilter.isAllowedAddress(inet)
                        && !inet.isLoopbackAddress()) {
                    return inet.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String ipv4FromInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address
                            && RemoteLanFilter.isAllowedAddress(addr)
                            && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
