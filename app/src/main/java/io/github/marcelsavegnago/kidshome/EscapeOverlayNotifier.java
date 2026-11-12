package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.Intent;

/**
 * Asks HomeButtonService to show/hide the escape corner overlay after kid-mode changes.
 */
public final class EscapeOverlayNotifier {
    public static final String ACTION_REFRESH_ESCAPE_OVERLAY =
            "io.github.marcelsavegnago.kidshome.REFRESH_ESCAPE_OVERLAY";

    private EscapeOverlayNotifier() {
    }

    public static void requestRefresh(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), HomeButtonService.class);
        intent.setAction(ACTION_REFRESH_ESCAPE_OVERLAY);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {
            ServiceMan.start(context);
        }
    }
}
