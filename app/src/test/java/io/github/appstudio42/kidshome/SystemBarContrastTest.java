package io.github.appstudio42.kidshome;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Status and navigation bars keep icon/background contrast from API 22 through 35.
 * API 28 is the Fire OS 7 case: a light navigation bar needs dark icons.
 */
@RunWith(RobolectricTestRunner.class)
public class SystemBarContrastTest {
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
        Prefs.setOnboardingDone(context, true);
        Prefs.setKidModeEnabled(context, false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @After
    public void tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    @Config(sdk = 28)
    public void api28ParentScreensUseDarkIconsOnTheLightNavigationBar() {
        for (Activity activity : parentScreens()) {
            Window window = activity.getWindow();
            assertTrue(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getNavigationBarColor()));
            assertTrue(activity.getClass().getSimpleName(), lightNavigationIcons(window));
            assertFalse(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getStatusBarColor()));
            assertFalse(activity.getClass().getSimpleName(), lightStatusIcons(window));
            activity.finish();
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "night")
    public void api28NightBarsKeepLightIconsOnDarkSurfaces() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        for (Activity activity : parentScreens()) {
            assertNight(activity);
            Window window = activity.getWindow();
            assertFalse(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getNavigationBarColor()));
            assertFalse(activity.getClass().getSimpleName(), lightNavigationIcons(window));
            assertTrue(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getStatusBarColor()));
            assertTrue(activity.getClass().getSimpleName(), lightStatusIcons(window));
            activity.finish();
        }
    }

    @Test
    @Config(sdk = 35)
    public void api35ParentScreensUseDarkIconsOverTheLightBackground() {
        for (Activity activity : parentScreens()) {
            Window window = activity.getWindow();
            assertTrue(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(windowBackground(activity)));
            assertTrue(activity.getClass().getSimpleName(), lightNavigationIcons(window));
            assertTrue(activity.getClass().getSimpleName(), lightStatusIcons(window));
            activity.finish();
        }
    }

    @Test
    @Config(sdk = 35, qualifiers = "night")
    public void api35NightParentScreensUseLightIconsOverTheDarkBackground() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        for (Activity activity : parentScreens()) {
            assertNight(activity);
            Window window = activity.getWindow();
            assertFalse(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(windowBackground(activity)));
            assertFalse(activity.getClass().getSimpleName(), lightNavigationIcons(window));
            assertFalse(activity.getClass().getSimpleName(), lightStatusIcons(window));
            activity.finish();
        }
    }

    @Test
    @Config(sdk = 22)
    public void api22UsesDarkBarsBecauseIconsCannotBeDark() {
        for (Activity activity : parentScreens()) {
            Window window = activity.getWindow();
            assertFalse(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getNavigationBarColor()));
            assertFalse(activity.getClass().getSimpleName(),
                    ThemeHelper.isLight(window.getStatusBarColor()));
            assertFalse(lightNavigationIcons(window));
            assertFalse(lightStatusIcons(window));
            activity.finish();
        }
    }

    @Test
    @Config(sdk = 22, qualifiers = "night")
    public void api22NightKeepsBothBarsDark() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        SettingsActivity activity = launch(SettingsActivity.class);
        assertNight(activity);
        Window window = activity.getWindow();
        assertFalse(ThemeHelper.isLight(window.getNavigationBarColor()));
        assertFalse(ThemeHelper.isLight(window.getStatusBarColor()));
        activity.finish();
    }

    @Test
    @Config(sdk = {28, 35})
    public void immersiveChildScreensKeepLightIconsAndHideTheBars() {
        Prefs.setKidModeEnabled(context, true);
        KidHomeActivity home = Robolectric.buildActivity(KidHomeActivity.class).create().get();
        assertFalse(home.isFinishing());
        assertTrue(ImmersiveHelper.handlesOwnInsets(home));
        assertFalse(lightNavigationIcons(home.getWindow()));
        assertFalse(lightStatusIcons(home.getWindow()));
        assertFalse(themeFlag(home, android.R.attr.windowLightNavigationBar));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            int visibility = home.getWindow().getDecorView().getSystemUiVisibility();
            assertTrue((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0);
            assertTrue((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
        }
        home.finish();

        ScreenTimeLockActivity lock =
                Robolectric.buildActivity(ScreenTimeLockActivity.class).create().get();
        assertTrue(ImmersiveHelper.handlesOwnInsets(lock));
        assertFalse(lightNavigationIcons(lock.getWindow()));
        assertFalse(lightStatusIcons(lock.getWindow()));
        assertFalse(themeFlag(lock, android.R.attr.windowLightNavigationBar));
        assertFalse(themeFlag(lock, android.R.attr.windowLightStatusBar));
        if (Build.VERSION.SDK_INT < 35) {
            assertFalse(ThemeHelper.isLight(lock.getWindow().getNavigationBarColor()));
            assertFalse(ThemeHelper.isLight(lock.getWindow().getStatusBarColor()));
        }
        lock.finish();
    }

    @Test
    @Config(sdk = {28, 35})
    public void escapePinKeepsContrastWhenTheLegacyMaskHidesTheBars() {
        EscapePinActivity pin = Robolectric.buildActivity(EscapePinActivity.class).create().get();
        assertTrue(ImmersiveHelper.handlesOwnInsets(pin));
        if (Build.VERSION.SDK_INT >= 35) {
            assertTrue(lightNavigationIcons(pin.getWindow()));
            assertTrue(lightStatusIcons(pin.getWindow()));
        } else {
            assertTrue(ThemeHelper.isLight(pin.getWindow().getNavigationBarColor()));
            assertTrue(lightNavigationIcons(pin.getWindow()));
            assertFalse(ThemeHelper.isLight(pin.getWindow().getStatusBarColor()));
            assertFalse(lightStatusIcons(pin.getWindow()));
            int visibility = pin.getWindow().getDecorView().getSystemUiVisibility();
            assertTrue((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0);
            assertTrue((visibility & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0);
        }
        pin.finish();
    }

    private Activity[] parentScreens() {
        Prefs.setOnboardingDone(context, false);
        Activity onboarding = launch(OnboardingActivity.class);
        Prefs.setOnboardingDone(context, true);
        return new Activity[] {
                launch(SettingsActivity.class),
                onboarding,
                launch(AboutActivity.class),
                launch(SetupActivity.class),
                launch(SiteShortcutsActivity.class),
                launch(MainActivity.class),
                launch(KidModeActivity.class),
                launch(AllowedAppsActivity.class),
                launch(SchedulesActivity.class),
                launch(AppLimitsActivity.class),
                launch(EarnTimeActivity.class),
                launch(WeeklyReportActivity.class),
                launch(RemoteControlActivity.class)
        };
    }

    private <T extends Activity> T launch(Class<T> type) {
        return Robolectric.buildActivity(type).setup().get();
    }

    private static void assertNight(Activity activity) {
        int night = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        assertEquals(Configuration.UI_MODE_NIGHT_YES, night);
    }

    private static int windowBackground(Activity activity) {
        TypedValue value = new TypedValue();
        assertTrue(activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true));
        if (value.resourceId != 0) {
            return ContextCompat.getColor(activity, value.resourceId);
        }
        return value.data;
    }

    private static boolean themeFlag(Activity activity, int attr) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(attr, value, true)) {
            return false;
        }
        return value.data != 0;
    }

    private static boolean lightNavigationIcons(Window window) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null && controller.isAppearanceLightNavigationBars()) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return (window.getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0;
        }
        return false;
    }

    private static boolean lightStatusIcons(Window window) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null && controller.isAppearanceLightStatusBars()) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (window.getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0;
        }
        return false;
    }
}
