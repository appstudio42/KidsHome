package io.github.marcelsavegnago.kidshome;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Simple kid launcher: large icons, allowed apps + site shortcuts, no app drawer.
 */
public class KidHomeActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView greeting;
    private TextView timeLeft;
    private KidHomeAdapter adapter;
    private final Runnable refreshTime = new Runnable() {
        @Override
        public void run() {
            if (timeLeft != null) {
                timeLeft.setText(ScreenTimeTracker.formatRemaining(KidHomeActivity.this));
            }
            if (ScreenTimeTracker.isBlocked(KidHomeActivity.this)) {
                ScreenTimeLockActivity.launch(KidHomeActivity.this);
            }
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // KidHome is also registered as CATEGORY_HOME. When kid mode is off, open parent UI.
        if (!Prefs.isKidModeEnabled(this)) {
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(main);
            finish();
            return;
        }

        setContentView(R.layout.activity_kid_home);

        FrameLayout root = findViewById(R.id.kidHomeRoot);
        root.setBackground(loadWallpaperDrawable());

        greeting = findViewById(R.id.kidHomeGreeting);
        timeLeft = findViewById(R.id.kidHomeTimeLeft);
        GridView grid = findViewById(R.id.kidHomeGrid);
        adapter = new KidHomeAdapter(this);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> onItemClicked(adapter.getItem(position)));

        Prefs.ensureDefaultSiteSuggestions(this);
        refreshHeader();
        refreshGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.isKidModeEnabled(this)) {
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(main);
            finish();
            return;
        }
        if (ScreenTimeTracker.isBlocked(this)) {
            ScreenTimeLockActivity.launch(this);
            return;
        }
        DeviceOwnerHelper.startLockTaskIfNeeded(this);
        refreshHeader();
        refreshGrid();
        handler.removeCallbacks(refreshTime);
        handler.post(refreshTime);
        ScreenTimeTracker.tick(this);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTime);
        Prefs.flushScreenTime(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Kid home is the root — ignore Back (escape is hold-Back / corner taps).
    }

    private void refreshHeader() {
        String name = Prefs.getChildName(this);
        if (name == null || name.trim().isEmpty()) {
            greeting.setText(R.string.kid_home_hello);
        } else {
            greeting.setText(getString(R.string.kid_home_hello_name, name.trim()));
        }
        timeLeft.setText(ScreenTimeTracker.formatRemaining(this));
    }

    private void refreshGrid() {
        List<KidHomeItem> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> allowed = Prefs.getAllowedPackages(this);
        String browser = Prefs.getBrowserPackage(this);
        for (String pkg : allowed) {
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            // Browser still allowed for sites, but only show once if also an allowed app.
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence label = info.loadLabel(pm);
                items.add(KidHomeItem.app(pkg, label != null ? label.toString() : pkg));
            } catch (Exception ignored) {
            }
        }
        for (SiteShortcut site : Prefs.getSiteShortcuts(this)) {
            if (site.url != null && !site.url.isEmpty()) {
                items.add(KidHomeItem.site(site));
            }
        }
        adapter.setItems(items);
    }

    private void onItemClicked(KidHomeItem item) {
        if (item == null) {
            return;
        }
        if (ScreenTimeTracker.isBlocked(this)) {
            ScreenTimeLockActivity.launch(this);
            return;
        }
        if (item.type == KidHomeItem.Type.APP) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (launch == null) {
                Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return;
        }
        openSite(item.url);
    }

    private void openSite(String url) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String browserPkg = Prefs.getBrowserPackage(this);
        String browserAct = Prefs.getBrowserActivity(this);
        if (browserPkg != null && !browserPkg.isEmpty()) {
            if (browserAct != null && !browserAct.isEmpty()) {
                view.setComponent(new ComponentName(browserPkg, browserAct));
            } else {
                view.setPackage(browserPkg);
            }
        }
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            // Fall back to default handler.
            view.setComponent(null);
            view.setPackage(null);
            try {
                startActivity(view);
            } catch (Exception ex) {
                Toast.makeText(this, R.string.browser_not_found, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static List<ResolveInfo> queryBrowsers(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        return pm.queryIntentActivities(intent, 0);
    }

    /**
     * Best-effort system wallpaper. Some firmwares require storage/wallpaper permissions;
     * we never request those — fall back to a solid brand color.
     */
    @SuppressLint("MissingPermission")
    private Drawable loadWallpaperDrawable() {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Drawable wall = wm.getFastDrawable();
            if (wall == null) {
                wall = wm.getBuiltInDrawable();
            }
            if (wall != null) {
                return wall;
            }
        } catch (Exception ignored) {
        }
        return new android.graphics.drawable.ColorDrawable(0xFF0B6E4F);
    }
}
