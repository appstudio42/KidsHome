package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
        pickAppButton = findViewById(R.id.pickAppButton);
        changePinButton = findViewById(R.id.changePinButton);
        saveButton = findViewById(R.id.saveKidModeButton);

        limitsNote.setText(R.string.kid_mode_limits);

        kidModeSwitch.setChecked(Prefs.isKidModeEnabled(this));
        boolean lockToApp = Prefs.isKidLockToApp(this);
        lockLauncher.setChecked(!lockToApp);
        lockApp.setChecked(lockToApp);
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

    private void save() {
        boolean enable = kidModeSwitch.isChecked();
        if (enable) {
            if (!Prefs.hasKidPin(this)) {
                Toast.makeText(this, R.string.pin_required, Toast.LENGTH_LONG).show();
                return;
            }
            if (!Prefs.hasChosenLauncher(this)) {
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
            Prefs.setKidModeEnabled(this, true);
            Toast.makeText(this, R.string.kid_mode_enabled, Toast.LENGTH_LONG).show();
            HomePress.perform(this, true);
        } else {
            Prefs.setKidModeEnabled(this, false);
            Toast.makeText(this, R.string.kid_mode_disabled, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
