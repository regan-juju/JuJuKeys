#!/usr/bin/env bash
# Screenshots of the whole app on a phone-sized emulator (1080x2400) for the Facebook post.
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/steps.txt; : > $LOG
n=0; shot() { n=$((n+1)); adb exec-out screencap -p > "shots/$(printf %02d $n)-$1.png"; echo "shot $n $1" >> $LOG; }
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1
sh settings put secure show_ime_with_hard_keyboard 1 >> $LOG
sh settings put system screen_off_timeout 1800000
echo "wm: $(sh wm size) $(sh wm density)" >> $LOG

prefs() {   # $1 theme  $2 bangla(true/false)
  cat > /tmp/p.xml <<X
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="theme">$1</string>
    <boolean name="last_bangla" value="$2" />
    <boolean name="default_bangla" value="$2" />
    <boolean name="auto_hide" value="false" />
    <boolean name="long_press_symbols" value="false" />
</map>
X
  adb push /tmp/p.xml /data/local/tmp/p.xml >/dev/null; sh chmod 644 /data/local/tmp/p.xml >/dev/null
  sh am force-stop $PKG
  sh run-as $PKG sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/p.xml shared_prefs/jujukeys_settings.xml'" >> $LOG
  [ "${KB_ON:-0}" = 1 ] && { sh ime enable $IME >/dev/null; sh ime set $IME >/dev/null; }
}
tap_text() {   # tap the first node whose text contains $1
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | tr '>' '\n' | grep -F "text=\"$1" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] || { echo "no node: $*" >> $LOG; return 1; }
  sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
tap_class() {
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | tr '>' '\n' | grep -F "class=\"$1\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] || { echo "no class node" >> $LOG; return 1; }
  sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
tap_switch() {   # the on/off switch on the row whose title is $1
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] || { echo "no switch row" >> $LOG; return 1; }
  sh input tap 975 $(( ($2 + $4) / 2 )) >/dev/null
}
home() { sh am start -W -n $PKG/.MainActivity >/dev/null; sleep 4; }


# Chrome: skip its first-run screens
sh "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line"
sh am set-debug-app --persistent com.android.chrome >/dev/null
# ---- how to install & turn on (fresh phone, keyboard not on yet)
sh am start -a android.intent.action.VIEW -d https://github.com/regan-juju/JuJuKeys/releases/latest >/dev/null; sleep 15
tap_text "No thanks"; sleep 3; shot setup-a-github-page
sh input swipe 540 1900 540 700 400; sleep 3; shot setup-b-github-assets
sh am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES >/dev/null; sleep 4; shot setup-c-unknown-apps
sh input keyevent KEYCODE_HOME; sleep 1
prefs current true; home; shot setup-1-app-turn-on
tap_text "Open settings"; sleep 4; shot setup-2-keyboard-list
tap_switch "JuJuKeys"; sleep 5; shot setup-3-warning
tap_text "OK"; sleep 4; shot setup-4-switched-on
home; shot setup-5-app-choose
tap_text "Choose keyboard"; sleep 5; shot setup-6-picker
tap_text "JuJuKeys"; sleep 3
for i in 1 2 3 4 5; do sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4; done
KB_ON=1
home; shot setup-7-app-ready

# ---- settings app
prefs current true; home
shot settings-home
sh input swipe 540 1800 540 700 400; sleep 2; shot settings-home2
for item in "Languages" "Preferences" "Themes" "Text correction" "Clipboard" "Translate" "Dictionary" "Emoji & stickers" "Privacy" "About"; do
  home; tap_text "$item" || { sh input swipe 540 1800 540 700 400; sleep 2; tap_text "$item"; }
  sleep 3; shot "page-$(echo $item | tr ' &' '__')"
  sh input keyevent KEYCODE_BACK; sleep 1
done

# ---- keyboard in every theme (Bangla), plus English
open_kb() { home; tap_text "Try it" || { sh input swipe 540 1800 540 700 400; sleep 2; tap_text "Try it"; }; sleep 3
  tap_class android.widget.EditText; sleep 4
  sh dumpsys input_method | grep -m1 -o 'mInputShown=[a-z]*' >> $LOG; }
for t in current deep midnight emerald aurora amoled puredark rainbow white; do
  prefs $t true; open_kb; shot "kb-$t"
done
prefs current false; open_kb; shot kb-english

# ---- light round the keyboard when it opens: hide the keyboard, open it again, photograph in a burst
sh settings put global animator_duration_scale 1; sh settings put global window_animation_scale 1; sh settings put global transition_animation_scale 1
prefs current true; open_kb; shot open-before
for t in 1 2; do
  sh input keyevent KEYCODE_BACK; sleep 3
  tap_class android.widget.EditText
  for b in 1 2 3 4; do adb exec-out screencap -p > "shots/open-glow-$t-$b.png"; done
  echo "burst $t done" >> $LOG; sleep 2
done

# ---- pressed keys (the press light): hold a key while photographing
hold() { adb shell "input swipe $1 $2 $1 $2 2500" & sleep 1.0; shot "$3"; wait; sleep 1; }
prefs current false; open_kb
hold 540 1756 press-letters-G
hold 967 2035 press-letters-return
hold 977 1472 press-top-backspace
sh input tap 77 2035; sleep 2; shot symbols-page
hold 486 1612 press-symbols-5
hold 990 1896 press-symbols-backspace
hold 990 2035 press-symbols-return
sh input tap 298 2037; sleep 2; shot numpad-page
hold 540 1756 press-numpad-5
hold 965 1896 press-numpad-backspace
hold 990 2035 press-numpad-return
hold 80 2035 press-numpad-ABC
sleep 2; sh input tap 77 2035; sleep 2
hold 80 2035 press-symbols-ABC

# ---- typing in Bangla, tools, stickers (1080x2400, keyboard as photographed)
prefs current true; open_kb
R1=1612; R2=1756; R3=1896; R4=2035
k() { case $1 in
  q) x=67;y=$R1;; w) x=166;y=$R1;; e) x=270;y=$R1;; r) x=378;y=$R1;; t) x=486;y=$R1;; y) x=590;y=$R1;; u) x=697;y=$R1;; i) x=805;y=$R1;; o) x=913;y=$R1;; p) x=1017;y=$R1;;
  a) x=108;y=$R2;; s) x=216;y=$R2;; d) x=324;y=$R2;; f) x=432;y=$R2;; g) x=540;y=$R2;; h) x=648;y=$R2;; j) x=756;y=$R2;; k) x=860;y=$R2;; l) x=967;y=$R2;;
  z) x=216;y=$R3;; x) x=324;y=$R3;; c) x=432;y=$R3;; v) x=540;y=$R3;; b) x=648;y=$R3;; n) x=751;y=$R3;; m) x=860;y=$R3;;
  " ") x=540;y=$R4;; esac; sh input tap $x $y >/dev/null; }
typ() { local w="$1" i; for ((i=0;i<${#w};i++)); do k "${w:i:1}"; done; }
typ "ami tomake bhalobashi"; sleep 2; shot type-bangla
k " "; typ "amar sonar bangla"; sleep 2; shot type-bangla2
sh input swipe 77 1472 77 1472 1200; sleep 2; shot tools-hold
sh input tap 207 2035; sleep 4; shot stickers-panel
sh input keyevent KEYCODE_BACK; sleep 2

sh dumpsys window windows | grep -A30 -i "InputMethod" | grep -m3 -iE "frame|mFrame|touchable" >> $LOG
sh logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|jujukeys" | tail -40 > shots/log.txt
cat $LOG
