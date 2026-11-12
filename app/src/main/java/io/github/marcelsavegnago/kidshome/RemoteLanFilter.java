package io.github.marcelsavegnago.kidshome;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Accepts only private / local LAN peer addresses for the parent remote server.
 */
public final class RemoteLanFilter {
    private RemoteLanFilter() {
    }

    public static boolean isAllowedPeer(String host) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        String cleaned = host.trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        // Strip IPv6 zone id or brackets.
        if (cleaned.startsWith("[") && cleaned.contains("]")) {
            cleaned = cleaned.substring(1, cleaned.indexOf(']'));
        }
        int zone = cleaned.indexOf('%');
        if (zone >= 0) {
            cleaned = cleaned.substring(0, zone);
        }
        try {
            InetAddress address = InetAddress.getByName(cleaned);
            return isAllowedAddress(address);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isAllowedAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address.isSiteLocalAddress()) {
            return true;
        }
        // Unique-local IPv6 (fc00::/7) — InetAddress may not mark as site-local on all JDKs.
        byte[] raw = address.getAddress();
        if (raw != null && raw.length == 16) {
            int first = raw[0] & 0xFE;
            if (first == 0xFC) {
                return true;
            }
        }
        // Explicit RFC1918 / CGNAT checks for broken InetAddress implementations.
        if (raw != null && raw.length == 4) {
            int a = raw[0] & 0xFF;
            int b = raw[1] & 0xFF;
            if (a == 10) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 100 && b >= 64 && b <= 127) {
                // CGNAT shared space — still private-ish LAN path on some carriers/emulators.
                return true;
            }
        }
        return false;
    }
}
