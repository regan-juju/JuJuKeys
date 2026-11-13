#!/usr/bin/env bash
# Photographs the app's own screens (settings, sticker maker) to check they stay clear of the system bars.
set -u
PKG=com.reganbarua.jujukeys
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-steps.txt
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1; sleep 10
sh dumpsys package $PKG | grep -E "targetSdk|versionName" >> $LOG
sh am start -n $PKG/.MainActivity >> $LOG; sleep 8
adb exec-out screencap -p > shots/api${API}-main-top.png
sh input swipe 160 500 160 120 400; sleep 1; sh input swipe 160 500 160 120 400; sleep 1
adb exec-out screencap -p > shots/api${API}-main-scrolled.png
sh input keyevent KEYCODE_HOME; sleep 1
sh am start -n $PKG/.sticker.StickerAddActivity >> $LOG; sleep 6
adb exec-out screencap -p > shots/api${API}-sticker-add.png
sh logcat -d -t 800 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -60 > shots/api${API}-log.txt
cat $LOG
