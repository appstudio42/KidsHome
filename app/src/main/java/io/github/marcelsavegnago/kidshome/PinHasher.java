package io.github.marcelsavegnago.kidshome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Parent PIN hashing.
 *
 * <p>New PINs are stored as one versioned record
 * {@code v1:iterations:saltHex:hashHex}. Version 1 is PBKDF2-HMAC-SHA1
 * ({@code PBKDF2WithHmacSHA1}, available since API 22) with a 16-byte
 * {@link SecureRandom} salt and {@link #DEFAULT_ITERATIONS} iterations.
 * The derived key is 256 bits, so the platform runs two SHA-1 blocks per
 * iteration. That cost is too high for a modest device's main thread
 * (well past a short UI hitch). Callers that run while the screen is
 * waiting must use {@link PinTasks}.
 *
 * <p>Installations that still have a separate salt plus a salted SHA-256
 * hex digest are checked with {@link #verifyLegacy} and rewritten to a
 * version 1 record after the next correct PIN.
 */
public final class PinHasher {
    public static final int CURRENT_VERSION = 1;
    /** High enough to slow an offline guess. UI work goes through {@link PinTasks}. */
    public static final int DEFAULT_ITERATIONS = 120_000;
    public static final int SALT_BYTES = 16;

    static final int HASH_BYTES = 32;
    /** Reject a tampered iteration count before it can stall a verify. */
    static final int MAX_ITERATIONS = 1_000_000;
    private static final String KDF = "PBKDF2WithHmacSHA1";
    private static final String VERSION_TOKEN = "v" + CURRENT_VERSION;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHasher() {
    }

    /**
     * Builds a version 1 record for {@code pin}. The salt is 16 random bytes.
     */
    public static String hash(String pin) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return hash(pin, salt, DEFAULT_ITERATIONS);
    }

    /** Visible for unit tests that need a deterministic salt and iteration count. */
    static String hash(String pin, byte[] salt, int iterations) {
        if (pin == null) {
            throw new IllegalArgumentException("PIN is required");
        }
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("PIN salt must be 16 bytes");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("PIN iterations are out of range");
        }
        byte[] derived = pbkdf2(pin, salt, iterations);
        return VERSION_TOKEN + ":" + iterations + ":" + toHex(salt) + ":" + toHex(derived);
    }

    /**
     * Checks {@code pin} against a versioned record. Comparison uses
     * {@link MessageDigest#isEqual(byte[], byte[])}.
     */
    public static boolean verify(String pin, String storedRecord) {
        Record record = parse(storedRecord);
        if (record == null || pin == null) {
            return false;
        }
        byte[] actual = pbkdf2(pin, record.salt, record.iterations);
        return equalsHash(actual, record.hash);
    }

    /** True when {@code stored} uses the versioned record layout (even if malformed). */
    public static boolean isVersionedRecord(String stored) {
        return stored != null && stored.startsWith(VERSION_TOKEN + ":");
    }

    /**
     * Salted SHA-256 used by installs created before the versioned record.
     * The salt is the stored hex string, hashed as UTF-8, not decoded.
     */
    static String legacyHash(String pin, String salt) {
        if (pin == null || salt == null) {
            throw new IllegalArgumentException("PIN and salt are required");
        }
        return toHex(legacyDigest(pin, salt));
    }

    /**
     * Checks a pre-migration hash. Comparison uses
     * {@link MessageDigest#isEqual(byte[], byte[])}.
     */
    public static boolean verifyLegacy(String pin, String salt, String expectedHash) {
        if (pin == null || salt == null || expectedHash == null) {
            return false;
        }
        byte[] actual = legacyDigest(pin, salt);
        byte[] expected = fromHex(expectedHash);
        return equalsHash(actual, expected);
    }

    /**
     * Parent PINs are ASCII digits only. {@link Character#isDigit(char)} is not
     * used because it also accepts Unicode decimal digits.
     */
    public static boolean isValidPinFormat(String pin) {
        if (pin == null) {
            return false;
        }
        int length = pin.length();
        if (length < 4 || length > 8) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BYTES * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF);
            byte[] derived = factory.generateSecret(spec).getEncoded();
            if (derived == null || derived.length != HASH_BYTES) {
                throw new IllegalStateException("Unexpected PBKDF2 length");
            }
            return derived;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash PIN", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] legacyDigest(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash PIN", e);
        }
    }

    private static Record parse(String stored) {
        if (!isVersionedRecord(stored)) {
            return null;
        }
        String[] parts = stored.split(":", -1);
        if (parts.length != 4 || !VERSION_TOKEN.equals(parts[0])) {
            return null;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            return null;
        }
        byte[] salt = fromHex(parts[2]);
        byte[] hash = fromHex(parts[3]);
        if (salt == null || salt.length != SALT_BYTES || hash == null || hash.length != HASH_BYTES) {
            return null;
        }
        return new Record(iterations, salt, hash);
    }

    /**
     * API 22's {@link MessageDigest#isEqual} throws if either array is null.
     * Both hashes are fixed length when a record parsed, so the compare itself
     * walks every byte.
     */
    private static boolean equalsHash(byte[] actual, byte[] expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(actual, expected);
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

    private static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static final class Record {
        final int iterations;
        final byte[] salt;
        final byte[] hash;

        Record(int iterations, byte[] salt, byte[] hash) {
            this.iterations = iterations;
            this.salt = salt;
            this.hash = hash;
        }
    }
}
