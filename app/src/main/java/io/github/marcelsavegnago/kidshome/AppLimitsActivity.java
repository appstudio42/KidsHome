package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Parent editor for per-app daily limits. */
public class AppLimitsActivity extends ParentPinActivity {
    private final List<String> packages = new ArrayList<>();
    private LimitsAdapter adapter;
    private PackageManager pm;

    @Override
    protected void onPinUnlocked() {
        setContentView(R.layout.activity_app_limits);
        MaterialToolbar toolbar = findViewById(R.id.appLimitsToolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applySystemBars(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        pm = getPackageManager();
        packages.clear();
        packages.addAll(Prefs.getAllowedPackages(this));
        RecyclerView list = findViewById(R.id.appLimitsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LimitsAdapter();
        list.setAdapter(adapter);
    }

    private String labelFor(String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = info.loadLabel(pm);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }

    private void editLimit(String packageName) {
        int current = Prefs.getAppLimitMinutes(this, packageName);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.app_limit_hint);
        if (current >= 0) {
            input.setText(String.valueOf(current));
        }
        new AlertDialog.Builder(this)
                .setTitle(labelFor(packageName))
                .setMessage(R.string.app_limit_dialog_message)
                .setView(input)
                .setNeutralButton(R.string.app_limit_unlimited, (d, w) -> {
                    Prefs.setAppLimitMinutes(this, packageName, RuleEngine.UNLIMITED_MINUTES);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.app_limits_saved, Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton(R.string.save, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (text.isEmpty()) {
                        Prefs.setAppLimitMinutes(this, packageName, RuleEngine.UNLIMITED_MINUTES);
                    } else {
                        try {
                            int minutes = Integer.parseInt(text);
                            Prefs.setAppLimitMinutes(this, packageName, Math.max(0, minutes));
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, R.string.app_limit_invalid, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.app_limits_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class LimitsAdapter extends RecyclerView.Adapter<LimitsAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_limit, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String pkg = packages.get(position);
            holder.name.setText(labelFor(pkg));
            try {
                holder.icon.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (Exception e) {
                holder.icon.setImageResource(R.drawable.ic_notification);
            }
            int limit = Prefs.getAppLimitMinutes(AppLimitsActivity.this, pkg);
            long rem = Prefs.remainingAppMillis(AppLimitsActivity.this, pkg);
            if (limit < 0) {
                holder.summary.setText(R.string.app_time_unlimited);
            } else {
                long remMin = rem <= 0L ? 0L : Math.max(0L, (rem + 59_999L) / 60_000L);
                holder.summary.setText(getString(
                        R.string.app_limit_summary_fmt,
                        limit,
                        remMin
                ));
            }
            holder.edit.setOnClickListener(v -> editLimit(pkg));
            holder.itemView.setOnClickListener(v -> editLimit(pkg));
        }

        @Override
        public int getItemCount() {
            return packages.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView summary;
            final MaterialButton edit;

            Holder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appLimitIcon);
                name = itemView.findViewById(R.id.appLimitName);
                summary = itemView.findViewById(R.id.appLimitSummary);
                edit = itemView.findViewById(R.id.appLimitEdit);
            }
        }
    }
}
