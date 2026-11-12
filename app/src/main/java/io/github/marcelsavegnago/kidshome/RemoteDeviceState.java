package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of tablet state exposed to the parent remote page.
 */
public final class RemoteDeviceState {
    public final boolean kidMode;
    public final boolean locked;
    public final boolean remoteLocked;
    public final boolean scheduleActive;
    public final String blockReason;
    public final boolean screenTimeEnabled;
    public final int dailyLimitMinutes;
    public final long usedMinutes;
    public final long bonusMinutes;
    public final long remainingMinutes;
    public final int starsTotal;
    public final int starsToday;
    public final String childName;
    public final List<AppUsage> apps;

    public static final class AppUsage {
        public final String packageName;
        public final String label;
        public final long usedMinutes;
        public final int limitMinutes;

        public AppUsage(String packageName, String label, long usedMinutes, int limitMinutes) {
            this.packageName = packageName == null ? "" : packageName;
            this.label = label == null || label.isEmpty() ? this.packageName : label;
            this.usedMinutes = Math.max(0L, usedMinutes);
            this.limitMinutes = limitMinutes;
        }
    }

    public RemoteDeviceState(
            boolean kidMode,
            boolean locked,
            boolean remoteLocked,
            boolean scheduleActive,
            String blockReason,
            boolean screenTimeEnabled,
            int dailyLimitMinutes,
            long usedMinutes,
            long bonusMinutes,
            long remainingMinutes,
            int starsTotal,
            int starsToday,
            String childName,
            List<AppUsage> apps
    ) {
        this.kidMode = kidMode;
        this.locked = locked;
        this.remoteLocked = remoteLocked;
        this.scheduleActive = scheduleActive;
        this.blockReason = blockReason == null ? "none" : blockReason;
        this.screenTimeEnabled = screenTimeEnabled;
        this.dailyLimitMinutes = Math.max(0, dailyLimitMinutes);
        this.usedMinutes = Math.max(0L, usedMinutes);
        this.bonusMinutes = Math.max(0L, bonusMinutes);
        this.remainingMinutes = Math.max(0L, remainingMinutes);
        this.starsTotal = Math.max(0, starsTotal);
        this.starsToday = Math.max(0, starsToday);
        this.childName = childName == null ? "" : childName;
        this.apps = apps == null
                ? new ArrayList<AppUsage>()
                : new ArrayList<>(apps);
    }

    public String toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("kidMode", kidMode);
            root.put("locked", locked);
            root.put("remoteLocked", remoteLocked);
            root.put("scheduleActive", scheduleActive);
            root.put("blockReason", blockReason);
            root.put("screenTimeEnabled", screenTimeEnabled);
            root.put("dailyLimitMinutes", dailyLimitMinutes);
            root.put("usedMinutes", usedMinutes);
            root.put("bonusMinutes", bonusMinutes);
            root.put("remainingMinutes", remainingMinutes);
            root.put("starsTotal", starsTotal);
            root.put("starsToday", starsToday);
            root.put("childName", childName);
            JSONArray arr = new JSONArray();
            for (AppUsage app : apps) {
                JSONObject o = new JSONObject();
                o.put("packageName", app.packageName);
                o.put("label", app.label);
                o.put("usedMinutes", app.usedMinutes);
                o.put("limitMinutes", app.limitMinutes);
                arr.put(o);
            }
            root.put("apps", arr);
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    public static List<AppUsage> appsFromMaps(
            Map<String, Long> usedMillis,
            Map<String, Integer> limits,
            Map<String, String> labels
    ) {
        List<AppUsage> list = new ArrayList<>();
        if (usedMillis == null) {
            return list;
        }
        for (Map.Entry<String, Long> e : usedMillis.entrySet()) {
            String pkg = e.getKey();
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            long usedMin = Math.max(0L, e.getValue() == null ? 0L : e.getValue()) / 60_000L;
            int limit = -1;
            if (limits != null && limits.containsKey(pkg)) {
                Integer v = limits.get(pkg);
                limit = v == null ? -1 : v;
            }
            String label = labels != null && labels.containsKey(pkg) ? labels.get(pkg) : pkg;
            list.add(new AppUsage(pkg, label, usedMin, limit));
        }
        return list;
    }
}
