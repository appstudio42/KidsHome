package io.github.marcelsavegnago.kidshome;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedDispatcher;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Back must not dismiss a lock screen on the legacy key path (API 28) or on the
 * predictive-back path (API 33+), where the system invokes {@code OnBackInvokedCallback}
 * and does not call {@code Activity.onBackPressed()}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 33, 34})
public class LockBackNavigationTest {
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
    public void kidHomeIgnoresLegacyAndPredictiveBack() {
        KidHomeActivity activity = launch(KidHomeActivity.class);
        assertFalse(activity.isFinishing());
        assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());

        pressLegacyBack(activity);
        assertFalse(activity.isFinishing());

        pressPredictiveBack(activity);
        pressPredictiveBack(activity);
        assertFalse(activity.isFinishing());
        assertEquals(KidHomeActivity.class, activity.getClass());
    }

    @Test
    public void kidHomeDropsTheCallbackWhileStopped() {
        ActivityController<KidHomeActivity> controller = Robolectric.buildActivity(KidHomeActivity.class);
        KidHomeActivity activity = controller.create().start().resume().get();
        assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());

        controller.pause().stop();
        assertFalse(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());

        controller.restart();
        assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());
        pressLegacyBack(activity);
        assertFalse(activity.isFinishing());
    }

    @Test
    public void screenTimeLockIgnoresLegacyAndPredictiveBack() {
        Prefs.setRemoteForceLocked(context, true);
        ScreenTimeLockActivity activity = launch(ScreenTimeLockActivity.class);
        assertFalse(activity.isFinishing());
        assertTrue(ScreenTimeTracker.isBlocked(context));

        pressLegacyBack(activity);
        pressPredictiveBack(activity);
        assertFalse(activity.isFinishing());
        assertTrue(KidModeGate.isScreenLockUiActive());
    }

    @Test
    public void escapePinBackReturnsToTheLockedTarget() {
        EscapePinActivity activity = launch(EscapePinActivity.class);
        assertFalse(activity.isFinishing());
        assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());

        pressLegacyBack(activity);
        assertTrue(activity.isFinishing());
        assertTrue("Back must run the lock return, not the default finish",
                finishingToKidApp(activity));
    }

    @Test
    public void escapePinPredictiveBackReturnsToTheLockedTarget() {
        EscapePinActivity activity = launch(EscapePinActivity.class);
        pressPredictiveBack(activity);
        assertTrue(activity.isFinishing());
        assertTrue(finishingToKidApp(activity));
    }

    @Test
    public void parentScreenStillCloses() {
        AboutActivity activity = launch(AboutActivity.class);
        assertFalse(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());
        pressLegacyBack(activity);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void lockScreensDoNotOverrideOnBackPressed() {
        assertNoOnBackPressedOverride(KidHomeActivity.class);
        assertNoOnBackPressedOverride(EscapePinActivity.class);
        assertNoOnBackPressedOverride(ScreenTimeLockActivity.class);
        assertNoOnBackPressedOverride(AboutActivity.class);
    }

    @Test
    public void manifestOptsInOnlyTheLockScreens() throws Exception {
        String xml = new String(Files.readAllBytes(manifestFile().toPath()), StandardCharsets.UTF_8);
        int application = xml.indexOf("<application");
        int firstActivity = xml.indexOf("<activity", application);
        String applicationTag = xml.substring(application, firstActivity);
        assertFalse(applicationTag.contains("enableOnBackInvokedCallback"));

        assertTrue(activityBlock(xml, ".EscapePinActivity").contains("android:enableOnBackInvokedCallback=\"true\""));
        assertTrue(activityBlock(xml, ".ScreenTimeLockActivity").contains("android:enableOnBackInvokedCallback=\"true\""));
        assertTrue(activityBlock(xml, ".KidHomeActivity").contains("android:enableOnBackInvokedCallback=\"true\""));
        assertFalse(activityBlock(xml, ".MainActivity").contains("enableOnBackInvokedCallback"));
        assertFalse(activityBlock(xml, ".AboutActivity").contains("enableOnBackInvokedCallback"));
        assertFalse(xml.contains("enableOnBackInvokedCallback=\"false\""));
    }

    private static <T extends Activity> T launch(Class<T> type) {
        return Robolectric.buildActivity(type).create().start().resume().get();
    }

    @SuppressWarnings("deprecation")
    private static void pressLegacyBack(Activity activity) {
        activity.onBackPressed();
    }

    private static void pressPredictiveBack(Activity activity) {
        if (Build.VERSION.SDK_INT < 33) {
            pressLegacyBack(activity);
            return;
        }
        OnBackPressedDispatcher dispatcher = ((ComponentActivity) activity).getOnBackPressedDispatcher();
        assertTrue("lock screen must register a platform back callback. " + fieldsOf(dispatcher),
                registeredWithPlatform(dispatcher));
        Object callback = platformCallback(dispatcher);
        assertNotNull("missing OnBackInvokedCallback. " + fieldsOf(dispatcher), callback);
        try {
            // Invoke through the public interface. The implementation class is
            // package-private, so Method from callback.getClass() is not accessible.
            Class<?> callbackType = Class.forName("android.window.OnBackInvokedCallback");
            Method onBackInvoked = callbackType.getMethod("onBackInvoked");
            onBackInvoked.invoke(callback);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not deliver predictive back. " + fieldsOf(dispatcher), e);
        }
    }

    private static boolean registeredWithPlatform(OnBackPressedDispatcher dispatcher) {
        Boolean registered = null;
        for (Field field : declaredFields(dispatcher.getClass())) {
            if (field.getType() != boolean.class) {
                continue;
            }
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("registered")) {
                continue;
            }
            try {
                field.setAccessible(true);
                registered = field.getBoolean(dispatcher);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(fieldsOf(dispatcher), e);
            }
        }
        if (registered == null) {
            fail("No platform-registration flag on OnBackPressedDispatcher. " + fieldsOf(dispatcher));
        }
        return registered;
    }

    private static Object platformCallback(OnBackPressedDispatcher dispatcher) {
        Class<?> callbackType;
        try {
            callbackType = Class.forName("android.window.OnBackInvokedCallback");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        Object found = null;
        for (Field field : declaredFields(dispatcher.getClass())) {
            if (!callbackType.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(dispatcher);
                if (value != null) {
                    if (found != null) {
                        fail("More than one OnBackInvokedCallback field. " + fieldsOf(dispatcher));
                    }
                    found = value;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(fieldsOf(dispatcher), e);
            }
        }
        return found;
    }

    private static List<Field> declaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static String fieldsOf(Object target) {
        StringBuilder builder = new StringBuilder();
        for (Field field : declaredFields(target.getClass())) {
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(target);
            } catch (ReflectiveOperationException e) {
                value = e.getClass().getSimpleName();
            }
            builder.append(field.getName())
                    .append(':')
                    .append(field.getType().getSimpleName())
                    .append('=')
                    .append(value)
                    .append("; ");
        }
        return builder.toString();
    }

    private static boolean finishingToKidApp(EscapePinActivity activity) {
        try {
            Field field = EscapePinActivity.class.getDeclaredField("finishingToKidApp");
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertNoOnBackPressedOverride(Class<?> type) {
        try {
            type.getDeclaredMethod("onBackPressed");
            fail(type.getSimpleName() + " still overrides onBackPressed");
        } catch (NoSuchMethodException expected) {
            // Migrated to OnBackPressedCallback.
        }
    }

    private static String activityBlock(String xml, String name) {
        String marker = "android:name=\"" + name + "\"";
        int nameAt = xml.indexOf(marker);
        assertTrue("Missing " + name, nameAt >= 0);
        int tagStart = xml.lastIndexOf("<activity", nameAt);
        int tagEnd = xml.indexOf('>', nameAt);
        assertTrue(tagStart >= 0 && tagEnd > nameAt);
        return xml.substring(tagStart, tagEnd + 1);
    }

    private static File manifestFile() {
        File module = new File("src/main/AndroidManifest.xml");
        if (module.isFile()) {
            return module;
        }
        File root = new File("app/src/main/AndroidManifest.xml");
        if (root.isFile()) {
            return root;
        }
        fail("AndroidManifest.xml not found from " + new File(".").getAbsolutePath());
        return module;
    }
}
