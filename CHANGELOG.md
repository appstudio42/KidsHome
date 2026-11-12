# Changelog

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

### Unchanged
- `applicationId` and release signing setup (CI keystore / optional release secrets) — `adb install -r` upgrades keep data.
