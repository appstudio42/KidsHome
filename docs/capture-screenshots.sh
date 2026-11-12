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

mkdir -p "$SHOT" "$MEDIA" "$RAW"

write_prefs() {
  local extra="${1:-}"
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/$CLS.MainActivity" >/dev/null
  sleep 1
  adb shell am force-stop "$PKG"
  adb shell "mkdir -p /data/data/$PKG/shared_prefs"
  adb shell "cat > /data/data/$PKG/shared_prefs/KidsHome.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <boolean name="KidModeEnabled" value="true" />
  <string name="KidPinSalt">${PIN_SALT}</string>
  <string name="KidPinHash">${PIN_HASH}</string>
  <string name="ChildName">Theo</string>
  <boolean name="KidLockToApp" value="false" />
  <set name="AllowedPackages">
    <string>com.android.chrome</string>
    <string>com.android.camera2</string>
    <string>com.google.android.apps.youtube.music</string>
    <string>com.google.android.apps.photos</string>
    <string>com.google.android.deskclock</string>
    <string>com.google.android.calendar</string>
  </set>
  <string name="SiteShortcutsJson">[{"id":"poki","name":"Poki Kids","url":"https://kids.poki.com/"},{"id":"pbs","name":"PBS Kids","url":"https://pbskids.org/games"},{"id":"coolmath","name":"Coolmath","url":"https://www.coolmathgames.com/"}]</string>
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
}

optimize_png() {
  local src="$1" dest="$2"
  convert "$src" -gravity North -crop 1280x700+0+0 +repage -strip "PNG32:/tmp/kh-crop.png" 2>/dev/null \
    || convert "$src" -strip "PNG32:/tmp/kh-crop.png"
  if command -v pngquant >/dev/null; then
    pngquant --force --quality=70-90 --output "$dest" /tmp/kh-crop.png
  else
    cp /tmp/kh-crop.png "$dest"
  fi
}

screencap() {
  local name="$1"
  sleep 1.8
  adb exec-out screencap -p > "${RAW}/${name}.png"
  optimize_png "${RAW}/${name}.png" "${SHOT}/${name}.png"
  echo "Wrote ${SHOT}/${name}.png"
}

echo "Capturing screenshots for $PKG …"
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true

write_prefs ""
adb shell am start -n "$PKG/$CLS.KidHomeActivity"
screencap "01-kid-home"

write_prefs '<long name="UsedMillisToday" value="3600000" />'
adb shell am start -n "$PKG/$CLS.KidHomeActivity"
screencap "02-time-up"

write_prefs ""
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.EscapePinActivity"
screencap "03-pin-entry"

write_prefs '<boolean name="KidModeEnabled" value="false" />
  <boolean name="BedtimeEnabled" value="true" />
  <boolean name="ScreenTimeEnabled" value="true" />'
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.KidModeActivity"
screencap "04-parent-kid-mode"
adb shell input swipe 640 700 640 150 400
screencap "05-screen-time-settings"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.SetupActivity"
screencap "06-device-setup"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.AllowedAppsActivity"
screencap "07-allowed-apps"

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$CLS.MainActivity"
screencap "08-main-hub"

echo "Done. Optional: record docs/media/setup.gif with adb screenrecord + ffmpeg (see README)."
