package io.github.marcelsavegnago.kidshome;

/**
 * Parent-assigned app categories for schedule rules and limits.
 */
public enum AppCategory {
    GAMES("games"),
    EDUCATION("education"),
    VIDEO("video"),
    OTHER("other");

    private final String prefsValue;

    AppCategory(String prefsValue) {
        this.prefsValue = prefsValue;
    }

    public String toPrefsValue() {
        return prefsValue;
    }

    public static AppCategory fromPrefsValue(String value) {
        if (value == null || value.isEmpty()) {
            return OTHER;
        }
        for (AppCategory category : values()) {
            if (category.prefsValue.equalsIgnoreCase(value)) {
                return category;
            }
        }
        return OTHER;
    }

    /** String resource name for UI labels (resolved by activities). */
    public int labelResId() {
        switch (this) {
            case GAMES:
                return R.string.category_games;
            case EDUCATION:
                return R.string.category_education;
            case VIDEO:
                return R.string.category_video;
            case OTHER:
            default:
                return R.string.category_other;
        }
    }
}
