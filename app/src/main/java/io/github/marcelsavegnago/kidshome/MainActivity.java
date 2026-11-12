package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ListView listApps;
    private TextView statusBanner;
    private View launcherSection;
    private MenuItem launcherFilter;
    private MenuItem hideSystemApps;
    private int prevSelectedIndex = 0;
    private boolean awaitingPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrateIfNeeded(this);

        if (!Prefs.isOnboardingDone(this) && !Prefs.isKidModeEnabled(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        if (Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
            awaitingPin = true;
            setContentView(R.layout.activity_pin_gate);
            findViewById(R.id.btnUnlock).setOnClickListener(v -> requestPinThenContinue());
            findViewById(R.id.btnBackToKidApp).setOnClickListener(v -> {
                HomePress.perform(this, true);
                finish();
            });
            return;
        }

        showMainUi();
    }

    private void requestPinThenContinue() {
        PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
            @Override
            public void onSuccess() {
                awaitingPin = false;
                showMainUi();
            }

            @Override
            public void onCancel() {
                // Stay on gate.
            }
        });
    }

    private void showMainUi() {
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        listApps = findViewById(R.id.lvApps);
        statusBanner = findViewById(R.id.statusBanner);
        launcherSection = findViewById(R.id.launcherSection);

        bindHubCard(R.id.cardChild, R.drawable.ic_section_child,
                R.string.section_child_profile, R.string.section_child_profile_sub,
                v -> startActivity(new Intent(this, KidModeActivity.class)));
        bindHubCard(R.id.cardApps, R.drawable.ic_section_apps,
                R.string.section_allowed_apps, R.string.section_allowed_apps_sub,
                v -> startActivity(new Intent(this, AllowedAppsActivity.class)));
        bindHubCard(R.id.cardWeb, R.drawable.ic_section_web,
                R.string.section_websites, R.string.section_websites_sub,
                v -> startActivity(new Intent(this, SiteShortcutsActivity.class)));
        bindHubCard(R.id.cardTime, R.drawable.ic_section_time,
                R.string.section_screen_time, R.string.section_screen_time_sub,
                v -> {
                    Intent intent = new Intent(this, KidModeActivity.class);
                    intent.putExtra(KidModeActivity.EXTRA_SCROLL_TO_TIME, true);
                    startActivity(intent);
                });
        bindHubCard(R.id.cardDevice, R.drawable.ic_section_device,
                R.string.section_device_setup, R.string.section_device_setup_sub,
                v -> startActivity(new Intent(this, SetupActivity.class)));
        bindHubCard(R.id.cardSecurity, R.drawable.ic_section_security,
                R.string.section_security, R.string.section_security_sub,
                v -> startActivity(new Intent(this, KidModeActivity.class)));
        bindHubCard(R.id.cardAbout, R.drawable.ic_section_about,
                R.string.about, R.string.section_about_sub,
                v -> startActivity(new Intent(this, AboutActivity.class)));

        ServiceMan.start(this);
        refreshStatusBanner();
        refreshLauncherSection();

        listApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int pos, long id) {
                AppAdapter adapter = (AppAdapter) parent.getAdapter();
                final ResolveInfo appInfo = (ResolveInfo) adapter.getItem(pos);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.set_launcher_title)
                        .setMessage(getString(
                                R.string.set_launcher_message,
                                appInfo.loadLabel(getPackageManager()),
                                appInfo.activityInfo.packageName
                        ))
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prevSelectedIndex = pos;
                                Prefs.setChosenLauncher(
                                        MainActivity.this,
                                        appInfo.activityInfo.packageName,
                                        appInfo.activityInfo.name
                                );
                                Toast.makeText(MainActivity.this, R.string.launcher_saved, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                            listApps.setSelection(prevSelectedIndex);
                            listApps.setItemChecked(prevSelectedIndex, true);
                            dialog.dismiss();
                        })
                        .show();
            }
        });

        EnforcementMode mode = EnforcementController.getEffectiveMode(this);
        if (EnforcementController.needsAccessibilityService(this)
                && !AccessibilityHelper.isAccessibilityEnabled(this)) {
            showAccessibilityDialog();
        } else if (mode == EnforcementMode.ACCESSIBILITY && !Prefs.hasChosenLauncher(this)) {
            Toast.makeText(this, R.string.please_select_launcher, Toast.LENGTH_LONG).show();
        } else if (SetupChecklist.countMissingRequired(this) > 0) {
            Toast.makeText(this, R.string.setup_incomplete_hint, Toast.LENGTH_LONG).show();
        }
    }

    private void bindHubCard(int includeId, int iconRes, int titleRes, int subtitleRes, View.OnClickListener click) {
        View card = findViewById(includeId);
        if (card == null) {
            return;
        }
        ImageView icon = card.findViewById(R.id.hubIcon);
        TextView title = card.findViewById(R.id.hubTitle);
        TextView subtitle = card.findViewById(R.id.hubSubtitle);
        if (icon != null) {
            icon.setImageResource(iconRes);
        }
        if (title != null) {
            title.setText(titleRes);
        }
        if (subtitle != null) {
            subtitle.setText(subtitleRes);
        }
        card.setOnClickListener(click);
        if (card instanceof MaterialCardView) {
            card.setContentDescription(getString(titleRes));
        }
    }

    private void refreshLauncherSection() {
        if (launcherSection == null) {
            return;
        }
        EnforcementMode mode = EnforcementController.getEffectiveMode(this);
        boolean show = mode == EnforcementMode.ACCESSIBILITY
                || Prefs.getEnforcementModePreference(this) == EnforcementMode.ACCESSIBILITY
                || (Prefs.getEnforcementModePreference(this) == EnforcementMode.AUTO
                && DeviceDetector.isFireOs(this));
        launcherSection.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void refreshStatusBanner() {
        if (statusBanner == null) {
            return;
        }
        EnforcementMode mode = EnforcementController.getEffectiveMode(this);
        int missing = SetupChecklist.countMissingRequired(this);
        if (missing > 0) {
            statusBanner.setVisibility(View.VISIBLE);
            statusBanner.setText(getString(R.string.banner_setup_incomplete, missing));
            statusBanner.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        } else if (EnforcementController.needsAccessibilityService(this)
                && !AccessibilityHelper.isAccessibilityEnabled(this)) {
            statusBanner.setVisibility(View.VISIBLE);
            statusBanner.setText(R.string.banner_a11y_disabled);
            statusBanner.setOnClickListener(v -> AccessibilityHelper.openAccessibilitySettings(this));
        } else if (mode == EnforcementMode.ACCESSIBILITY
                && (!Prefs.hasChosenLauncher(this)
                || !PackageUtils.isPackageInstalled(this, Prefs.getChosenLauncherPackage(this)))) {
            statusBanner.setVisibility(View.VISIBLE);
            statusBanner.setText(R.string.banner_no_launcher);
            statusBanner.setOnClickListener(null);
        } else if (mode == EnforcementMode.LAUNCHER && !LauncherHelper.isDefaultHome(this)) {
            statusBanner.setVisibility(View.VISIBLE);
            statusBanner.setText(R.string.banner_not_default_home);
            statusBanner.setOnClickListener(v -> LauncherHelper.openDefaultHomePicker(this));
        } else if (Prefs.isKidModeEnabled(this)) {
            statusBanner.setVisibility(View.VISIBLE);
            statusBanner.setText(R.string.banner_kid_mode_on);
            statusBanner.setOnClickListener(null);
        } else {
            statusBanner.setVisibility(View.GONE);
        }
    }

    private void showAccessibilityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.a11y_disabled_title)
                .setMessage(R.string.a11y_disabled_message)
                .setCancelable(true)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.setup_title, (d, w) ->
                        startActivity(new Intent(this, SetupActivity.class)));

        if (!getPackageManager().hasSystemFeature("android.software.leanback")) {
            builder.setNeutralButton(R.string.open_settings,
                    (d, w) -> AccessibilityHelper.openAccessibilitySettings(this));
        }
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!awaitingPin) {
            refreshStatusBanner();
            refreshLauncherSection();
            if (launcherFilter != null) {
                updateList();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (awaitingPin) {
            return false;
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        hideSystemApps = menu.findItem(R.id.sysApps);
        launcherFilter = menu.findItem(R.id.launcher);
        if (launcherFilter != null) {
            launcherFilter.setChecked(true);
        }
        if (hideSystemApps != null) {
            hideSystemApps.setChecked(true);
        }
        updateList();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.launcher) {
            item.setChecked(!item.isChecked());
            if (item.isChecked() && hideSystemApps != null) {
                hideSystemApps.setChecked(true);
            }
            updateList();
            return true;
        }
        if (id == R.id.sysApps) {
            item.setChecked(!item.isChecked());
            updateList();
            return true;
        }
        if (id == R.id.help) {
            openHelp();
            return true;
        }
        if (id == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.kidMode) {
            startActivity(new Intent(this, KidModeActivity.class));
            return true;
        }
        if (id == R.id.setup) {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        }
        if (id == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openHelp() {
        Intent helpIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/marcelsavegnago/KidsHome#readme"));
        startActivity(helpIntent);
    }

    private void updateList() {
        if (listApps == null || launcherFilter == null || hideSystemApps == null) {
            return;
        }
        boolean launchersOnly = launcherFilter.isChecked();
        boolean hideSystem = hideSystemApps.isChecked();
        List<ResolveInfo> appInfo = PackageUtils.getInstalledApplications(this, launchersOnly, hideSystem);
        AppAdapter adapter = new AppAdapter(this, appInfo, getPackageManager());
        listApps.setAdapter(adapter);

        String selectedPackage = Prefs.getChosenLauncherPackage(this);
        for (int i = 0; i < appInfo.size(); i++) {
            if (appInfo.get(i).activityInfo.packageName.equals(selectedPackage)) {
                prevSelectedIndex = i;
                listApps.setSelection(i);
                listApps.setItemChecked(i, true);
                break;
            }
        }
    }
}
