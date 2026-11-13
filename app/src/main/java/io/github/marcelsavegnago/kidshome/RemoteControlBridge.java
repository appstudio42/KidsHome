package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Android-backed bridge for {@link RemoteRequestHandler}.
 */
public final class RemoteControlBridge implements RemoteRequestHandler.DeviceBridge {
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());

    public RemoteControlBridge(Context context) {
        this.app = context.getApplicationContext();
    }

    @Override
    public RemoteDeviceState readState() {
        Prefs.ensureScreenTimeDay(app);
        ScreenTimeTracker.BlockReason reason = ScreenTimeTracker.currentBlockReason(app);
        RuleEngine.ScheduleDecision schedule = Prefs.currentScheduleDecision(app);
        boolean scheduleActive = Prefs.isSchedulesEnabled(app)
                && schedule != null
                && schedule.window != null;

        long used = Prefs.getUsedMillisToday(app);
        long bonus = Prefs.getBonusMillisToday(app);
        int baseLimit = Prefs.getDailyLimitMinutes(app);
        long bonusMin = RemoteTimeMath.millisToWholeMinutes(bonus);
        int effectiveLimit = RemoteTimeMath.effectiveLimitMinutes(baseLimit, bonusMin);
        long remainingMin;
        if (!Prefs.isScreenTimeEnabled(app) || Prefs.isScreenTimeDisabledToday(app)) {
            remainingMin = Long.MAX_VALUE;
        } else {
            remainingMin = RemoteTimeMath.remainingMinutes(used, bonus, baseLimit);
        }

        Map<String, Long> usedMap = Prefs.getAppUsedMillisToday(app);
        Map<String, Integer> limits = Prefs.getAppLimits(app);
        Map<String, String> labels = new HashMap<>();
        PackageManager pm = app.getPackageManager();
        Set<String> allowed = Prefs.getAllowedPackages(app);
        List<String> pkgs = new ArrayList<>(usedMap.keySet());
        for (String pkg : allowed) {
            if (!pkgs.contains(pkg)) {
                pkgs.add(pkg);
            }
        }
        Map<String, Long> filteredUsed = new HashMap<>();
        for (String pkg : pkgs) {
            Long millis = usedMap.get(pkg);
            filteredUsed.put(pkg, millis == null ? 0L : millis);
            labels.put(pkg, labelFor(pm, pkg));
        }

        String reasonKey = "none";
        if (reason == ScreenTimeTracker.BlockReason.REMOTE) {
            reasonKey = "remote";
        } else if (reason == ScreenTimeTracker.BlockReason.BEDTIME) {
            reasonKey = "bedtime";
        } else if (reason == ScreenTimeTracker.BlockReason.DAILY_LIMIT) {
            reasonKey = "daily_limit";
        }

        return new RemoteDeviceState(
                Prefs.isKidModeEnabled(app),
                reason != ScreenTimeTracker.BlockReason.NONE,
                Prefs.isRemoteForceLocked(app),
                scheduleActive,
                reasonKey,
                Prefs.isScreenTimeEnabled(app) && !Prefs.isScreenTimeDisabledToday(app),
                // Parent page shows today's total allowance (base limit + bonus).
                effectiveLimit,
                RemoteTimeMath.usedMinutes(used),
                bonusMin,
                remainingMin == Long.MAX_VALUE ? -1L : remainingMin,
                Prefs.getStarsTotal(app),
                Prefs.getStarsToday(app),
                Prefs.getChildName(app),
                RemoteDeviceState.appsFromMaps(filteredUsed, limits, labels)
        );
    }

    @Override
    public boolean verifyPin(String pin) {
        PinAttemptTracker.attach(app);
        if (PinAttemptTracker.isLockedOut()) {
            return false;
        }
        if (Prefs.verifyKidPin(app, pin)) {
            PinAttemptTracker.reset();
            return true;
        }
        PinAttemptTracker.registerFailure();
        return false;
    }

    @Override
    public void addBonusMinutes(int minutes) {
        Prefs.addBonusMinutes(app, minutes);
        // If parent adds time while remotely locked, keep remote lock unless they unlock.
        // Adding time should still refresh remaining immediately for daily-limit blocks.
        main.post(() -> {
            if (!ScreenTimeTracker.isBlocked(app)) {
                HomePress.perform(app, true);
            }
        });
    }

    @Override
    public void lockNow() {
        Prefs.setRemoteForceLocked(app, true);
        main.post(() -> ScreenTimeLockActivity.launch(app));
    }

    @Override
    public void unlockNow() {
        Prefs.setRemoteForceLocked(app, false);
        main.post(() -> {
            if (!ScreenTimeTracker.isBlocked(app)) {
                HomePress.perform(app, true);
            } else {
                // Still blocked by bedtime / daily limit — refresh lock UI reason.
                ScreenTimeLockActivity.launch(app);
            }
        });
    }

    @Override
    public void logAction(String action, int minutes) {
        Prefs.appendRemoteAction(app, action, minutes);
    }

    @Override
    public String readAsset(String relativePath) {
        String path = "remote/" + relativePath;
        try (InputStream in = app.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String labelFor(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = info.loadLabel(pm);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }
}
