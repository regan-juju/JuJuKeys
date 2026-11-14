#!/usr/bin/env bash
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-steps.txt
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1; sleep 15
sh settings put secure show_ime_with_hard_keyboard 1 >> $LOG
for i in 1 2 3 4 5 6; do sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG; sleep 3; sh settings get secure default_input_method | grep -q jujukeys && break; done
focus_field() {
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] && sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
for round in warm real; do
  sh am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >> $LOG; sleep 8
  for t in 1 2 3 4; do focus_field; sleep 3; sh dumpsys input_method | grep -q 'mInputShown=true' && break; done
  sleep 1
  sh input swipe 20 330 20 330 900; sleep 2
  adb exec-out screencap -p > shots/api${API}-${round}-tools.png
  for x in 56 70; do
    sh input tap $x 330; sleep 2
    adb exec-out screencap -p > shots/api${API}-${round}-tap$x.png
    [ $x = 56 ] && { sh input swipe 20 330 20 330 900; sleep 2; }
  done
  sh input keyevent KEYCODE_HOME; sleep 1
done
sh logcat -d -t 600 | grep -iE "AndroidRuntime|FATAL" | tail -40 > shots/api${API}-log.txt
cat $LOG
