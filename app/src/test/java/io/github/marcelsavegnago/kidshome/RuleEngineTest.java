package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleEngineTest {

    @Test
    public void scheduleOutsideWindowIsNormal() {
        ScheduleWindow school = new ScheduleWindow(
                "1", "School", true, ScheduleWindow.weekdaysMask(),
                7 * 60, 12 * 60, ScheduleWindow.Mode.ALLOW_CATEGORIES,
                EnumSet.of(AppCategory.EDUCATION)
        );
        // Monday 15:00 — outside school window
        RuleEngine.ScheduleDecision d = RuleEngine.evaluateSchedule(
                Collections.singletonList(school), Calendar.MONDAY, 15 * 60);
        assertEquals(RuleEngine.AccessMode.NORMAL, d.mode);
    }

    @Test
    public void scheduleCategoryFilterHidesGames() {
        ScheduleWindow school = new ScheduleWindow(
                "1", "School", true, ScheduleWindow.weekdaysMask(),
                7 * 60, 12 * 60, ScheduleWindow.Mode.ALLOW_CATEGORIES,
                EnumSet.of(AppCategory.EDUCATION)
        );
        RuleEngine.ScheduleDecision d = RuleEngine.evaluateSchedule(
                Collections.singletonList(school), Calendar.WEDNESDAY, 9 * 60);
        assertEquals(RuleEngine.AccessMode.CATEGORY_FILTER, d.mode);
        assertTrue(RuleEngine.isAllowedBySchedule(d, AppCategory.EDUCATION));
        assertFalse(RuleEngine.isAllowedBySchedule(d, AppCategory.GAMES));
        assertFalse(RuleEngine.isAllowedBySchedule(d, AppCategory.VIDEO));
    }

    @Test
    public void bedtimeScheduleBlocksEverything() {
        ScheduleWindow bed = new ScheduleWindow(
                "b", "Bedtime", true, ScheduleWindow.everydayMask(),
                20 * 60, 7 * 60, ScheduleWindow.Mode.BEDTIME,
                EnumSet.noneOf(AppCategory.class)
        );
        RuleEngine.ScheduleDecision night = RuleEngine.evaluateSchedule(
                Collections.singletonList(bed), Calendar.FRIDAY, 21 * 60);
        assertTrue(night.isBedtime());
        assertFalse(RuleEngine.isAllowedBySchedule(night, AppCategory.EDUCATION));

        RuleEngine.ScheduleDecision morning = RuleEngine.evaluateSchedule(
                Collections.singletonList(bed), Calendar.SATURDAY, 8 * 60);
        assertEquals(RuleEngine.AccessMode.NORMAL, morning.mode);
    }

    @Test
    public void bedtimeTakesPriorityOverCategoryFilter() {
        ScheduleWindow reading = new ScheduleWindow(
                "r", "Reading", true, ScheduleWindow.everydayMask(),
                20 * 60, 21 * 60, ScheduleWindow.Mode.ALLOW_CATEGORIES,
                EnumSet.of(AppCategory.EDUCATION)
        );
        ScheduleWindow bed = new ScheduleWindow(
                "b", "Bed", true, ScheduleWindow.everydayMask(),
                20 * 60, 7 * 60, ScheduleWindow.Mode.BEDTIME,
                EnumSet.noneOf(AppCategory.class)
        );
        RuleEngine.ScheduleDecision d = RuleEngine.evaluateSchedule(
                Arrays.asList(reading, bed), Calendar.SUNDAY, 20 * 60 + 30);
        assertTrue(d.isBedtime());
    }

    @Test
    public void disabledScheduleIgnored() {
        ScheduleWindow school = new ScheduleWindow(
                "1", "School", false, ScheduleWindow.weekdaysMask(),
                7 * 60, 12 * 60, ScheduleWindow.Mode.ALLOW_CATEGORIES,
                EnumSet.of(AppCategory.EDUCATION)
        );
        RuleEngine.ScheduleDecision d = RuleEngine.evaluateSchedule(
                Collections.singletonList(school), Calendar.MONDAY, 9 * 60);
        assertEquals(RuleEngine.AccessMode.NORMAL, d.mode);
    }

    @Test
    public void perAppLimitAndBonus() {
        assertEquals(Long.MAX_VALUE,
                RuleEngine.remainingAppMillis(10_000L, 0L, RuleEngine.UNLIMITED_MINUTES));
        assertFalse(RuleEngine.isAppLimitReached(40 * 60_000L, 0L, RuleEngine.UNLIMITED_MINUTES));

        long rem = RuleEngine.remainingAppMillis(25 * 60_000L, 10 * 60_000L, 30);
        assertEquals(15 * 60_000L, rem);
        assertTrue(RuleEngine.isAppLimitReached(40 * 60_000L, 5 * 60_000L, 30));
        assertFalse(RuleEngine.isAppLimitReached(20 * 60_000L, 0L, 30));
    }

    @Test
    public void addBonusMillis() {
        assertEquals(15 * 60_000L, RuleEngine.addBonusMillis(0L, 15));
        assertEquals(45 * 60_000L, RuleEngine.addBonusMillis(30 * 60_000L, 15));
        assertEquals(10L, RuleEngine.addBonusMillis(10L, 0));
    }

    @Test
    public void dayRolloverDetection() {
        assertTrue(RuleEngine.shouldRollDay("20260101", "20260102"));
        assertTrue(RuleEngine.shouldRollDay("", "20260102"));
        assertTrue(RuleEngine.shouldRollDay(null, "20260102"));
        assertFalse(RuleEngine.shouldRollDay("20260102", "20260102"));
    }

    @Test
    public void getAppLimitDefaultsUnlimited() {
        Map<String, Integer> limits = new HashMap<>();
        limits.put("com.video", 30);
        assertEquals(30, RuleEngine.getAppLimitMinutes(limits, "com.video"));
        assertEquals(RuleEngine.UNLIMITED_MINUTES, RuleEngine.getAppLimitMinutes(limits, "com.edu"));
        assertEquals(RuleEngine.UNLIMITED_MINUTES, RuleEngine.getAppLimitMinutes(null, "com.edu"));
    }

    @Test
    public void combinedBedtimePrefAndSchedule() {
        RuleEngine.ScheduleDecision normal = RuleEngine.ScheduleDecision.normal();
        assertTrue(RuleEngine.isAnyBedtimeActive(true, false, 20 * 60, 7 * 60, 21 * 60, normal));
        assertFalse(RuleEngine.isAnyBedtimeActive(true, true, 20 * 60, 7 * 60, 21 * 60, normal));
        assertFalse(RuleEngine.isAnyBedtimeActive(false, false, 20 * 60, 7 * 60, 21 * 60, normal));

        ScheduleWindow bed = new ScheduleWindow(
                "b", "Bed", true, ScheduleWindow.everydayMask(),
                20 * 60, 7 * 60, ScheduleWindow.Mode.BEDTIME,
                EnumSet.noneOf(AppCategory.class)
        );
        RuleEngine.ScheduleDecision scheduled = RuleEngine.evaluateSchedule(
                Collections.singletonList(bed), Calendar.MONDAY, 22 * 60);
        assertTrue(RuleEngine.isAnyBedtimeActive(false, false, 0, 0, 22 * 60, scheduled));
    }

    @Test
    public void scheduleJsonRoundTrip() {
        List<ScheduleWindow> original = Collections.singletonList(new ScheduleWindow(
                "abc", "Reading", true, ScheduleWindow.maskForDays(Calendar.SATURDAY, Calendar.SUNDAY),
                20 * 60, 21 * 60, ScheduleWindow.Mode.ALLOW_CATEGORIES,
                EnumSet.of(AppCategory.EDUCATION, AppCategory.OTHER)
        ));
        List<ScheduleWindow> parsed = ScheduleWindow.fromJson(ScheduleWindow.toJson(original));
        assertEquals(1, parsed.size());
        assertEquals("abc", parsed.get(0).id);
        assertEquals("Reading", parsed.get(0).name);
        assertTrue(parsed.get(0).appliesToDay(Calendar.SATURDAY));
        assertFalse(parsed.get(0).appliesToDay(Calendar.MONDAY));
        assertTrue(parsed.get(0).categories.contains(AppCategory.EDUCATION));
    }
}
