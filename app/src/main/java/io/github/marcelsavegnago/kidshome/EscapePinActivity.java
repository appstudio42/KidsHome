package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Emergency parent PIN screen shown above the locked kid app / launcher.
 */
public class EscapePinActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText pinInput;
    private TextView statusText;
    private Button submitButton;
    private boolean finishingToKidApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KidModeGate.markEscapeUiOpen();

        // Keep soft keyboard and activity above the locked app.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_escape_pin);
        setTitle(R.string.escape_pin_title);

        pinInput = findViewById(R.id.escapePinInput);
        statusText = findViewById(R.id.escapeStatus);
        submitButton = findViewById(R.id.escapeSubmit);
        Button cancelButton = findViewById(R.id.escapeCancel);

        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        statusText.setText(R.string.escape_pin_message);

        submitButton.setOnClickListener(v -> attemptPin());
        cancelButton.setOnClickListener(v -> returnToLockedTarget());

        refreshLockoutState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        KidModeGate.markEscapeUiOpen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        KidModeGate.markEscapeUiOpen();
        refreshLockoutState();
    }

    @Override
    public void onBackPressed() {
        // Back should not dismiss without going through cancel / return-to-kid-app.
        returnToLockedTarget();
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
        String pin = pinInput.getText() != null ? pinInput.getText().toString() : "";
        if (Prefs.verifyKidPin(this, pin)) {
            PinAttemptTracker.reset();
            SessionAuth.markUnlocked();
            showParentOptions();
            return;
        }

        pinInput.setText("");
        long delay = PinAttemptTracker.registerFailure();
        Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
        statusText.setText(getString(R.string.escape_pin_wrong_wait, (int) Math.ceil(delay / 1000.0)));
        submitButton.setEnabled(false);
        handler.postDelayed(this::refreshLockoutState, delay);
    }

    private void showParentOptions() {
        new AlertDialog.Builder(this)
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
        // Synchronous write so a service restart cannot re-read the old "enabled" value.
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
        // Keep kid mode on; SessionAuth lets the parent use KidsHome without being yanked.
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
            // If the activity is dying unexpectedly, release the gate after a short grace.
            if (!finishingToKidApp && Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
                KidModeGate.markEscapeUiClosed();
            }
        }
        super.onDestroy();
    }
}
