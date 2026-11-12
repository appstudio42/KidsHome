package io.github.marcelsavegnago.kidshome;

/**
 * Built-in emoji avatar choices for the kid profile. Stored as plain strings in prefs.
 */
public final class ChildAvatars {
    public static final String DEFAULT = "🙂";

    public static final String[] ALL = {
            "🙂", "😀", "😄", "🥰", "😎",
            "🦊", "🐱", "🐶", "🐼", "🦁",
            "🦄", "🐸", "🐰", "🐻", "🐯",
            "🌟", "🚀", "⚽", "🎨", "🎵"
    };

    private ChildAvatars() {
    }

    public static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }
        String trimmed = value.trim();
        for (String avatar : ALL) {
            if (avatar.equals(trimmed)) {
                return avatar;
            }
        }
        // Allow a custom single emoji/grapheme cluster parents may have set.
        return trimmed.length() <= 8 ? trimmed : DEFAULT;
    }
}
