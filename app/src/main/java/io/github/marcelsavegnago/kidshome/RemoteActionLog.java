package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Local log of parent-remote actions for the weekly report (no cloud).
 */
public final class RemoteActionLog {
    public static final String SOURCE_LABEL = "parent remote";
    public static final String SOURCE_LABEL_PT = "controle remoto";

    public static final String ACTION_ADD_TIME = "add_time";
    public static final String ACTION_LOCK = "lock";
    public static final String ACTION_UNLOCK = "unlock";

    public static final class Entry {
        public final long timestampMs;
        public final String dayKey;
        public final String action;
        public final int minutes;

        public Entry(long timestampMs, String dayKey, String action, int minutes) {
            this.timestampMs = timestampMs;
            this.dayKey = dayKey == null ? "" : dayKey;
            this.action = action == null ? "" : action;
            this.minutes = Math.max(0, minutes);
        }
    }

    private RemoteActionLog() {
    }

    public static List<Entry> fromJson(String json) {
        List<Entry> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("entries");
            if (arr == null) {
                return list;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                list.add(new Entry(
                        o.optLong("ts", 0L),
                        o.optString("day", ""),
                        o.optString("action", ""),
                        o.optInt("minutes", 0)
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static String toJson(List<Entry> entries) {
        JSONObject root = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            if (entries != null) {
                for (Entry e : entries) {
                    JSONObject o = new JSONObject();
                    o.put("ts", e.timestampMs);
                    o.put("day", e.dayKey);
                    o.put("action", e.action);
                    o.put("minutes", e.minutes);
                    arr.put(o);
                }
            }
            root.put("entries", arr);
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    public static List<Entry> append(List<Entry> existing, Entry entry, int retainDays) {
        List<Entry> next = new ArrayList<>();
        if (existing != null) {
            next.addAll(existing);
        }
        if (entry != null) {
            next.add(entry);
        }
        return prune(next, entry != null ? entry.dayKey : ScreenTimeState.dayKeyNow(), retainDays);
    }

    public static List<Entry> prune(List<Entry> entries, String todayKey, int retainDays) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        int keep = Math.max(1, retainDays);
        String cutoff = UsageHistory.shiftDayKey(todayKey, -(keep - 1));
        List<Entry> pruned = new ArrayList<>();
        for (Entry e : entries) {
            if (e.dayKey == null || e.dayKey.isEmpty()) {
                continue;
            }
            if (cutoff == null || e.dayKey.compareTo(cutoff) >= 0) {
                pruned.add(e);
            }
        }
        // Soft cap to avoid unbounded growth within the window.
        if (pruned.size() > 500) {
            return new ArrayList<>(pruned.subList(pruned.size() - 500, pruned.size()));
        }
        return pruned;
    }

    public static List<Entry> forDay(List<Entry> entries, String dayKey) {
        if (entries == null || dayKey == null) {
            return Collections.emptyList();
        }
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries) {
            if (dayKey.equals(e.dayKey)) {
                result.add(e);
            }
        }
        return result;
    }

    public static List<Entry> forWeek(List<Entry> entries, String todayKey) {
        if (entries == null || todayKey == null) {
            return Collections.emptyList();
        }
        List<Entry> result = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            String key = UsageHistory.shiftDayKey(todayKey, -offset);
            result.addAll(forDay(entries, key));
        }
        return result;
    }

    public static String formatLabel(Entry entry, boolean portuguese) {
        if (entry == null) {
            return "";
        }
        String source = portuguese ? SOURCE_LABEL_PT : SOURCE_LABEL;
        if (ACTION_ADD_TIME.equals(entry.action)) {
            return String.format(Locale.getDefault(), "%s · +%d min", source, entry.minutes);
        }
        if (ACTION_LOCK.equals(entry.action)) {
            return portuguese ? source + " · bloquear" : source + " · lock";
        }
        if (ACTION_UNLOCK.equals(entry.action)) {
            return portuguese ? source + " · desbloquear" : source + " · unlock";
        }
        return source;
    }
}
