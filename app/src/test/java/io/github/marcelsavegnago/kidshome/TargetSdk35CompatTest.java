package io.github.marcelsavegnago.kidshome;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Target SDK 35: edge-to-edge insets stay idempotent, pre-35 screens are not
 * double-padded, and background relaunch opts into the API 34/35 pending-intent modes.
 */
@RunWith(RobolectricTestRunner.class)
public class TargetSdk35CompatTest {
    private static final String CREATOR_MODE_KEY =
            "android.activity.pendingIntentCreatorBackgroundActivityStartMode";
    /**
     * Sender mode is written by {@code ComponentOptions} under this key
     * (API 34/35 framework). The public setter is still
     * {@code setPendingIntentBackgroundActivityStartMode}.
     */
    private static final String SENDER_MODE_KEY =
            "android.pendingIntent.backgroundActivityAllowed";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        SessionAuth.lock();
        PinAttemptTracker.attach(context);
        PinAttemptTracker.reset();
        KidModeGate.markEscapeUiClosed();
        KidModeGate.markScreenLockUiClosed();
        Prefs.setOnboardingDone(context, true);
        Prefs.setKidModeEnabled(context, true);
    }

    @Test
    @Config(sdk = {28, 35})
    public void contentInsetsApplyOnceWhenRequestedTwice() {
        View view = new View(context);
        view.setPaddingRelative(4, 8, 12, 16);
        ImmersiveHelper.applyContentInsets(view);
        ImmersiveHelper.applyContentInsets(view);

        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(1, 10, 2, 40))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(6, 30, 0, 0))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 120))
                .build();
        ViewCompat.dispatchApplyWindowInsets(view, insets);
        ViewCompat.dispatchApplyWindowInsets(view, insets);

        // API 29 and below round-trip insets through system-window insets only.
        // Cutout and IME types survive from API 30 up, which includes Android 15.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(4 + 6, view.getPaddingStart());
            assertEquals(8 + 30, view.getPaddingTop());
            assertEquals(12 + 2, view.getPaddingEnd());
            assertEquals(16 + 120, view.getPaddingBottom());
        } else {
            assertEquals(4 + 1, view.getPaddingStart());
            assertEquals(8 + 10, view.getPaddingTop());
            assertEquals(12 + 2, view.getPaddingEnd());
            assertEquals(16 + 40, view.getPaddingBottom());
        }
    }

    @Test
    @Config(sdk = 28)
    public void pre35ParentScreenDoesNotPadForSystemBars() {
        AboutActivity activity = Robolectric.buildActivity(AboutActivity.class).setup().get();
        View content = activity.findViewById(android.R.id.content);
        assertNull(content.getTag(R.id.kh_inset_padding_base));
        int top = content.getPaddingTop();
        ViewCompat.dispatchApplyWindowInsets(content, systemBars(40, 80));
        assertEquals(top, content.getPaddingTop());
    }

    @Test
    @Config(sdk = 35)
    public void api35ParentScreenPadsContentForSystemBars() {
        AboutActivity activity = Robolectric.buildActivity(AboutActivity.class).setup().get();
        assertFalse(ImmersiveHelper.handlesOwnInsets(activity));
        View content = activity.findViewById(android.R.id.content);
        int[] base = paddingBase(content);
        ViewCompat.dispatchApplyWindowInsets(content, systemBars(50, 90));
        assertEquals(base[1] + 50, content.getPaddingTop());
        assertEquals(base[3] + 90, content.getPaddingBottom());
        ImmersiveHelper.applyContentInsets(content);
        ViewCompat.dispatchApplyWindowInsets(content, systemBars(50, 90));
        assertEquals(base[1] + 50, content.getPaddingTop());
        assertEquals(base[3] + 90, content.getPaddingBottom());
    }

    @Test
    @Config(sdk = 35)
    public void lockScreensPadTheirOwnRootAndNotTheWindowFrame() {
        KidHomeActivity home = Robolectric.buildActivity(KidHomeActivity.class).create().get();
        assertFalse(home.isFinishing());
        assertTrue(ImmersiveHelper.handlesOwnInsets(home));
        assertInsetsOnce(home.findViewById(R.id.kidHomeContent), 24, 48);
        assertFrameNotPaddedByUs(home.findViewById(android.R.id.content));

        ScreenTimeLockActivity lock = Robolectric.buildActivity(ScreenTimeLockActivity.class).create().get();
        assertTrue(ImmersiveHelper.handlesOwnInsets(lock));
        assertInsetsOnce(lock.findViewById(R.id.lockRoot), 11, 22);
        assertFrameNotPaddedByUs(lock.findViewById(android.R.id.content));

        EscapePinActivity pin = Robolectric.buildActivity(EscapePinActivity.class).create().get();
        assertTrue(ImmersiveHelper.handlesOwnInsets(pin));
        assertInsetsOnce(pin.findViewById(R.id.escapePinRoot), 7, 15);
        assertFrameNotPaddedByUs(pin.findViewById(android.R.id.content));
    }

    @Test
    @Config(sdk = 35)
    public void pinDialogOptsOutOfEdgeToEdgeWhileTheActivityDoesNot() {
        AboutActivity activity = Robolectric.buildActivity(AboutActivity.class).setup().get();
        TypedValue activityValue = new TypedValue();
        boolean activityResolved = activity.getTheme().resolveAttribute(
                android.R.attr.windowOptOutEdgeToEdgeEnforcement, activityValue, true);
        assertFalse(activityResolved && activityValue.data != 0);

        AlertDialog dialog = Dialogs.builder(activity)
                .setTitle("PIN")
                .setMessage("parent")
                .create();
        dialog.show();
        TypedValue dialogValue = new TypedValue();
        assertTrue(dialog.getContext().getTheme().resolveAttribute(
                android.R.attr.windowOptOutEdgeToEdgeEnforcement, dialogValue, true));
        assertTrue(dialogValue.data != 0);
        dialog.dismiss();
    }

    @Test
    @Config(sdk = 28)
    public void legacyBackgroundLaunchDoesNotSetApi34Modes() {
        assertNull(BackgroundActivityStarter.creatorOptionsBundle());
        assertNull(BackgroundActivityStarter.senderOptionsBundle());
        assertTrue(launchAbout());
    }

    @Test
    @Config(sdk = {34, 35})
    public void backgroundLaunchOptsIntoCreatorAndSenderModes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions creator = ActivityOptions.makeBasic();
            creator.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            assertEquals(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    creator.getPendingIntentCreatorBackgroundActivityStartMode());
            ActivityOptions sender = ActivityOptions.makeBasic();
            sender.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            assertEquals(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    sender.getPendingIntentBackgroundActivityStartMode());
        }

        Bundle creatorBundle = BackgroundActivityStarter.creatorOptionsBundle();
        Bundle senderBundle = BackgroundActivityStarter.senderOptionsBundle();
        assertNotNull(creatorBundle);
        assertNotNull(senderBundle);
        assertCreatorOptIn(creatorBundle);
        assertSenderOptIn(senderBundle);

        Intent home = new Intent(context, KidHomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertTrue(String.valueOf(BackgroundActivityStarter.lastError),
                BackgroundActivityStarter.launch(
                        context, home, BackgroundActivityStarter.REQUEST_HOME));
        assertTrue(String.valueOf(BackgroundActivityStarter.lastError), launchAbout());
        ScreenTimeLockActivity.launch(context);
        assertTrue(KidModeGate.isScreenLockUiActive());
    }

    private boolean launchAbout() {
        Intent intent = new Intent(context, AboutActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return BackgroundActivityStarter.launch(
                context, intent, BackgroundActivityStarter.REQUEST_ESCAPE_PIN);
    }

    private static void assertCreatorOptIn(Bundle bundle) {
        assertNotNull(bundle);
        if (!bundle.containsKey(CREATOR_MODE_KEY)) {
            fail("Missing creator opt-in bundle=" + bundle);
        }
        assertEquals(bundle.toString(),
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                bundle.getInt(CREATOR_MODE_KEY));
    }

    /**
     * API 34 stores the sender opt-in as a boolean. API 35 stores
     * {@code MODE_BACKGROUND_ACTIVITY_START_ALLOWED} as an int. Both mean allowed.
     */
    private static void assertSenderOptIn(Bundle bundle) {
        assertNotNull(bundle);
        if (!bundle.containsKey(SENDER_MODE_KEY)) {
            fail("Missing sender opt-in bundle=" + bundle);
        }
        Object value = bundle.get(SENDER_MODE_KEY);
        if (value instanceof Boolean) {
            assertEquals(Boolean.TRUE, value);
            return;
        }
        assertEquals(bundle.toString(),
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                bundle.getInt(SENDER_MODE_KEY));
    }

    private static WindowInsetsCompat systemBars(int top, int bottom) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, 0, bottom))
                .build();
    }

    private static int[] paddingBase(View view) {
        Object tag = view.getTag(R.id.kh_inset_padding_base);
        assertTrue(tag instanceof int[]);
        return (int[]) tag;
    }

    private static void assertInsetsOnce(View content, int top, int bottom) {
        assertNotNull(content);
        int[] base = paddingBase(content);
        ViewCompat.dispatchApplyWindowInsets(content, systemBars(top, bottom));
        ViewCompat.dispatchApplyWindowInsets(content, systemBars(top, bottom));
        assertEquals(base[1] + top, content.getPaddingTop());
        assertEquals(base[3] + bottom, content.getPaddingBottom());
    }

    private static void assertFrameNotPaddedByUs(View frame) {
        assertNull(frame.getTag(R.id.kh_inset_padding_base));
        assertFalse(frame.getFitsSystemWindows());
    }
}
