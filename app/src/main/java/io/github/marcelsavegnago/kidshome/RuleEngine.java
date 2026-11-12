package io.github.marcelsavegnago.kidshome;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure rule evaluation for schedules, per-app limits, and bonus minutes.
 * No Android framework dependencies — unit-testable.
 */
public final class RuleEngine {
    public static final int UNLIMITED_MINUTES = -1;

    private RuleEngine() {
    }

    public enum AccessMode {
        /** No active schedule window — normal allowlist rules. */
        NORMAL,
        /** Active window restricts apps to listed categories. */
        CATEGORY_FILTER,
        /** Active bedtime-style window blocks everything. */
        BEDTIME
    }

    public static final class ScheduleDecision {
        public final AccessMode mode;
        public final ScheduleWindow window;
        public final Set<AppCategory> allowedCategories;

        private ScheduleDecision(AccessMode mode, ScheduleWindow window, Set<AppCategory> categories) {
            this.mode = mode;
            this.window = window;
            this.allowedCategories = categories == null
                    ? Collections.<AppCategory>emptySet()
                    : Collections.unmodifiableSet(EnumSet.copyOf(categories));
        }

        public static ScheduleDecision normal() {
            return new ScheduleDecision(AccessMode.NORMAL, null, EnumSet.noneOf(AppCategory.class));
        }

        public static ScheduleDecision bedtime(ScheduleWindow window) {
            return new ScheduleDecision(AccessMode.BEDTIME, window, EnumSet.noneOf(AppCategory.class));
        }

        public static ScheduleDecision filter(ScheduleWindow window) {
            return new ScheduleDecision(AccessMode.CATEGORY_FILTER, window, window.categories);
        }

        public boolean isBedtime() {
            return mode == AccessMode.BEDTIME;
        }
    }

    /**
     * Picks the first enabled window that matches day + time.
     * Bedtime windows take priority over category filters when both match.
     */
    public static ScheduleDecision evaluateSchedule(
            List<ScheduleWindow> windows,
            int calendarDayOfWeek,
            int minutesOfDay
    ) {
        if (windows == null || windows.isEmpty()) {
            return ScheduleDecision.normal();
        }
        ScheduleDecision categoryHit = null;
        for (ScheduleWindow window : windows) {
            if (window == null || !window.enabled) {
                continue;
            }
            if (!window.appliesToDay(calendarDayOfWeek)) {
                continue;
            }
            if (!ScreenTimeState.isInBedtimeWindow(minutesOfDay, window.startMinutes, window.endMinutes)) {
                continue;
            }
            if (window.mode == ScheduleWindow.Mode.BEDTIME) {
                return ScheduleDecision.bedtime(window);
            }
            if (categoryHit == null) {
                categoryHit = ScheduleDecision.filter(window);
            }
        }
        return categoryHit != null ? categoryHit : ScheduleDecision.normal();
    }

    /**
     * Whether an allowed app should appear / launch under the current schedule.
     * Sites (null category) stay visible unless bedtime.
     */
    public static boolean isAllowedBySchedule(ScheduleDecision decision, AppCategory category) {
        if (decision == null || decision.mode == AccessMode.NORMAL) {
            return true;
        }
        if (decision.mode == AccessMode.BEDTIME) {
            return false;
        }
        AppCategory cat = category == null ? AppCategory.OTHER : category;
        return decision.allowedCategories.contains(cat);
    }

    /**
     * Remaining millis for a single app. Returns {@link Long#MAX_VALUE} when unlimited.
     * Limit minutes of {@link #UNLIMITED_MINUTES} (or missing) means unlimited.
     */
    public static long remainingAppMillis(long usedMillis, long bonusMillis, int limitMinutes) {
        if (limitMinutes < 0) {
            return Long.MAX_VALUE;
        }
        long limitMillis = limitMinutes * 60L * 1000L;
        // Bonus extends the per-app allowance (same rule as the daily total limit).
        return Math.max(0L, limitMillis - Math.max(0L, usedMillis) + Math.max(0L, bonusMillis));
    }

    public static boolean isAppLimitReached(long usedMillis, long bonusMillis, int limitMinutes) {
        if (limitMinutes < 0) {
            return false;
        }
        return remainingAppMillis(usedMillis, bonusMillis, limitMinutes) <= 0L;
    }

    public static int getAppLimitMinutes(Map<String, Integer> limits, String packageName) {
        if (limits == null || packageName == null) {
            return UNLIMITED_MINUTES;
        }
        Integer value = limits.get(packageName);
        return value == null ? UNLIMITED_MINUTES : value;
    }

    public static AppCategory getAppCategory(Map<String, AppCategory> categories, String packageName) {
        if (categories == null || packageName == null) {
            return AppCategory.OTHER;
        }
        AppCategory cat = categories.get(packageName);
        return cat == null ? AppCategory.OTHER : cat;
    }

    /**
     * After granting {@code bonusMinutes}, returns the new bonus millis total.
     */
    public static long addBonusMillis(long currentBonusMillis, int bonusMinutes) {
        if (bonusMinutes <= 0) {
            return Math.max(0L, currentBonusMillis);
        }
        return Math.max(0L, currentBonusMillis) + bonusMinutes * 60L * 1000L;
    }

    /**
     * Day-rollover: when the stored day key differs from today, usage/bonus reset.
     */
    public static boolean shouldRollDay(String storedDayKey, String todayKey) {
        if (todayKey == null || todayKey.isEmpty()) {
            return false;
        }
        return storedDayKey == null || storedDayKey.isEmpty() || !todayKey.equals(storedDayKey);
    }

    /**
     * Combine global bedtime preference with schedule bedtime.
     */
    public static boolean isAnyBedtimeActive(
            boolean prefsBedtimeEnabled,
            boolean prefsBedtimeDisabledToday,
            int prefsStart,
            int prefsEnd,
            int minutesOfDay,
            ScheduleDecision schedule
    ) {
        if (schedule != null && schedule.isBedtime()) {
            return true;
        }
        if (!prefsBedtimeEnabled || prefsBedtimeDisabledToday) {
            return false;
        }
        return ScreenTimeState.isInBedtimeWindow(minutesOfDay, prefsStart, prefsEnd);
    }
}
