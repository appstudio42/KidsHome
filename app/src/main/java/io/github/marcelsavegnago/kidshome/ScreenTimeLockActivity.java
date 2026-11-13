package io.github.marcelsavegnago.kidshome;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
        try {
            context.getApplicationContext().startActivity(intent);
        } catch (Exception e) {
            KidModeGate.markScreenLockUiClosed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KidModeGate.markScreenLockUiOpen();
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
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.lock_bg_bedtime));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.lock_bg_bedtime));
        } else if (reason == ScreenTimeTracker.BlockReason.REMOTE) {
            title.setText(R.string.remote_lock_title);
            message.setText(R.string.remote_lock_message);
            // Official KidsHome mark (same vector as launcher foreground / brand SVG).
            illustration.setImageResource(R.drawable.ic_home_foreground);
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.lock_bg_timeup));
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.lock_bg_timeup));
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.lock_bg_timeup));
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
    @SuppressLint("MissingSuperCall")
    public void onBackPressed() {
        // Stay on the lock screen until PIN unlock.
    }

    private void promptPin(ScreenTimeTracker.BlockReason reason) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pin_enter_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String pin = input.getText() != null ? input.getText().toString() : "";
                    if (!Prefs.verifyKidPin(this, pin)) {
                        HapticHelper.error(this);
                        Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SessionAuth.markUnlocked();
                    showParentChoices(reason);
                })
                .show();
    }

    private void showParentChoices(ScreenTimeTracker.BlockReason reason) {
        if (reason == ScreenTimeTracker.BlockReason.BEDTIME) {
            new AlertDialog.Builder(this)
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
            new AlertDialog.Builder(this)
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

        new AlertDialog.Builder(this)
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
