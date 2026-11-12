package io.github.marcelsavegnago.kidshome;

public final class KidHomeItem {
    public enum Type { APP, SITE }

    public final Type type;
    public final String packageName;
    public final String label;
    public final String url;
    public final String siteId;
    /** True when schedule hides the app or its per-app limit is exhausted. */
    public final boolean locked;
    /** Short remaining-time label for apps with a per-app limit (may be empty). */
    public final String timeLabel;

    private KidHomeItem(
            Type type,
            String packageName,
            String label,
            String url,
            String siteId,
            boolean locked,
            String timeLabel
    ) {
        this.type = type;
        this.packageName = packageName;
        this.label = label;
        this.url = url;
        this.siteId = siteId;
        this.locked = locked;
        this.timeLabel = timeLabel == null ? "" : timeLabel;
    }

    public static KidHomeItem app(String packageName, String label) {
        return app(packageName, label, false, "");
    }

    public static KidHomeItem app(String packageName, String label, boolean locked, String timeLabel) {
        return new KidHomeItem(Type.APP, packageName, label, null, null, locked, timeLabel);
    }

    public static KidHomeItem site(SiteShortcut shortcut) {
        return new KidHomeItem(Type.SITE, null, shortcut.name, shortcut.url, shortcut.id, false, "");
    }
}
