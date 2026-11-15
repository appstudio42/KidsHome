package io.github.marcelsavegnago.kidshome;

/**
 * Pure kid-mode allow / redirect decisions used by {@link AccServ}.
 * Callers supply package policy and screen-time answers; this class does not
 * read preferences or the clock.
 */
public final class KidModeEnforcement {
    static final String[] HIJACK_PACKAGES = {
            "com.amazon.firelauncher",
            "com.amazon.launcher",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3"
    };

    public enum Action {
        /** Leave the foreground app alone. */
        ALLOW,
        /** Send the child back to the kid target or the screen-time lock. */
        REDIRECT
    }

    private KidModeEnforcement() {
    }

    public static boolean isHijackPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String hijack : HIJACK_PACKAGES) {
            if (hijack.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSystemPackage(String packageName) {
        return packageName != null
                && ("com.android.systemui".equals(packageName)
                || "android".equals(packageName)
                || packageName.startsWith("com.android.systemui"));
    }

    /**
     * Whether {@code packageName} may stay in front while kid mode is enforcing.
     * Own app, system UI, and the locked/allowlisted target are allowed.
     * Stock launchers are not. {@link SiteActivity} is the own package, so the
     * in-app site viewer is not a foreign app for {@link AccServ} or home hijack.
     * A WebAPK opened that way does not need its shell package, or Chrome, to
     * stay allowlisted: neither is the foreground app.
     */
    public static boolean isAllowedInKidMode(
            String packageName,
            String ownPackage,
            boolean lockToSingleApp,
            String lockedPackage,
            boolean lockedPackageCanLaunch,
            boolean allowlisted,
            boolean chosenBrowser,
            boolean allowlistedOrBrowserCanLaunch
    ) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (ownPackage != null && packageName.equals(ownPackage)) {
            return true;
        }
        if (isSystemPackage(packageName)) {
            return true;
        }
        if (isHijackPackage(packageName)) {
            return false;
        }
        if (lockToSingleApp) {
            return lockedPackage != null
                    && packageName.equals(lockedPackage)
                    && lockedPackageCanLaunch;
        }
        if (!allowlisted && !chosenBrowser) {
            return false;
        }
        return allowlistedOrBrowserCanLaunch;
    }

    /**
     * Same branching as {@code AccServ.maybeEnforceKidMode}.
     * An active escape / lock gate skips enforcement. A screen-time block always
     * redirects. When full package blocking is off, only an allowlisted app that
     * failed its own limit is redirected.
     */
    public static Action enforcementAction(
            boolean kidModeEnabled,
            boolean escapeUiActive,
            boolean screenTimeBlocked,
            boolean blockNonAllowedApps,
            String packageName,
            boolean allowlisted,
            boolean allowedInKidMode,
            boolean canLaunchAllowlisted
    ) {
        if (!kidModeEnabled || escapeUiActive) {
            return Action.ALLOW;
        }
        if (screenTimeBlocked) {
            return Action.REDIRECT;
        }
        if (!blockNonAllowedApps) {
            if (packageName != null && !packageName.isEmpty()
                    && allowlisted && !canLaunchAllowlisted) {
                return Action.REDIRECT;
            }
            return Action.ALLOW;
        }
        if (packageName == null || packageName.isEmpty()) {
            return Action.ALLOW;
        }
        if (allowedInKidMode) {
            return Action.ALLOW;
        }
        return Action.REDIRECT;
    }

    public enum WebApkHandoff {
        /**
         * The shell is allowlisted and has an https URL. Open {@link SiteActivity}
         * and do not send the child home. The shell would otherwise delegate to
         * Chrome, which the parent did not allow.
         */
        OPEN_SITE,
        /** Use {@link #enforcementAction}. */
        KEEP
    }

    /**
     * Whether a WebAPK shell in the foreground should be replaced by
     * {@link SiteActivity}. The shell does not become implicitly allowed: a
     * package the parent did not list stays on the normal redirect path.
     * After the handoff the foreground app is KidsHome, so the shell package
     * no longer needs to be allowlisted for the page to stay open, and Chrome
     * is never required.
     */
    public static WebApkHandoff webApkHandoff(
            boolean kidModeEnabled,
            boolean escapeUiActive,
            boolean screenTimeBlocked,
            boolean webApkWithHttpsUrl,
            boolean allowlisted,
            boolean canLaunch
    ) {
        if (!kidModeEnabled || escapeUiActive || screenTimeBlocked) {
            return WebApkHandoff.KEEP;
        }
        if (!webApkWithHttpsUrl || !allowlisted || !canLaunch) {
            return WebApkHandoff.KEEP;
        }
        return WebApkHandoff.OPEN_SITE;
    }
}
