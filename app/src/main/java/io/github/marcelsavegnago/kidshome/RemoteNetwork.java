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
 * Resolves the tablet's current LAN IPv4 for the parent remote URL and for the
 * HTTP socket bind address. The server is never told to listen on every interface.
 */
public final class RemoteNetwork {
    /** Used when no private Wi-Fi/Ethernet IPv4 is available, so the port stays off the LAN. */
    public static final String LOOPBACK_BIND_HOST = "127.0.0.1";

    private RemoteNetwork() {
    }

    /**
     * Bind address for the parent server.
     * A dotted private IPv4 is returned as-is. Anything else, including CGNAT,
     * public addresses, and hostnames, becomes loopback.
     */
    public static String chooseBindHost(String lanIpv4) {
        byte[] raw = dottedIpv4(lanIpv4 == null ? null : lanIpv4.trim());
        if (raw == null) {
            return LOOPBACK_BIND_HOST;
        }
        try {
            InetAddress address = InetAddress.getByAddress(raw);
            if (!(address instanceof Inet4Address)
                    || address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress()
                    || !RemoteLanFilter.isAllowedAddress(address)) {
                return LOOPBACK_BIND_HOST;
            }
            String host = address.getHostAddress();
            return host == null || host.isEmpty() ? LOOPBACK_BIND_HOST : host;
        } catch (Exception e) {
            return LOOPBACK_BIND_HOST;
        }
    }

    /** Private Wi-Fi/Ethernet IPv4 when one exists; otherwise loopback. */
    public static String bindHost(Context context) {
        return chooseBindHost(getLanIpv4(context));
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
            byte[] raw = new byte[]{
                    (byte) (ip & 0xff),
                    (byte) ((ip >> 8) & 0xff),
                    (byte) ((ip >> 16) & 0xff),
                    (byte) ((ip >> 24) & 0xff)
            };
            return usableLanIpv4(InetAddress.getByAddress(raw));
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
            if (caps == null
                    || (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                return null;
            }
            LinkProperties props = cm.getLinkProperties(active);
            if (props == null) {
                return null;
            }
            for (LinkAddress addr : props.getLinkAddresses()) {
                String usable = usableLanIpv4(addr.getAddress());
                if (usable != null) {
                    return usable;
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
                if (!nif.isUp() || nif.isLoopback() || nif.isPointToPoint()) {
                    continue;
                }
                if (!isHouseholdInterface(nif.getName())) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    String usable = usableLanIpv4(addr);
                    if (usable != null) {
                        return usable;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Wi-Fi and Ethernet names stay. Cellular, VPN, and tunnel interfaces do not,
     * even when the carrier hands them an RFC 1918 address.
     */
    static boolean isHouseholdInterface(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.US);
        return !normalized.isEmpty()
                && !isLoopbackName(normalized)
                && !isCarrierOrTunnel(normalized);
    }

    private static boolean isLoopbackName(String name) {
        return "lo".equals(name) || (name.startsWith("lo") && name.length() > 2 && Character.isDigit(name.charAt(2)));
    }

    private static boolean isCarrierOrTunnel(String name) {
        String[] blocked = {
                "tun", "tap", "ppp", "ipsec", "wg", "dummy", "sit", "rmnet",
                "ccmni", "pdp", "wwan", "clat", "epdg", "ifb", "gre", "ip6tnl",
                "vpn", "radio", "rev_rmnet"
        };
        for (String prefix : blocked) {
            if (name.startsWith(prefix) || name.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Private unicast IPv4 that a parent on the same home network can open. */
    private static String usableLanIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)
                || address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()
                || !RemoteLanFilter.isAllowedAddress(address)) {
            return null;
        }
        return address.getHostAddress();
    }

    /** Four decimal octets, or null. Does not resolve hostnames. */
    private static byte[] dottedIpv4(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] raw = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            for (int c = 0; c < part.length(); c++) {
                if (part.charAt(c) < '0' || part.charAt(c) > '9') {
                    return null;
                }
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octet > 255) {
                return null;
            }
            raw[i] = (byte) octet;
        }
        return raw;
    }
}
