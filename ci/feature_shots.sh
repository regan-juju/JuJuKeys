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

kb() { sh dumpsys input_method | grep -q 'mInputShown=true' || { note "keyboard hidden -> reopen"; sh input tap 400 205; sleep 3; }; }
focused() { sh dumpsys window | grep -E "mCurrentFocus" | grep -q BlurDemo; }
open_demo() {  # extras...
  for n in 1 2 3; do
    sh input keyevent KEYCODE_HOME; sleep 1; sh ime set $IME >> $LOG
    sh am start --activity-clear-task -n $PKG/.BlurDemoActivity "$@" >> $LOG
    for w in 1 2 3 4 5 6 7 8; do sleep 2; focused && break; done
    focused && break; note "demo not focused, retry $n"
  done
  sleep 3
  for t in 1 2 3 4; do
    sh input tap 400 205; sleep 3
    sh dumpsys input_method | grep -q 'mInputShown=true' && break
  done
  sleep 5
}

# ---- 1. blur theme open (Bangla default), then tools row
open_demo --es theme blur
shot 01-blur-open
open_demo --es theme current --ez plain true
shot 01q-glass-dark
open_demo --es theme current --ez plain true --ez white true
shot 01r-glass-white-app
open_demo --es theme darkglass --ez plain true
shot 01s-darkglass
open_demo --es theme noirglass --ez plain true
shot 01t-noirglass
open_demo --es theme blur --ez plain true
shot 01b-blur-dark-bg
open_demo --es theme blur --ez plain true --ez white true --ez noglow true
shot 01c-blur-white-app-noglow
open_demo --es theme blur --ez noglow true
shot 01d-blur-colour-app-noglow
open_demo --es theme blur --ez noglow false
kb; longpress_lang
shot 02-tools-row

# ---- 2. English: omw + space → On my way!, then backspace brings omw back
sleep 4; kb; tapp $LANG_X $LANG_Y 1.5; kb; shot 02b-after-lang
word omw; sleep 1
shot 03-omw-typed
note "before space: $(fieldtext)"
kb; tapp $SPACE_X $SPACE_Y 1
note "after space (expect On my way! ): $(fieldtext)"
shot 04-omw-replaced
tapp $BACK_X $BACK_Y 1
note "after backspace (expect omw): $(fieldtext)"
shot 05-omw-undone
tapp $BACK_X $BACK_Y 0.6; tapp $BACK_X $BACK_Y 0.6; tapp $BACK_X $BACK_Y 0.6
# capital: type Omw? (shift) not covered; plain word that is not a shortcut must stay
word hello; tapp $SPACE_X $SPACE_Y 1
note "plain word (expect hello ): $(fieldtext)"

# ---- 2b. Smart touch: "th" then a touch at the W/E edge (W side) must type e → "the"; mid-key W must stay w
kb; sh input keycombination CTRL_LEFT A; sleep 0.4; sh input keyevent 67; sleep 0.5
word th; tapp 210 1260 0.8
note "smart touch edge (expect the): $(fieldtext)"
shot 05b-smart-edge
tapp 978 1120 0.5
tapp ${X[w]} 1260 0.8
note "smart touch middle of W (expect thw): $(fieldtext)"
kb; sh input keycombination CTRL_LEFT A; sleep 0.4; sh input keyevent 67; sleep 0.5
word hello; tapp $SPACE_X $SPACE_Y 1

# ---- 3. copy → round Paste button
kb; sh input keycombination CTRL_LEFT A; sleep 0.5; sh input keyevent 278; sleep 1; sh input keyevent KEYCODE_MOVE_END; sleep 1.5   # select all, copy
shot 06-paste-round
tapp 195 1120 1.5
note "after Paste tap (text doubled?): $(fieldtext)"
shot 07-pasted

# ---- 4. Text Replacement screen from the tools row
kb; longpress_lang
tapp 518 1120 3
shot 08-textreplace-list
sh input tap 1000 118; sleep 2
shot 09-add-sheet

# ---- 4a. every press lights a new colour (same key g, tapped and captured in one shell call)
open_demo --es theme noirglass --ez plain true
kb
for n in 1 2 3 4; do sh "input tap 540 1402; screencap -p /sdcard/p$n.png"; adb pull /sdcard/p$n.png shots/api${API}-08c-press$n.png >/dev/null 2>&1; sleep 1; done

# ---- 4b. clipboard page (real panel)
open_demo --es theme noirglass --ez plain true
kb; longpress_lang; tapp 220 1120 2.5
shot 08b-clipboard

# ---- 4c. tools page (Gboard style), text editing, themes, one-handed
open_demo --es theme noirglass --ez plain true
kb; longpress_lang
shot 08f-tools-page
sh input swipe 800 1400 200 1400 200; sleep 1
shot 08g-tools-page2
sh input swipe 200 1400 800 1400 200; sleep 1
tapp 270 1710 1.5
shot 08h-edit-page
tapp 80 1120 1.0
longpress_lang; tapp 810 1710 1.5
shot 08i-themes-page

# ---- 4d. every theme: keypad + bottom strip (globe / mic colours)
for T in current darkglass noirglass; do
  open_demo --es theme $T --ez plain true
  kb; shot 09-theme-$T
done

# ---- 4e. backspace light walks through colours by itself
open_demo --es theme current --ez plain true
sh settings put global animator_duration_scale 1.0; sleep 1
kb; sh input tap 400 205; sleep 1.2
sh "for n in 1 2 3 4; do screencap -p /sdcard/b\$n.png; sleep 0.3; done"
for n in 1 2 3 4; do adb pull /sdcard/b$n.png shots/api${API}-13-back$n.png >/dev/null 2>&1; done

# ---- 5. password entry + phone lock
sh locksettings set-pin 1234 >> $LOG
open_demo --es theme blur --ez password true --ez seedpw true
tapp $LANG_X $LANG_Y 1
word pwx; sleep 1
tapp $SPACE_X $SPACE_Y 4
shot 10-lock-prompt
sh input text 1234; sleep 1; sh input keyevent 66; sleep 5
shot 11-after-unlock
note "password field right after unlock: $(fieldtext)"
sh input tap 400 205; sleep 4
shot 12-reopened
note "password field after keyboard reopened (expect 10 dots): $(fieldtext)"
sh logcat -d -t 600 | grep -iE "jujukeys|AndroidRuntime|FATAL" | tail -60 > shots/api${API}-log.txt
# ---- last: one-handed mode (it is saved in prefs, so nothing may run after it)
open_demo --es theme noirglass --ez plain true
kb; longpress_lang; sh input swipe 800 1400 200 1400 200; sleep 1; tapp 270 1500 1.5
shot 08j-one-handed
cat $LOG
