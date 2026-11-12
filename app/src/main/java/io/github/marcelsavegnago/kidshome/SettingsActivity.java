package io.github.marcelsavegnago.kidshome;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private CheckBox hardwareDetection;
    private CheckBox launcherOpen;
    private CheckBox broadcastReceiver;
    private CheckBox overlayDetection;
    private CheckBox disableWhileMenuHeld;
    private CheckBox disableInTaskSwitcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.isKidModeEnabled(this) && !SessionAuth.isUnlocked()) {
            PinDialogHelper.promptVerify(this, new PinDialogHelper.PinCallback() {
                @Override
                public void onSuccess() {
                    inflateSettings();
                }

                @Override
                public void onCancel() {
                    finish();
                }
            });
            return;
        }
        inflateSettings();
    }

    private void inflateSettings() {
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);

        hardwareDetection = findViewById(R.id.hardwareCB);
        launcherOpen = findViewById(R.id.openApplicationCB);
        broadcastReceiver = findViewById(R.id.broadcastCB);
        overlayDetection = findViewById(R.id.overlayCB);
        disableWhileMenuHeld = findViewById(R.id.menuButtonOverrideCB);
        disableInTaskSwitcher = findViewById(R.id.taskSwitchCB);

        hardwareDetection.setChecked(Prefs.hardwareDetection(this));
        launcherOpen.setChecked(Prefs.applicationOpenDetection(this));
        broadcastReceiver.setChecked(Prefs.broadcastDetection(this));
        overlayDetection.setChecked(Prefs.overlayDetection(this));
        disableWhileMenuHeld.setChecked(Prefs.menuButtonOverride(this));
        disableInTaskSwitcher.setChecked(Prefs.recentAppOverride(this));

        bindRow(R.id.hardwareCBView, hardwareDetection);
        bindRow(R.id.openApplicationCBView, launcherOpen);
        bindRow(R.id.broadcastCBView, broadcastReceiver);
        bindRow(R.id.overlayCBView, overlayDetection);
        bindRow(R.id.menuButtonOverrideCBView, disableWhileMenuHeld);
        bindRow(R.id.taskSwitchCBView, disableInTaskSwitcher);

        findViewById(R.id.saveButton).setOnClickListener(v -> {
            Prefs.saveDetectionSettings(
                    this,
                    hardwareDetection.isChecked(),
                    launcherOpen.isChecked(),
                    broadcastReceiver.isChecked(),
                    overlayDetection.isChecked(),
                    disableWhileMenuHeld.isChecked(),
                    disableInTaskSwitcher.isChecked()
            );
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());
    }

    private void bindRow(int viewId, final CheckBox checkBox) {
        View row = findViewById(viewId);
        row.setOnClickListener(v -> checkBox.toggle());
        row.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setBackgroundColor(getResources().getColor(R.color.colorAccentSoft));
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
            return false;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settingsmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.loadDefaults) {
            Prefs.loadDetectionDefaults(this);
            hardwareDetection.setChecked(Prefs.hardwareDetection(this));
            launcherOpen.setChecked(Prefs.applicationOpenDetection(this));
            broadcastReceiver.setChecked(Prefs.broadcastDetection(this));
            overlayDetection.setChecked(Prefs.overlayDetection(this));
            disableWhileMenuHeld.setChecked(Prefs.menuButtonOverride(this));
            disableInTaskSwitcher.setChecked(Prefs.recentAppOverride(this));
            Toast.makeText(this, R.string.defaults_loaded, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
