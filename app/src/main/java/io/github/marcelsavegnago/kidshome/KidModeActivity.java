package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;

/**
 * Optional PIN-protected kid mode configuration.
 */
public class KidModeActivity extends AppCompatActivity {
    public static final String EXTRA_SCROLL_TO_TIME = "scroll_to_time";

    private MaterialSwitch kidModeSwitch;
    private RadioButton lockKidHome;
    private RadioButton lockApp;
    private TextView kidAppLabel;
    private TextView browserLabel;
    private TextView limitsNote;
    private TextView escapeHelp;
    private TextView childAvatarPreview;
    private TextView dailyLimitLabel;
    private EditText childNameInput;
    private EditText dailyLimitInput;
    private CheckBox escapeCorner;
    private CheckBox escapeBackHold;
    private CheckBox screenTimeEnabled;
    private CheckBox bedtimeEnabled;
    private CheckBox touchSounds;
    private MaterialButton pickAppButton;
    private MaterialButton bedtimeStartButton;
    private MaterialButton bedtimeEndButton;
    private Slider dailyLimitSlider;

    private String pendingAppPackage = "";
    private String pendingAppActivity = "";
    private String pendingAvatar = ChildAvatars.DEFAULT;
    private int bedtimeStartMinutes;
    private int bedtimeEndMinutes;

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
        MaterialToolbar toolbar = findViewById(R.id.kidModeToolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applySystemBars(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        kidModeSwitch = findViewById(R.id.kidModeSwitch);
        lockKidHome = findViewById(R.id.lockKidHome);
        lockApp = findViewById(R.id.lockApp);
        kidAppLabel = findViewById(R.id.kidAppLabel);
        browserLabel = findViewById(R.id.browserLabel);
        limitsNote = findViewById(R.id.limitsNote);
        escapeHelp = findViewById(R.id.escapeHelp);
        childNameInput = findViewById(R.id.childNameInput);
        childAvatarPreview = findViewById(R.id.childAvatarPreview);
        dailyLimitInput = findViewById(R.id.dailyLimitInput);
        dailyLimitLabel = findViewById(R.id.dailyLimitLabel);
        dailyLimitSlider = findViewById(R.id.dailyLimitSlider);
        escapeCorner = findViewById(R.id.escapeCornerCB);
        escapeBackHold = findViewById(R.id.escapeBackHoldCB);
        touchSounds = findViewById(R.id.touchSoundsCB);
        screenTimeEnabled = findViewById(R.id.screenTimeEnabled);
        bedtimeEnabled = findViewById(R.id.bedtimeEnabled);
        pickAppButton = findViewById(R.id.pickAppButton);
        bedtimeStartButton = findViewById(R.id.bedtimeStartButton);
        bedtimeEndButton = findViewById(R.id.bedtimeEndButton);

        limitsNote.setText(R.string.kid_mode_limits);
        escapeHelp.setText(R.string.kid_mode_escape_help);

        kidModeSwitch.setChecked(Prefs.isKidModeEnabled(this));
        boolean lockToApp = Prefs.isKidLockToApp(this);
        lockKidHome.setChecked(!lockToApp);
        lockApp.setChecked(lockToApp);
        escapeCorner.setChecked(Prefs.isEscapeCornerEnabled(this));
        escapeBackHold.setChecked(Prefs.isEscapeBackHoldEnabled(this));
        touchSounds.setChecked(Prefs.isTouchSoundsEnabled(this));
        screenTimeEnabled.setChecked(Prefs.isScreenTimeEnabled(this));
        bedtimeEnabled.setChecked(Prefs.isBedtimeEnabled(this));
        childNameInput.setText(Prefs.getChildName(this));
        pendingAvatar = Prefs.getChildAvatar(this);
        childAvatarPreview.setText(pendingAvatar);
        int limit = Prefs.getDailyLimitMinutes(this);
        dailyLimitInput.setText(String.valueOf(limit));
        dailyLimitSlider.setValue(Math.max(15, Math.min(240, limit)));
        dailyLimitLabel.setText(getString(R.string.daily_limit_slider_fmt, Math.round(dailyLimitSlider.getValue())));
        dailyLimitSlider.addOnChangeListener((slider, value, fromUser) -> {
            int minutes = Math.round(value);
            dailyLimitLabel.setText(getString(R.string.daily_limit_slider_fmt, minutes));
            dailyLimitInput.setText(String.valueOf(minutes));
        });
        bedtimeStartMinutes = Prefs.getBedtimeStartMinutes(this);
        bedtimeEndMinutes = Prefs.getBedtimeEndMinutes(this);
        pendingAppPackage = Prefs.getKidAppPackage(this);
        pendingAppActivity = Prefs.getKidAppActivity(this);
        refreshKidAppLabel();
        refreshBrowserLabel();
        refreshBedtimeButtons();
        refreshEnabledState();

        findViewById(R.id.pickAvatarButton).setOnClickListener(v -> showAvatarPicker());
        if (getIntent().getBooleanExtra(EXTRA_SCROLL_TO_TIME, false)) {
            View card = findViewById(R.id.screenTimeCard);
            NestedScrollView scroll = findScrollParent(card);
            if (scroll != null && card != null) {
                card.post(() -> scroll.smoothScrollTo(0, card.getTop()));
            }
        }

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
        lockKidHome.setOnClickListener(radioClick);
        lockApp.setOnClickListener(radioClick);

        pickAppButton.setOnClickListener(v -> showAppPicker());
        findViewById(R.id.manageAllowedApps).setOnClickListener(v ->
                startActivity(new Intent(this, AllowedAppsActivity.class)));
        findViewById(R.id.manageSites).setOnClickListener(v ->
                startActivity(new Intent(this, SiteShortcutsActivity.class)));
        findViewById(R.id.pickBrowser).setOnClickListener(v -> showBrowserPicker());

        bedtimeStartButton.setOnClickListener(v -> pickTime(true));
        bedtimeEndButton.setOnClickListener(v -> pickTime(false));

        findViewById(R.id.openSetupButton).setOnClickListener(v ->
                startActivity(new Intent(this, SetupActivity.class)));
        findViewById(R.id.changePinButton).setOnClickListener(v -> changePin());
        findViewById(R.id.saveKidModeButton).setOnClickListener(v -> save());
        findViewById(R.id.cancelKidModeButton).setOnClickListener(v -> finish());
    }

    private NestedScrollView findScrollParent(View view) {
        View current = view;
        while (current != null) {
            if (current instanceof NestedScrollView) {
                return (NestedScrollView) current;
            }
            if (!(current.getParent() instanceof View)) {
                return null;
            }
            current = (View) current.getParent();
        }
        return null;
    }

    private void showAvatarPicker() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_avatar)
                .setItems(ChildAvatars.ALL, (d, which) -> {
                    pendingAvatar = ChildAvatars.ALL[which];
                    childAvatarPreview.setText(pendingAvatar);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void changePin() {
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
    }

    private void refreshEnabledState() {
        boolean enabled = kidModeSwitch.isChecked();
        lockKidHome.setEnabled(enabled);
        lockApp.setEnabled(enabled);
        pickAppButton.setEnabled(enabled && lockApp.isChecked());
        findViewById(R.id.manageAllowedApps).setEnabled(enabled && lockKidHome.isChecked());
        findViewById(R.id.manageSites).setEnabled(enabled && lockKidHome.isChecked());
        findViewById(R.id.pickBrowser).setEnabled(enabled && lockKidHome.isChecked());
        escapeCorner.setEnabled(enabled);
        escapeBackHold.setEnabled(enabled);
        screenTimeEnabled.setEnabled(enabled);
        bedtimeEnabled.setEnabled(enabled);
        dailyLimitInput.setEnabled(enabled);
        bedtimeStartButton.setEnabled(enabled);
        bedtimeEndButton.setEnabled(enabled);
        childNameInput.setEnabled(enabled);
    }

    private void refreshKidAppLabel() {
        if (pendingAppPackage == null || pendingAppPackage.isEmpty()) {
            kidAppLabel.setText(R.string.kid_app_none);
        } else {
            kidAppLabel.setText(getString(R.string.kid_app_selected, pendingAppPackage));
        }
    }

    private void refreshBrowserLabel() {
        String pkg = Prefs.getBrowserPackage(this);
        if (pkg == null || pkg.isEmpty()) {
            browserLabel.setText(R.string.browser_default);
        } else {
            browserLabel.setText(getString(R.string.browser_selected, pkg));
        }
    }

    private void refreshBedtimeButtons() {
        bedtimeStartButton.setText(getString(R.string.bedtime_start_fmt, formatMinutes(bedtimeStartMinutes)));
        bedtimeEndButton.setText(getString(R.string.bedtime_end_fmt, formatMinutes(bedtimeEndMinutes)));
    }

    private static String formatMinutes(int minutes) {
        int h = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60) / 60;
        int m = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private void pickTime(boolean start) {
        int current = start ? bedtimeStartMinutes : bedtimeEndMinutes;
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            int value = hourOfDay * 60 + minute;
            if (start) {
                bedtimeStartMinutes = value;
            } else {
                bedtimeEndMinutes = value;
            }
            refreshBedtimeButtons();
        }, current / 60, current % 60, true).show();
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

    private void showBrowserPicker() {
        final List<ResolveInfo> browsers = KidHomeActivity.queryBrowsers(getPackageManager());
        CharSequence[] labels = new CharSequence[browsers.size() + 1];
        labels[0] = getString(R.string.browser_default);
        for (int i = 0; i < browsers.size(); i++) {
            labels[i + 1] = browsers.get(i).loadLabel(getPackageManager())
                    + " (" + browsers.get(i).activityInfo.packageName + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_browser)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        Prefs.setBrowser(this, "", "");
                    } else {
                        ResolveInfo info = browsers.get(which - 1);
                        Prefs.setBrowser(this, info.activityInfo.packageName, info.activityInfo.name);
                    }
                    refreshBrowserLabel();
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

        Prefs.setChildProfile(
                this,
                childNameInput.getText() != null ? childNameInput.getText().toString() : "",
                pendingAvatar
        );
        Prefs.setEscapeOptions(this, corner, backHold);
        Prefs.setTouchSoundsEnabled(this, touchSounds.isChecked());
        Prefs.setScreenTimeEnabled(this, screenTimeEnabled.isChecked());
        Prefs.setBedtimeEnabled(this, bedtimeEnabled.isChecked());
        Prefs.setBedtimeWindow(this, bedtimeStartMinutes, bedtimeEndMinutes);
        try {
            int limit = Integer.parseInt(dailyLimitInput.getText() != null
                    ? dailyLimitInput.getText().toString().trim() : "60");
            Prefs.setDailyLimitMinutes(this, limit);
        } catch (Exception e) {
            Prefs.setDailyLimitMinutes(this, Math.round(dailyLimitSlider.getValue()));
        }

        if (enable) {
            if (!corner && !backHold) {
                Toast.makeText(this, R.string.escape_option_required, Toast.LENGTH_LONG).show();
                return;
            }
            if (!Prefs.hasKidPin(this)) {
                Toast.makeText(this, R.string.pin_required, Toast.LENGTH_LONG).show();
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
                Prefs.ensureDefaultSiteSuggestions(this);
                if (Prefs.getAllowedPackages(this).isEmpty() && Prefs.getSiteShortcuts(this).isEmpty()) {
                    Toast.makeText(this, R.string.kid_home_empty_warning, Toast.LENGTH_LONG).show();
                }
            }
            if (corner && !ensureOverlayIfNeeded()) {
                Toast.makeText(this, R.string.overlay_permission_later, Toast.LENGTH_LONG).show();
            }
            Prefs.setKidModeEnabled(this, true);
            EnforcementController.onKidModeEnabled(this);
            EscapeOverlayNotifier.requestRefresh(this);
            Toast.makeText(this, R.string.kid_mode_enabled, Toast.LENGTH_LONG).show();
            // Guide parent through missing setup steps when needed.
            if (SetupChecklist.countMissingRequired(this) > 0) {
                startActivity(new Intent(this, SetupActivity.class));
            }
            HomePress.perform(this, true);
        } else {
            EnforcementController.onKidModeDisabled(this);
            Prefs.setKidModeEnabled(this, false);
            SessionAuth.markUnlocked();
            PinAttemptTracker.attach(this);
            PinAttemptTracker.reset();
            KidModeGate.markEscapeUiClosed();
            EscapeOverlayNotifier.requestRefresh(this);
            Toast.makeText(this, R.string.kid_mode_disabled, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
