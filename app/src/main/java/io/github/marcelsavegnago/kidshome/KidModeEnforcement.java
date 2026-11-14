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
     * Stock launchers are not.
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
}
