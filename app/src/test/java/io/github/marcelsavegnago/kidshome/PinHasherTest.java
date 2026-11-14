package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PinHasherTest {
    /**
     * SHA-256 of the UTF-8 bytes of this salt string followed by "1234".
     * The salt is not hex-decoded. This is the pre-PBKDF2 on-disk hash.
     */
    private static final String LEGACY_SALT = "00112233445566778899aabbccddeeff";
    private static final String LEGACY_HASH =
            "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";

    @Test
    public void validPinFormats() {
        assertTrue(PinHasher.isValidPinFormat("1234"));
        assertTrue(PinHasher.isValidPinFormat("0000"));
        assertTrue(PinHasher.isValidPinFormat("12345678"));
        assertFalse(PinHasher.isValidPinFormat("123"));
        assertFalse(PinHasher.isValidPinFormat("123456789"));
        assertFalse(PinHasher.isValidPinFormat("12ab"));
        assertFalse(PinHasher.isValidPinFormat("12 34"));
        assertFalse(PinHasher.isValidPinFormat(""));
        assertFalse(PinHasher.isValidPinFormat(null));
    }

    @Test
    public void unicodeDigitsAreNotAValidPin() {
        String arabicIndic = "\u0661\u0662\u0663\u0664";
        String fullwidth = "\uFF11\uFF12\uFF13\uFF14";
        assertTrue(Character.isDigit(arabicIndic.charAt(0)));
        assertTrue(Character.isDigit(fullwidth.charAt(0)));
        assertEquals(4, arabicIndic.length());
        assertEquals(4, fullwidth.length());
        assertFalse(PinHasher.isValidPinFormat(arabicIndic));
        assertFalse(PinHasher.isValidPinFormat(fullwidth));
        assertFalse(PinHasher.isValidPinFormat("123\u0664"));
    }

    @Test
    public void versionedHashUsesPbkdf2AndDefaultIterations() {
        assertEquals(120_000, PinHasher.DEFAULT_ITERATIONS);
        assertEquals(16, PinHasher.SALT_BYTES);
        assertEquals(1, PinHasher.CURRENT_VERSION);

        String first = PinHasher.hash("2468");
        String second = PinHasher.hash("2468");
        assertTrue(first.startsWith("v1:120000:"));
        assertTrue(PinHasher.isVersionedRecord(first));
        assertNotEquals(first, second);
        assertTrue(PinHasher.verify("2468", first));
        assertFalse(PinHasher.verify("2469", first));
        assertFalse(PinHasher.verify("", first));
        assertFalse(PinHasher.verify(null, first));
    }

    @Test
    public void sameSaltAndIterationsAreDeterministic() {
        byte[] salt = new byte[PinHasher.SALT_BYTES];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) (i + 1);
        }
        String record = PinHasher.hash("2468", salt, 1_000);
        assertEquals(record, PinHasher.hash("2468", salt, 1_000));
        assertTrue(record.startsWith("v1:1000:"));
        assertTrue(PinHasher.verify("2468", record));
        assertFalse(PinHasher.verify("2469", record));
        assertFalse(PinHasher.verify("2468", record.replace(":1000:", ":1001:")));

        int saltStart = record.indexOf(':', record.indexOf(':') + 1) + 1;
        char flipped = record.charAt(saltStart) == '0' ? '1' : '0';
        String tamperedSalt = record.substring(0, saltStart) + flipped + record.substring(saltStart + 1);
        assertFalse(PinHasher.verify("2468", tamperedSalt));

        String upperHex = record.substring(0, saltStart) + record.substring(saltStart).toUpperCase(Locale.ROOT);
        assertTrue(PinHasher.verify("2468", upperHex));

        assertFalse(PinHasher.verify("2468", null));
        assertFalse(PinHasher.verify("2468", "v1:nope:aa:bb"));
        assertFalse(PinHasher.verify("2468", "v1:0:" + repeat("00", 16) + ":" + repeat("ab", 32)));
        assertFalse(PinHasher.verify("2468", "v1:1000001:" + repeat("00", 16) + ":" + repeat("ab", 32)));
        assertFalse(PinHasher.verify("2468", "v2:1000:" + repeat("11", 16) + ":" + repeat("22", 32)));
        assertFalse(PinHasher.isVersionedRecord("v2:1000:aa:bb"));
    }

    @Test
    public void legacySaltedSha256StillVerifies() {
        assertEquals(LEGACY_HASH, PinHasher.legacyHash("1234", LEGACY_SALT));
        assertTrue(PinHasher.verifyLegacy("1234", LEGACY_SALT, LEGACY_HASH));
        assertFalse(PinHasher.verifyLegacy("1235", LEGACY_SALT, LEGACY_HASH));
        assertFalse(PinHasher.verifyLegacy("1234", LEGACY_SALT + "00", LEGACY_HASH));
        assertFalse(PinHasher.verifyLegacy("1234", LEGACY_SALT, null));
        assertFalse(PinHasher.isVersionedRecord(LEGACY_HASH));
        assertFalse(PinHasher.verify("1234", LEGACY_HASH));
    }

    @Test
    public void verifyDoesNotChangeAttemptTracker() {
        PinAttemptTracker.resetForTests();
        byte[] salt = new byte[PinHasher.SALT_BYTES];
        salt[0] = 7;
        String record = PinHasher.hash("1357", salt, 1_000);

        assertFalse(PinHasher.verify("0000", record));
        assertEquals(1_000L, PinAttemptTracker.registerFailure());
        assertTrue(PinAttemptTracker.isLockedOut());
        assertEquals(1, PinAttemptTracker.failureCount());

        assertTrue(PinHasher.verify("1357", record));
        assertEquals(1, PinAttemptTracker.failureCount());
        assertTrue(PinAttemptTracker.isLockedOut());

        PinAttemptTracker.reset();
        assertFalse(PinAttemptTracker.isLockedOut());
        assertEquals(0, PinAttemptTracker.failureCount());
    }

    private static String repeat(String part, int times) {
        StringBuilder builder = new StringBuilder(part.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(part);
        }
        return builder.toString();
    }
}
