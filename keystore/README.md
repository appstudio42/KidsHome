# Test signing keystore (NOT for production)

`debug.keystore` is a **fixed CI/test** key committed so every GitHub Actions build
without release secrets produces an APK with the **same signature**. That lets you
`adb install -r` upgrades without `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

| Field | Value |
|-------|-------|
| File | `keystore/debug.keystore` |
| Store password | `android` |
| Key alias | `androiddebugkey` |
| Key password | `android` |

**Do not** use this key for Play Store, F-Droid, or other public distribution.
F-Droid signs its own builds. `assembleRelease` uses this keystore only when
Gradle is invoked with `-PciSignRelease=true` and `keystore.properties` is
absent. GitHub Actions passes that property when `SIGNING_*` secrets are not
set. With those secrets, CI writes `keystore.properties` and signs with your
private release keystore instead. A plain `./gradlew assembleRelease` produces
an unsigned APK. Switching from this test key to a release key (or the reverse)
requires uninstalling the app once (settings will be lost).
