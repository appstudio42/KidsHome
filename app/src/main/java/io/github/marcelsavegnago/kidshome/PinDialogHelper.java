package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.text.TextUtils;
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
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(R.string.pin_hint);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.pin_enter_title)
                .setMessage(R.string.pin_enter_message)
                .setView(wrap(activity, input))
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> callback.onCancel())
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String pin = input.getText() != null ? input.getText().toString() : "";
                    if (Prefs.verifyKidPin(activity, pin)) {
                        SessionAuth.markUnlocked();
                        callback.onSuccess();
                    } else {
                        Toast.makeText(activity, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
                        callback.onCancel();
                    }
                })
                .show();
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

        new AlertDialog.Builder(activity)
                .setTitle(R.string.pin_create_title)
                .setMessage(R.string.pin_create_message)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> callback.onCancel())
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String a = pin1.getText() != null ? pin1.getText().toString() : "";
                    String b = pin2.getText() != null ? pin2.getText().toString() : "";
                    if (!PinHasher.isValidPinFormat(a)) {
                        Toast.makeText(activity, R.string.pin_invalid, Toast.LENGTH_LONG).show();
                        callback.onCancel();
                        return;
                    }
                    if (!TextUtils.equals(a, b)) {
                        Toast.makeText(activity, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                        callback.onCancel();
                        return;
                    }
                    Prefs.setKidPin(activity, a);
                    SessionAuth.markUnlocked();
                    callback.onSuccess();
                })
                .show();
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
