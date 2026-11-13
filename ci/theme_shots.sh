#!/usr/bin/env bash
# Photographs the keyboard in several themes (pattern glass, glass, black, white) with the ক/A key.
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-steps.txt
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1; sleep 15
sh settings put secure show_ime_with_hard_keyboard 1 >> $LOG
for i in 1 2 3 4 5; do
  sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4
done
focus_field() {
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] && sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
set_theme() {
  printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<string name=\"theme\">%s</string>\n<boolean name=\"auto_hide\" value=\"false\" />\n</map>\n" "$1" > /tmp/p.xml
  adb push /tmp/p.xml /data/local/tmp/p.xml >/dev/null
  sh am force-stop $PKG
  sh "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/p.xml shared_prefs/jujukeys_settings.xml'" >> $LOG
  sh "run-as $PKG cat shared_prefs/jujukeys_settings.xml" >> $LOG
  for i in 1 2 3 4 5 6; do sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG; sleep 3; sh settings get secure default_input_method | grep -q jujukeys && break; done
  echo "default IME: $(sh settings get secure default_input_method)" >> $LOG
}
for T in warmup pattern patternB; do
  TH=$T; [ "$T" = warmup ] && TH=current; set_theme $TH
  sh am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >> $LOG; sleep 8
  for t in 1 2 3 4; do
    focus_field; sleep 3
    sh dumpsys input_method | grep -q 'mInputShown=true' && break
  done
  sleep 2
  adb exec-out screencap -p > shots/api${API}-theme-$T.png
  sh dumpsys input_method | grep -E 'mInputShown' >> $LOG
  sh input keyevent KEYCODE_BACK; sh input keyevent KEYCODE_HOME; sleep 1
done
sh logcat -d -t 600 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -60 > shots/api${API}-log.txt
cat $LOG
