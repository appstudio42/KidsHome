package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Friendly full-screen block when daily limit or bedtime is reached.
 */
public class ScreenTimeLockActivity extends AppCompatActivity {
    public static void launch(Context context) {
        if (KidModeGate.isEscapeUiActive() && !KidModeGate.isScreenLockUiActive()) {
            if (KidModeGate.openedAtMs() > 0) {
                return;
            }
        }
        KidModeGate.markScreenLockUiOpen();
        Intent intent = new Intent(context.getApplicationContext(), ScreenTimeLockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        if (!BackgroundActivityStarter.launch(
                context.getApplicationContext(),
                intent,
                BackgroundActivityStarter.REQUEST_SCREEN_LOCK)) {
            KidModeGate.markScreenLockUiClosed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KidModeGate.markScreenLockUiOpen();
        // Stay on the lock screen until a parent PIN unlocks it.
        LockBack.consume(this, getOnBackPressedDispatcher());
        // Do not keep the screen on — overnight lock must follow the system timeout
        // so screen-on time does not burn the next day's allowance.
        setContentView(R.layout.activity_screen_time_lock);
        ImmersiveHelper.enter(this);
        // Inset the ScrollView so the unlock button stays reachable when bars peek.
        ImmersiveHelper.applyContentInsets(findViewById(R.id.lockRoot));

        TextView title = findViewById(R.id.lockTitle);
        TextView message = findViewById(R.id.lockMessage);
        ImageView illustration = findViewById(R.id.lockIllustration);
        android.view.View root = findViewById(R.id.lockRoot);

        ScreenTimeTracker.BlockReason reason = ScreenTimeTracker.currentBlockReason(this);
        if (reason == ScreenTimeTracker.BlockReason.BEDTIME) {
            title.setText(R.string.bedtime_lock_title);
            message.setText(R.string.bedtime_lock_message);
            illustration.setImageResource(R.drawable.illust_bedtime);
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.lock_bg_bedtime));
            paintLockBars(ContextCompat.getColor(this, R.color.lock_bg_bedtime));
        } else if (reason == ScreenTimeTracker.BlockReason.REMOTE) {
            title.setText(R.string.remote_lock_title);
            message.setText(R.string.remote_lock_message);
            // Official KidsHome mark (same vector as launcher foreground / brand SVG).
            illustration.setImageResource(R.drawable.ic_home_foreground);
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.lock_bg_timeup));
            paintLockBars(ContextCompat.getColor(this, R.color.lock_bg_timeup));
        } else {
            title.setText(R.string.screen_time_lock_title);
            message.setText(R.string.screen_time_lock_message);
            illustration.setImageResource(R.drawable.illust_time_up);
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.lock_bg_timeup));
        }

        MotionHelper.fadeInUp(illustration, this);
        MotionHelper.fadeInUp(title, this);

        findViewById(R.id.lockParentUnlock).setOnClickListener(v -> promptPin(reason));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImmersiveHelper.enter(this);
        KidModeGate.markScreenLockUiOpen();
        if (!ScreenTimeTracker.isBlocked(this)) {
            finishToHome();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // A PIN dialog can clear immersive mode. Hide the bars again so Back/Home
        // stay out of reach when focus returns to the lock screen.
        if (hasFocus && !isFinishing()) {
            ImmersiveHelper.enter(this);
        }
    }

    private void paintLockBars(int color) {
        // Status and navigation bar colors have no effect at targetSdk 35.
        // The lock background shows through the transparent bars; insets keep
        // the message and unlock button in the safe area.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
    }

    private void promptPin(ScreenTimeTracker.BlockReason reason) {
        PinAttemptTracker.attach(this);
        if (PinAttemptTracker.isLockedOut()) {
            showPinLockout();
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        final boolean[] handled = {false};
        AlertDialog dialog = Dialogs.builder(this)
                .setTitle(R.string.pin_enter_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (!handled[0]) {
                handled[0] = true;
            }
        });
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive == null) {
                return;
            }
            positive.setOnClickListener(v -> {
                if (isFinishing() || isDestroyed() || handled[0]) {
                    return;
                }
                if (PinAttemptTracker.isLockedOut()) {
                    handled[0] = true;
                    dialog.dismiss();
                    showPinLockout();
                    return;
                }
                String pin = input.getText() != null ? input.getText().toString() : "";
                input.setEnabled(false);
                positive.setEnabled(false);
                positive.setText(R.string.pin_checking);
                if (negative != null) {
                    negative.setEnabled(false);
                }
                dialog.setCancelable(false);
                PinTasks.verifyKidPin(this, pin, ok -> {
                    if (isFinishing() || isDestroyed() || handled[0]) {
                        return;
                    }
                    handled[0] = true;
                    dialog.dismiss();
                    if (!ok) {
                        long delay = PinAttemptTracker.registerFailure();
                        int seconds = (int) Math.ceil(delay / 1000.0);
                        HapticHelper.error(this);
                        Toast.makeText(this,
                                getString(R.string.escape_pin_wrong_wait, seconds),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    PinAttemptTracker.reset();
                    SessionAuth.markUnlocked();
                    showParentChoices(reason);
                });
            });
        });
        dialog.show();
    }

    private void showPinLockout() {
        int seconds = (int) Math.ceil(PinAttemptTracker.remainingLockMs() / 1000.0);
        Toast.makeText(this, getString(R.string.escape_pin_locked_out, seconds), Toast.LENGTH_LONG).show();
    }

    private void showParentChoices(ScreenTimeTracker.BlockReason reason) {
        if (reason == ScreenTimeTracker.BlockReason.BEDTIME) {
            Dialogs.builder(this)
                    .setTitle(R.string.escape_unlocked_title)
                    .setItems(new CharSequence[]{
                            getString(R.string.bedtime_disable_tonight),
                            getString(R.string.escape_disable_kid_mode),
                            getString(R.string.escape_open_settings)
                    }, (d, which) -> {
                        if (which == 0) {
                            Prefs.setBedtimeDisabledToday(this, true);
                            finishToHome();
                        } else if (which == 1) {
                            disableKidMode();
                        } else {
                            openSettings();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        if (reason == ScreenTimeTracker.BlockReason.REMOTE) {
            Dialogs.builder(this)
                    .setTitle(R.string.escape_unlocked_title)
                    .setItems(new CharSequence[]{
                            getString(R.string.remote_unlock_here),
                            getString(R.string.escape_disable_kid_mode),
                            getString(R.string.escape_open_settings)
                    }, (d, which) -> {
                        if (which == 0) {
                            Prefs.setRemoteForceLocked(this, false);
                            finishToHome();
                        } else if (which == 1) {
                            Prefs.setRemoteForceLocked(this, false);
                            disableKidMode();
                        } else {
                            openSettings();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        Dialogs.builder(this)
                .setTitle(R.string.escape_unlocked_title)
                .setItems(new CharSequence[]{
                        getString(R.string.screen_time_bonus_15),
                        getString(R.string.screen_time_bonus_30),
                        getString(R.string.screen_time_disable_today),
                        getString(R.string.escape_disable_kid_mode),
                        getString(R.string.escape_open_settings)
                }, (d, which) -> {
                    switch (which) {
                        case 0:
                            Prefs.addBonusMinutes(this, 15);
                            finishToHome();
                            break;
                        case 1:
                            Prefs.addBonusMinutes(this, 30);
                            finishToHome();
                            break;
                        case 2:
                            Prefs.setScreenTimeDisabledToday(this, true);
                            finishToHome();
                            break;
                        case 3:
                            disableKidMode();
                            break;
                        case 4:
                            openSettings();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void disableKidMode() {
        Prefs.setKidModeEnabled(this, false);
        SessionAuth.markUnlocked();
        EscapeOverlayNotifier.requestRefresh(this);
        KidModeGate.markScreenLockUiClosed();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void openSettings() {
        SessionAuth.markUnlocked();
        KidModeGate.markScreenLockUiClosed();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        startActivity(new Intent(this, KidModeActivity.class));
        finish();
    }

    private void finishToHome() {
        KidModeGate.markScreenLockUiClosed();
        HomePress.perform(this, true);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (!isChangingConfigurations()) {
            KidModeGate.markScreenLockUiClosed();
        }
        super.onDestroy();
    }
}
