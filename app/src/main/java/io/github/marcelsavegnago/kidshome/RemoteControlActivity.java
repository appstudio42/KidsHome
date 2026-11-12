package io.github.marcelsavegnago.kidshome;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;

/**
 * Parent settings for LAN remote control: enable, show URL + QR, regenerate token.
 */
public class RemoteControlActivity extends ParentPinActivity {
    private MaterialSwitch enableSwitch;
    private View details;
    private TextView urlView;
    private TextView hintView;
    private ImageView qrView;

    @Override
    protected void onPinUnlocked() {
        setContentView(R.layout.activity_remote_control);
        bindParentChrome(R.id.parentRoot, R.id.remoteToolbar);

        enableSwitch = findViewById(R.id.remoteEnable);
        details = findViewById(R.id.remoteDetails);
        urlView = findViewById(R.id.remoteUrl);
        hintView = findViewById(R.id.remoteHint);
        qrView = findViewById(R.id.remoteQr);

        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed() && !buttonView.isAccessibilityFocused()) {
                // Still handle programmatic sets in refresh().
            }
            Prefs.setRemoteControlEnabled(this, isChecked);
            if (isChecked) {
                Prefs.getOrCreateRemoteToken(this);
                RemoteControlService.start(this);
            } else {
                Prefs.setRemoteForceLocked(this, false);
                RemoteControlService.stop(this);
            }
            refresh();
        });

        MaterialButton regenerate = findViewById(R.id.remoteRegenerate);
        regenerate.setOnClickListener(v -> {
            Prefs.regenerateRemoteToken(this);
            RemoteControlService.refreshAuthToken(this);
            Toast.makeText(this, R.string.remote_token_regenerated, Toast.LENGTH_SHORT).show();
            refresh();
        });

        findViewById(R.id.remoteCopyUrl).setOnClickListener(v -> {
            String url = RemoteNetwork.buildPairingUrl(this);
            if (url == null) {
                Toast.makeText(this, R.string.remote_no_wifi, Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KidsHome remote", url));
                Toast.makeText(this, R.string.remote_url_copied, Toast.LENGTH_SHORT).show();
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isUnlocked()) {
            refresh();
        }
    }

    private void refresh() {
        boolean enabled = Prefs.isRemoteControlEnabled(this);
        enableSwitch.setOnCheckedChangeListener(null);
        enableSwitch.setChecked(enabled);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.setRemoteControlEnabled(this, isChecked);
            if (isChecked) {
                Prefs.getOrCreateRemoteToken(this);
                RemoteControlService.start(this);
            } else {
                Prefs.setRemoteForceLocked(this, false);
                RemoteControlService.stop(this);
            }
            refreshUiOnly();
        });
        refreshUiOnly();
    }

    private void refreshUiOnly() {
        boolean enabled = Prefs.isRemoteControlEnabled(this);
        details.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            return;
        }
        String pairing = RemoteNetwork.buildPairingUrl(this);
        String base = RemoteNetwork.buildBaseUrl(this);
        if (pairing == null || base == null) {
            urlView.setText(R.string.remote_no_wifi);
            hintView.setText(R.string.remote_no_wifi_hint);
            qrView.setImageDrawable(null);
            return;
        }
        urlView.setText(base);
        hintView.setText(getString(R.string.remote_url_hint, pairing));
        int size = Math.round(getResources().getDisplayMetrics().density * 220f);
        Bitmap qr = RemoteQrEncoder.encode(pairing, size);
        if (qr != null) {
            qrView.setImageBitmap(qr);
        }
    }
}
