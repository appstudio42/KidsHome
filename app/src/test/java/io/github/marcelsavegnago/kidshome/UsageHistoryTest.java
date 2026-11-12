package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UsageHistoryTest {

    @Test
    public void jsonRoundTripAndPrune() {
        Map<String, UsageHistory.DayUsage> days = new HashMap<>();
        for (int i = 1; i <= 35; i++) {
            String key = String.format("202601%02d", Math.min(i, 31));
            if (i > 31) {
                key = String.format("202602%02d", i - 31);
            }
            Map<String, Long> apps = new HashMap<>();
            apps.put("com.demo", (long) i * 60_000L);
            days.put(key, new UsageHistory.DayUsage(key, apps, i * 60_000L, i % 3));
        }
        Map<String, UsageHistory.DayUsage> pruned = UsageHistory.prune(days, "20260204");
        assertTrue(pruned.size() <= UsageHistory.RETAIN_DAYS);
        String json = UsageHistory.toJson(pruned);
        Map<String, UsageHistory.DayUsage> again = UsageHistory.fromJson(json);
        assertEquals(pruned.size(), again.size());
    }

    @Test
    public void upsertAndWeekAggregation() {
        UsageHistory.DayUsage day = UsageHistory.upsertAppMillis(null, "20261112", "com.a", 5_000L);
        day = UsageHistory.upsertAppMillis(day, "20261112", "com.a", 3_000L);
        day = UsageHistory.upsertAppMillis(day, "20261112", "com.b", 2_000L);
        assertEquals(8_000L, (long) day.appMillis.get("com.a"));
        assertEquals(10_000L, day.totalMillis);

        Map<String, UsageHistory.DayUsage> all = new HashMap<>();
        all.put("20261112", day);
        all.put("20261111", new UsageHistory.DayUsage(
                "20261111",
                java.util.Collections.singletonMap("com.a", 4_000L),
                4_000L,
                1
        ));
        List<UsageHistory.DayUsage> week = UsageHistory.weekDays(all, "20261112");
        assertEquals(7, week.size());
        Map<String, Long> totals = UsageHistory.aggregateApps(week);
        assertEquals(12_000L, (long) totals.get("com.a"));
        assertEquals(2_000L, (long) totals.get("com.b"));
    }

    @Test
    public void csvContainsHeaderAndRows() {
        Map<String, UsageHistory.DayUsage> days = new HashMap<>();
        Map<String, Long> apps = new HashMap<>();
        apps.put("com.foo", 1200L);
        days.put("20261112", new UsageHistory.DayUsage("20261112", apps, 1200L, 2));
        String csv = UsageHistory.toCsv(days);
        assertTrue(csv.startsWith("day,package,millis"));
        assertTrue(csv.contains("com.foo"));
        assertTrue(csv.contains("1200"));
        assertFalse(csv.contains("null"));
    }

    @Test
    public void shiftDayKey() {
        assertEquals("20260101", UsageHistory.shiftDayKey("20260102", -1));
        assertEquals("20251231", UsageHistory.shiftDayKey("20260101", -1));
    }
}
