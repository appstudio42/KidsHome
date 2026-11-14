package io.github.marcelsavegnago.kidshome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/**
 * Parent onboarding: shows active enforcement mode and missing permissions/steps.
 */
public class SetupActivity extends AppCompatActivity {
    private static final int REQUEST_NOTIFICATIONS = 4201;

    private TextView deviceInfo;
    private TextView modeActive;
    private TextView deviceOwnerCommand;
    private RadioButton modeAuto;
    private RadioButton modeAccessibility;
    private RadioButton modeLauncher;
    private RadioButton modeDeviceOwner;
    private CheckBox extraBlocking;
    private CheckBox disableStatusBar;
    private CheckBox disableKeyguard;
    private LinearLayout stepsContainer;
    private Button removeOwnerButton;

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
        setContentView(R.layout.activity_setup);
        ThemeHelper.applySystemBars(this);
        setTitle(R.string.setup_title);

        deviceInfo = findViewById(R.id.setupDeviceInfo);
        modeActive = findViewById(R.id.setupModeActive);
        deviceOwnerCommand = findViewById(R.id.deviceOwnerCommand);
        modeAuto = findViewById(R.id.modeAuto);
        modeAccessibility = findViewById(R.id.modeAccessibility);
        modeLauncher = findViewById(R.id.modeLauncher);
        modeDeviceOwner = findViewById(R.id.modeDeviceOwner);
        extraBlocking = findViewById(R.id.extraBlockingCB);
        disableStatusBar = findViewById(R.id.disableStatusBarCB);
        disableKeyguard = findViewById(R.id.disableKeyguardCB);
        stepsContainer = findViewById(R.id.setupStepsContainer);
        removeOwnerButton = findViewById(R.id.removeDeviceOwner);

        bindModeRadios(Prefs.getEnforcementModePreference(this));
        extraBlocking.setChecked(Prefs.isAccessibilityExtraBlocking(this));
        disableStatusBar.setChecked(Prefs.isLockTaskDisableStatusBar(this));
        disableKeyguard.setChecked(Prefs.isLockTaskDisableKeyguard(this));

        deviceOwnerCommand.setText(DeviceOwnerHelper.getDeviceOwnerAdbCommand(this));
        findViewById(R.id.copyOwnerCommand).setOnClickListener(v -> copyOwnerCommand());
        removeOwnerButton.setOnClickListener(v -> confirmRemoveOwner());
        findViewById(R.id.saveSetupButton).setOnClickListener(v -> save());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (deviceInfo != null) {
            refresh();
        }
    }

    private void bindModeRadios(EnforcementMode mode) {
        modeAuto.setChecked(mode == EnforcementMode.AUTO);
        modeAccessibility.setChecked(mode == EnforcementMode.ACCESSIBILITY);
        modeLauncher.setChecked(mode == EnforcementMode.LAUNCHER);
        modeDeviceOwner.setChecked(mode == EnforcementMode.DEVICE_OWNER);
    }

    private EnforcementMode selectedMode() {
        if (modeAccessibility.isChecked()) {
            return EnforcementMode.ACCESSIBILITY;
        }
        if (modeLauncher.isChecked()) {
            return EnforcementMode.LAUNCHER;
        }
        if (modeDeviceOwner.isChecked()) {
            return EnforcementMode.DEVICE_OWNER;
        }
        return EnforcementMode.AUTO;
    }

    private void refresh() {
        boolean fire = DeviceDetector.isFireOs(this);
        boolean owner = DeviceOwnerHelper.isDeviceOwner(this);
        EnforcementMode preferred = Prefs.getEnforcementModePreference(this);
        EnforcementMode effective = EnforcementController.getEffectiveMode(this);

        deviceInfo.setText(getString(
                R.string.setup_device_info,
                DeviceDetector.describeDevice(),
                fire ? getString(R.string.setup_yes) : getString(R.string.setup_no),
                owner ? getString(R.string.setup_yes) : getString(R.string.setup_no)
        ));
        modeActive.setText(getString(
                R.string.setup_mode_active_fmt,
                labelFor(preferred),
                labelFor(effective)
        ));

        removeOwnerButton.setEnabled(owner);
        disableStatusBar.setEnabled(owner || preferred == EnforcementMode.DEVICE_OWNER);
        disableKeyguard.setEnabled(owner || preferred == EnforcementMode.DEVICE_OWNER);

        stepsContainer.removeAllViews();
        for (SetupChecklist.Step step : SetupChecklist.build(this)) {
            stepsContainer.addView(createStepView(step));
        }
    }

    private String labelFor(EnforcementMode mode) {
        switch (mode) {
            case ACCESSIBILITY:
                return getString(R.string.setup_mode_accessibility_short);
            case LAUNCHER:
                return getString(R.string.setup_mode_launcher_short);
            case DEVICE_OWNER:
                return getString(R.string.setup_mode_device_owner_short);
            case AUTO:
            default:
                return getString(R.string.setup_mode_auto_short);
        }
    }

    private View createStepView(SetupChecklist.Step step) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 16, 0, 8);

        TextView title = new TextView(this);
        String mark = step.done ? "✓ " : (step.required ? "○ " : "· ");
        title.setText(mark + step.title);
        title.setTextSize(14f);
        title.setTextColor(step.done ? 0xFF1B5E20 : (step.required ? 0xFFB71C1C : 0xFF424242));
        row.addView(title);

        TextView detail = new TextView(this);
        detail.setText(step.detail);
        detail.setTextSize(12f);
        detail.setPadding(24, 4, 0, 0);
        row.addView(detail);

        if (!step.done) {
            Button action = new Button(this);
            action.setText(actionLabel(step.id));
            action.setOnClickListener(v -> runStepAction(step.id));
            row.addView(action);
        }
        return row;
    }

    private String actionLabel(SetupChecklist.StepId id) {
        switch (id) {
            case ACCESSIBILITY:
                return getString(R.string.setup_action_a11y);
            case OVERLAY:
                return getString(R.string.setup_action_overlay);
            case BATTERY:
                return getString(R.string.setup_action_battery);
            case DEFAULT_HOME:
                return getString(R.string.setup_action_home);
            case DEVICE_OWNER:
                return getString(R.string.setup_copy_owner_command);
            case NOTIFICATIONS:
                return getString(R.string.setup_action_notif);
            default:
                return getString(R.string.open_settings);
        }
    }

    private void runStepAction(SetupChecklist.StepId id) {
        switch (id) {
            case ACCESSIBILITY:
                AccessibilityHelper.openAccessibilitySettings(this);
                break;
            case OVERLAY:
                openOverlaySettings();
                break;
            case BATTERY:
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this);
                break;
            case DEFAULT_HOME:
                LauncherHelper.openDefaultHomePicker(this);
                break;
            case DEVICE_OWNER:
                copyOwnerCommand();
                break;
            case NOTIFICATIONS:
                requestNotificationPermission();
                break;
            default:
                break;
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (Exception e) {
            Toast.makeText(this, R.string.overlay_permission_adb, Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        }
    }

    private void copyOwnerCommand() {
        String cmd = DeviceOwnerHelper.getDeviceOwnerAdbCommand(this);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("device-owner", cmd));
        }
        Toast.makeText(this, R.string.setup_command_copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmRemoveOwner() {
        if (!DeviceOwnerHelper.isDeviceOwner(this)) {
            Toast.makeText(this, R.string.setup_not_device_owner, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialogs.builder(this)
                .setTitle(R.string.setup_remove_device_owner)
                .setMessage(R.string.setup_remove_device_owner_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    DeviceOwnerHelper.stopLockTaskQuietly(this);
                    boolean ok = DeviceOwnerHelper.clearDeviceOwner(this);
                    Toast.makeText(
                            this,
                            ok ? R.string.setup_device_owner_removed : R.string.setup_device_owner_remove_failed,
                            Toast.LENGTH_LONG
                    ).show();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void save() {
        Prefs.setEnforcementModePreference(this, selectedMode());
        Prefs.setAccessibilityExtraBlocking(this, extraBlocking.isChecked());
        Prefs.setLockTaskDisableStatusBar(this, disableStatusBar.isChecked());
        Prefs.setLockTaskDisableKeyguard(this, disableKeyguard.isChecked());
        if (Prefs.isKidModeEnabled(this)) {
            EnforcementController.onKidModeEnabled(this);
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        refresh();
    }
}
