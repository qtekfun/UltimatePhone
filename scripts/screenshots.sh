#!/usr/bin/env bash
# Takes the store screenshots on a running emulator (1080x2400, the "pixel_6" profile) with the debug build installed.
# Usage: scripts/screenshots.sh <output dir>. Run by .github/workflows/screenshots.yml; coordinates assume that screen size.
set -uo pipefail

PKG=com.qtekfun.ultimatephone.debug
OUT=${1:-screenshots}
APK=app/build/outputs/apk/debug/app-debug.apk
CALLER=5551234567 # a fictional number: the emulator's own "caller" for simulated calls

tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
shot() { adb exec-out screencap -p > "$OUT/$LOCALE/$1.png"; echo "shot $LOCALE/$1"; }

adb install -r "$APK"
adb shell cmd role add-role-holder android.app.role.DIALER "$PKG" || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING "$PKG" || true

for LOCALE in en-US es-ES; do
  mkdir -p "$OUT/$LOCALE"
  adb shell am force-stop "$PKG"
  adb shell pm clear "$PKG"
  adb shell cmd locale set-app-locales "$PKG" --locales "$LOCALE" || true
  for p in CALL_PHONE READ_PHONE_STATE READ_PHONE_NUMBERS POST_NOTIFICATIONS ANSWER_PHONE_CALLS READ_CALL_LOG; do
    adb shell pm grant "$PKG" "android.permission.$p" || true
  done
  adb shell am start -n "$PKG/com.qtekfun.ultimatephone.MainActivity" > /dev/null
  sleep 6
  shot 01_welcome
  tap 902 2241 3
  shot 02_roles
  for _ in 1 2 3 4 5; do tap 584 2241 2; done
  tap 540 1561 3                      # "Done"
  # Keypad with a number typed (the key positions are those of the 4x3 pad).
  tap 882 1412 1; tap 198 1243 1; tap 540 1224 1; tap 882 1224 1; tap 540 1568 1; tap 198 1568 1; tap 540 1412 1; tap 882 1568 1; tap 540 1730 2
  shot 03_keypad
  tap 952 2244 3                      # Settings tab
  shot 04_settings
  tap 120 1136 9                      # Settings > Data (downloads the manifest)
  shot 05_data
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap 300 958 3                       # Settings > Spam filter
  shot 06_spam
  adb shell input keyevent KEYCODE_BACK; sleep 2
  # A simulated incoming call, then answered.
  adb shell am start -n "$PKG/com.qtekfun.ultimatephone.MainActivity" > /dev/null; sleep 3
  adb emu gsm call "$CALLER"; sleep 6
  shot 07_incoming_call
  adb shell input keyevent KEYCODE_CALL; sleep 4
  shot 08_in_call
  adb emu gsm cancel "$CALLER"; sleep 3
done
ls -R "$OUT"
