package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinHasherTest {
    @Test
    public void validPinFormats() {
        assertTrue(PinHasher.isValidPinFormat("1234"));
        assertTrue(PinHasher.isValidPinFormat("12345678"));
        assertFalse(PinHasher.isValidPinFormat("123"));
        assertFalse(PinHasher.isValidPinFormat("123456789"));
        assertFalse(PinHasher.isValidPinFormat("12ab"));
        assertFalse(PinHasher.isValidPinFormat(null));
    }

    @Test
    public void hashAndVerify() {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash("2468", salt);
        assertTrue(PinHasher.verify("2468", salt, hash));
        assertFalse(PinHasher.verify("2469", salt, hash));
        assertFalse(PinHasher.verify("2468", "othersalt", hash));
    }
}
