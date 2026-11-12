package io.github.marcelsavegnago.kidshome;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base for PIN-gated parent screens. Subclasses implement {@link #onPinUnlocked()}.
 */
public abstract class ParentPinActivity extends AppCompatActivity {
    private boolean unlocked;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrateIfNeeded(this);
        if (Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
            PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                @Override
                public void onSuccess() {
                    unlocked = true;
                    onPinUnlocked();
                }

                @Override
                public void onCancel() {
                    finish();
                }
            });
            return;
        }
        unlocked = true;
        onPinUnlocked();
    }

    protected abstract void onPinUnlocked();

    protected boolean isUnlocked() {
        return unlocked;
    }
}
