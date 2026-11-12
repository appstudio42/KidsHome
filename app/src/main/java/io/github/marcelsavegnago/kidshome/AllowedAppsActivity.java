package io.github.marcelsavegnago.kidshome;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** PIN-gated multi-select of apps allowed on the kid home grid. */
public class AllowedAppsActivity extends AppCompatActivity {
    private final Set<String> selected = new HashSet<>();
    private final List<ResolveInfo> allApps = new ArrayList<>();
    private final List<ResolveInfo> visibleApps = new ArrayList<>();
    private AllowedAdapter adapter;
    private PackageManager pm;

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
        MaterialToolbar toolbar = findViewById(R.id.allowedAppsToolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applySystemBars(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        pm = getPackageManager();
        selected.addAll(Prefs.getAllowedPackages(this));
        allApps.clear();
        allApps.addAll(PackageUtils.getLaunchableApps(this));
        visibleApps.clear();
        visibleApps.addAll(allApps);

        RecyclerView list = findViewById(R.id.allowedAppsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AllowedAdapter();
        list.setAdapter(adapter);

        TextInputEditText search = findViewById(R.id.allowedAppsSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.allowedAppsSelectAll).setOnClickListener(v -> {
            for (ResolveInfo info : visibleApps) {
                selected.add(info.activityInfo.packageName);
            }
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.allowedAppsClear).setOnClickListener(v -> {
            for (ResolveInfo info : visibleApps) {
                selected.remove(info.activityInfo.packageName);
            }
            adapter.notifyDataSetChanged();
        });

        findViewById(R.id.allowedAppsSave).setOnClickListener(v -> {
            String browser = Prefs.getBrowserPackage(this);
            if (browser != null && !browser.isEmpty()) {
                selected.add(browser);
            }
            Prefs.setAllowedPackages(this, selected);
            DeviceOwnerHelper.refreshLockTaskPackages(this);
            Toast.makeText(this, R.string.allowed_apps_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.allowedAppsCancel).setOnClickListener(v -> finish());
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase(Locale.getDefault());
        visibleApps.clear();
        if (q.isEmpty()) {
            visibleApps.addAll(allApps);
        } else {
            for (ResolveInfo info : allApps) {
                CharSequence label = info.loadLabel(pm);
                String name = label != null ? label.toString().toLowerCase(Locale.getDefault()) : "";
                String pkg = info.activityInfo.packageName.toLowerCase(Locale.getDefault());
                if (name.contains(q) || pkg.contains(q)) {
                    visibleApps.add(info);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private final class AllowedAdapter extends RecyclerView.Adapter<AllowedAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_allowed_app, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ResolveInfo info = visibleApps.get(position);
            String pkg = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            holder.label.setText(label != null ? label : pkg);
            holder.pkg.setText(pkg);
            holder.icon.setImageDrawable(info.loadIcon(pm));
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(selected.contains(pkg));
            holder.itemView.setContentDescription(
                    getString(R.string.cd_allowed_app_row, holder.label.getText()));
            View.OnClickListener toggleClick = v -> {
                boolean next = !selected.contains(pkg);
                if (next) {
                    selected.add(pkg);
                } else {
                    selected.remove(pkg);
                }
                holder.toggle.setChecked(next);
            };
            holder.itemView.setOnClickListener(toggleClick);
            holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selected.add(pkg);
                } else {
                    selected.remove(pkg);
                }
            });
        }

        @Override
        public int getItemCount() {
            return visibleApps.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            final TextView pkg;
            final MaterialSwitch toggle;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
                pkg = itemView.findViewById(R.id.appPackage);
                toggle = itemView.findViewById(R.id.appToggle);
            }
        }
    }
}
