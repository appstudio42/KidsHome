package io.github.marcelsavegnago.kidshome;

import android.app.TimePickerDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parent editor for weekday routine windows and app categories. */
public class SchedulesActivity extends ParentPinActivity {
    private final List<ScheduleWindow> windows = new ArrayList<>();
    private LinearLayout schedulesList;
    private LinearLayout categoriesList;
    private MaterialSwitch schedulesEnabled;

    @Override
    protected void onPinUnlocked() {
        setContentView(R.layout.activity_schedules);
        bindParentChrome(R.id.parentRoot, R.id.schedulesToolbar);

        schedulesList = findViewById(R.id.schedulesList);
        categoriesList = findViewById(R.id.categoriesList);
        schedulesEnabled = findViewById(R.id.schedulesEnabled);
        schedulesEnabled.setChecked(Prefs.isSchedulesEnabled(this));
        schedulesEnabled.setOnCheckedChangeListener((b, checked) ->
                Prefs.setSchedulesEnabled(this, checked));

        windows.clear();
        windows.addAll(Prefs.getSchedules(this));
        findViewById(R.id.schedulesAdd).setOnClickListener(v -> editWindow(null, -1));
        refreshSchedules();
        refreshCategories();
    }

    private void refreshSchedules() {
        schedulesList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < windows.size(); i++) {
            final int index = i;
            ScheduleWindow window = windows.get(i);
            View row = inflater.inflate(R.layout.item_schedule_row, schedulesList, false);
            TextView name = row.findViewById(R.id.scheduleName);
            TextView summary = row.findViewById(R.id.scheduleSummary);
            MaterialSwitch enabled = row.findViewById(R.id.scheduleEnabled);
            name.setText(window.name);
            summary.setText(summarize(window));
            enabled.setOnCheckedChangeListener(null);
            enabled.setChecked(window.enabled);
            enabled.setOnCheckedChangeListener((b, checked) -> {
                windows.set(index, copyWithEnabled(window, checked));
                persist();
                refreshSchedules();
            });
            row.findViewById(R.id.scheduleEdit).setOnClickListener(v -> editWindow(window, index));
            row.findViewById(R.id.scheduleDelete).setOnClickListener(v -> {
                windows.remove(index);
                persist();
                refreshSchedules();
            });
            schedulesList.addView(row);
        }
    }

    private ScheduleWindow copyWithEnabled(ScheduleWindow window, boolean enabled) {
        return new ScheduleWindow(
                window.id, window.name, enabled, window.daysMask,
                window.startMinutes, window.endMinutes, window.mode, window.categories
        );
    }

    private void persist() {
        Prefs.setSchedules(this, windows);
    }

    private String summarize(ScheduleWindow window) {
        String days = formatDays(window.daysMask);
        String start = formatMinutes(window.startMinutes);
        String end = formatMinutes(window.endMinutes);
        if (window.mode == ScheduleWindow.Mode.BEDTIME) {
            return getString(R.string.schedule_summary_bedtime, days, start, end);
        }
        String cats = formatCategories(window.categories);
        return getString(R.string.schedule_summary_filter, days, start, end, cats);
    }

    private String formatDays(int mask) {
        String[] labels = getResources().getStringArray(R.array.weekday_short);
        StringBuilder sb = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            if ((mask & (1 << (day - 1))) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(labels[day - 1]);
            }
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private String formatCategories(Set<AppCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return getString(R.string.category_other);
        }
        StringBuilder sb = new StringBuilder();
        for (AppCategory cat : categories) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(getString(cat.labelResId()));
        }
        return sb.toString();
    }

    private static String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private void editWindow(ScheduleWindow existing, int index) {
        final boolean[] days = new boolean[7];
        int mask = existing == null ? ScheduleWindow.weekdaysMask() : existing.daysMask;
        for (int i = 0; i < 7; i++) {
            days[i] = (mask & (1 << i)) != 0;
        }
        final int[] start = {existing == null ? 7 * 60 : existing.startMinutes};
        final int[] end = {existing == null ? 12 * 60 : existing.endMinutes};
        final int[] modeIndex = {existing != null && existing.mode == ScheduleWindow.Mode.BEDTIME ? 1 : 0};
        final boolean[] cats = new boolean[AppCategory.values().length];
        Set<AppCategory> existingCats = existing == null
                ? EnumSet.of(AppCategory.EDUCATION)
                : existing.categories;
        AppCategory[] allCats = AppCategory.values();
        for (int i = 0; i < allCats.length; i++) {
            cats[i] = existingCats.contains(allCats[i]);
        }
        showEditWizard(existing, index, days, start, end, modeIndex, cats);
    }

    private void showEditWizard(
            ScheduleWindow existing,
            int index,
            boolean[] days,
            int[] start,
            int[] end,
            int[] modeIndex,
            boolean[] cats
    ) {
        final EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.schedule_name_hint);
        if (existing != null) {
            nameInput.setText(existing.name);
        }

        Dialogs.builder(this)
                .setTitle(existing == null ? R.string.schedules_add : R.string.schedules_edit)
                .setView(nameInput)
                .setPositiveButton(R.string.next, (d, w) -> {
                    String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.schedule_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pickDays(existing, index, name, days, start, end, modeIndex, cats);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDays(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int[] start,
            int[] end,
            int[] modeIndex,
            boolean[] cats
    ) {
        Dialogs.builder(this)
                .setTitle(R.string.schedule_pick_days)
                .setMultiChoiceItems(R.array.weekday_full, days, (d, which, checked) -> days[which] = checked)
                .setPositiveButton(R.string.next, (d, w) ->
                        pickStart(existing, index, name, days, start, end, modeIndex, cats))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickStart(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int[] start,
            int[] end,
            int[] modeIndex,
            boolean[] cats
    ) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            start[0] = hourOfDay * 60 + minute;
            pickEnd(existing, index, name, days, start, end, modeIndex, cats);
        }, start[0] / 60, start[0] % 60, true).show();
    }

    private void pickEnd(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int[] start,
            int[] end,
            int[] modeIndex,
            boolean[] cats
    ) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            end[0] = hourOfDay * 60 + minute;
            pickMode(existing, index, name, days, start, end, modeIndex, cats);
        }, end[0] / 60, end[0] % 60, true).show();
    }

    private void pickMode(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int[] start,
            int[] end,
            int[] modeIndex,
            boolean[] cats
    ) {
        CharSequence[] modeLabels = {
                getString(R.string.schedule_mode_categories),
                getString(R.string.schedule_mode_bedtime)
        };
        Dialogs.builder(this)
                .setTitle(R.string.schedule_pick_mode)
                .setSingleChoiceItems(modeLabels, modeIndex[0], (d, which) -> modeIndex[0] = which)
                .setPositiveButton(R.string.next, (d, w) -> {
                    if (modeIndex[0] == 1) {
                        finishSave(existing, index, name, days, start[0], end[0],
                                ScheduleWindow.Mode.BEDTIME, EnumSet.noneOf(AppCategory.class));
                    } else {
                        pickCategories(existing, index, name, days, start, end, cats);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickCategories(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int[] start,
            int[] end,
            boolean[] cats
    ) {
        CharSequence[] labels = new CharSequence[AppCategory.values().length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(AppCategory.values()[i].labelResId());
        }
        Dialogs.builder(this)
                .setTitle(R.string.schedule_pick_categories)
                .setMultiChoiceItems(labels, cats, (d, which, checked) -> cats[which] = checked)
                .setPositiveButton(R.string.save, (d, w) -> {
                    EnumSet<AppCategory> selected = EnumSet.noneOf(AppCategory.class);
                    AppCategory[] all = AppCategory.values();
                    for (int i = 0; i < all.length; i++) {
                        if (cats[i]) {
                            selected.add(all[i]);
                        }
                    }
                    if (selected.isEmpty()) {
                        selected.add(AppCategory.EDUCATION);
                    }
                    finishSave(existing, index, name, days, start[0], end[0],
                            ScheduleWindow.Mode.ALLOW_CATEGORIES, selected);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void finishSave(
            ScheduleWindow existing,
            int index,
            String name,
            boolean[] days,
            int startMinutes,
            int endMinutes,
            ScheduleWindow.Mode mode,
            Set<AppCategory> categories
    ) {
        int mask = 0;
        for (int i = 0; i < 7; i++) {
            if (days[i]) {
                mask |= (1 << i);
            }
        }
        if (mask == 0) {
            mask = ScheduleWindow.everydayMask();
        }
        ScheduleWindow next = new ScheduleWindow(
                existing == null ? null : existing.id,
                name,
                existing == null || existing.enabled,
                mask,
                startMinutes,
                endMinutes,
                mode,
                categories
        );
        if (index >= 0 && index < windows.size()) {
            windows.set(index, next);
        } else {
            windows.add(next);
        }
        persist();
        Prefs.setSchedulesEnabled(this, true);
        schedulesEnabled.setChecked(true);
        refreshSchedules();
        Toast.makeText(this, R.string.schedules_saved, Toast.LENGTH_SHORT).show();
    }

    private void refreshCategories() {
        categoriesList.removeAllViews();
        PackageManager pm = getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String pkg : Prefs.getAllowedPackages(this)) {
            final String packageName = pkg;
            View row = inflater.inflate(R.layout.item_category_app, categoriesList, false);
            ImageView icon = row.findViewById(R.id.categoryAppIcon);
            TextView name = row.findViewById(R.id.categoryAppName);
            ChipGroup chips = row.findViewById(R.id.categoryChipGroup);
            Chip chipGames = row.findViewById(R.id.chipGames);
            Chip chipEducation = row.findViewById(R.id.chipEducation);
            Chip chipVideo = row.findViewById(R.id.chipVideo);
            Chip chipOther = row.findViewById(R.id.chipOther);

            String resolvedLabel = pkg;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence loaded = info.loadLabel(pm);
                if (loaded != null) {
                    resolvedLabel = loaded.toString();
                }
                icon.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (Exception e) {
                icon.setImageResource(R.drawable.ic_notification);
            }
            name.setText(resolvedLabel);

            AppCategory current = Prefs.getAppCategory(this, packageName);
            chipGames.setChecked(current == AppCategory.GAMES);
            chipEducation.setChecked(current == AppCategory.EDUCATION);
            chipVideo.setChecked(current == AppCategory.VIDEO);
            chipOther.setChecked(current == AppCategory.OTHER);

            chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    return;
                }
                int id = checkedIds.get(0);
                AppCategory selected = AppCategory.OTHER;
                if (id == R.id.chipGames) {
                    selected = AppCategory.GAMES;
                } else if (id == R.id.chipEducation) {
                    selected = AppCategory.EDUCATION;
                } else if (id == R.id.chipVideo) {
                    selected = AppCategory.VIDEO;
                }
                Prefs.setAppCategory(this, packageName, selected);
            });
            categoriesList.addView(row);
        }
        if (categoriesList.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.categories_empty);
            empty.setPadding(0, 16, 0, 16);
            categoriesList.addView(empty);
        }
    }
}
