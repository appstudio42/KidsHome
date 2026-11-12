# Changelog

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
