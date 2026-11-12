package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteActionLogTest {
    @Test
    public void roundTripJson() {
        RemoteActionLog.Entry entry = new RemoteActionLog.Entry(
                1_700_000_000_000L, "20260312", RemoteActionLog.ACTION_ADD_TIME, 30);
        String json = RemoteActionLog.toJson(Collections.singletonList(entry));
        List<RemoteActionLog.Entry> parsed = RemoteActionLog.fromJson(json);
        assertEquals(1, parsed.size());
        assertEquals(RemoteActionLog.ACTION_ADD_TIME, parsed.get(0).action);
        assertEquals(30, parsed.get(0).minutes);
    }

    @Test
    public void formatParentRemoteLabel() {
        RemoteActionLog.Entry entry = new RemoteActionLog.Entry(
                0L, "20260312", RemoteActionLog.ACTION_LOCK, 0);
        assertTrue(RemoteActionLog.formatLabel(entry, false).contains("parent remote"));
        assertTrue(RemoteActionLog.formatLabel(entry, true).contains("controle remoto"));
    }
}
