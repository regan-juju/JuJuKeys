#!/usr/bin/env bash
set -u
PKG=com.reganbarua.jujulauncher
OUT=shots; mkdir -p $OUT
APK=$(ls launcher/app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >/dev/null
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
adb shell cmd package set-home-activity $PKG/.MainActivity
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
adb shell dumpsys battery set level 64
adb shell dumpsys battery set status 3
sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 4
shot() { adb exec-out screencap -p > $OUT/$1.png; echo "shot $1"; }
dump() { adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml $OUT/u.xml >/dev/null 2>&1; }
tapt() { python3 - "$1" <<'PY' > $OUT/xy.txt
import re,sys
x=open('shots/u.xml',encoding='utf8').read()
for n in re.findall(r'<node [^>]*text="%s"[^>]*>'%re.escape(sys.argv[1]),x):
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if b:
        a=[int(v) for v in b.groups()]; print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
cat $OUT/xy.txt; }
dump
if grep -q 'Select a Home app' $OUT/u.xml; then
  set -- $(tapt 'JuJu Launcher'); adb shell input tap $1 $2; sleep 1
  dump; set -- $(tapt 'Always'); adb shell input tap $1 $2; sleep 3
fi
shot 01_home_bar
adb shell dumpsys battery set level 12; sleep 5; shot 02_low_battery
adb shell dumpsys battery set status 2; adb shell dumpsys battery set ac 1; sleep 5; shot 03_charging
adb shell dumpsys battery reset
# settings overlay: long press empty area -> edit -> Settings
adb shell input swipe 540 1000 540 1000 900; sleep 2
dump; set -- $(tapt 'Edit Home Screen'); [ -n "${1:-}" ] && adb shell input tap $1 $2; sleep 2
dump; set -- $(tapt 'Settings'); [ -n "${1:-}" ] && adb shell input tap $1 $2; sleep 2
adb shell input swipe 540 1700 540 700 300; sleep 1; shot 04_settings
