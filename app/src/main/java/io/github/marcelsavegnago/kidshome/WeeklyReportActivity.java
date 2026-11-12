package io.github.marcelsavegnago.kidshome;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Parent weekly usage report with a simple Canvas bar chart. */
public class WeeklyReportActivity extends ParentPinActivity {
    private TextView summary;
    private SimpleBarChartView chart;
    private LinearLayout appsList;

    @Override
    protected void onPinUnlocked() {
        setContentView(R.layout.activity_weekly_report);
        bindParentChrome(R.id.parentRoot, R.id.reportToolbar);

        summary = findViewById(R.id.reportSummary);
        chart = findViewById(R.id.reportChart);
        appsList = findViewById(R.id.reportAppsList);

        findViewById(R.id.reportExport).setOnClickListener(v -> exportCsv());
        findViewById(R.id.reportClear).setOnClickListener(v -> confirmClear());
        refresh();
    }

    private void refresh() {
        Map<String, UsageHistory.DayUsage> history = Prefs.getUsageHistoryIncludingToday(this);
        String today = ScreenTimeState.dayKeyNow();
        List<UsageHistory.DayUsage> week = UsageHistory.weekDays(history, today);

        long weekTotal = 0L;
        int weekStars = 0;
        List<SimpleBarChartView.Bar> bars = new ArrayList<>();
        String[] dayLabels = getResources().getStringArray(R.array.weekday_short);
        for (UsageHistory.DayUsage day : week) {
            weekTotal += day.totalMillis;
            weekStars += day.starsEarned;
            int dow = dayOfWeekIndex(day.dayKey);
            String label = dow >= 0 && dow < dayLabels.length ? dayLabels[dow] : day.dayKey.substring(6);
            boolean isToday = today.equals(day.dayKey);
            bars.add(new SimpleBarChartView.Bar(label, day.totalMillis / 60_000f, isToday));
        }
        chart.setBars(bars);
        if (Prefs.isScreenTimeEnabled(this)) {
            chart.setLimitValue(Prefs.getDailyLimitMinutes(this));
        } else {
            chart.setLimitValue(-1f);
        }

        long limitMin = Prefs.getDailyLimitMinutes(this);
        summary.setText(getString(
                R.string.report_summary_fmt,
                formatDuration(weekTotal),
                weekStars,
                Prefs.isScreenTimeEnabled(this) ? limitMin : 0
        ));

        Map<String, Long> appTotals = UsageHistory.aggregateApps(week);
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(appTotals.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });

        appsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        PackageManager pm = getPackageManager();

        List<RemoteActionLog.Entry> remoteWeek = RemoteActionLog.forWeek(
                Prefs.getRemoteActionLog(this), today);
        if (!remoteWeek.isEmpty()) {
            TextView remoteHeader = new TextView(this);
            remoteHeader.setText(R.string.report_remote_section);
            remoteHeader.setPadding(0, 8, 0, 8);
            remoteHeader.setTextAppearance(this, R.style.TextAppearance_KidsHome_Section);
            appsList.addView(remoteHeader);
            String lang = getResources().getConfiguration().locale.getLanguage();
            boolean portuguese = lang != null && lang.toLowerCase().startsWith("pt");
            for (RemoteActionLog.Entry entry : remoteWeek) {
                TextView line = new TextView(this);
                line.setText(RemoteActionLog.formatLabel(entry, portuguese));
                line.setPadding(0, 4, 0, 4);
                appsList.addView(line);
            }
        }

        if (sorted.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.report_empty);
            empty.setPadding(0, 16, 0, 16);
            appsList.addView(empty);
            return;
        }
        for (Map.Entry<String, Long> entry : sorted) {
            View row = inflater.inflate(R.layout.item_report_app, appsList, false);
            ImageView icon = row.findViewById(R.id.reportAppIcon);
            TextView name = row.findViewById(R.id.reportAppName);
            TextView line = row.findViewById(R.id.reportAppSummary);
            ProgressBar progress = row.findViewById(R.id.reportAppProgress);
            String pkg = entry.getKey();
            name.setText(labelFor(pkg));
            try {
                icon.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (Exception e) {
                icon.setImageResource(R.drawable.ic_notification);
            }
            int limit = Prefs.getAppLimitMinutes(this, pkg);
            String limitText = limit < 0
                    ? getString(R.string.app_time_unlimited)
                    : getString(R.string.report_limit_fmt, limit);
            line.setText(getString(R.string.report_app_line_fmt, formatDuration(entry.getValue()), limitText));
            long usedMin = Math.max(0L, entry.getValue() / 60_000L);
            if (limit < 0) {
                progress.setMax(100);
                progress.setProgress((int) Math.min(100, usedMin));
            } else {
                progress.setMax(Math.max(1, limit));
                progress.setProgress((int) Math.min(limit, usedMin));
            }
            appsList.addView(row);
        }
    }

    private int dayOfWeekIndex(String dayKey) {
        try {
            int y = Integer.parseInt(dayKey.substring(0, 4));
            int m = Integer.parseInt(dayKey.substring(4, 6));
            int d = Integer.parseInt(dayKey.substring(6, 8));
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.clear();
            cal.set(y, m - 1, d);
            return cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatDuration(long millis) {
        long totalMin = Math.max(0L, millis / 60_000L);
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (h > 0) {
            return getString(R.string.report_duration_hm, h, m);
        }
        return getString(R.string.report_duration_m, m);
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

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.report_clear)
                .setMessage(R.string.report_clear_confirm)
                .setPositiveButton(R.string.report_clear, (d, w) -> {
                    Prefs.clearUsageHistory(this);
                    refresh();
                    Toast.makeText(this, R.string.report_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportCsv() {
        Map<String, UsageHistory.DayUsage> history = Prefs.getUsageHistoryIncludingToday(this);
        String csv = UsageHistory.toCsv(history);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, csv);
        share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_export_subject));
        try {
            startActivity(Intent.createChooser(share, getString(R.string.report_export_csv)));
        } catch (Exception ex) {
            Toast.makeText(this, R.string.report_export_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
