package io.github.marcelsavegnago.kidshome;

public final class KidHomeItem {
    public enum Type { APP, SITE }

    public final Type type;
    public final String packageName;
    public final String label;
    public final String url;
    public final String siteId;

    private KidHomeItem(Type type, String packageName, String label, String url, String siteId) {
        this.type = type;
        this.packageName = packageName;
        this.label = label;
        this.url = url;
        this.siteId = siteId;
    }

    public static KidHomeItem app(String packageName, String label) {
        return new KidHomeItem(Type.APP, packageName, label, null, null);
    }

    public static KidHomeItem site(SiteShortcut shortcut) {
        return new KidHomeItem(Type.SITE, null, shortcut.name, shortcut.url, shortcut.id);
    }
}
