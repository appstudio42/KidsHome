package io.github.marcelsavegnago.kidshome;

import android.content.pm.ApplicationInfo;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Parent screen to grant bonus minutes and manage earn-time tasks. */
public class EarnTimeActivity extends ParentPinActivity {
    private final List<EarnTask> tasks = new ArrayList<>();
    private LinearLayout tasksList;
    private TextView starsSummary;

    @Override
    protected void onPinUnlocked() {
        setContentView(R.layout.activity_earn_time);
        bindParentChrome(R.id.parentRoot, R.id.earnToolbar);

        tasksList = findViewById(R.id.earnTasksList);
        starsSummary = findViewById(R.id.earnStarsSummary);
        tasks.clear();
        tasks.addAll(Prefs.getEarnTasks(this));

        findViewById(R.id.earnGrant15).setOnClickListener(v -> grantNow(15));
        findViewById(R.id.earnGrant30).setOnClickListener(v -> grantNow(30));
        findViewById(R.id.earnAddTask).setOnClickListener(v -> addTask());
        refresh();
    }

    private void refresh() {
        starsSummary.setText(getString(
                R.string.earn_stars_summary,
                Prefs.getStarsTotal(this),
                Prefs.getStarsToday(this)
        ));
        tasksList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i;
            EarnTask task = tasks.get(i);
            View row = inflater.inflate(R.layout.item_earn_task, tasksList, false);
            TextView title = row.findViewById(R.id.earnTaskTitle);
            TextView reward = row.findViewById(R.id.earnTaskReward);
            CheckBox done = row.findViewById(R.id.earnTaskDone);
            title.setText(task.title);
            reward.setText(formatReward(task));
            boolean completed = Prefs.isEarnTaskDoneToday(this, task.id);
            done.setOnCheckedChangeListener(null);
            done.setChecked(completed);
            done.setEnabled(!completed);
            done.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int minutes = Prefs.completeEarnTask(this, task.id);
                if (minutes > 0) {
                    Toast.makeText(this, getString(R.string.earn_task_completed, minutes),
                            Toast.LENGTH_SHORT).show();
                }
                refresh();
            });
            row.findViewById(R.id.earnTaskDelete).setOnClickListener(v -> {
                tasks.remove(index);
                Prefs.setEarnTasks(this, tasks);
                refresh();
            });
            tasksList.addView(row);
        }
    }

    private String formatReward(EarnTask task) {
        if (task.rewardsTotal()) {
            return getString(R.string.earn_reward_total, task.rewardMinutes);
        }
        return getString(R.string.earn_reward_app, task.rewardMinutes, labelFor(task.rewardTarget));
    }

    private String labelFor(String pkg) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = info.loadLabel(getPackageManager());
            return label != null ? label.toString() : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }

    private void grantNow(int minutes) {
        CharSequence[] choices = buildTargetChoices();
        final String[] targets = buildTargetValues();
        Dialogs.builder(this)
                .setTitle(getString(R.string.earn_grant_title, minutes))
                .setItems(choices, (d, which) -> {
                    Prefs.grantBonusMinutes(this, minutes, targets[which]);
                    if (which == 0) {
                        // Also bump a star for manual grants to feel rewarding.
                        // Stars for manual grants: optional — skip to keep stars task-linked.
                    }
                    Toast.makeText(this, getString(R.string.earn_granted, minutes), Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private CharSequence[] buildTargetChoices() {
        List<String> allowed = new ArrayList<>(Prefs.getAllowedPackages(this));
        CharSequence[] labels = new CharSequence[allowed.size() + 1];
        labels[0] = getString(R.string.earn_target_total);
        for (int i = 0; i < allowed.size(); i++) {
            labels[i + 1] = labelFor(allowed.get(i));
        }
        return labels;
    }

    private String[] buildTargetValues() {
        List<String> allowed = new ArrayList<>(Prefs.getAllowedPackages(this));
        String[] values = new String[allowed.size() + 1];
        values[0] = EarnTask.TARGET_TOTAL;
        for (int i = 0; i < allowed.size(); i++) {
            values[i + 1] = allowed.get(i);
        }
        return values;
    }

    private void addTask() {
        final EditText title = new EditText(this);
        title.setHint(R.string.earn_task_title_hint);
        final EditText minutes = new EditText(this);
        minutes.setHint(R.string.earn_task_minutes_hint);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setText("10");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(title);
        box.addView(minutes);

        CharSequence[] choices = buildTargetChoices();
        final String[] targets = buildTargetValues();
        final int[] selected = {0};

        Dialogs.builder(this)
                .setTitle(R.string.earn_add_task)
                .setView(box)
                .setSingleChoiceItems(choices, 0, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = title.getText() != null ? title.getText().toString().trim() : "";
                    String minText = minutes.getText() != null ? minutes.getText().toString().trim() : "10";
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.earn_task_title_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int reward;
                    try {
                        reward = Math.max(1, Integer.parseInt(minText));
                    } catch (NumberFormatException e) {
                        reward = 10;
                    }
                    tasks.add(new EarnTask(null, name, reward, targets[selected[0]], true));
                    Prefs.setEarnTasks(this, tasks);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
