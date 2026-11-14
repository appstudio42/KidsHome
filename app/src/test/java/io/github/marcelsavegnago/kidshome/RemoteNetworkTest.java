package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteNetworkTest {
    @Test
    public void chooseBindHostKeepsPrivateLanAddresses() {
        assertEquals("192.168.1.20", RemoteNetwork.chooseBindHost("192.168.1.20"));
        assertEquals("10.1.2.3", RemoteNetwork.chooseBindHost("10.1.2.3"));
        assertEquals("172.16.5.5", RemoteNetwork.chooseBindHost("172.16.5.5"));
        assertEquals("172.31.255.254", RemoteNetwork.chooseBindHost("172.31.255.254"));
        assertEquals("169.254.3.4", RemoteNetwork.chooseBindHost("169.254.3.4"));
        assertEquals("192.168.1.20", RemoteNetwork.chooseBindHost("192.168.001.020"));
    }

    @Test
    public void chooseBindHostFallsBackToLoopback() {
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost(null));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost(""));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("0.0.0.0"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("8.8.8.8"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("100.64.0.1"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("100.127.255.254"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("172.32.0.1"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("example.com"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("::1"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("10.0.0.256"));
        assertEquals(RemoteNetwork.LOOPBACK_BIND_HOST, RemoteNetwork.chooseBindHost("127.0.0.1"));
    }

    @Test
    public void householdInterfacesSkipCarrierAndTunnels() {
        assertTrue(RemoteNetwork.isHouseholdInterface("wlan0"));
        assertTrue(RemoteNetwork.isHouseholdInterface("eth0"));
        assertTrue(RemoteNetwork.isHouseholdInterface("ap0"));
        assertTrue(RemoteNetwork.isHouseholdInterface("swlan0"));
        assertFalse(RemoteNetwork.isHouseholdInterface(null));
        assertFalse(RemoteNetwork.isHouseholdInterface("lo"));
        assertFalse(RemoteNetwork.isHouseholdInterface("lo0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("tun0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("ppp0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("rmnet_data0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("ccmni0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("vpn0"));
        assertFalse(RemoteNetwork.isHouseholdInterface("wg0"));
    }
}
