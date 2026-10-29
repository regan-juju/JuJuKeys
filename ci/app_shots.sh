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
</map>
X
  adb push /tmp/p.xml /data/local/tmp/p.xml >/dev/null; sh chmod 644 /data/local/tmp/p.xml >/dev/null
  sh am force-stop $PKG
  sh run-as $PKG sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/p.xml shared_prefs/jujukeys_settings.xml'" >> $LOG
}
tap_text() {   # tap the first node whose text contains $1
  sh uiautomator dump /sdcard/u.xml >/dev/null
  local b; b=$(sh cat /sdcard/u.xml | tr '>' '\n' | grep -F "text=\"$1" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ $# -eq 4 ] || { echo "no node: $*" >> $LOG; return 1; }
  sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
home() { sh am start -W -n $PKG/.MainActivity >/dev/null; sleep 4; }


# ---- how to install & turn on (fresh phone, keyboard not on yet)
sh am start -a android.intent.action.VIEW -d https://github.com/regan-juju/JuJuKeys/releases/latest >/dev/null; sleep 15
shot setup-a-github-page
sh input swipe 540 1900 540 700 400; sleep 3; shot setup-b-github-assets
sh am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES >/dev/null; sleep 4; shot setup-c-unknown-apps
sh input keyevent KEYCODE_HOME; sleep 1
prefs current true; home; shot setup-1-app-turn-on
tap_text "Open settings"; sleep 4; shot setup-2-keyboard-list
tap_text "JuJuKeys"; sleep 3; shot setup-3-warning
tap_text "OK"; sleep 3; shot setup-4-switched-on
home; shot setup-5-app-choose
tap_text "Choose keyboard"; sleep 3; shot setup-6-picker
tap_text "JuJuKeys"; sleep 3
for i in 1 2 3 4 5; do sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4; done
home; shot setup-7-app-ready

# ---- settings app
prefs current true; home
sh input keyevent KEYCODE_BACK; home   # dismiss anything
shot settings-home
sh input swipe 540 1800 540 700 400; sleep 2; shot settings-home2
for item in "Languages" "Preferences" "Themes" "Text correction" "Clipboard" "Translate" "Dictionary" "Emoji & stickers" "Privacy" "About"; do
  home; tap_text "$item" || { sh input swipe 540 1800 540 700 400; sleep 2; tap_text "$item"; }
  sleep 3; shot "page-$(echo $item | tr ' &' '__')"
  sh input keyevent KEYCODE_BACK; sleep 1
done

# ---- keyboard in every theme (Bangla), plus English
open_kb() { home; tap_text "Try it" || { sh input swipe 540 1800 540 700 400; sleep 2; tap_text "Try it"; }; sleep 3
  tap_text "Type here" ; sleep 4
  sh dumpsys input_method | grep -m1 -o 'mInputShown=[a-z]*' >> $LOG; }
for t in current deep midnight emerald aurora amoled puredark rainbow white; do
  prefs $t true; open_kb; shot "kb-$t"
done
prefs current false; open_kb; shot kb-english
sh dumpsys window windows | grep -A30 -i "InputMethod" | grep -m3 -iE "frame|mFrame|touchable" >> $LOG
sh logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|jujukeys" | tail -40 > shots/log.txt
cat $LOG
