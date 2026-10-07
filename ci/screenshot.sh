#!/usr/bin/env bash
# Opens the keyboard in a real text field and saves screenshots.
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-steps.txt
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1
# the emulator has a "hardware keyboard", which hides on-screen keyboards — show them anyway
sh settings put secure show_ime_with_hard_keyboard 1 >> $LOG
for i in 1 2 3 4 5; do
  sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4
done
echo "default IME: $(sh settings get secure default_input_method)" >> $LOG
focus_field() {
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  echo "edit bounds: $b" >> $LOG
  set -- $b; [ $# -eq 4 ] && sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
sh am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >> $LOG; sleep 10
for t in 1 2 3 4; do
  focus_field; sleep 3
  sh dumpsys input_method | grep -q 'mInputShown=true' && break
done
# the keyboard hides itself after a few idle seconds (auto-hide) — photograph quickly
sleep 2
adb exec-out screencap -p > shots/api${API}-1-letters.png
sh dumpsys input_method | grep -E 'mInputShown|mCurMethodId' >> $LOG
sleep 2; focus_field; sleep 3
adb exec-out screencap -p > shots/api${API}-2-again.png
sh settings put system accelerometer_rotation 0; sh settings put system user_rotation 1; sleep 4; focus_field; sleep 3
adb exec-out screencap -p > shots/api${API}-3-landscape.png
sh settings put system user_rotation 0; sleep 4; focus_field; sleep 3
adb exec-out screencap -p > shots/api${API}-4-back.png
sh logcat -d -t 600 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -80 > shots/api${API}-log.txt
cat $LOG
