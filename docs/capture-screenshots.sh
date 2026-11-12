#!/usr/bin/env bash
# Capture KidsHome README screenshots + setup GIF from a connected emulator/device.
# Requires: adb, ffmpeg, convert (ImageMagick), pngquant (optional).
# Install a release or debug APK first. Rooted emulator recommended for writing prefs.
#
# Densities (override with env):
#   TABLET_SIZE / TABLET_DPI  — README defaults (1280x800 @ 213 ≈ mdpi+ tablet)
#   FIRE7_SIZE  / FIRE7_DPI   — Fire 7 landscape check (1024x600 @ 160)
#   PHONE_SIZE  / PHONE_DPI   — phone portrait check (1080x1920 @ 420)
set -euo pipefail

PKG="${PKG:-io.github.marcelsavegnago.kidshome}"
CLS="io.github.marcelsavegnago.kidshome"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOT="${ROOT}/docs/screenshots"
MEDIA="${ROOT}/docs/media"
RAW="/tmp/kidshome-raw"
EXTRA="${ROOT}/docs/screenshots-extra"
PIN_SALT="0123456789abcdef0123456789abcdef"
PIN_HASH="ae60819222c8c4c5e7a63dd250d88f4913926c0ca96d4fcd0accb1b14b34fc7e"
TODAY="$(adb shell date +%Y%m%d | tr -d '\r')"
APP_UID="$(adb shell stat -c %u /data/data/$PKG 2>/dev/null | tr -d '\r')"

TABLET_SIZE="${TABLET_SIZE:-1280x800}"
TABLET_DPI="${TABLET_DPI:-213}"
FIRE7_SIZE="${FIRE7_SIZE:-1024x600}"
FIRE7_DPI="${FIRE7_DPI:-160}"
PHONE_SIZE="${PHONE_SIZE:-1080x1920}"
PHONE_DPI="${PHONE_DPI:-420}"

mkdir -p "$SHOT" "$MEDIA" "$RAW" "$EXTRA"

# Prefer kid-friendly launchable packages present on the emulator. Never include Settings.
pick_demo_packages() {
  local candidates=(
    "com.google.android.apps.youtube.kids"
    "com.google.android.youtube"
    "com.android.chrome"
    "com.android.camera2"
    "com.google.android.GoogleCamera"
    "com.google.android.apps.photos"
    "com.google.android.apps.maps"
    "com.google.android.deskclock"
  )
  local chosen=()
  local pkg
  for pkg in "${candidates[@]}"; do
    if adb shell pm path "$pkg" >/dev/null 2>&1; then
      chosen+=("$pkg")
    fi
    # Cap at 6 apps so + 2 site shortcuts = 8 tiles (2×4 on Fire 7 landscape).
    if [ "${#chosen[@]}" -ge 6 ]; then
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
  convert "$src" -strip "PNG32:/tmp/kh-crop.png"
  if command -v pngquant >/dev/null; then
    pngquant --force --quality=85-100 --speed 1 --output "$dest" /tmp/kh-crop.png \
      || cp /tmp/kh-crop.png "$dest"
  else
    cp /tmp/kh-crop.png "$dest"
  fi
}

set_display() {
  local size="$1" dpi="$2"
  adb shell wm size "$size"
  adb shell wm density "$dpi"
  sleep 0.6
}

screencap_to() {
  local dest_dir="$1"
  local name="$2"
  local wait_s="${3:-2.0}"
  sleep "$wait_s"
  adb exec-out screencap -p > "${RAW}/${name}.png"
  optimize_png "${RAW}/${name}.png" "${dest_dir}/${name}.png"
  echo "Wrote ${dest_dir}/${name}.png ($(identify -format '%wx%h dens=%x %b' "${dest_dir}/${name}.png"))"
}

screencap() {
  screencap_to "$SHOT" "$1" "${2:-2.0}"
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

capture_kid_trio() {
  local dest="$1"
  local prefix="$2"
  write_prefs ""
  adb shell am start -n "$PKG/$CLS.KidHomeActivity"
  sleep 1.0
  dismiss_immersive_cling
  screencap_to "$dest" "${prefix}01-kid-home" 1.5

  write_prefs '<long name="UsedMillisToday" value="3600000" />'
  adb shell am start -n "$PKG/$CLS.KidHomeActivity"
  sleep 1.0
  dismiss_immersive_cling
  screencap_to "$dest" "${prefix}02-time-up" 1.5

  write_prefs ""
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/$CLS.EscapePinActivity"
  sleep 1.0
  dismiss_immersive_cling
  screencap_to "$dest" "${prefix}03-pin-entry" 1.5
}

echo "Capturing screenshots for $PKG …"
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell "settings put secure immersive_mode_confirmations '*'" || true

# --- README set: typical tablet landscape @ ~213dpi ---
echo "Tablet ${TABLET_SIZE} @ ${TABLET_DPI}dpi (README)"
set_display "$TABLET_SIZE" "$TABLET_DPI"
capture_kid_trio "$SHOT" ""

write_prefs '<boolean name="KidModeEnabled" value="false" />
  <boolean name="BedtimeEnabled" value="true" />
  <boolean name="ScreenTimeEnabled" value="true" />'
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.KidModeActivity"
screencap "04-parent-kid-mode"
# Swipe depends on density; use relative mid-screen swipes.
W=$(echo "$TABLET_SIZE" | cut -dx -f1)
H=$(echo "$TABLET_SIZE" | cut -dx -f2)
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/8)) 300
sleep 0.4
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/8)) 300
screencap "05-screen-time-settings"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.SetupActivity"
screencap "06-device-setup"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.AllowedAppsActivity"
screencap "07-allowed-apps" 3.2

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.MainActivity"
sleep 3.0
adb shell input tap 24 24 >/dev/null 2>&1 || true
screencap "08-main-hub" 1.2

# --- Fire 7 landscape check ---
echo "Fire 7 ${FIRE7_SIZE} @ ${FIRE7_DPI}dpi"
set_display "$FIRE7_SIZE" "$FIRE7_DPI"
capture_kid_trio "$EXTRA" "fire7-"

# --- Phone portrait check ---
echo "Phone ${PHONE_SIZE} @ ${PHONE_DPI}dpi"
set_display "$PHONE_SIZE" "$PHONE_DPI"
capture_kid_trio "$EXTRA" "phone-"

# Restore tablet for any follow-up work
set_display "$TABLET_SIZE" "$TABLET_DPI"

echo "Done. Extra shots in ${EXTRA}. Optional: build docs/media/setup.gif with ffmpeg."
