package io.github.marcelsavegnago.kidshome;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;

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
        bindParentChrome(R.id.parentRoot, R.id.appLimitsToolbar);

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
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_limit, null, false);
        TextView valueLabel = dialogView.findViewById(R.id.limitDialogValue);
        Slider slider = dialogView.findViewById(R.id.limitDialogSlider);
        Chip preset15 = dialogView.findViewById(R.id.preset15);
        Chip preset30 = dialogView.findViewById(R.id.preset30);
        Chip preset60 = dialogView.findViewById(R.id.preset60);
        Chip presetUnlimited = dialogView.findViewById(R.id.presetUnlimited);

        final int[] selected = {
                current < 0 ? RuleEngine.UNLIMITED_MINUTES : Math.max(0, Math.min(120, current))
        };
        Runnable refreshLabel = () -> {
            if (selected[0] < 0) {
                valueLabel.setText(R.string.app_time_unlimited);
                presetUnlimited.setChecked(true);
            } else {
                valueLabel.setText(getString(R.string.app_limit_minutes_fmt, selected[0]));
                slider.setValue(selected[0]);
                preset15.setChecked(selected[0] == 15);
                preset30.setChecked(selected[0] == 30);
                preset60.setChecked(selected[0] == 60);
                presetUnlimited.setChecked(false);
            }
        };
        if (selected[0] >= 0) {
            slider.setValue(selected[0]);
        } else {
            slider.setValue(30);
        }
        refreshLabel.run();

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            selected[0] = Math.round(value);
            refreshLabel.run();
        });
        preset15.setOnClickListener(v -> {
            selected[0] = 15;
            refreshLabel.run();
        });
        preset30.setOnClickListener(v -> {
            selected[0] = 30;
            refreshLabel.run();
        });
        preset60.setOnClickListener(v -> {
            selected[0] = 60;
            refreshLabel.run();
        });
        presetUnlimited.setOnClickListener(v -> {
            selected[0] = RuleEngine.UNLIMITED_MINUTES;
            refreshLabel.run();
        });

        Dialogs.builder(this)
                .setTitle(labelFor(packageName))
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Prefs.setAppLimitMinutes(this, packageName, selected[0]);
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
            long used = Prefs.getAppUsedMillisToday(AppLimitsActivity.this, pkg);
            long rem = Prefs.remainingAppMillis(AppLimitsActivity.this, pkg);
            if (limit < 0) {
                holder.summary.setText(R.string.app_time_unlimited);
                holder.progress.setMax(100);
                holder.progress.setProgress(0);
                holder.progress.setVisibility(View.INVISIBLE);
            } else {
                long remMin = rem <= 0L ? 0L : Math.max(0L, (rem + 59_999L) / 60_000L);
                holder.summary.setText(getString(R.string.app_limit_summary_fmt, limit, remMin));
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setMax(Math.max(1, limit));
                int usedMin = (int) Math.min(limit, Math.max(0L, (used + 59_999L) / 60_000L));
                holder.progress.setProgress(usedMin);
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
            final ProgressBar progress;

            Holder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appLimitIcon);
                name = itemView.findViewById(R.id.appLimitName);
                summary = itemView.findViewById(R.id.appLimitSummary);
                edit = itemView.findViewById(R.id.appLimitEdit);
                progress = itemView.findViewById(R.id.appLimitProgress);
            }
        }
    }
}
