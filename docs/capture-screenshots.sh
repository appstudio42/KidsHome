#!/usr/bin/env bash
# Capture KidsHome README screenshots + setup GIF from a connected emulator/device.
# Requires: adb, ffmpeg, convert (ImageMagick), pngquant (optional), gifsicle (optional).
# Install a release or debug APK first. Rooted emulator recommended for writing prefs.
set -euo pipefail

PKG="${PKG:-io.github.marcelsavegnago.kidshome}"
CLS="io.github.marcelsavegnago.kidshome"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOT="${ROOT}/docs/screenshots"
MEDIA="${ROOT}/docs/media"
RAW="/tmp/kidshome-raw"
PIN_SALT="0123456789abcdef0123456789abcdef"
PIN_HASH="ae60819222c8c4c5e7a63dd250d88f4913926c0ca96d4fcd0accb1b14b34fc7e"
TODAY="$(adb shell date +%Y%m%d | tr -d '\r')"
APP_UID="$(adb shell stat -c %u /data/data/$PKG 2>/dev/null | tr -d '\r')"

mkdir -p "$SHOT" "$MEDIA" "$RAW"

# Prefer kid-friendly launchable packages present on the emulator. Never include Settings.
pick_demo_packages() {
    # Kid-facing only — never Settings / Gmail / Calendar / Music.
    local candidates=(
    "com.google.android.apps.youtube.kids"
    "com.google.android.youtube"
    "com.android.chrome"
    "com.android.camera2"
    "com.google.android.GoogleCamera"
    "com.google.android.apps.photos"
    "com.google.android.apps.maps"
  )
  local chosen=()
  local pkg
  for pkg in "${candidates[@]}"; do
    if adb shell pm path "$pkg" >/dev/null 2>&1; then
      chosen+=("$pkg")
    fi
    # Cap at 4 apps so + 2 site shortcuts = 2 full rows (no clipped third row).
    if [ "${#chosen[@]}" -ge 4 ]; then
      break
    fi
  done
  DEMO_PACKAGES_XML=""
  for pkg in "${chosen[@]}"; do
    DEMO_PACKAGES_XML+="    <string>${pkg}</string>"$'\n'
  done
  if [ -z "$DEMO_PACKAGES_XML" ]; then
    DEMO_PACKAGES_XML=$'    <string>com.android.chrome</string>\n    <string>com.android.camera2</string>\n    <string>com.google.android.apps.photos</string>\n'
  fi
}

pick_demo_packages

write_prefs() {
  local extra="${1:-}"
  adb shell am force-stop "$PKG"
  sleep 0.4
  adb shell "mkdir -p /data/data/$PKG/shared_prefs"
  adb shell "cat > /data/data/$PKG/shared_prefs/KidsHome.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <boolean name="KidModeEnabled" value="true" />
  <string name="KidPinSalt">${PIN_SALT}</string>
  <string name="KidPinHash">${PIN_HASH}</string>
  <string name="ChildName">Theo</string>
  <string name="ChildAvatar">🦊</string>
  <boolean name="OnboardingDone" value="true" />
  <int name="PrefsVersion" value="1" />
  <boolean name="KidLockToApp" value="false" />
  <set name="AllowedPackages">
${DEMO_PACKAGES_XML}  </set>
  <string name="SiteShortcutsJson">[{"id":"poki","name":"Poki Kids","url":"https://kids.poki.com/"},{"id":"pbs","name":"PBS Kids","url":"https://pbskids.org/games"}]</string>
  <boolean name="siteSuggestionsSeeded" value="true" />
  <string name="BrowserPackage">com.android.chrome</string>
  <string name="BrowserActivity">com.google.android.apps.chrome.Main</string>
  <boolean name="ScreenTimeEnabled" value="true" />
  <int name="DailyLimitMinutes" value="60" />
  <long name="UsedMillisToday" value="900000" />
  <long name="BonusMillisToday" value="0" />
  <string name="UsageDayKey">${TODAY}</string>
  <boolean name="BedtimeEnabled" value="false" />
  <string name="EnforcementMode">launcher</string>
  <boolean name="defaultsLoaded" value="true" />
  <boolean name="EscapeCornerEnabled" value="true" />
  <boolean name="EscapeBackHoldEnabled" value="true" />
  <boolean name="AccessibilityExtraBlocking" value="true" />
  ${extra}
</map>
EOF
  if [ -n "${APP_UID}" ]; then
    adb shell "chown ${APP_UID}:${APP_UID} /data/data/$PKG/shared_prefs/KidsHome.xml; chmod 660 /data/data/$PKG/shared_prefs/KidsHome.xml"
  fi
}

optimize_png() {
  local src="$1" dest="$2"
  # Keep enough colors that fine UI (PIN digits, icons) stays crisp in README shots.
  convert "$src" -strip "PNG32:/tmp/kh-crop.png"
  if command -v pngquant >/dev/null; then
    pngquant --force --quality=85-100 --speed 1 --output "$dest" /tmp/kh-crop.png \
      || cp /tmp/kh-crop.png "$dest"
  else
    cp /tmp/kh-crop.png "$dest"
  fi
}

screencap() {
  local name="$1"
  local wait_s="${2:-2.4}"
  sleep "$wait_s"
  adb exec-out screencap -p > "${RAW}/${name}.png"
  optimize_png "${RAW}/${name}.png" "${SHOT}/${name}.png"
  echo "Wrote ${SHOT}/${name}.png ($(identify -format '%wx%h %b' "${SHOT}/${name}.png"))"
}

# Dismiss the one-time "Viewing full screen / Got it" immersive cling if shown.
dismiss_immersive_cling() {
  local dump="/sdcard/kh-ui.xml"
  adb shell uiautomator dump "$dump" >/dev/null 2>&1 || return 0
  local line
  line="$(adb shell cat "$dump" 2>/dev/null | tr '>' '\n' | grep 'resource-id="android:id/ok"' | head -1 || true)"
  if [ -z "$line" ]; then
    return 0
  fi
  local coords
  coords="$(printf '%s' "$line" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')"
  if [ -n "$coords" ]; then
    # shellcheck disable=SC2086
    set -- $coords
    local cx=$(( ($1 + $3) / 2 ))
    local cy=$(( ($2 + $4) / 2 ))
    adb shell input tap "$cx" "$cy" >/dev/null 2>&1 || true
    sleep 0.5
  fi
}

echo "Capturing screenshots for $PKG …"
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
# Skip the one-time "Viewing full screen" immersive confirmation dialog.
# Quote carefully — unquoted * is rejected by `settings` on API 34.
adb shell "settings put secure immersive_mode_confirmations '*'" || true

write_prefs ""
adb shell am start -n "$PKG/$CLS.KidHomeActivity"
sleep 1.2
dismiss_immersive_cling
screencap "01-kid-home" 1.6

write_prefs '<long name="UsedMillisToday" value="3600000" />'
adb shell am start -n "$PKG/$CLS.KidHomeActivity"
sleep 1.0
dismiss_immersive_cling
screencap "02-time-up" 1.6

write_prefs ""
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.EscapePinActivity"
sleep 1.0
dismiss_immersive_cling
screencap "03-pin-entry" 1.6

write_prefs '<boolean name="KidModeEnabled" value="false" />
  <boolean name="BedtimeEnabled" value="true" />
  <boolean name="ScreenTimeEnabled" value="true" />'
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.KidModeActivity"
screencap "04-parent-kid-mode"
adb shell input swipe 640 620 640 80 300
sleep 0.4
adb shell input swipe 640 620 640 80 300
screencap "05-screen-time-settings"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.SetupActivity"
screencap "06-device-setup"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.AllowedAppsActivity"
screencap "07-allowed-apps" 3.5

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.MainActivity"
sleep 3.5
adb shell input tap 20 20 >/dev/null 2>&1 || true
screencap "08-main-hub" 1.2

echo "Done. Optional: record docs/media/setup.gif with adb screenrecord + ffmpeg (see README)."
