package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class PinDialogHelper {
    public interface PinCallback {
        void onSuccess();

        void onCancel();
    }

    private PinDialogHelper() {
    }

    public static void promptVerify(Activity activity, PinCallback callback) {
        PinAttemptTracker.attach(activity);
        if (PinAttemptTracker.isLockedOut()) {
            showLockout(activity);
            callback.onCancel();
            return;
        }

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(R.string.pin_hint);

        AlertDialog dialog = Dialogs.builder(activity)
                .setTitle(R.string.pin_enter_title)
                .setMessage(R.string.pin_enter_message)
                .setView(wrap(activity, input))
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> callback.onCancel())
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive == null) {
                return;
            }
            positive.setOnClickListener(v -> submitVerify(activity, dialog, input, positive, negative, callback));
        });
        dialog.show();
    }

    public static void promptCreate(Activity activity, PinCallback callback) {
        final EditText pin1 = new EditText(activity);
        pin1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin1.setHint(R.string.pin_new_hint);

        final EditText pin2 = new EditText(activity);
        pin2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin2.setHint(R.string.pin_confirm_hint);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(pin1);
        layout.addView(pin2);

        AlertDialog dialog = Dialogs.builder(activity)
                .setTitle(R.string.pin_create_title)
                .setMessage(R.string.pin_create_message)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> callback.onCancel())
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive == null) {
                return;
            }
            positive.setOnClickListener(v -> submitCreate(
                    activity, dialog, pin1, pin2, positive, negative, callback));
        });
        dialog.show();
    }

    private static void submitVerify(Activity activity, AlertDialog dialog, EditText input,
                                     Button positive, Button negative, PinCallback callback) {
        if (!isAlive(activity)) {
            return;
        }
        if (PinAttemptTracker.isLockedOut()) {
            showLockout(activity);
            dialog.dismiss();
            callback.onCancel();
            return;
        }
        String pin = input.getText() != null ? input.getText().toString() : "";
        setBusy(input, positive, negative, true);
        PinTasks.verifyKidPin(activity, pin, ok -> {
            if (!isAlive(activity)) {
                return;
            }
            dialog.dismiss();
            if (ok) {
                PinAttemptTracker.reset();
                SessionAuth.markUnlocked();
                callback.onSuccess();
                return;
            }
            long delay = PinAttemptTracker.registerFailure();
            int seconds = (int) Math.ceil(delay / 1000.0);
            Toast.makeText(activity,
                    activity.getString(R.string.escape_pin_wrong_wait, seconds),
                    Toast.LENGTH_LONG).show();
            callback.onCancel();
        });
    }

    private static void submitCreate(Activity activity, AlertDialog dialog, EditText pin1, EditText pin2,
                                     Button positive, Button negative, PinCallback callback) {
        if (!isAlive(activity)) {
            return;
        }
        String a = pin1.getText() != null ? pin1.getText().toString() : "";
        String b = pin2.getText() != null ? pin2.getText().toString() : "";
        if (!PinHasher.isValidPinFormat(a)) {
            Toast.makeText(activity, R.string.pin_invalid, Toast.LENGTH_LONG).show();
            dialog.dismiss();
            callback.onCancel();
            return;
        }
        if (!TextUtils.equals(a, b)) {
            Toast.makeText(activity, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            callback.onCancel();
            return;
        }
        setBusy(pin1, positive, negative, true);
        pin2.setEnabled(false);
        PinTasks.setKidPin(activity, a, saved -> {
            if (!isAlive(activity)) {
                return;
            }
            if (!saved) {
                setBusy(pin1, positive, negative, false);
                pin2.setEnabled(true);
                positive.setText(R.string.ok);
                return;
            }
            dialog.dismiss();
            SessionAuth.markUnlocked();
            callback.onSuccess();
        });
    }

    private static void setBusy(EditText input, Button positive, Button negative, boolean busy) {
        input.setEnabled(!busy);
        positive.setEnabled(!busy);
        if (busy) {
            positive.setText(R.string.pin_checking);
        }
        if (negative != null) {
            negative.setEnabled(!busy);
        }
    }

    private static boolean isAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void showLockout(Activity activity) {
        int seconds = (int) Math.ceil(PinAttemptTracker.remainingLockMs() / 1000.0);
        Toast.makeText(activity,
                activity.getString(R.string.escape_pin_locked_out, seconds),
                Toast.LENGTH_LONG).show();
    }

    private static LinearLayout wrap(Activity activity, EditText input) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(input);
        return layout;
    }
}
