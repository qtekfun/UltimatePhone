#!/usr/bin/env bash
# Takes the store screenshots on a running emulator with the debug build installed, in English and Spanish.
# Usage: scripts/screenshots.sh <output dir>. Run by .github/workflows/screenshots.yml. Elements are found by their text
# (scripts/ui.sh), so layout changes do not break it; a step that cannot find its element is reported and skipped.
set -uo pipefail
. "$(dirname "$0")/ui.sh"

PKG=com.qtekfun.ultimatephone.debug
OUT=${1:-screenshots}
APK=app/build/outputs/apk/debug/app-debug.apk
CALLER=5551234567 # a fictional number

shot() { adb exec-out screencap -p > "$OUT/$LOCALE/$1.png"; echo "shot $LOCALE/$1"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }

adb install -r "$APK"
adb shell cmd role add-role-holder android.app.role.DIALER "$PKG" || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING "$PKG" || true

for LOCALE in en-US es-ES; do
  mkdir -p "$OUT/$LOCALE"
  adb shell am force-stop "$PKG"
  adb shell pm clear "$PKG"
  adb shell cmd locale set-app-locales "$PKG" --locales "$LOCALE" || true
  for p in CALL_PHONE READ_PHONE_STATE READ_PHONE_NUMBERS POST_NOTIFICATIONS ANSWER_PHONE_CALLS READ_CALL_LOG WRITE_CALL_LOG READ_CONTACTS WRITE_CONTACTS; do
    adb shell pm grant "$PKG" "android.permission.$p" || true
  done
  # A fictional contact for the list and the call lookup (the emulator starts empty).
  adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s: --bind account_name:s: || true
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"Ana García" || true
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+34612345678 || true

  adb shell am start -n "$PKG/com.qtekfun.ultimatephone.MainActivity" > /dev/null
  sleep 7
  shot 01_welcome
  tap_text '^(Get started|Empezar)$' 3
  shot 02_roles
  for _ in 1 2 3 4 5; do tap_text '^(Skip for now|Omitir por ahora)$' 2 || break; done
  tap_text '^(Finish|Terminar)$' 3
  for d in 6 1 2 3 8 7 5 9 0; do tap_text "^$d\$" 1; done
  sleep 1
  shot 03_keypad
  tap_text '^(Recents|Recientes)$' 3
  shot 04_recents
  tap_text '^(Contacts|Contactos)$' 4
  shot 05_contacts
  tap_text '^(Settings|Ajustes)$' 3
  shot 06_settings
  tap_text '^(Spam filter|Filtro de spam)' 3 && { shot 07_spam; back; }
  tap_text '^(Data|Datos)' 9 && { shot 08_data; back; }
  # A simulated incoming call with the screen off, so the full-screen call UI appears; then answered.
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell input keyevent KEYCODE_SLEEP; sleep 2
  adb emu gsm call "$CALLER"; sleep 7
  shot 09_incoming_call
  adb shell input keyevent KEYCODE_CALL; sleep 4
  shot 10_in_call
  adb emu gsm cancel "$CALLER"; sleep 3
  adb shell input keyevent KEYCODE_WAKEUP; sleep 1
done
ls -R "$OUT"
