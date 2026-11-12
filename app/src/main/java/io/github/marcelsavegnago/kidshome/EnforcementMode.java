package io.github.marcelsavegnago.kidshome;

/**
 * How KidsHome enforces kid mode on this device.
 */
public enum EnforcementMode {
    /** Pick the strongest available mode for the device. */
    AUTO,
    /** Fire OS style: AccessibilityService hijacks Home / stock launchers. */
    ACCESSIBILITY,
    /** Regular Android: KidsHome is the default HOME/launcher. */
    LAUNCHER,
    /** Strongest: Device Owner + lock task (kiosk). */
    DEVICE_OWNER;

    public static EnforcementMode fromPrefsValue(String value) {
        if (value == null || value.isEmpty()) {
            return AUTO;
        }
        switch (value) {
            case "accessibility":
                return ACCESSIBILITY;
            case "launcher":
                return LAUNCHER;
            case "device_owner":
                return DEVICE_OWNER;
            case "auto":
            default:
                return AUTO;
        }
    }

    public String toPrefsValue() {
        switch (this) {
            case ACCESSIBILITY:
                return "accessibility";
            case LAUNCHER:
                return "launcher";
            case DEVICE_OWNER:
                return "device_owner";
            case AUTO:
            default:
                return "auto";
        }
    }
}
