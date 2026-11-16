package io.github.appstudio42.kidshome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opens an installed WebAPK inside {@link SiteActivity} instead of the shell
 * that delegates to Chrome.
 *
 * <p>The kid-launcher tile still comes from the allowlist. The tap does not
 * start {@code org.chromium.webapk.*}, so Chrome never comes to the foreground
 * and does not need to be allowlisted. {@link SiteActivity} is this app, which
 * {@link AccServ} already allows. The shell package does not have to stay on
 * the allowlist for that session to continue.
 *
 * <p>If the shell does reach the foreground and the parent allowlisted it,
 * {@link #consumeShell} replaces it with {@link SiteActivity} instead of
 * leaving it to open Chrome and instead of sending the child home. A shell
 * the parent did not allow is left to the normal block. When no https URL can
 * be read, the caller launches the package as usual.
 */
public final class WebApkLaunch {
    private static final long HANDOFF_DEBOUNCE_MS = 2000L;
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static volatile String lastHandoffPackage = "";
    private static volatile long lastHandoffElapsedMs;

    private WebApkLaunch() {
    }

    /**
     * Starts {@link SiteActivity} when {@code packageName} is a WebAPK with an
     * https URL. Returns false when the caller should launch the package itself.
     */
    public static boolean open(Context context, String packageName) {
        Resolved resolved = resolve(context, packageName);
        if (resolved == null) {
            return false;
        }
        start(context, packageName, resolved, false);
        return true;
    }

    /**
     * Accessibility-service handoff. Returns true when this foreground package
     * was consumed (site opened, or a handoff is already in flight) and the
     * caller must not redirect home.
     */
    public static boolean consumeShell(Context context, String packageName) {
        if (!WebApkUrls.isWebApkPackage(packageName) || context == null) {
            return false;
        }
        boolean kidMode = Prefs.isKidModeEnabled(context);
        boolean escape = KidModeGate.isEscapeUiActive();
        boolean blocked = ScreenTimeTracker.isBlocked(context);
        if (!kidMode || escape || blocked) {
            return false;
        }
        // A handoff already replaced this shell. Do not read the package again
        // on every accessibility event, and do not send the child home.
        if (packageName.equals(lastHandoffPackage)
                && SystemClock.elapsedRealtime() - lastHandoffElapsedMs < HANDOFF_DEBOUNCE_MS) {
            return true;
        }
        boolean allowlisted = isListedForKid(context, packageName);
        boolean canLaunch = allowlisted && ScreenTimeTracker.canLaunchApp(context, packageName);
        Resolved resolved = null;
        if (allowlisted && canLaunch) {
            resolved = resolve(context, packageName);
        }
        if (resolved == null || KidModeEnforcement.webApkHandoff(
                true,
                false,
                false,
                true,
                allowlisted,
                canLaunch) != KidModeEnforcement.WebApkHandoff.OPEN_SITE) {
            return false;
        }
        return start(context, packageName, resolved, true);
    }

    private static boolean isListedForKid(Context context, String packageName) {
        if (Prefs.getAllowedPackages(context).contains(packageName)) {
            return true;
        }
        return Prefs.isKidLockToApp(context) && packageName.equals(Prefs.getKidAppPackage(context));
    }

    private static boolean start(Context context, String packageName, Resolved resolved, boolean fromService) {
        AccServ.lastApp = context.getPackageName();
        // Daily time is counted because SiteActivity ticks. Per-app time follows
        // this package even though the window itself is KidsHome, so a limit the
        // parent set on the WebAPK still fills up. AccServ sees KidsHome.
        Prefs.setForegroundTrackPackage(context, packageName);
        Intent intent = SiteActivity.webApkIntent(
                context,
                packageName,
                resolved.label,
                resolved.target.startUrl,
                resolved.target.scopeUrl);
        if (context instanceof Activity) {
            ((Activity) context).startActivity(intent);
            return true;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        boolean started = BackgroundActivityStarter.launch(
                context, intent, BackgroundActivityStarter.REQUEST_SITE);
        if (started && fromService) {
            lastHandoffPackage = packageName;
            lastHandoffElapsedMs = SystemClock.elapsedRealtime();
        }
        return started;
    }

    private static Resolved resolve(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info;
        try {
            info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        Bundle meta = info.metaData;
        boolean hasStart = meta != null && meta.containsKey(WebApkUrls.META_START_URL);
        boolean hasScope = meta != null && meta.containsKey(WebApkUrls.META_SCOPE);
        if (!WebApkUrls.isWebApk(packageName, hasStart, hasScope)) {
            return null;
        }
        String start = hasStart ? meta.getString(WebApkUrls.META_START_URL) : null;
        String scope = hasScope ? meta.getString(WebApkUrls.META_SCOPE) : null;
        List<String> filters = WebApkUrls.httpsUrl(start) != null
                ? new ArrayList<String>()
                : httpsFilterUrls(context, packageName);
        WebApkUrls.Target target = WebApkUrls.resolve(
                packageName, hasStart, hasScope, start, scope, filters);
        if (target == null) {
            return null;
        }
        return new Resolved(labelOf(pm, info, packageName), target);
    }

    private static String labelOf(PackageManager pm, ApplicationInfo info, String packageName) {
        CharSequence label = info.loadLabel(pm);
        if (label == null) {
            return packageName;
        }
        String text = label.toString().trim();
        return text.isEmpty() ? packageName : text;
    }

    /**
     * https URLs declared by the package. {@code queryIntentActivities} is
     * first. Chrome puts the https data on a second filter, so the launcher
     * match often has no host; API 31+ declared hosts and, from API 28, the
     * package manifest are the backup. Callers that still get nothing launch
     * the package normally.
     */
    private static List<String> httpsFilterUrls(Context context, String packageName) {
        List<String> urls = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        collectResolved(pm, launcherIntent(packageName), urls);
        collectResolved(pm, viewIntent(packageName), urls);
        if (Build.VERSION.SDK_INT >= 31) {
            Hosts31.collect(context, packageName, urls);
        }
        if (urls.isEmpty() && Build.VERSION.SDK_INT >= 28) {
            Manifest28.collect(context, packageName, urls);
        }
        return urls;
    }

    private static Intent launcherIntent(String packageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);
        return intent;
    }

    private static Intent viewIntent(String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage(packageName);
        return intent;
    }

    @SuppressWarnings("deprecation")
    private static void collectResolved(PackageManager pm, Intent intent, List<String> urls) {
        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, resolveFlags());
        } catch (RuntimeException e) {
            return;
        }
        if (resolved == null) {
            return;
        }
        for (ResolveInfo info : resolved) {
            if (info != null) {
                collectFilter(info.filter, urls);
            }
        }
    }

    private static int resolveFlags() {
        int flags = PackageManager.GET_RESOLVED_FILTER;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= MatchAll23.flag();
        }
        return flags;
    }

    private static void collectFilter(IntentFilter filter, List<String> urls) {
        if (filter == null) {
            return;
        }
        boolean https = false;
        Iterator<String> schemes = filter.schemesIterator();
        if (schemes != null) {
            while (schemes.hasNext()) {
                if ("https".equalsIgnoreCase(schemes.next())) {
                    https = true;
                    break;
                }
            }
        }
        if (!https) {
            return;
        }
        String path = firstConcretePath(filter);
        int count = filter.countDataAuthorities();
        for (int i = 0; i < count; i++) {
            IntentFilter.AuthorityEntry authority = filter.getDataAuthority(i);
            if (authority == null) {
                continue;
            }
            addUrl(urls, WebApkUrls.httpsUrlFromFilter(authority.getHost(), path));
        }
    }

    private static String firstConcretePath(IntentFilter filter) {
        int count = filter.countDataPaths();
        for (int i = 0; i < count; i++) {
            PatternMatcher matcher = filter.getDataPath(i);
            if (matcher == null) {
                continue;
            }
            int type = matcher.getType();
            if (type != PatternMatcher.PATTERN_LITERAL && type != PatternMatcher.PATTERN_PREFIX) {
                continue;
            }
            String path = matcher.getPath();
            if (path != null && path.indexOf('*') < 0) {
                return path;
            }
        }
        return "/";
    }

    private static void addUrl(List<String> urls, String url) {
        if (url != null && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private static final class Resolved {
        final String label;
        final WebApkUrls.Target target;

        Resolved(String label, WebApkUrls.Target target) {
            this.label = label;
            this.target = target;
        }
    }

    @RequiresApi(23)
    static final class MatchAll23 {
        private MatchAll23() {
        }

        static int flag() {
            return PackageManager.MATCH_ALL;
        }
    }

    @RequiresApi(31)
    static final class Hosts31 {
        private Hosts31() {
        }

        static void collect(Context context, String packageName, List<String> urls) {
            android.content.pm.verify.domain.DomainVerificationManager manager =
                    context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager.class);
            if (manager == null) {
                return;
            }
            try {
                android.content.pm.verify.domain.DomainVerificationUserState state =
                        manager.getDomainVerificationUserState(packageName);
                if (state == null || state.getHostToStateMap() == null) {
                    return;
                }
                for (String host : state.getHostToStateMap().keySet()) {
                    addUrl(urls, WebApkUrls.httpsUrlFromFilter(host, "/"));
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // The shell was removed between the allowlist check and this read.
            }
        }
    }

    /**
     * Reads {@code <data android:scheme="https" android:host="...">} from the
     * installed manifest. Chrome's https filter is not the one
     * {@code queryIntentActivities} attaches to the launcher intent.
     */
    @RequiresApi(28)
    static final class Manifest28 {
        private Manifest28() {
        }

        static void collect(Context context, String packageName, List<String> urls) {
            XmlResourceParser parser = null;
            try {
                Context other = context.createPackageContext(packageName, 0);
                parser = other.getAssets().openXmlResourceParser("AndroidManifest.xml");
                boolean inFilter = false;
                String scheme = null;
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("intent-filter".equals(name)) {
                            inFilter = true;
                            scheme = null;
                        } else if (inFilter && "data".equals(name)) {
                            String tagScheme = attr(parser, "scheme");
                            if (tagScheme != null) {
                                scheme = tagScheme;
                            }
                            if ("https".equalsIgnoreCase(scheme)) {
                                String path = attr(parser, "pathPrefix");
                                if (path == null) {
                                    path = attr(parser, "path");
                                }
                                addUrl(urls, WebApkUrls.httpsUrlFromFilter(attr(parser, "host"), path));
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG && "intent-filter".equals(parser.getName())) {
                        inFilter = false;
                        scheme = null;
                    }
                    event = parser.next();
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Package disappeared.
            } catch (IOException ignored) {
                // Manifest is not readable. The caller opens the package normally.
            } catch (XmlPullParserException ignored) {
                // Same as a missing filter.
            } catch (RuntimeException ignored) {
                // A broken binary manifest must not take down the launcher.
            } finally {
                if (parser != null) {
                    parser.close();
                }
            }
        }

        private static String attr(XmlResourceParser parser, String localName) {
            String value = parser.getAttributeValue(ANDROID_NS, localName);
            if (value != null) {
                return value;
            }
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String name = parser.getAttributeName(i);
                if (localName.equals(name) || (name != null && name.endsWith(":" + localName))) {
                    return parser.getAttributeValue(i);
                }
            }
            return null;
        }
    }
}
