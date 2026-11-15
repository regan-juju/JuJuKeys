#!/usr/bin/env bash
# Emulator run of the launcher: set as home, take screenshots, try the main gestures.
set -u
PKG=com.reganbarua.jujulauncher
OUT=shots; mkdir -p $OUT
APK=$(ls launcher/app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >/dev/null
adb shell settings put global window_animation_scale 0 ; adb shell settings put global transition_animation_scale 0
adb shell cmd package set-home-activity $PKG/.MainActivity
sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 4
shot() { adb exec-out screencap -p > $OUT/$1.png; echo "shot $1"; }
dump() { adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml $OUT/u.xml >/dev/null 2>&1; }
# prints "x y" centre of first node whose text equals $1 (index $2, default 0)
find_text() { python3 - "$1" "${2:-0}" <<'PY'
import re,sys
x=open('shots/u.xml',encoding='utf8').read()
m=[re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n) for n in re.findall(r'<node [^>]*text="%s"[^>]*>'%re.escape(sys.argv[1]),x)]
m=[b for b in m if b]
i=int(sys.argv[2])
if i<len(m):
    a=[int(v) for v in m[i].groups()]; print((a[0]+a[2])//2,(a[1]+a[3])//2)
PY
}
adb shell wm size; adb shell wm density
shot 01_home
dump; grep -o 'text="[^"]*"' $OUT/u.xml | head -40 | tr '\n' ' '; echo

# swipe to App Library and back
adb shell input swipe 900 1000 150 1000 300; sleep 2; shot 02_library
adb shell input swipe 150 1000 900 1000 300; sleep 2

# search: swipe from an empty area, then from over an icon, then the Search pill
adb shell input swipe 540 1250 540 1900 400; sleep 2; shot 03_search_swipe_empty
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe 540 700 540 1500 400; sleep 2; shot 03b_search_swipe_icon
adb shell input keyevent KEYCODE_BACK; sleep 1
dump; Q=$(find_text "Search" 0); echo "pill: $Q"; set -- $Q
adb shell input tap $1 $2; sleep 2; shot 03c_search_pill
adb shell input text "chr"; sleep 2; shot 04_search_results
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
shot 04b_after_backs

# long-press first app label area
dump
P=$(find_text "Chrome"); [ -z "$P" ] && P=$(find_text "Settings")
echo "icon target: $P"; set -- $P
if [ -n "${1:-}" ]; then
  adb shell input swipe $1 $(( $2 - 70 )) $1 $(( $2 - 70 )) 900; sleep 2; shot 05_menu
  dump; E=$(find_text "Edit Home Screen"); echo "edit: $E"
  if [ -n "$E" ]; then set -- $E; adb shell input tap $1 $2; sleep 2; shot 06_edit; fi
fi

# drag one app onto another (folder)
dump
A=$(find_text "Chrome"); B=$(find_text "Settings"); [ -z "$B" ] && B=$(find_text "Maps")
echo "drag $A -> $B"
if [ -n "$A" ] && [ -n "$B" ]; then
  set -- $A; ax=$1; ay=$(( $2 - 70 )); set -- $B; bx=$1; by=$(( $2 - 70 ))
  adb shell input swipe $ax $ay $bx $by 1200; sleep 2; shot 07_after_drag
  dump; grep -o 'text="[^"]*"' $OUT/u.xml | head -30 | tr '\n' ' '; echo
  F=$(find_text "Folder")
  if [ -n "$F" ]; then set -- $F; adb shell input tap $1 $(( $2 - 70 )); sleep 2; shot 08_folder_open; adb shell input keyevent KEYCODE_BACK; sleep 1; fi
fi

# settings from edit mode
dump; S=$(find_text "Settings" 0); echo "settings pill: $S"
adb shell input tap 150 160 ; sleep 2; shot 09_settings
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1

# custom icon from a PNG (own-drawn red/blue test icon)
python3 - <<'PY'
import zlib,struct
w=h=256
rows=b''.join(b'\x00'+bytes([220,40,60,255] if (x//32+y//32)%2 else [30,90,230,255])*1 if False else b'' for y in range(0))
raw=b''
for y in range(h):
    raw+=b'\x00'
    for x in range(w):
        raw+=bytes([220,40,60,255]) if (x//32+y//32)%2 else bytes([30,90,230,255])
def ch(t,d): c=struct.pack('>I',len(d))+t+d; return c+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
open('shots/test_icon.png','wb').write(b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+ch(b'IDAT',zlib.compress(raw))+ch(b'IEND',b''))
PY
adb push $OUT/test_icon.png /data/local/tmp/t.png >/dev/null
adb shell run-as $PKG mkdir -p files/icons
adb shell run-as $PKG cp /data/local/tmp/t.png files/icons/com.android.settings.png
adb shell am force-stop $PKG; adb shell input keyevent KEYCODE_HOME; sleep 4
shot 10_custom_icon
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 2
echo done
