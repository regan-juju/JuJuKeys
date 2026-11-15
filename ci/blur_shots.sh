#!/usr/bin/env bash
# Blur glass theme: open the colourful debug screen with the keyboard and take screenshots.
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-blur-steps.txt
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1
sh settings put secure show_ime_with_hard_keyboard 1 >> $LOG
for i in 1 2 3 4 5; do
  sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4
done
shotfor() {  # $1 = theme id, $2 = file
  sh input keyevent KEYCODE_HOME; sleep 1; sh ime set $IME >> $LOG
  sh am start --activity-clear-task -n $PKG/.BlurDemoActivity --es theme "$1" >> $LOG; sleep 7
  for t in 1 2 3 4; do
    sh input tap 300 140; sleep 3
    sh dumpsys input_method | grep -q 'mInputShown=true' && break
  done
  sleep 2
  adb exec-out screencap -p > shots/api${API}-$2.png
  sh dumpsys input_method | grep -E "mCurMethodId|mInputShown" >> $LOG
}
shotfor blur 1-blur
shotfor current 2-current
sh logcat -d -t 400 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -50 > shots/api${API}-blur-log.txt
cat $LOG
