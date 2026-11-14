# Changelog

## Unreleased

### Changed
- `compileSdk` and `targetSdk` are 35 (Android 15). `versionCode` / `versionName` stay 151 / 1.5.1. AGP stays 8.7.3.
- Lock screens and the kid home apply status, navigation, cutout, and IME insets. Immersive bars still hide. Parent screens that are not immersive get the same padding on API 35 only, so Fire OS keeps fitted windows.
- PIN and other `AlertDialog`s on API 35 use a dialog theme that opts out of edge-to-edge, so the platform keeps them above the bars and the keyboard.
- Home, screen-time lock, and emergency PIN relaunch opt into background activity starts (`PendingIntent` creator and sender modes) on API 34+.
- The remote-control foreground service passes `specialUse` to `startForeground` on API 34+.
- Website tiles load favicons from the site itself. The Google favicon service is no longer used.
- `assembleRelease` stays unsigned unless `keystore.properties` exists or `-PciSignRelease=true` is set, so F-Droid can sign the build. CI still passes that property. Release APKs no longer embed git or dependency metadata.
- Gradle wrapper jar replaced with the official Gradle 8.9 wrapper. The previous jar was the Gradle 2.10 bootstrap. `distributionSha256Sum` pins the 8.9 distribution.
- Removed the unused prebuilt `disabling pkg/pm.jar` left over from LauncherHijack.

### Added
- Fastlane metadata for F-Droid (`en-US` and `pt-BR`), including the versionCode 151 changelog.
- Draft fdroiddata recipe at `docs/fdroid/io.github.marcelsavegnago.kidshome.yml`.

## 1.5.1

Bugfix: overnight lock screens no longer keep the display awake or burn the next day's screen-time allowance.

### Fixed
- Removed `FLAG_KEEP_SCREEN_ON` from the screen-time lock and escape PIN activities so the tablet follows its normal screen timeout.
- Screen-time ticks no longer accrue daily or per-app usage while blocked (daily limit, bedtime, schedule bedtime, remote lock) or while KidsHome lock/PIN UI is visible.
- Usage deltas that cross midnight are split so pre-midnight time is not charged to the new day.

### Changed
- Version **1.5.1** (`versionCode` 151).

## 1.5.0

Parent remote control over the local Wi‑Fi — tiny embedded HTTP page on the tablet, no cloud / accounts / phone app.

### Added
- **Remote control** parent hub section (off by default): enable a LAN web page on port **8642**, show URL + QR (pairing token), regenerate token.
- Mobile-friendly HTML/CSS/JS page (browser language: **en** / **pt-BR**) with today’s usage, remaining time, per-app usage, stars, kid-mode/lock/schedule state; actions: add time, lock now, unlock; auto-refresh.
- Security: pairing token or parent PIN → session cookie; PIN rate-limit/lockout; private LAN peers only; PIN never exposed; token regenerate revokes sessions.
- Foreground service + low-priority notification while enabled; restarts on boot; handles Wi‑Fi IP changes in the notification URL.
- Remote actions logged for the weekly report as **parent remote**.
- Unit tests for auth, LAN filter, request handling, rate limiting, and time math.
- Screenshots under `docs/screenshots/1.5.0/`.

### Changed
- Version **1.5.0** (`versionCode` 150).
- Prefs schema **v3** migration for remote keys (keeps PIN / allowlist / limits).

### Unchanged
- `applicationId` `io.github.marcelsavegnago.kidshome` and release signing setup — `adb install -r` upgrades keep data.
- GPL-3.0; F-Droid-friendly dependencies only (NanoHTTPD, ZXing core on Maven Central).

## 1.4.0

Offline parent controls for routines, per-app limits, earn-time, and weekly usage — Material 3 Views UI, no Compose / no cloud.

### Added
- **Schedules / routines** — weekday time windows with category filters (Games, Education/Reading, Video, Other) or a bedtime-style full block. Outside any window, normal allowlist rules apply.
- **Per-app time limits** — daily minutes on top of the total limit; locked/greyed tiles with remaining time; enforced in Fire accessibility and other modes.
- **Earn time / stars** — PIN-gated bonus minutes (total or per app), optional task checklist, star celebration on kid home, star counter.
- **Weekly usage report** — local per-app/per-day history (~4 weeks), Canvas bar chart, totals vs limits, clear history, CSV share sheet.
- Parent hub cards for all four features; strings in **en / pt-BR / es / fr / de**.
- Unit tests for the rule engine (schedule windows, per-app limits, bonus minutes, day rollover) and usage history helpers.

### Changed
- Version **1.4.0** (`versionCode` 140).
- Prefs schema **v2** migration keeps PIN, allowlist, total limit, and bedtime from 1.3.0.
- Parent screens (schedules, per-app limits, earn time, weekly report) use the same immersive / lock-task chrome as kid-facing screens, and match the Material 3 pastel hub styling (category chips, limit slider/presets + progress, report chart polish).
- Bedtime illustration blends with the lock background (no opaque square).

### Unchanged
- `applicationId` and release signing setup (CI keystore / optional release secrets) — `adb install -r` upgrades keep data.
