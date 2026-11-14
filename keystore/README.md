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
absent. Pull requests and branch builds on GitHub Actions pass that property
when `SIGNING_*` secrets are not set. A push of a `v*` tag does **not**: the
job fails if `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_STORE_PASSWORD`, or `SIGNING_KEY_PASSWORD` is missing, and Gradle
is invoked with `-PrequireReleaseSigning=true`, which refuses this debug
keystore. With those secrets, CI writes `keystore.properties` and signs with
your private release keystore instead. A plain `./gradlew assembleRelease`
produces an unsigned APK. Switching from this test key to a release key (or
the reverse) requires uninstalling the app once (settings will be lost).

How to create the release key and the four secrets is in the README
(English and pt-BR), section **CI signing**.
