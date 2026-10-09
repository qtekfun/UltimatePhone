#!/usr/bin/env bash
# A realistic run on a running emulator (1080x2400) with the debug build: default phone and screening roles, every runtime
# permission, a contact, Spanish, the onboarding, every tab, the Settings sub-screens, and a simulated incoming call that is
# answered and ended. It fails if the app crashes at any step. Run by .github/workflows/instrumented.yml.
set -uo pipefail

PKG=com.qtekfun.ultimatephone.debug
APK=app/build/outputs/apk/debug/app-debug.apk
CALLER=5551234567 # fictional
fail=0

tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }

# Fails the run (but keeps going, to see every problem) if the app crashed or died.
check() {
  if adb logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
    echo "::error::crash after: $1"
    adb logcat -d -b crash | tail -80
    adb logcat -c
    fail=1
  fi
  if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
    echo "::error::the app is not running after: $1"
    fail=1
  fi
}

adb install -r "$APK" || exit 1
adb shell cmd role add-role-holder android.app.role.DIALER "$PKG" || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING "$PKG" || true
adb shell am force-stop "$PKG"
adb shell pm clear "$PKG"
adb shell cmd locale set-app-locales "$PKG" --locales es-ES || true
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE READ_PHONE_NUMBERS ANSWER_PHONE_CALLS RECORD_AUDIO POST_NOTIFICATIONS; do
  adb shell pm grant "$PKG" "android.permission.$p" || true
done
# One contact, so the contact list, the dialer search and the caller lookup have something to read.
adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s: --bind account_name:s: || true
adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"Ana Garcia" || true
adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+34612345678 || true
adb logcat -c

adb shell am start -n "$PKG/com.qtekfun.ultimatephone.MainActivity" > /dev/null
sleep 7; check "start"

tap 902 2241 3; check "onboarding step 1"
for _ in 1 2 3 4 5; do tap 584 2241 2; done
check "onboarding steps"
tap 540 1561 3; check "onboarding done"

# Keypad: type, delete, type; then each tab.
tap 882 1412 1; tap 198 1243 1; tap 540 1224 2; check "keypad typing"
tap 402 2244 3; check "recents tab"
tap 677 2244 4; check "contacts tab"
tap 952 2244 3; check "settings tab"

# Settings sub-screens: rows from the top (the first card is the phone-app status).
for y in 770 950 1120 1300 1480 1660; do
  tap 150 "$y" 4; check "settings row at y=$y"
  back
done

# An incoming call with the screen off, answered, then ended.
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb emu gsm call "$CALLER"; sleep 7; check "incoming call"
adb shell input keyevent KEYCODE_CALL; sleep 4; check "answered call"
adb emu gsm cancel "$CALLER"; sleep 4; check "call ended"
adb shell input keyevent KEYCODE_WAKEUP; sleep 2

# Kill and start again: the previous run must not have left a crash report.
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/com.qtekfun.ultimatephone.MainActivity" > /dev/null
sleep 6; check "second start"

if [ "$fail" -ne 0 ]; then echo "scenario FAILED"; exit 1; fi
echo "scenario passed"
