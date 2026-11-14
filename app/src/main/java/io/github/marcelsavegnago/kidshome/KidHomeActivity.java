package io.github.marcelsavegnago.kidshome;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.OvershootInterpolator;
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
    private TextView starsView;
    private TextView scheduleBanner;
    private TextView avatar;
    private RemainingTimeRing timeRing;
    private FrameLayout celebrationOverlay;
    private TextView celebrationMessage;
    private TextView celebrationStar;
    private KidHomeAdapter adapter;
    private ToneGenerator toneGenerator;
    private boolean entryAnimated;
    private final Runnable refreshTime = new Runnable() {
        @Override
        public void run() {
            refreshHeader();
            refreshGrid();
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

        // Kid home is the root. Back must not reveal the previous task.
        LockBack.consume(this, getOnBackPressedDispatcher());

        setContentView(R.layout.activity_kid_home);
        ImmersiveHelper.enter(this);
        ImmersiveHelper.applyContentInsets(findViewById(R.id.kidHomeContent));

        FrameLayout root = findViewById(R.id.kidHomeRoot);
        root.setBackground(loadWallpaperDrawable());

        greeting = findViewById(R.id.kidHomeGreeting);
        timeLeft = findViewById(R.id.kidHomeTimeLeft);
        starsView = findViewById(R.id.kidHomeStars);
        scheduleBanner = findViewById(R.id.kidHomeScheduleBanner);
        avatar = findViewById(R.id.kidHomeAvatar);
        timeRing = findViewById(R.id.kidHomeTimeRing);
        celebrationOverlay = findViewById(R.id.kidCelebrationOverlay);
        celebrationMessage = findViewById(R.id.kidCelebrationMessage);
        celebrationStar = findViewById(R.id.kidCelebrationStar);
        celebrationOverlay.setOnClickListener(v -> hideCelebration());
        GridView grid = findViewById(R.id.kidHomeGrid);
        adapter = new KidHomeAdapter(this);
        applyAdaptiveColumns(grid);
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

    /** 4 columns on wide landscape (Fire 7 / tablets), 3 on portrait/phone, 2 on narrow phones. */
    private void applyAdaptiveColumns(GridView grid) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int columns;
        if (landscape && widthDp >= 560f) {
            columns = 4;
        } else if (widthDp >= 360f) {
            columns = 3;
        } else {
            columns = 2;
        }
        grid.setNumColumns(columns);
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
        maybeShowCelebration();
        maybeAnimateEntry();
        handler.removeCallbacks(refreshTime);
        handler.post(refreshTime);
        ScreenTimeTracker.tick(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isFinishing() && Prefs.isKidModeEnabled(this)) {
            ImmersiveHelper.enter(this);
        }
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
        int stars = Prefs.getStarsTotal(this);
        starsView.setText(getString(R.string.stars_counter, stars));
        starsView.setContentDescription(getString(R.string.cd_stars, stars));
        updateScheduleBanner();
        updateTimeRing();
    }

    private void updateScheduleBanner() {
        RuleEngine.ScheduleDecision decision = Prefs.currentScheduleDecision(this);
        if (decision.mode == RuleEngine.AccessMode.CATEGORY_FILTER && decision.window != null) {
            scheduleBanner.setVisibility(View.VISIBLE);
            scheduleBanner.setText(getString(R.string.schedule_active_banner, decision.window.name));
        } else {
            scheduleBanner.setVisibility(View.GONE);
        }
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
        RuleEngine.ScheduleDecision schedule = Prefs.currentScheduleDecision(this);
        for (String pkg : allowed) {
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            AppCategory category = Prefs.getAppCategory(this, pkg);
            if (!RuleEngine.isAllowedBySchedule(schedule, category)) {
                // Hidden during category filter windows (not shown greyed — fully hidden).
                continue;
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence label = info.loadLabel(pm);
                boolean locked = Prefs.isAppLimitReached(this, pkg);
                String timeLabel = ScreenTimeTracker.formatAppRemainingShort(this, pkg);
                items.add(KidHomeItem.app(
                        pkg,
                        label != null ? label.toString() : pkg,
                        locked,
                        timeLabel
                ));
            } catch (Exception ignored) {
            }
        }
        // Sites stay available outside bedtime (bedtime uses the lock screen).
        if (schedule.mode != RuleEngine.AccessMode.BEDTIME) {
            for (SiteShortcut site : Prefs.getSiteShortcuts(this)) {
                if (site.url != null && !site.url.isEmpty()) {
                    items.add(KidHomeItem.site(site));
                }
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
            if (item.locked || !ScreenTimeTracker.canLaunchApp(this, item.packageName)) {
                Toast.makeText(this, R.string.app_limit_reached_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent launch = getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (launch == null) {
                Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.setForegroundTrackPackage(this, item.packageName);
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
            if (!ScreenTimeTracker.canLaunchApp(this, browserPkg)) {
                Toast.makeText(this, R.string.app_limit_reached_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            if (browserAct != null && !browserAct.isEmpty()) {
                view.setComponent(new ComponentName(browserPkg, browserAct));
            } else {
                view.setPackage(browserPkg);
            }
            Prefs.setForegroundTrackPackage(this, browserPkg);
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

    private void maybeShowCelebration() {
        int minutes = Prefs.consumePendingCelebration(this);
        if (minutes <= 0 || celebrationOverlay == null) {
            return;
        }
        celebrationMessage.setText(getString(R.string.earn_celebration_minutes, minutes));
        celebrationOverlay.setVisibility(View.VISIBLE);
        celebrationOverlay.setAlpha(0f);
        celebrationOverlay.animate().alpha(1f).setDuration(250L).start();
        if (celebrationStar != null && MotionHelper.animationsEnabled(this)) {
            celebrationStar.setScaleX(0.2f);
            celebrationStar.setScaleY(0.2f);
            celebrationStar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500L)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        }
        handler.postDelayed(this::hideCelebration, 2800L);
        refreshHeader();
    }

    private void hideCelebration() {
        if (celebrationOverlay == null || celebrationOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        celebrationOverlay.animate().alpha(0f).setDuration(200L).withEndAction(() -> {
            celebrationOverlay.setVisibility(View.GONE);
            celebrationOverlay.setAlpha(1f);
        }).start();
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
