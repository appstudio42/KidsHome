package io.github.marcelsavegnago.kidshome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Emergency parent PIN screen with modern keypad, dots, shake, and lockout.
 */
public class EscapePinActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText pinInput;
    private TextView statusText;
    private MaterialButton submitButton;
    private ViewShakeTarget shakeTarget;
    private PinKeypadController keypad;
    private boolean finishingToKidApp;
    /** Keeps a visible PIN screen inside the escape-gate window. */
    private final Runnable refreshEscapeGate = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            KidModeGate.markEscapeUiOpen();
            handler.postDelayed(this, KidModeGate.ESCAPE_UI_MAX_AGE_MS / 3L);
        }
    };

    private static final class ViewShakeTarget {
        final android.view.View view;

        ViewShakeTarget(android.view.View view) {
            this.view = view;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KidModeGate.markEscapeUiOpen();
        // Same as Cancel: leave this screen and return to the locked target.
        // Do not let the system finish the task onto whatever sits behind it.
        LockBack.intercept(this, getOnBackPressedDispatcher(), this::returnToLockedTarget);

        // Do not keep the screen on — PIN / lock UI must follow the system timeout.
        setContentView(R.layout.activity_escape_pin);
        ImmersiveHelper.enter(this);
        View root = findViewById(R.id.escapePinRoot);
        if (root != null) {
            ImmersiveHelper.applyContentInsets(root);
        }
        setTitle(R.string.escape_pin_title);

        pinInput = findViewById(R.id.escapePinInput);
        statusText = findViewById(R.id.escapeStatus);
        submitButton = findViewById(R.id.escapeSubmit);
        MaterialButton cancelButton = findViewById(R.id.escapeCancel);
        LinearLayout dots = findViewById(R.id.pinDots);
        shakeTarget = new ViewShakeTarget(findViewById(R.id.pinDots));

        keypad = new PinKeypadController(findViewById(android.R.id.content), pinInput, dots, 8);

        statusText.setText(R.string.escape_pin_message);
        submitButton.setOnClickListener(v -> attemptPin());
        cancelButton.setOnClickListener(v -> returnToLockedTarget());

        PinAttemptTracker.attach(this);
        refreshLockoutState();
        MotionHelper.fadeInUp(statusText, this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        KidModeGate.markEscapeUiOpen();
    }

    @Override
    protected void onStart() {
        super.onStart();
        KidModeGate.markEscapeUiOpen();
        handler.removeCallbacks(refreshEscapeGate);
        handler.postDelayed(refreshEscapeGate, KidModeGate.ESCAPE_UI_MAX_AGE_MS / 3L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImmersiveHelper.enter(this);
        KidModeGate.markEscapeUiOpen();
        refreshLockoutState();
    }

    @Override
    protected void onStop() {
        // A stopped PIN screen (shade → Settings, another task) must not keep
        // enforcement and screen-time paused until onDestroy. onStart restores
        // the gate if this screen comes back. Rotation keeps the flag.
        handler.removeCallbacks(refreshEscapeGate);
        if (!isChangingConfigurations()) {
            KidModeGate.markEscapeUiClosed();
        }
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isFinishing()) {
            ImmersiveHelper.enter(this);
        }
    }

    private void refreshLockoutState() {
        if (PinAttemptTracker.isLockedOut()) {
            long remain = PinAttemptTracker.remainingLockMs();
            submitButton.setEnabled(false);
            statusText.setText(getString(R.string.escape_pin_locked_out, (int) Math.ceil(remain / 1000.0)));
            handler.postDelayed(this::refreshLockoutState, Math.min(remain + 50L, 1000L));
        } else {
            submitButton.setEnabled(true);
            if (PinAttemptTracker.failureCount() > 0) {
                statusText.setText(getString(R.string.escape_pin_try_again, PinAttemptTracker.failureCount()));
            } else {
                statusText.setText(R.string.escape_pin_message);
            }
        }
    }

    private void attemptPin() {
        if (PinAttemptTracker.isLockedOut()) {
            refreshLockoutState();
            return;
        }
        String pin = keypad != null ? keypad.getPin() : "";
        if (Prefs.verifyKidPin(this, pin)) {
            PinAttemptTracker.reset();
            SessionAuth.markUnlocked();
            showParentOptions();
            return;
        }

        if (keypad != null) {
            keypad.clear();
        } else {
            pinInput.setText("");
        }
        long delay = PinAttemptTracker.registerFailure();
        HapticHelper.error(this);
        if (shakeTarget != null) {
            MotionHelper.shake(shakeTarget.view, this);
        }
        Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
        statusText.setText(getString(R.string.escape_pin_wrong_wait, (int) Math.ceil(delay / 1000.0)));
        submitButton.setEnabled(false);
        handler.postDelayed(this::refreshLockoutState, delay);
    }

    private void showParentOptions() {
        Dialogs.builder(this)
                .setTitle(R.string.escape_unlocked_title)
                .setMessage(R.string.escape_unlocked_message)
                .setCancelable(false)
                .setPositiveButton(R.string.escape_disable_kid_mode, (d, w) -> disableKidMode())
                .setNeutralButton(R.string.escape_open_settings, (d, w) -> openKidsHome())
                .setNegativeButton(R.string.escape_stay_locked, (d, w) -> {
                    SessionAuth.lock();
                    returnToLockedTarget();
                })
                .show();
    }

    private void disableKidMode() {
        EnforcementController.onKidModeDisabled(this);
        Prefs.setKidModeEnabled(this, false);
        SessionAuth.markUnlocked();
        PinAttemptTracker.reset();
        EscapeOverlayNotifier.requestRefresh(this);
        Toast.makeText(this, R.string.kid_mode_disabled, Toast.LENGTH_LONG).show();
        finishingToKidApp = false;
        KidModeGate.markEscapeUiClosed();
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(main);
        finish();
    }

    private void openKidsHome() {
        DeviceOwnerHelper.stopLockTaskQuietly(this);
        SessionAuth.markUnlocked();
        PinAttemptTracker.reset();
        finishingToKidApp = false;
        KidModeGate.markEscapeUiClosed();
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(main);
        startActivity(new Intent(this, KidModeActivity.class));
        finish();
    }

    private void returnToLockedTarget() {
        finishingToKidApp = true;
        KidModeGate.markEscapeUiClosed();
        HomePress.perform(this, true);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!isChangingConfigurations()) {
            if (!finishingToKidApp && Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
                KidModeGate.markEscapeUiClosed();
            }
        }
        super.onDestroy();
    }
}
