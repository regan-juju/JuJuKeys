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
tapp() { sh input tap $1 $2; sleep ${3:-0.6}; }
shot() { adb exec-out screencap -p > shots/api${API}-$1.png; note "shot $1"; }
fieldtext() { sh uiautomator dump /sdcard/u.xml >/dev/null; sh cat /sdcard/u.xml | grep -oE '<node [^>]*class="android.widget.EditText"[^>]*>' | grep -oE ' text="[^"]*"' | head -1; }
declare -A X=( [q]=60 [w]=168 [e]=274 [r]=380 [t]=486 [y]=592 [u]=698 [i]=804 [o]=912 [p]=1018 [a]=114 [s]=220 [d]=326 [f]=432 [g]=540 [h]=646 [j]=752 [k]=860 [l]=966 [z]=220 [x]=326 [c]=432 [v]=540 [b]=646 [n]=752 [m]=860 )
declare -A Y=( [q]=1260 [w]=1260 [e]=1260 [r]=1260 [t]=1260 [y]=1260 [u]=1260 [i]=1260 [o]=1260 [p]=1260 [a]=1402 [s]=1402 [d]=1402 [f]=1402 [g]=1402 [h]=1402 [j]=1402 [k]=1402 [l]=1402 [z]=1542 [x]=1542 [c]=1542 [v]=1542 [b]=1542 [n]=1542 [m]=1542 )
word() { for ch in $(echo "$1" | grep -o .); do tapp ${X[$ch]} ${Y[$ch]} 0.35; done; }
SPACE_X=540; SPACE_Y=1684; BACK_X=978; BACK_Y=1120; LANG_X=80; LANG_Y=1120
longpress_lang() { sh input swipe $LANG_X $LANG_Y $LANG_X $LANG_Y 900; sleep 1.2; }

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
longpress_lang
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
tapp 195 1120 1.5
note "after Paste tap (text doubled?): $(fieldtext)"
shot 07-pasted

# ---- 4. Text Replacement screen from the tools row
longpress_lang
tapp 518 1120 3
shot 08-textreplace-list
sh input tap 1000 118; sleep 2
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
