package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteLanFilterTest {
    @Test
    public void allowsPrivateRanges() {
        assertTrue(RemoteLanFilter.isAllowedPeer("192.168.1.20"));
        assertTrue(RemoteLanFilter.isAllowedPeer("10.0.0.5"));
        assertTrue(RemoteLanFilter.isAllowedPeer("172.16.4.8"));
        assertTrue(RemoteLanFilter.isAllowedPeer("127.0.0.1"));
        assertTrue(RemoteLanFilter.isAllowedPeer("::1"));
    }

    @Test
    public void rejectsPublicAddresses() {
        assertFalse(RemoteLanFilter.isAllowedPeer("8.8.8.8"));
        assertFalse(RemoteLanFilter.isAllowedPeer("1.1.1.1"));
        assertFalse(RemoteLanFilter.isAllowedPeer(""));
        assertFalse(RemoteLanFilter.isAllowedPeer(null));
    }
}
