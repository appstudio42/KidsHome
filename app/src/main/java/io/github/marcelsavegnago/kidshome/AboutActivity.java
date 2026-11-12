package io.github.marcelsavegnago.kidshome;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.about);

        TextView version = findViewById(R.id.aboutVersion);
        version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        findViewById(R.id.aboutLicenseLink).setOnClickListener(v ->
                openUrl("https://www.gnu.org/licenses/gpl-3.0.html"));
        findViewById(R.id.aboutSourceLink).setOnClickListener(v ->
                openUrl("https://github.com/marcelsavegnago/KidsHome"));
        findViewById(R.id.aboutBaronLink).setOnClickListener(v ->
                openUrl("https://github.com/BaronKiko/LauncherHijack"));
        findViewById(R.id.aboutMeemsLink).setOnClickListener(v ->
                openUrl("https://github.com/Meems33/LauncherHijack"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
