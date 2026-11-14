package io.github.marcelsavegnago.kidshome;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Manage website tiles shown on the kid home grid. */
public class SiteShortcutsActivity extends AppCompatActivity {
    private final List<SiteShortcut> shortcuts = new ArrayList<>();
    private ArrayAdapter<String> adapter;

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
        setContentView(R.layout.activity_site_shortcuts);
        setTitle(R.string.site_shortcuts_title);

        Prefs.ensureDefaultSiteSuggestions(this);
        shortcuts.clear();
        shortcuts.addAll(Prefs.getSiteShortcuts(this));

        ListView list = findViewById(R.id.siteList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels());
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            shortcuts.remove(position);
            refresh();
            return true;
        });

        findViewById(R.id.siteAdd).setOnClickListener(v -> promptAdd());
        findViewById(R.id.siteAddSuggestions).setOnClickListener(v -> {
            for (SiteShortcut suggestion : SiteShortcut.defaultSuggestions()) {
                boolean exists = false;
                for (SiteShortcut s : shortcuts) {
                    if (s.url.equalsIgnoreCase(suggestion.url)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    shortcuts.add(suggestion);
                }
            }
            refresh();
        });
        findViewById(R.id.siteSave).setOnClickListener(v -> {
            Prefs.setSiteShortcuts(this, shortcuts);
            Toast.makeText(this, R.string.site_shortcuts_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.siteCancel).setOnClickListener(v -> finish());
    }

    private List<String> labels() {
        List<String> out = new ArrayList<>();
        for (SiteShortcut s : shortcuts) {
            out.add(s.name + "\n" + s.url);
        }
        return out;
    }

    private void refresh() {
        adapter.clear();
        adapter.addAll(labels());
        adapter.notifyDataSetChanged();
    }

    private void promptAdd() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        EditText name = new EditText(this);
        name.setHint(R.string.site_name_hint);
        EditText url = new EditText(this);
        url.setHint(R.string.site_url_hint);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(name);
        layout.addView(url);

        Dialogs.builder(this)
                .setTitle(R.string.site_add_title)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String n = name.getText() != null ? name.getText().toString().trim() : "";
                    String u = url.getText() != null ? url.getText().toString().trim() : "";
                    if (n.isEmpty() || u.isEmpty()) {
                        Toast.makeText(this, R.string.site_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "https://" + u;
                    }
                    shortcuts.add(new SiteShortcut(UUID.randomUUID().toString(), n, u));
                    FaviconHelper.fetchAsync(this, u, bmp -> { });
                    refresh();
                })
                .show();
    }
}
