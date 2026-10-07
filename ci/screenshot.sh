#!/usr/bin/env bash
# Opens the keyboard in a real text field and saves screenshots (letters, Bangla, emoji/stickers).
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
mkdir -p shots
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >/dev/null && echo "installed $APK"
sh ime enable $IME; sh ime set $IME
SIZE=$(sh wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
sh am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >/dev/null; sleep 10
for tryy in 25 30 20 35 40; do
  sh dumpsys input_method | grep -q 'mInputShown=true' && break
  sh input tap $(( W / 2 )) $(( H * tryy / 100 )); sleep 4
done
sleep 12
adb exec-out screencap -p > shots/api${API}-1-letters.png
sh dumpsys input_method | grep -E 'mInputShown|mCurMethodId' > shots/api${API}-ime.txt
# rotate the device once and back: keyboards that measured too early show it here
sh settings put system accelerometer_rotation 0; sh settings put system user_rotation 1; sleep 6
adb exec-out screencap -p > shots/api${API}-2-landscape.png
sh settings put system user_rotation 0; sleep 6
adb exec-out screencap -p > shots/api${API}-3-back.png
sh logcat -d -t 400 | grep -iE "jujukeys|AndroidRuntime" | tail -80 > shots/api${API}-log.txt
ls -la shots
