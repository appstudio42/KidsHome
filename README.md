🇬🇧 **English** | [🇧🇷 Português](README.pt-BR.md) | [🇪🇸 Español](README.es.md) | [🇫🇷 Français](README.fr.md) | [🇩🇪 Deutsch](README.de.md)

<p align="center">
  <img src="docs/logo.png" alt="KidsHome logo" width="128" height="128" />
</p>

<h1 align="center">KidsHome</h1>

<p align="center"><strong>A lightweight kid-mode home for Android phones, tablets, and Amazon Fire OS.</strong></p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><img src="https://img.shields.io/github/v/release/appstudio42/KidsHome?label=latest%20release" alt="Latest release" /></a>
  <a href="https://github.com/appstudio42/KidsHome/releases"><img src="https://img.shields.io/github/downloads/appstudio42/KidsHome/total" alt="Downloads" /></a>
  <a href="https://github.com/appstudio42/KidsHome/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/appstudio42/KidsHome/android.yml?branch=master&label=CI" alt="CI status" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/appstudio42/KidsHome" alt="License" /></a>
  <img src="https://img.shields.io/badge/min%20Android-5.1%20(API%2022)-green" alt="Min Android 5.1 API 22" />
</p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><strong>⬇ Download APK (latest release)</strong></a>
</p>

KidsHome (v1.5.1) turns a spare phone or tablet into a kid-friendly home screen: only the apps and sites you allow, with daily screen-time limits, bedtime lock, a parent PIN escape, and optional LAN remote control from a phone browser. It picks the best enforcement mode for the device (or you override it in **Device setup**). Maintained by [App Studio 42](https://appstudio42.github.io).

<p align="center">
  <img src="docs/media/setup.gif" alt="KidsHome setup: create PIN, choose apps, set screen time, enter kid mode" width="720" />
</p>

<p align="center"><em>Setup flow — create PIN → choose allowed apps → set screen time → enter kid mode</em></p>

## Screenshots

Real captures from an Android tablet emulator (1280×800) with demo child name **Theo**.

| Kid home | Time's up | Parent PIN |
|:---:|:---:|:---:|
| ![Kid home](docs/screenshots/01-kid-home.png) | ![Time's up](docs/screenshots/02-time-up.png) | ![PIN entry](docs/screenshots/03-pin-entry.png) |
| Kid home grid | Screen-time lock | Emergency PIN exit |

| Kid mode | Screen time | Device setup |
|:---:|:---:|:---:|
| ![Kid mode](docs/screenshots/04-parent-kid-mode.png) | ![Screen time](docs/screenshots/05-screen-time-settings.png) | ![Device setup](docs/screenshots/06-device-setup.png) |
| Parent kid-mode settings | Limits & bedtime | Mode checklist |

| Allowed apps | Parent hub |
|:---:|:---:|
| ![Allowed apps](docs/screenshots/07-allowed-apps.png) | ![Main hub](docs/screenshots/08-main-hub.png) |
| Pick apps for the grid | Main KidsHome screen |

| Schedules | Per-app limits | Earn time |
|:---:|:---:|:---:|
| ![Schedules](docs/screenshots/09-schedules.png) | ![App limits](docs/screenshots/10-app-limits.png) | ![Earn time](docs/screenshots/11-earn-time.png) |
| Routine windows & categories | Daily minutes per app | Tasks & bonus minutes |

| Weekly report | Star celebration | Bedtime |
|:---:|:---:|:---:|
| ![Report](docs/screenshots/12-weekly-report.png) | ![Celebration](docs/screenshots/13-star-celebration.png) | ![Bedtime](docs/screenshots/14-bedtime.png) |
| Local usage chart | Earn-time celebration | Friendly bedtime lock |

| Remote settings | Phone page | After +time | Remote lock | Phone · locked |
|:---:|:---:|:---:|:---:|:---:|
| ![Remote settings](docs/screenshots/1.5.0/01-remote-settings-qr.png) | ![Remote page](docs/screenshots/1.5.0/02-remote-phone-page.png) | ![After add](docs/screenshots/1.5.0/03-remote-after-add-time.png) | ![Remote lock](docs/screenshots/1.5.0/04-remote-lock-tablet.png) | ![Phone locked](docs/screenshots/1.5.0/09-remote-phone-locked.png) |
| URL + QR in parent settings | Usage & state on phone | Time added remotely | Lock now on tablet | Kid mode on · remote lock |

| Remote settings (pt-BR) | Phone page (pt-BR) | Lock screen (pt-BR) | Phone · kid mode locked (pt-BR) |
|:---:|:---:|:---:|:---:|
| ![Remote settings pt-BR](docs/screenshots/1.5.0/05-pt-remote-settings-qr.png) | ![Remote page pt-BR](docs/screenshots/1.5.0/06-pt-remote-phone-page.png) | ![Remote lock pt-BR](docs/screenshots/1.5.0/07-pt-remote-lock-tablet.png) | ![Phone locked pt-BR](docs/screenshots/1.5.0/08-pt-remote-phone-locked.png) |
| Settings + QR | Usage & state | Tablet lock | Kid mode on · remote lock |

## Features

- **Three enforcement modes** (auto-detected, with manual override):
  - Accessibility Home hijack — Amazon Fire tablets
  - Default launcher — regular Android
  - Device Owner + lock task — strongest kiosk
- **Modern Material 3 UI** — cheerful seed palette (dynamic color on Android 12+), light/dark parent themes, first-run onboarding
- **Kid home** — large rounded app/site tiles, child avatar, remaining-time ring, soft motion (respects reduce-motion)
- **Screen time** — daily limit slider, bedtime window, friendly lock illustrations, PIN to add time / disable for today
- **Schedules / routines** — weekday time windows (school mornings, reading time, bedtime) with app categories
- **Per-app limits** — daily minutes per allowed app on top of the total limit; locked tiles when exhausted
- **Earn time / stars** — PIN-gated bonus minutes and a simple task checklist with celebration + star counter
- **Weekly usage report** — local per-app/per-day history (~4 weeks), Canvas bar chart, clear / CSV export
- **Remote control (LAN)** — optional embedded HTTP page on the tablet Wi‑Fi (port 8642); QR pairing token or parent PIN; add time, lock/unlock; off by default; no cloud
- **Emergency PIN exit** — modern keypad with dots, shake + haptics, lockout; 5 taps top-right or hold Back ~3s
- **Parent hub** — section cards for profile, apps, websites, screen time, schedules, limits, earn time, report, remote control, device setup, security, about
- Survives reboot; warns if accessibility is off when that mode needs it
- Optional accessibility blocking of non-allowed apps as an **extra layer** in launcher / Device Owner modes
- UI strings in **en / pt-BR / es / fr / de**

## Remote control

Off by default. In the parent hub open **Remote control**, enable it, then scan the QR (or open `http://<tablet-ip>:8642`) from a phone on the **same Wi‑Fi**. The page shows today’s usage, stars, and tablet state; parents can add +15/+30/+60 minutes (or custom), **Lock now**, or **Unlock**. The QR pairing link exchanges its token once for an HttpOnly session cookie and redirects to a URL without the token. After that the page uses the cookie; if the session is gone, scan the QR again or sign in with the parent PIN (the PIN is never shown, and the token is not accepted in the query string). PIN attempts are rate-limited; regenerating the token revokes sessions. Only private LAN peers are accepted. Actions appear in the weekly report as **parent remote**. FOSS stack (NanoHTTPD + ZXing core) — F-Droid friendly, no accounts/cloud.

## Supported devices

| Mode | Best for | How it works |
|------|----------|--------------|
| **Accessibility Home hijack** | Amazon Fire tablets | AccessibilityService redirects Home / Fire launcher |
| **Default launcher** | Regular Android phones & tablets | KidsHome registers as `CATEGORY_HOME` and is set as the default Home app |
| **Device Owner + lock task** | Strongest kiosk | One-time `dpm set-device-owner`, then native lock-task whitelist |

> Primary low-end test device: Amazon Fire 7 (9th gen, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28). **iOS is out of scope.**

GPL-3 fork of [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), based on [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

## Quick start

1. Download the APK from the [latest release](https://github.com/appstudio42/KidsHome/releases/latest) (or the GitHub Actions artifact `KidsHome-release-apk`).
2. Install (same CI test signature across builds → upgrades keep data):

   ```bash
   adb install -r KidsHome-release.apk
   ```

3. Open KidsHome → menu → **Device setup** and finish the checklist for your mode.
4. Menu → **Kid mode** → create PIN, configure apps / screen time, enable.

### A) Amazon Fire OS (accessibility hijack)

Fire OS does **not** allow replacing the default launcher. KidsHome keeps the classic AccServ Home hijack.

1. Enable accessibility (UI **or** ADB).

   **UI:** Settings → Accessibility → **KidsHome home detection** → enable.

   **ADB component:**

   ```text
   io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   ```

   ```bash
   adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   adb shell settings put secure accessibility_enabled 1
   ```

2. (Optional, for corner-tap escape)

   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```

3. In KidsHome, go to **Kid mode** (or pick a fallback launcher if you use non-kid Home hijack).
4. Reboot once. Press Home — kid home / chosen target should open (a brief Fire launcher flash may appear).

### B) Regular Android (default launcher)

1. Open **Device setup** → mode **Automatic** or **Default launcher**.
2. Tap **Set as default Home** (uses `RoleManager.ROLE_HOME` on Android 10+, otherwise Home settings).
3. Choose **KidsHome** in the system picker.
4. Grant overlay / battery exemption as prompted.
5. Optionally enable accessibility as an **extra layer** to bounce non-allowed apps.
6. Enable **Kid mode**. Home should open the kid grid.

### C) Device Owner (strongest)

Device Owner can only be set when the device has **no accounts** (remove Google/Amazon accounts) or after a **factory reset**, and typically before other device admins are active.

1. Install KidsHome.
2. Provision Device Owner (component must match exactly):

   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```

   The same command is shown / copyable in **Device setup**.

3. In **Device setup**, confirm Device Owner = yes. Optionally disable status bar / keyguard while locked.
4. Enable **Kid mode**. KidsHome will:
   - `setLockTaskPackages` for allowed apps + browser + itself
   - `addPersistentPreferredActivity` for HOME → `KidHomeActivity`
   - `startLockTask` from kid home (kiosk)
5. Emergency PIN exit calls `stopLockTask` and can disable kid mode.

## Screen time, schedules & earn time

In the **parent hub** (PIN-protected) you can configure:

- **Daily screen-time limit** (minutes) — kid home shows time left; when the limit is reached, a friendly “Time's up” lock appears.
- **Bedtime block** — sleep window (e.g. 20:00–07:00); during bedtime the lock says “It's bedtime”.
- **Schedules / routines** — weekday windows that show only certain app categories or block everything until morning.
- **Per-app limits** — e.g. 30 min/day for video apps, unlimited for education.
- **Earn time** — grant bonus minutes (total or per app) or mark checklist tasks done to award stars.
- **Weekly report** — local usage by day/app, totals vs limits, clear history, optional CSV share.

A parent unlocks with the PIN to add 15/30 minutes, disable the limit for today, or disable bedtime for tonight. Everything stays on-device (no network / analytics / cloud).

## Emergency PIN exit

Works in **all** enforcement modes:

- Tap the **top-right corner** 5 times quickly (needs “Display over other apps”), or
- **Hold Back** for about 3 seconds

Then enter the parent PIN to disable kid mode, open KidsHome settings, or stay in kid mode.

## FAQ & troubleshooting

### How do I exit kid mode via ADB?

```bash
adb shell am force-stop io.github.marcelsavegnago.kidshome
# Or clear kid-mode prefs (resets settings):
adb shell pm clear io.github.marcelsavegnago.kidshome
```

If Device Owner lock-task is active, clear Device Owner first (see below), or unlock with the PIN in the UI.

### Upgrading without losing settings

Use the same signing key (CI test keystore by default) and reinstall with replace:

```bash
adb install -r KidsHome-release.apk
```

Switching to a different signing key requires one uninstall first.

### How do I remove Device Owner?

In **Device setup** → **Remove Device Owner** (PIN if kid mode is on), or the app calls `clearDeviceOwnerApp`. Then:

```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

If you skip clearing Device Owner, Android will refuse to uninstall the app.

### Undo / uninstall (non–Device Owner)

```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Known limitations

- Fire: `com.amazon.firelauncher` often cannot be disabled; hijack is the only Home option.
- Accessibility / launcher modes are **not** full MDM — ADB / force-stop can bypass.
- Device Owner is strong but needs a clean device and ADB once; clearing owner is required to uninstall.
- After low-memory kills on Fire, the first Home press can be slow — press again if needed.

## Building from source

```bash
./gradlew assembleRelease
```

Requires **JDK 17+** and Android SDK (`compileSdk` / `targetSdk` 34). `minSdk 22` (Fire 7 / SDK 28 fully supported).

### CI signing

| Mode | When | Signature |
|------|------|-----------|
| **Stable CI test key** | No `SIGNING_*` secrets | `keystore/debug.keystore` (committed) — same across CI runs, so `adb install -r` upgrades keep settings |
| **Release key** | Secrets set | Your private keystore |

| Secret | Purpose |
|--------|---------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Key password |

See `keystore/README.md`.

## Releasing

1. Bump `versionName` / `versionCode` in `app/build.gradle`.
2. Push a tag matching `vX.Y.Z`:

   ```bash
   git tag v1.3.0
   git push origin v1.3.0
   ```

3. GitHub Actions (`.github/workflows/android.yml`) builds the release APK and attaches it to the GitHub Release.

## Contributing

Issues and pull requests are welcome. Please keep changes focused, test on a real Fire tablet or emulator when touching enforcement / accessibility, and prefer English for code and comments.

Screenshot / GIF regeneration (optional): see [`.github/workflows/screenshots.yml`](.github/workflows/screenshots.yml) and [`docs/capture-screenshots.sh`](docs/capture-screenshots.sh).

## License

KidsHome is licensed under the **GNU GPL v3** — see [LICENSE](LICENSE).

Credits: BaronKiko (LauncherHijack), Meems33 / WhiteRaven777 (Launcher Replacer).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes (current: **1.5.1**).
