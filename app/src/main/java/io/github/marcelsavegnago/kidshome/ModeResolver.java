package io.github.marcelsavegnago.kidshome;

/**
 * Pure logic for picking the effective enforcement mode (unit-testable).
 */
public final class ModeResolver {
    private ModeResolver() {
    }

    /**
     * Resolve the mode that should actually run.
     *
     * @param preference stored preference (may be {@link EnforcementMode#AUTO})
     * @param isDeviceOwner whether this app is currently device owner
     * @param isFireOs whether the device looks like Fire OS / Amazon
     */
    public static EnforcementMode resolve(
            EnforcementMode preference,
            boolean isDeviceOwner,
            boolean isFireOs
    ) {
        if (preference == null || preference == EnforcementMode.AUTO) {
            if (isDeviceOwner) {
                return EnforcementMode.DEVICE_OWNER;
            }
            if (isFireOs) {
                return EnforcementMode.ACCESSIBILITY;
            }
            return EnforcementMode.LAUNCHER;
        }
        // Manual override: DEVICE_OWNER only "works" when actually provisioned;
        // callers still return the preference so the setup UI can show what's missing.
        return preference;
    }

    /** True when Home should be redirected via accessibility (Fire-style hijack). */
    public static boolean shouldHijackHome(EnforcementMode effective) {
        return effective == EnforcementMode.ACCESSIBILITY;
    }

    /**
     * True when accessibility should block non-allowed packages.
     * Device Owner / Launcher can still enable this as an extra layer.
     */
    public static boolean shouldBlockNonAllowed(
            EnforcementMode effective,
            boolean extraBlockingEnabled
    ) {
        if (effective == EnforcementMode.ACCESSIBILITY) {
            return true;
        }
        return extraBlockingEnabled;
    }
}
