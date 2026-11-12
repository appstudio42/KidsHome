package io.github.marcelsavegnago.kidshome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parent-configured website tile on the kid home grid. */
public final class SiteShortcut {
    public final String id;
    public final String name;
    public final String url;

    public SiteShortcut(String id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }

    public static List<SiteShortcut> defaultSuggestions() {
        List<SiteShortcut> list = new ArrayList<>();
        list.add(new SiteShortcut("poki", "Poki Kids", "https://kids.poki.com/"));
        list.add(new SiteShortcut("pbs", "PBS Kids Games", "https://pbskids.org/games"));
        list.add(new SiteShortcut("coolmath", "Coolmath Games", "https://www.coolmathgames.com/"));
        return list;
    }

    public static String toJson(List<SiteShortcut> shortcuts) {
        JSONArray array = new JSONArray();
        try {
            for (SiteShortcut s : shortcuts) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("url", s.url);
                array.put(o);
            }
        } catch (Exception ignored) {
        }
        return array.toString();
    }

    public static List<SiteShortcut> fromJson(String json) {
        List<SiteShortcut> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                list.add(new SiteShortcut(
                        o.optString("id", "s" + i),
                        o.optString("name", "Site"),
                        o.optString("url", "")
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
