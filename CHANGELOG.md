# Changelog

## 1.6.1

The application id is now `io.github.appstudio42.kidshome`, matching [App Studio 42](https://appstudio42.github.io) so F-Droid can accept `io.github.appstudio42.*`. Release 1.6.0 used `io.github.marcelsavegnago.kidshome`. Those are different apps. Uninstall 1.6.0 and set the PIN, allowed apps, accessibility service, and Device Owner up again. Nothing is copied across.

### Changed
- `applicationId` and `namespace` are `io.github.appstudio42.kidshome`. The Java sources, unit tests, custom view class names, accessibility settings activity, and in-app intent actions (`REMOTE_REFRESH`, `REFRESH_ESCAPE_OVERLAY`) use that package.
- Version **1.6.1** (`versionCode` 161). `compileSdk` and `targetSdk` stay 35. AGP stays 8.7.3. `minSdk` stays 22.
- Draft fdroiddata recipe renamed to `docs/fdroid/io.github.appstudio42.kidshome.yml`. Builds older than 1.6.1 are removed. The only build is 1.6.1 / 161 with `commit: v1.6.1` (create that tag on the release commit before submitting the recipe). `AuthorName` is App Studio 42. `AuthorEmail` stays `marcel.savegnago@gmail.com`.
- Fastlane changelogs for versionCode 161 (`en-US` and `pt-BR`).

## 1.6.0

Android 15 edge-to-edge, a back handler that keeps lock screens up, screen-time gates that survive the clock and the watchdog, a LAN-only parent remote, a PBKDF2 PIN with backup turned off, in-app sites and Chrome PWAs, and navigation-bar contrast.

### Added
- Themed launcher icon (`monochrome`) on the adaptive icon, reusing `ic_home_foreground`.
- Fastlane changelogs for versionCode 160 (`en-US` and `pt-BR`).
- F-Droid build block 1.6.0 / 160. `commit` is the tag `v1.6.0` (create that tag on the release commit before submitting the recipe). `IssueTracker` is omitted because GitHub Issues and Discussions are disabled.
- Fastlane metadata for F-Droid (`en-US` and `pt-BR`), including the versionCode 151 changelog.
- Draft fdroiddata recipe at `docs/fdroid/io.github.marcelsavegnago.kidshome.yml`.
- Site shortcuts open full screen inside KidsHome (title and icon, no address bar) without allowing Chrome. Navigation stays on the shortcut host and its subdomains; other links, popups to other sites, downloads, and non-http schemes stay in the app and do not open a browser. Time on the page counts toward the daily limit, and bedtime / the daily limit still lock the screen. Embedded mode is the default, including when a browser was already chosen; that browser setting is kept and can be selected again.
- Allowed Chrome WebAPKs (`org.chromium.webapk.*`, for example GS Kids) open in the in-app site viewer instead of handing off to Chrome. The parent does not need to allow Chrome. The start URL comes from `org.chromium.webapk.shell_apk.startUrl`. When `org.chromium.webapk.shell_apk.scope` is set, navigation stays on that path prefix, so another site on the same host stays out. Time on the page counts toward the daily limit. DOM storage, IndexedDB, Cache Storage, and service workers (API 24+) stay in the WebView profile and are not cleared when the screen closes. That cache is separate from Chrome: the PWA has to be opened here once while online before it can work offline.

### Changed
- Version **1.6.0** (`versionCode` 160). `compileSdk` and `targetSdk` stay 35. AGP stays 8.7.3. `minSdk` stays 22.
- AppCompat **1.7.1** (1.8.0 requires minSdk 23), Material **1.13.0** (1.14.0 requires minSdk 23), RecyclerView **1.4.0**, ConstraintLayout **2.2.2**. `androidx.activity` stays **1.9.3**. `colorPrimary` is read from AppCompat because Material 1.13 no longer re-exports it.
- A `v*` tag build fails when any `SIGNING_*` secret is missing and never signs with `keystore/debug.keystore`. Pull requests and branch builds still pass `-PciSignRelease=true`.
- `compileSdk` and `targetSdk` are 35 (Android 15).
- Lock screens and the kid home apply status, navigation, cutout, and IME insets. Immersive bars still hide. Parent screens that are not immersive get the same padding on API 35 only, so Fire OS keeps fitted windows.
- PIN and other `AlertDialog`s on API 35 use a dialog theme that opts out of edge-to-edge, so the platform keeps them above the bars and the keyboard.
- Home, screen-time lock, and emergency PIN relaunch opt into background activity starts (`PendingIntent` creator and sender modes) on API 34+.
- The remote-control foreground service passes `specialUse` to `startForeground` on API 34+.
- Website tiles load favicons from the site itself. The Google favicon service is no longer used.
- `assembleRelease` stays unsigned unless `keystore.properties` exists or `-PciSignRelease=true` is set, so F-Droid can sign the build. Pull requests and branch CI still pass that property. A tag release passes `-PrequireReleaseSigning=true` instead and fails closed. Release APKs no longer embed git or dependency metadata.
- Gradle wrapper jar replaced with the official Gradle 8.9 wrapper. The previous jar was the Gradle 2.10 bootstrap. `distributionSha256Sum` pins the 8.9 distribution.
- Removed the unused prebuilt `disabling pkg/pm.jar` left over from LauncherHijack.
- Screen time stays blocked when the escape gate closes, the clock changes, or the accessibility watchdog restarts. Device Owner requires automatic time.
- The parent remote binds only to the private Wi-Fi or Ethernet address and no longer treats CGNAT as LAN.
- The parent PIN is stored as PBKDF2-HMAC-SHA1. Android backup and device-to-device transfer are off.
- Website favicons decode off the main thread. `BootReceiver` finishes with `goAsync`.

### Removed
- Automatic Poki Kids, PBS Kids Games, and Coolmath Games suggestions, and the button that added them. Shortcuts a parent already saved are kept.

### Fixed
- Navigation-bar contrast on Fire OS 7: dark icons on a light bar from API 27 through 34, dark bars below API 27/23 where the platform can only draw light icons, and matching contrast on API 35 in light and night themes, on parent and setup screens.

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
