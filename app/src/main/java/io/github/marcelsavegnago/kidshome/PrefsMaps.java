package io.github.marcelsavegnago.kidshome;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Small JSON map helpers for SharedPreferences string values. */
public final class PrefsMaps {
    private PrefsMaps() {
    }

    public static Map<String, Integer> intMapFromJson(String json) {
        Map<String, Integer> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, o.optInt(key, RuleEngine.UNLIMITED_MINUTES));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static String intMapToJson(Map<String, Integer> map) {
        JSONObject o = new JSONObject();
        try {
            if (map != null) {
                for (Map.Entry<String, Integer> e : map.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        o.put(e.getKey(), e.getValue().intValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    public static Map<String, Long> longMapFromJson(String json) {
        Map<String, Long> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, o.optLong(key, 0L));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static String longMapToJson(Map<String, Long> map) {
        JSONObject o = new JSONObject();
        try {
            if (map != null) {
                for (Map.Entry<String, Long> e : map.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        o.put(e.getKey(), e.getValue().longValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    public static Map<String, String> stringMapFromJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, o.optString(key, ""));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static String stringMapToJson(Map<String, String> map) {
        JSONObject o = new JSONObject();
        try {
            if (map != null) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        o.put(e.getKey(), e.getValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    public static Map<String, AppCategory> categoryMapFromJson(String json) {
        Map<String, AppCategory> map = new HashMap<>();
        for (Map.Entry<String, String> e : stringMapFromJson(json).entrySet()) {
            map.put(e.getKey(), AppCategory.fromPrefsValue(e.getValue()));
        }
        return map;
    }

    public static String categoryMapToJson(Map<String, AppCategory> map) {
        Map<String, String> strings = new HashMap<>();
        if (map != null) {
            for (Map.Entry<String, AppCategory> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    strings.put(e.getKey(), e.getValue().toPrefsValue());
                }
            }
        }
        return stringMapToJson(strings);
    }
}
