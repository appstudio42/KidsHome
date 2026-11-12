package io.github.marcelsavegnago.kidshome;

import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** PIN-gated multi-select of apps allowed on the kid home grid. */
public class AllowedAppsActivity extends AppCompatActivity {
    private AppAdapter adapter;
    private ListView listView;
    private List<ResolveInfo> apps;
    private final Set<String> selected = new HashSet<>();

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
        setContentView(R.layout.activity_allowed_apps);
        setTitle(R.string.allowed_apps_title);

        selected.addAll(Prefs.getAllowedPackages(this));
        apps = PackageUtils.getLaunchableApps(this);
        listView = findViewById(R.id.allowedAppsList);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new AppAdapter(this, apps, getPackageManager());
        listView.setAdapter(adapter);

        for (int i = 0; i < apps.size(); i++) {
            if (selected.contains(apps.get(i).activityInfo.packageName)) {
                listView.setItemChecked(i, true);
            }
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = apps.get(position).activityInfo.packageName;
            if (listView.isItemChecked(position)) {
                selected.add(pkg);
            } else {
                selected.remove(pkg);
            }
        });

        findViewById(R.id.allowedAppsSave).setOnClickListener(v -> {
            // Also keep the configured browser allowed.
            String browser = Prefs.getBrowserPackage(this);
            if (browser != null && !browser.isEmpty()) {
                selected.add(browser);
            }
            Prefs.setAllowedPackages(this, selected);
            Toast.makeText(this, R.string.allowed_apps_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.allowedAppsCancel).setOnClickListener(v -> finish());
    }
}
