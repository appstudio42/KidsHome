package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure helpers for weekly usage history (about 4 weeks retained).
 */
public final class UsageHistory {
    public static final int RETAIN_DAYS = 28;

    private UsageHistory() {
    }

    public static final class DayUsage {
        public final String dayKey;
        public final Map<String, Long> appMillis;
        public final long totalMillis;
        public final int starsEarned;

        public DayUsage(String dayKey, Map<String, Long> appMillis, long totalMillis, int starsEarned) {
            this.dayKey = dayKey;
            this.appMillis = appMillis == null
                    ? Collections.<String, Long>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(appMillis));
            this.totalMillis = Math.max(0L, totalMillis);
            this.starsEarned = Math.max(0, starsEarned);
        }
    }

    public static Map<String, DayUsage> fromJson(String json) {
        Map<String, DayUsage> map = new TreeMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject days = root.optJSONObject("days");
            if (days == null) {
                return map;
            }
            Iterator<String> keys = days.keys();
            while (keys.hasNext()) {
                String day = keys.next();
                JSONObject o = days.optJSONObject(day);
                if (o == null) {
                    continue;
                }
                Map<String, Long> apps = new LinkedHashMap<>();
                JSONObject appsJson = o.optJSONObject("apps");
                if (appsJson != null) {
                    Iterator<String> appKeys = appsJson.keys();
                    while (appKeys.hasNext()) {
                        String pkg = appKeys.next();
                        apps.put(pkg, Math.max(0L, appsJson.optLong(pkg, 0L)));
                    }
                }
                map.put(day, new DayUsage(
                        day,
                        apps,
                        o.optLong("total", 0L),
                        o.optInt("stars", 0)
                ));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static String toJson(Map<String, DayUsage> days) {
        JSONObject root = new JSONObject();
        try {
            JSONObject daysJson = new JSONObject();
            if (days != null) {
                for (Map.Entry<String, DayUsage> entry : days.entrySet()) {
                    DayUsage day = entry.getValue();
                    JSONObject o = new JSONObject();
                    JSONObject apps = new JSONObject();
                    for (Map.Entry<String, Long> app : day.appMillis.entrySet()) {
                        apps.put(app.getKey(), app.getValue());
                    }
                    o.put("apps", apps);
                    o.put("total", day.totalMillis);
                    o.put("stars", day.starsEarned);
                    daysJson.put(entry.getKey(), o);
                }
            }
            root.put("days", daysJson);
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    /** Drop entries older than {@link #RETAIN_DAYS} relative to {@code todayKey} (yyyyMMdd). */
    public static Map<String, DayUsage> prune(Map<String, DayUsage> days, String todayKey) {
        if (days == null || days.isEmpty()) {
            return new TreeMap<>();
        }
        Map<String, DayUsage> pruned = new TreeMap<>();
        List<String> keys = new ArrayList<>(days.keySet());
        Collections.sort(keys);
        // Keep the newest RETAIN_DAYS keys, preferring those <= today.
        int start = Math.max(0, keys.size() - RETAIN_DAYS);
        for (int i = start; i < keys.size(); i++) {
            String key = keys.get(i);
            DayUsage day = days.get(key);
            if (day != null) {
                pruned.put(key, day);
            }
        }
        // Also drop future keys beyond today if clock jumped backwards mid-prune.
        if (todayKey != null && !todayKey.isEmpty()) {
            Iterator<Map.Entry<String, DayUsage>> it = pruned.entrySet().iterator();
            int count = 0;
            List<String> ordered = new ArrayList<>(pruned.keySet());
            Collections.sort(ordered);
            // Prefer last RETAIN_DAYS ending at today or latest.
            if (ordered.size() > RETAIN_DAYS) {
                Map<String, DayUsage> trimmed = new TreeMap<>();
                for (int i = ordered.size() - RETAIN_DAYS; i < ordered.size(); i++) {
                    trimmed.put(ordered.get(i), pruned.get(ordered.get(i)));
                }
                return trimmed;
            }
        }
        return pruned;
    }

    public static DayUsage upsertAppMillis(DayUsage existing, String dayKey, String packageName, long addMillis) {
        Map<String, Long> apps = new LinkedHashMap<>();
        long total = 0L;
        int stars = 0;
        if (existing != null) {
            apps.putAll(existing.appMillis);
            total = existing.totalMillis;
            stars = existing.starsEarned;
        }
        if (packageName != null && !packageName.isEmpty() && addMillis > 0L) {
            long prev = apps.containsKey(packageName) ? apps.get(packageName) : 0L;
            apps.put(packageName, prev + addMillis);
            total += addMillis;
        }
        return new DayUsage(dayKey, apps, total, stars);
    }

    public static DayUsage withStars(DayUsage existing, String dayKey, int starsEarned) {
        Map<String, Long> apps = existing == null
                ? new LinkedHashMap<String, Long>()
                : new LinkedHashMap<>(existing.appMillis);
        long total = existing == null ? 0L : existing.totalMillis;
        return new DayUsage(dayKey, apps, total, starsEarned);
    }

    /** Days of the current ISO-ish week containing {@code todayKey}, oldest first (Mon–Sun-ish by date). */
    public static List<DayUsage> weekDays(Map<String, DayUsage> all, String todayKey) {
        List<DayUsage> result = new ArrayList<>();
        if (all == null || todayKey == null || todayKey.length() != 8) {
            return result;
        }
        // Walk back up to 6 days and include today.
        List<String> keys = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            String key = shiftDayKey(todayKey, -offset);
            if (key != null) {
                keys.add(key);
            }
        }
        for (String key : keys) {
            DayUsage day = all.get(key);
            result.add(day != null ? day : new DayUsage(key, Collections.<String, Long>emptyMap(), 0L, 0));
        }
        return result;
    }

    public static String shiftDayKey(String dayKey, int deltaDays) {
        if (dayKey == null || dayKey.length() != 8) {
            return dayKey;
        }
        try {
            int y = Integer.parseInt(dayKey.substring(0, 4));
            int m = Integer.parseInt(dayKey.substring(4, 6));
            int d = Integer.parseInt(dayKey.substring(6, 8));
            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault(), Locale.US);
            cal.clear();
            cal.set(y, m - 1, d);
            cal.add(java.util.Calendar.DAY_OF_MONTH, deltaDays);
            return String.format(Locale.US, "%04d%02d%02d",
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH));
        } catch (Exception e) {
            return dayKey;
        }
    }

    public static String toCsv(Map<String, DayUsage> days) {
        StringBuilder sb = new StringBuilder();
        sb.append("day,package,millis,stars_day,total_day\n");
        if (days == null) {
            return sb.toString();
        }
        List<String> keys = new ArrayList<>(days.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            DayUsage day = days.get(key);
            if (day == null) {
                continue;
            }
            if (day.appMillis.isEmpty()) {
                sb.append(key).append(",,").append(0).append(',')
                        .append(day.starsEarned).append(',').append(day.totalMillis).append('\n');
                continue;
            }
            for (Map.Entry<String, Long> app : day.appMillis.entrySet()) {
                sb.append(key).append(',')
                        .append(escapeCsv(app.getKey())).append(',')
                        .append(app.getValue()).append(',')
                        .append(day.starsEarned).append(',')
                        .append(day.totalMillis).append('\n');
            }
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** Aggregate app totals for a list of days. */
    public static Map<String, Long> aggregateApps(List<DayUsage> days) {
        Map<String, Long> totals = new LinkedHashMap<>();
        if (days == null) {
            return totals;
        }
        for (DayUsage day : days) {
            for (Map.Entry<String, Long> e : day.appMillis.entrySet()) {
                long prev = totals.containsKey(e.getKey()) ? totals.get(e.getKey()) : 0L;
                totals.put(e.getKey(), prev + e.getValue());
            }
        }
        return totals;
    }
}
