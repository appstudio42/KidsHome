package io.github.marcelsavegnago.kidshome;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Base for PIN-gated parent screens. Applies immersive bars (and lock-task when
 * kid mode is on) so the system taskbar is not an escape route.
 */
public abstract class ParentPinActivity extends AppCompatActivity {
    private boolean unlocked;
    private int contentRootId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The PIN dialog can return before setContentView. Mark the window now so the
        // API 35 callback does not pad android.R.id.content and then parentRoot as well.
        ImmersiveHelper.markEdgeToEdgeHandled(this);
        Prefs.migrateIfNeeded(this);
        if (Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
            PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                @Override
                public void onSuccess() {
                    unlocked = true;
                    onPinUnlocked();
                    applyParentChrome();
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
        applyParentChrome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlocked) {
            applyParentChrome();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && unlocked) {
            applyParentChrome();
        }
    }

    protected abstract void onPinUnlocked();

    protected boolean isUnlocked() {
        return unlocked;
    }

    /**
     * Call after {@code setContentView} so immersive insets target the screen root.
     */
    protected void bindParentChrome(@IdRes int rootId, @IdRes int toolbarId) {
        contentRootId = rootId;
        MaterialToolbar toolbar = findViewById(toolbarId);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        ThemeHelper.applySystemBars(this);
        applyParentChrome();
    }

    private void applyParentChrome() {
        ImmersiveHelper.enter(this);
        if (contentRootId != 0) {
            View root = findViewById(contentRootId);
            if (root != null) {
                ImmersiveHelper.applyContentInsets(root);
            }
        }
        if (Prefs.isKidModeEnabled(this)) {
            DeviceOwnerHelper.startLockTaskIfNeeded(this);
        }
    }
}
