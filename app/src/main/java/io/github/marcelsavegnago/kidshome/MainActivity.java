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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ListView listApps;
    private TextView statusBanner;
    private MenuItem launcherFilter;
    private MenuItem hideSystemApps;
    private int prevSelectedIndex = 0;
    private boolean awaitingPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        listApps = findViewById(R.id.lvApps);
        statusBanner = findViewById(R.id.statusBanner);

        ServiceMan.start(this);
        refreshStatusBanner();

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
