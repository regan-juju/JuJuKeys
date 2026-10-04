#!/usr/bin/env bash
# Heat check: CPU used by the keyboard process — v1.0.15 vs the new build, same emulator,
# same steps: start, 60 s idle with the keyboard open, 150 key taps.
set -u
[ "${API:-0}" = "33" ] || exit 0
PKG=com.reganbarua.jujukeys
IME=$PKG/.JuJuKeysInputMethodService
sh() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
ticks() { local p; p=$(sh pidof $PKG); [ -z "$p" ] && { echo 0; return; }
          sh cat /proc/$p/stat | awk '{print $14+$15}'; }   # utime+stime, 1 tick = 10 ms
SIZE=$(sh wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
DEN=$(sh wm density | grep -o '[0-9]*' | tail -1)
dp() { echo $(( $1 * DEN / 160 )); }
ROW2_Y=$(( H - $(dp 48) - $(dp 62) - $(dp 52) - $(dp 52) - $(dp 26) ))   # nav, strip, row4, row3, half row2

measure() {   # $1 = label
  sh am force-stop $PKG; sleep 1
  sh ime enable $IME >/dev/null; sh ime set $IME >/dev/null
  sh am start -a android.settings.APP_SEARCH_SETTINGS >/dev/null; sleep 20     # keyboard opens, dictionaries load
  local start; start=$(ticks)
  sleep 60; local idle; idle=$(( $(ticks) - start ))
  local t0; t0=$(ticks)
  for i in $(seq 1 150); do
    x=$(( (i * 7 % 9) * W / 10 + W / 20 )); sh input tap $x $ROW2_Y
  done
  local typing; typing=$(( $(ticks) - t0 ))
  local mem; mem=$(sh dumpsys meminfo $PKG | grep -E 'TOTAL PSS' | awk '{print $3}')
  local shown; shown=$(sh dumpsys input_method | grep -m1 -o 'mInputShown=[a-z]*')
  echo "$1: start=${start}0ms idle60s=${idle}0ms typing150=${typing}0ms (~$(( typing * 10 / 150 ))ms/key) PSS=${mem}KB $shown" | tee -a perf.txt
}

adb uninstall $PKG >/dev/null 2>&1
adb install signed/t-old-v15.apk >/dev/null 2>&1 && measure "v1.0.15"
adb install -r signed/t-new-rotated.apk >/dev/null 2>&1 && measure "new"
echo "::notice title=heat check (EMULATOR CPU time)::$(tr '\n' '‖' < perf.txt)"
