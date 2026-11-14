package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BootReceiverTest {
    @Test
    public void warmupStaysInsideTheBroadcastBudget() {
        assertEquals(2500L, BootReceiver.warmupDelayMs());
        assertTrue(BootReceiver.warmupDelayMs() < BootReceiver.BROADCAST_BUDGET_MS);
        assertTrue(BootReceiver.warmupDelayMs() < 10_000L);
    }

    @Test
    public void longDelayIsClampedBeforeTheBudget() {
        assertEquals(7000L, BootReceiver.boundedDelay(9000L, 8000L));
        assertEquals(0L, BootReceiver.boundedDelay(5000L, 1000L));
        assertEquals(0L, BootReceiver.boundedDelay(-1L, 8000L));
        assertEquals(2500L, BootReceiver.boundedDelay(2500L, 8000L));
    }
}
