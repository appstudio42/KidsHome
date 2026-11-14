package io.github.marcelsavegnago.kidshome;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Accepts only private / local LAN peer addresses for the parent remote server.
 * Household ranges are loopback, link-local, RFC 1918, and IPv6 unique-local.
 * 100.64.0.0/10 (RFC 6598 carrier-grade NAT) is rejected: that space belongs to
 * the ISP, not to the family's Wi-Fi.
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
        byte[] raw = address.getAddress();
        // Checked before site-local: a stub that treats 100.64/10 as local must still reject it.
        if (isCarrierGradeNat(raw)) {
            return false;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address.isSiteLocalAddress()) {
            return true;
        }
        // Unique-local IPv6 (fc00::/7) — InetAddress may not mark as site-local on all JDKs.
        if (raw != null && raw.length == 16) {
            int first = raw[0] & 0xFE;
            if (first == 0xFC) {
                return true;
            }
        }
        // Explicit RFC 1918 checks for broken InetAddress implementations.
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
        }
        return false;
    }

    /** RFC 6598 shared address space used by carrier NAT, not by a home LAN. */
    private static boolean isCarrierGradeNat(byte[] raw) {
        if (raw == null || raw.length != 4) {
            return false;
        }
        int a = raw[0] & 0xFF;
        int b = raw[1] & 0xFF;
        return a == 100 && b >= 64 && b <= 127;
    }
}
