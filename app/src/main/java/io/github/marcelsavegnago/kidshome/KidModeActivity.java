package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Optional PIN-protected kid mode. Accessibility-based — not a true device-owner lock.
 */
public class KidModeActivity extends AppCompatActivity {
    private Switch kidModeSwitch;
    private RadioButton lockLauncher;
    private RadioButton lockApp;
    private TextView kidAppLabel;
    private TextView limitsNote;
    private TextView escapeHelp;
    private CheckBox escapeCorner;
    private CheckBox escapeBackHold;
    private Button pickAppButton;
    private Button changePinButton;
    private Button saveButton;

    private String pendingAppPackage = "";
    private String pendingAppActivity = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
            PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                @Override
                public void onSuccess() {
                    inflateUi();
                }

                @Override
                public void onCancel() {
                    finish();
                }
            });
            return;
        }
        inflateUi();
    }

    private void inflateUi() {
        setContentView(R.layout.activity_kid_mode);
        setTitle(R.string.kid_mode);

        kidModeSwitch = findViewById(R.id.kidModeSwitch);
        lockLauncher = findViewById(R.id.lockLauncher);
        lockApp = findViewById(R.id.lockApp);
        kidAppLabel = findViewById(R.id.kidAppLabel);
        limitsNote = findViewById(R.id.limitsNote);
        escapeHelp = findViewById(R.id.escapeHelp);
        escapeCorner = findViewById(R.id.escapeCornerCB);
        escapeBackHold = findViewById(R.id.escapeBackHoldCB);
        pickAppButton = findViewById(R.id.pickAppButton);
        changePinButton = findViewById(R.id.changePinButton);
        saveButton = findViewById(R.id.saveKidModeButton);

        limitsNote.setText(R.string.kid_mode_limits);
        escapeHelp.setText(R.string.kid_mode_escape_help);

        kidModeSwitch.setChecked(Prefs.isKidModeEnabled(this));
        boolean lockToApp = Prefs.isKidLockToApp(this);
        lockLauncher.setChecked(!lockToApp);
        lockApp.setChecked(lockToApp);
        escapeCorner.setChecked(Prefs.isEscapeCornerEnabled(this));
        escapeBackHold.setChecked(Prefs.isEscapeBackHoldEnabled(this));
        pendingAppPackage = Prefs.getKidAppPackage(this);
        pendingAppActivity = Prefs.getKidAppActivity(this);
        refreshKidAppLabel();
        refreshEnabledState();

        kidModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !Prefs.hasKidPin(this)) {
                PinDialogHelper.promptCreate(this, new PinDialogHelper.PinCallback() {
                    @Override
                    public void onSuccess() {
                        refreshEnabledState();
                    }

                    @Override
                    public void onCancel() {
                        kidModeSwitch.setChecked(false);
                        refreshEnabledState();
                    }
                });
            } else if (!isChecked && Prefs.isKidModeEnabled(this)) {
                PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                    @Override
                    public void onSuccess() {
                        refreshEnabledState();
                    }

                    @Override
                    public void onCancel() {
                        kidModeSwitch.setChecked(true);
                        refreshEnabledState();
                    }
                });
            } else {
                refreshEnabledState();
            }
        });

        View.OnClickListener radioClick = v -> refreshEnabledState();
        lockLauncher.setOnClickListener(radioClick);
        lockApp.setOnClickListener(radioClick);

        pickAppButton.setOnClickListener(v -> showAppPicker());
        changePinButton.setOnClickListener(v -> {
            if (!Prefs.hasKidPin(this)) {
                PinDialogHelper.promptCreate(this, new PinDialogHelper.PinCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(KidModeActivity.this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancel() {
                    }
                });
                return;
            }
            PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                @Override
                public void onSuccess() {
                    PinDialogHelper.promptCreate(KidModeActivity.this, new PinDialogHelper.PinCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(KidModeActivity.this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
                }

                @Override
                public void onCancel() {
                }
            });
        });

        saveButton.setOnClickListener(v -> save());
        findViewById(R.id.cancelKidModeButton).setOnClickListener(v -> finish());
    }

    private void refreshEnabledState() {
        boolean enabled = kidModeSwitch.isChecked();
        lockLauncher.setEnabled(enabled);
        lockApp.setEnabled(enabled);
        pickAppButton.setEnabled(enabled && lockApp.isChecked());
        escapeCorner.setEnabled(enabled);
        escapeBackHold.setEnabled(enabled);
        changePinButton.setEnabled(true);
    }

    private void refreshKidAppLabel() {
        if (pendingAppPackage == null || pendingAppPackage.isEmpty()) {
            kidAppLabel.setText(R.string.kid_app_none);
        } else {
            kidAppLabel.setText(getString(R.string.kid_app_selected, pendingAppPackage));
        }
    }

    private void showAppPicker() {
        final List<ResolveInfo> apps = PackageUtils.getLaunchableApps(this);
        CharSequence[] labels = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).loadLabel(getPackageManager())
                    + " (" + apps.get(i).activityInfo.packageName + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_kid_app)
                .setItems(labels, (dialog, which) -> {
                    ResolveInfo info = apps.get(which);
                    pendingAppPackage = info.activityInfo.packageName;
                    pendingAppActivity = info.activityInfo.name;
                    refreshKidAppLabel();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean ensureOverlayIfNeeded() {
        if (!escapeCorner.isChecked()) {
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Settings.canDrawOverlays(this)) {
            return true;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())
                        ));
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.overlay_permission_adb, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return false;
    }

    private void save() {
        boolean enable = kidModeSwitch.isChecked();
        boolean corner = escapeCorner.isChecked();
        boolean backHold = escapeBackHold.isChecked();

        if (enable && !corner && !backHold) {
            Toast.makeText(this, R.string.escape_option_required, Toast.LENGTH_LONG).show();
            return;
        }

        Prefs.setEscapeOptions(this, corner, backHold);

        if (enable) {
            if (!Prefs.hasKidPin(this)) {
                Toast.makeText(this, R.string.pin_required, Toast.LENGTH_LONG).show();
                return;
            }
            if (!Prefs.hasChosenLauncher(this) && !lockApp.isChecked()) {
                Toast.makeText(this, R.string.please_select_launcher, Toast.LENGTH_LONG).show();
                return;
            }
            if (lockApp.isChecked()) {
                if (pendingAppPackage == null || pendingAppPackage.isEmpty()
                        || !PackageUtils.isActivityAvailable(this, pendingAppPackage, pendingAppActivity)) {
                    Toast.makeText(this, R.string.kid_app_required, Toast.LENGTH_LONG).show();
                    return;
                }
                Prefs.setKidApp(this, pendingAppPackage, pendingAppActivity);
                Prefs.setKidLockToApp(this, true);
            } else {
                Prefs.setKidLockToApp(this, false);
            }
            if (corner && !ensureOverlayIfNeeded()) {
                // Still allow enabling; Back-hold remains as escape. Corner will activate once overlay is granted.
                Toast.makeText(this, R.string.overlay_permission_later, Toast.LENGTH_LONG).show();
            }
            Prefs.setKidModeEnabled(this, true);
            EscapeOverlayNotifier.requestRefresh(this);
            Toast.makeText(this, R.string.kid_mode_enabled, Toast.LENGTH_LONG).show();
            HomePress.perform(this, true);
        } else {
            // Synchronous disable before any service bounce can re-read prefs.
            Prefs.setKidModeEnabled(this, false);
            SessionAuth.markUnlocked();
            PinAttemptTracker.reset();
            KidModeGate.markEscapeUiClosed();
            EscapeOverlayNotifier.requestRefresh(this);
            Toast.makeText(this, R.string.kid_mode_disabled, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
