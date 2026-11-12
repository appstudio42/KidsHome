package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parent-defined checklist item that grants bonus minutes when marked done.
 */
public final class EarnTask {
    /** Reward applies to the total daily screen-time pool. */
    public static final String TARGET_TOTAL = "total";

    public final String id;
    public final String title;
    public final int rewardMinutes;
    /** {@link #TARGET_TOTAL} or an app package name. */
    public final String rewardTarget;
    public final boolean enabled;

    public EarnTask(String id, String title, int rewardMinutes, String rewardTarget, boolean enabled) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.title = title == null ? "" : title;
        this.rewardMinutes = Math.max(0, rewardMinutes);
        this.rewardTarget = rewardTarget == null || rewardTarget.isEmpty() ? TARGET_TOTAL : rewardTarget;
        this.enabled = enabled;
    }

    public boolean rewardsTotal() {
        return TARGET_TOTAL.equals(rewardTarget);
    }

    public static String toJson(List<EarnTask> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (EarnTask t : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("title", t.title);
                o.put("minutes", t.rewardMinutes);
                o.put("target", t.rewardTarget);
                o.put("enabled", t.enabled);
                array.put(o);
            }
        } catch (Exception ignored) {
        }
        return array.toString();
    }

    public static List<EarnTask> fromJson(String json) {
        List<EarnTask> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                list.add(new EarnTask(
                        o.optString("id", "t" + i),
                        o.optString("title", "Task"),
                        o.optInt("minutes", 5),
                        o.optString("target", TARGET_TOTAL),
                        o.optBoolean("enabled", true)
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
