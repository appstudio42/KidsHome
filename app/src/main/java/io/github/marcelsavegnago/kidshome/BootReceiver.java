package io.github.marcelsavegnago.kidshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Restarts helper services after boot and resets the chosen launcher if it was uninstalled.
 * Boot work that must outlive {@link #onReceive} uses {@link #goAsync()} and always
 * finishes the pending result, including when a step throws.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "KidsHome.Boot";

    /** Same pause as before, so the home process can settle after boot. */
    static final long LAUNCHER_WARMUP_DELAY_MS = 2500L;

    /**
     * Background broadcasts are treated as hung after about 10 seconds.
     * Finish earlier so a busy main thread still returns the pending result in time.
     */
    static final long BROADCAST_BUDGET_MS = 8000L;

    private static final long BUDGET_MARGIN_MS = 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        final PendingResult pending = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable finish = () -> finishPending(pending, finished);
        // Backstop: never hold the broadcast for the whole system timeout.
        handler.postDelayed(finish, BROADCAST_BUDGET_MS);

        try {
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_BOOT_COMPLETED:
                case Intent.ACTION_MY_PACKAGE_REPLACED:
                    onBootOrUpdate(context, handler, finish);
                    break;

                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                case Intent.ACTION_PACKAGE_REMOVED:
                    onPackageRemoved(context, intent);
                    finish.run();
                    break;

                default:
                    finish.run();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Broadcast handling failed", e);
            finish.run();
        }
    }

    private void onBootOrUpdate(Context context, Handler handler, Runnable finish) {
        try {
            Log.i(TAG, "Boot/update received; starting services");
            Prefs.ensureScreenTimeDay(context);
            Prefs.flushScreenTime(context);
            ServiceMan.start(context);
            StatusNotifier.ensureChannel(context);
            EscapeOverlayNotifier.requestRefresh(context);
            if (Prefs.isRemoteControlEnabled(context)) {
                RemoteControlService.start(context);
            }

            if (EnforcementController.needsAccessibilityService(context)
                    && !AccessibilityHelper.isAccessibilityEnabled(context)) {
                StatusNotifier.notifyAccessibilityDisabled(context);
            }

            DeviceOwnerHelper.requireAutoTime(context);
            if (Prefs.isKidModeEnabled(context) && DeviceOwnerHelper.isDeviceOwner(context)) {
                DeviceOwnerHelper.applyKidModePolicies(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Boot handling failed", e);
        }

        handler.postDelayed(() -> {
            try {
                warmLauncher(context);
            } catch (Exception e) {
                Log.e(TAG, "Launcher warmup failed", e);
            } finally {
                finish.run();
            }
        }, warmupDelayMs());
    }

    private void warmLauncher(Context context) {
        ScreenTimeTracker.tick(context);
        EnforcementMode mode = EnforcementController.getEffectiveMode(context);
        if (mode == EnforcementMode.ACCESSIBILITY
                && (Prefs.hasChosenLauncher(context) || Prefs.isKidModeEnabled(context))) {
            HomePress.perform(context, true);
        } else if (Prefs.isKidModeEnabled(context)) {
            HomePress.perform(context, true);
        }
    }

    private void onPackageRemoved(Context context, Intent intent) {
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        String removed = data.getSchemeSpecificPart();
        if (removed == null) {
            return;
        }

        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (replacing) {
            return;
        }

        String chosen = Prefs.getChosenLauncherPackage(context);
        if (removed.equals(chosen)) {
            Log.w(TAG, "Chosen launcher uninstalled; falling back to KidsHome");
            Prefs.clearChosenLauncher(context);
        }

        String kidApp = Prefs.getKidAppPackage(context);
        if (removed.equals(kidApp)) {
            Log.w(TAG, "Kid app uninstalled; disabling app-lock, keeping kid home if kid mode on");
            Prefs.setKidLockToApp(context, false);
        }

        java.util.Set<String> allowed = Prefs.getAllowedPackages(context);
        if (allowed.remove(removed)) {
            Prefs.setAllowedPackages(context, allowed);
        }
        if (removed.equals(Prefs.getBrowserPackage(context))) {
            Prefs.setBrowser(context, "", "");
        }
    }

    private static void finishPending(PendingResult pending, AtomicBoolean finished) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            pending.finish();
        } catch (RuntimeException e) {
            Log.w(TAG, "PendingResult.finish failed", e);
        }
    }

    /** Warmup delay clamped so the pending result is finished inside the broadcast budget. */
    static long warmupDelayMs() {
        return boundedDelay(LAUNCHER_WARMUP_DELAY_MS, BROADCAST_BUDGET_MS);
    }

    static long boundedDelay(long requestedMs, long budgetMs) {
        if (budgetMs <= BUDGET_MARGIN_MS) {
            return 0L;
        }
        long latest = budgetMs - BUDGET_MARGIN_MS;
        if (requestedMs <= 0L) {
            return 0L;
        }
        return Math.min(requestedMs, latest);
    }
}
