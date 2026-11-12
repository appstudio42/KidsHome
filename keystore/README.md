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

**Do not** use this key for Play Store / public “production” distribution. When
`SIGNING_*` repository secrets are configured, CI signs with your private release
keystore instead. Switching from this test key to a release key (or the reverse)
requires uninstalling the app once (settings will be lost).
