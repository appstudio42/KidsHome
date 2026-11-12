package io.github.marcelsavegnago.kidshome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Simple salted SHA-256 PIN hashing. Not a device-owner lock — only slows casual bypass.
 */
public final class PinHasher {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PinHasher() {
    }

    public static String newSalt() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    public static String hash(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash PIN", e);
        }
    }

    public static boolean verify(String pin, String salt, String expectedHash) {
        String actual = hash(pin, salt);
        return constantTimeEquals(actual, expectedHash);
    }

    public static boolean isValidPinFormat(String pin) {
        if (pin == null) {
            return false;
        }
        if (pin.length() < 4 || pin.length() > 8) {
            return false;
        }
        for (int i = 0; i < pin.length(); i++) {
            if (!Character.isDigit(pin.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }
}
