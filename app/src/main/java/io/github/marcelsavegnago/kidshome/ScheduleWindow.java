package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Parent-defined routine window for one or more weekdays.
 * Days use Calendar constants: Sunday=1 … Saturday=7, stored as a bitmask
 * where bit (day-1) is set when the day is active.
 */
public final class ScheduleWindow {
    public enum Mode {
        /** Only apps in {@link #categories} are visible / launchable. */
        ALLOW_CATEGORIES("allow_categories"),
        /** Everything blocked — bedtime-style lock until the window ends. */
        BEDTIME("bedtime");

        private final String prefsValue;

        Mode(String prefsValue) {
            this.prefsValue = prefsValue;
        }

        public String toPrefsValue() {
            return prefsValue;
        }

        public static Mode fromPrefsValue(String value) {
            if (BEDTIME.prefsValue.equalsIgnoreCase(value)) {
                return BEDTIME;
            }
            return ALLOW_CATEGORIES;
        }
    }

    public final String id;
    public final String name;
    public final boolean enabled;
    /** Bitmask for Calendar.SUNDAY(1)…SATURDAY(7). */
    public final int daysMask;
    public final int startMinutes;
    public final int endMinutes;
    public final Mode mode;
    public final Set<AppCategory> categories;

    public ScheduleWindow(
            String id,
            String name,
            boolean enabled,
            int daysMask,
            int startMinutes,
            int endMinutes,
            Mode mode,
            Set<AppCategory> categories
    ) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "" : name;
        this.enabled = enabled;
        this.daysMask = daysMask;
        this.startMinutes = Math.max(0, Math.min(24 * 60 - 1, startMinutes));
        this.endMinutes = Math.max(0, Math.min(24 * 60 - 1, endMinutes));
        this.mode = mode == null ? Mode.ALLOW_CATEGORIES : mode;
        this.categories = categories == null || categories.isEmpty()
                ? EnumSet.noneOf(AppCategory.class)
                : EnumSet.copyOf(categories);
    }

    public boolean appliesToDay(int calendarDayOfWeek) {
        if (calendarDayOfWeek < 1 || calendarDayOfWeek > 7) {
            return false;
        }
        return (daysMask & (1 << (calendarDayOfWeek - 1))) != 0;
    }

    public static int maskForDays(int... calendarDays) {
        int mask = 0;
        if (calendarDays == null) {
            return 0;
        }
        for (int day : calendarDays) {
            if (day >= 1 && day <= 7) {
                mask |= (1 << (day - 1));
            }
        }
        return mask;
    }

    public static int weekdaysMask() {
        // Mon–Fri
        return maskForDays(2, 3, 4, 5, 6);
    }

    public static int everydayMask() {
        return maskForDays(1, 2, 3, 4, 5, 6, 7);
    }

    public static String toJson(List<ScheduleWindow> windows) {
        JSONArray array = new JSONArray();
        try {
            for (ScheduleWindow w : windows) {
                JSONObject o = new JSONObject();
                o.put("id", w.id);
                o.put("name", w.name);
                o.put("enabled", w.enabled);
                o.put("daysMask", w.daysMask);
                o.put("start", w.startMinutes);
                o.put("end", w.endMinutes);
                o.put("mode", w.mode.toPrefsValue());
                JSONArray cats = new JSONArray();
                for (AppCategory c : w.categories) {
                    cats.put(c.toPrefsValue());
                }
                o.put("categories", cats);
                array.put(o);
            }
        } catch (Exception ignored) {
        }
        return array.toString();
    }

    public static List<ScheduleWindow> fromJson(String json) {
        List<ScheduleWindow> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Set<AppCategory> cats = EnumSet.noneOf(AppCategory.class);
                JSONArray catsJson = o.optJSONArray("categories");
                if (catsJson != null) {
                    for (int j = 0; j < catsJson.length(); j++) {
                        cats.add(AppCategory.fromPrefsValue(catsJson.optString(j)));
                    }
                }
                list.add(new ScheduleWindow(
                        o.optString("id", "w" + i),
                        o.optString("name", String.format(Locale.US, "Window %d", i + 1)),
                        o.optBoolean("enabled", true),
                        o.optInt("daysMask", everydayMask()),
                        o.optInt("start", 0),
                        o.optInt("end", 0),
                        Mode.fromPrefsValue(o.optString("mode", Mode.ALLOW_CATEGORIES.toPrefsValue())),
                        cats
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
