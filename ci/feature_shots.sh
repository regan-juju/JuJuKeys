#!/usr/bin/env bash
# Real-emulator check: blur theme, tools row, Text replacement (omw → On my way!, undo),
# round Paste button, Text Replacement screen, password entry + phone lock.
set -u
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
mkdir -p shots; LOG=shots/api${API}-steps.txt; : > $LOG
note() { echo "[$(date +%T)] $*" | tee -a $LOG; }
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" >> $LOG 2>&1
for i in 1 2 3 4 5; do
  sh ime enable $IME >> $LOG; sh ime set $IME >> $LOG
  sh settings get secure default_input_method | grep -q jujukeys && break; sleep 4
done
sh wm size 1080x2400 >> $LOG; sh wm density 420 >> $LOG; sleep 3
read W H <<<"$(sh wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')"
note "screen ${W}x${H}"
S=$(awk "BEGIN{print $W/640}")
# panel coordinates (640 wide reference picture, 1280 tall, bottom anchored) -> screen pixels
px() { awk "BEGIN{printf \"%d\", $1*$S}"; }
py() { awk "BEGIN{printf \"%d\", $H-(1280-$1)*$S}"; }
tapp() { sh input tap $(px $1) $(py $2); sleep ${3:-0.6}; }
shot() { adb exec-out screencap -p > shots/api${API}-$1.png; note "shot $1"; }
fieldtext() { sh uiautomator dump /sdcard/u.xml >/dev/null; sh cat /sdcard/u.xml | grep -oE '<node [^>]*class="android.widget.EditText"[^>]*>' | grep -oE ' text="[^"]*"' | head -1; }
declare -A X=( [q]=37 [w]=100 [e]=163 [r]=226 [t]=289 [y]=352 [u]=414 [i]=477 [o]=539 [p]=601 [a]=68 [s]=131 [d]=194 [f]=258 [g]=320 [h]=381 [j]=445 [k]=508 [l]=571 [z]=131 [x]=194 [c]=258 [v]=320 [b]=381 [n]=445 [m]=508 )
declare -A Y=( [q]=778 [w]=778 [e]=778 [r]=778 [t]=778 [y]=778 [u]=778 [i]=778 [o]=778 [p]=778 [a]=886 [s]=886 [d]=886 [f]=886 [g]=886 [h]=886 [j]=886 [k]=886 [l]=886 [z]=994 [x]=994 [c]=994 [v]=994 [b]=994 [n]=994 [m]=994 )
word() { for ch in $(echo "$1" | grep -o .); do tapp ${X[$ch]} ${Y[$ch]} 0.35; done; }
SPACE_X=320; SPACE_Y=1100; BACK_X=560; BACK_Y=670; LANG_X=60; LANG_Y=670

open_demo() {  # extras...
  sh input keyevent KEYCODE_HOME; sleep 1; sh ime set $IME >> $LOG
  sh am start --activity-clear-task -n $PKG/.BlurDemoActivity "$@" >> $LOG; sleep 6
  for t in 1 2 3 4; do
    sh input tap 400 205; sleep 3
    sh dumpsys input_method | grep -q 'mInputShown=true' && break
  done
  sleep 6
}

# ---- 1. blur theme open (Bangla default), then tools row
open_demo --es theme blur
shot 01-blur-open
sh input swipe $(px $LANG_X) $(py $LANG_Y) $(px $LANG_X) $(py $LANG_Y) 900; sleep 1.2
shot 02-tools-row

# ---- 2. English: omw + space → On my way!, then backspace brings omw back
sleep 6; tapp $LANG_X $LANG_Y 1
word omw; sleep 1
shot 03-omw-typed
note "before space: $(fieldtext)"
tapp $SPACE_X $SPACE_Y 1
note "after space (expect On my way! ): $(fieldtext)"
shot 04-omw-replaced
tapp $BACK_X $BACK_Y 1
note "after backspace (expect omw): $(fieldtext)"
shot 05-omw-undone
tapp $BACK_X $BACK_Y 0.6; tapp $BACK_X $BACK_Y 0.6; tapp $BACK_X $BACK_Y 0.6
# capital: type Omw? (shift) not covered; plain word that is not a shortcut must stay
word hello; tapp $SPACE_X $SPACE_Y 1
note "plain word (expect hello ): $(fieldtext)"

# ---- 3. copy → round Paste button
sh input keycombination CTRL_LEFT A; sleep 0.5; sh input keyevent 278; sleep 2   # select all, copy
shot 06-paste-round
tapp 155 670 1.5
note "after Paste tap (text doubled?): $(fieldtext)"
shot 07-pasted

# ---- 4. Text Replacement screen from the tools row
sh input swipe $(px $LANG_X) $(py $LANG_Y) $(px $LANG_X) $(py $LANG_Y) 900; sleep 1.2
tapp 411 670 3
shot 08-textreplace-list
sh input tap $(awk "BEGIN{printf \"%d\", $W*0.93}") $(awk "BEGIN{printf \"%d\", $H*0.075}"); sleep 2
shot 09-add-sheet

# ---- 5. password entry + phone lock
sh locksettings set-pin 1234 >> $LOG
open_demo --es theme blur --ez password true --ez seedpw true
tapp $LANG_X $LANG_Y 1
word pwx; sleep 1
tapp $SPACE_X $SPACE_Y 4
shot 10-lock-prompt
sh input text 1234; sleep 1; sh input keyevent 66; sleep 5
shot 11-after-unlock
note "password field after unlock (expect 10 dots): $(fieldtext)"
sh logcat -d -t 600 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -60 > shots/api${API}-log.txt
cat $LOG
