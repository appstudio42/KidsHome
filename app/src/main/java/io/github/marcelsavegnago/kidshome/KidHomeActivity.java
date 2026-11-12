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
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kid launcher: rounded tiles, friendly header, remaining-time ring, soft motion.
 */
public class KidHomeActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView greeting;
    private TextView timeLeft;
    private TextView avatar;
    private RemainingTimeRing timeRing;
    private KidHomeAdapter adapter;
    private ToneGenerator toneGenerator;
    private boolean entryAnimated;
    private final Runnable refreshTime = new Runnable() {
        @Override
        public void run() {
            refreshHeader();
            if (ScreenTimeTracker.isBlocked(KidHomeActivity.this)) {
                ScreenTimeLockActivity.launch(KidHomeActivity.this);
            }
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Prefs.isKidModeEnabled(this)) {
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(main);
            finish();
            return;
        }

        setContentView(R.layout.activity_kid_home);
        ImmersiveHelper.enter(this);
        ImmersiveHelper.applyContentInsets(findViewById(R.id.kidHomeContent));

        FrameLayout root = findViewById(R.id.kidHomeRoot);
        root.setBackground(loadWallpaperDrawable());

        greeting = findViewById(R.id.kidHomeGreeting);
        timeLeft = findViewById(R.id.kidHomeTimeLeft);
        avatar = findViewById(R.id.kidHomeAvatar);
        timeRing = findViewById(R.id.kidHomeTimeRing);
        GridView grid = findViewById(R.id.kidHomeGrid);
        adapter = new KidHomeAdapter(this);
        // Prefer 3 columns; on very short/wide Fire 7 landscape keep tiles readable.
        grid.setNumColumns(3);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            MotionHelper.bouncePress(view, this);
            playSoftClick();
            onItemClicked(adapter.getItem(position));
        });

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
        ImmersiveHelper.enter(this);
        DeviceOwnerHelper.startLockTaskIfNeeded(this);
        refreshHeader();
        refreshGrid();
        maybeAnimateEntry();
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
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Kid home is the root — ignore Back (escape is hold-Back / corner taps).
    }

    private void maybeAnimateEntry() {
        if (entryAnimated) {
            return;
        }
        entryAnimated = true;
        View header = findViewById(R.id.kidHomeHeader);
        MotionHelper.fadeInUp(header, this);
        GridView grid = findViewById(R.id.kidHomeGrid);
        if (grid == null || !MotionHelper.animationsEnabled(this)) {
            return;
        }
        grid.post(() -> {
            int count = Math.min(grid.getChildCount(), 9);
            for (int i = 0; i < count; i++) {
                MotionHelper.popIn(grid.getChildAt(i), this, i * 40L);
            }
        });
    }

    private void refreshHeader() {
        String name = Prefs.getChildName(this);
        if (name == null || name.trim().isEmpty()) {
            greeting.setText(R.string.kid_home_hello);
        } else {
            greeting.setText(getString(R.string.kid_home_hello_name, name.trim()));
        }
        avatar.setText(Prefs.getChildAvatar(this));
        avatar.setContentDescription(getString(R.string.cd_child_avatar, Prefs.getChildAvatar(this)));
        timeLeft.setText(ScreenTimeTracker.formatRemaining(this));
        updateTimeRing();
    }

    private void updateTimeRing() {
        if (timeRing == null) {
            return;
        }
        if (!Prefs.isScreenTimeEnabled(this) || Prefs.isScreenTimeDisabledToday(this)) {
            timeRing.setProgress(1f);
            timeRing.setCenterLabel("∞");
            timeRing.setContentDescription(getString(R.string.screen_time_off));
            return;
        }
        long limit = Prefs.getDailyLimitMinutes(this) * 60L * 1000L;
        long remaining = ScreenTimeTracker.remainingMillis(this);
        float fraction = limit <= 0L ? 0f : Math.max(0f, Math.min(1f, remaining / (float) limit));
        timeRing.setProgress(fraction);
        long minutes = Math.max(0L, (remaining + 59_999L) / 60_000L);
        String label = minutes >= 60
                ? (minutes / 60) + "h"
                : String.valueOf(minutes);
        timeRing.setCenterLabel(label);
        timeRing.setContentDescription(ScreenTimeTracker.formatRemaining(this));
    }

    private void refreshGrid() {
        List<KidHomeItem> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> allowed = Prefs.getAllowedPackages(this);
        for (String pkg : allowed) {
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
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
            view.setComponent(null);
            view.setPackage(null);
            try {
                startActivity(view);
            } catch (Exception ex) {
                Toast.makeText(this, R.string.browser_not_found, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void playSoftClick() {
        if (!Prefs.isTouchSoundsEnabled(this)) {
            return;
        }
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 40);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 40);
        } catch (Exception ignored) {
        }
    }

    public static List<ResolveInfo> queryBrowsers(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        return pm.queryIntentActivities(intent, 0);
    }

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
        return new android.graphics.drawable.ColorDrawable(0xFF0F766E);
    }
}
