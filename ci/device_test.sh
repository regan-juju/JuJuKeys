#!/usr/bin/env bash
# Runs on an Android emulator (API $API). Checks: update over v1.0.15 keeps data, old plain
# data is moved into encrypted form, which key the phone trusts, what happens to an update
# signed only with the OLD public key, and what Google backup takes.
set -u
PKG=com.reganbarua.jujukeys
D=signed
FAILED=0
ok()   { echo "::notice title=API $API ✓::$*"; echo "PASS $*" >> "results-$API.txt"; }
bad()  { echo "::error title=API $API ✗::$*";  echo "FAIL $*" >> "results-$API.txt"; FAILED=1; }
info() { echo "::notice title=API $API::$*";   echo "INFO $*" >> "results-$API.txt"; }
sh()   { adb shell "$@" 2>&1 | tr -d '\r'; }

adb root >/dev/null 2>&1; sleep 4; adb wait-for-device

# 1) old version, as a user has it now
r=$(adb install "$D/t-old-v15.apk" 2>&1); echo "$r" | grep -q Success && ok "v1.0.15 (old key) installed" || bad "v1.0.15 install: $r"
FIRST1=$(sh dumpsys package $PKG | grep -m1 firstInstallTime)

# 2) data like an existing user's: plain clipboard + plain API key (old versions) + a setting
sh am force-stop $PKG
APPUID=$(sh stat -c %u /data/data/$PKG)
cat > settings.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="cloud_key">CI-KEY-777</string>
    <string name="theme">deep</string>
    <boolean name="ci_marker" value="true" />
</map>
X
cat > clip.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="items">[{&quot;id&quot;:1,&quot;text&quot;:&quot;  CI clip\n  line2 &quot;,&quot;pinned&quot;:true}]</string>
</map>
X
adb push settings.xml /data/local/tmp/s.xml >/dev/null; adb push clip.xml /data/local/tmp/c.xml >/dev/null
sh "mkdir -p /data/data/$PKG/shared_prefs && cp /data/local/tmp/s.xml /data/data/$PKG/shared_prefs/jujukeys_settings.xml && cp /data/local/tmp/c.xml /data/data/$PKG/shared_prefs/jujukeys_clipboard.xml && chown -R $APPUID:$APPUID /data/data/$PKG/shared_prefs && chmod 771 /data/data/$PKG/shared_prefs && chmod 660 /data/data/$PKG/shared_prefs/*.xml && restorecon -R /data/data/$PKG/shared_prefs" >/dev/null

# 3) update to the new version (rotated signing)
r=$(adb install -r "$D/t-new-rotated.apk" 2>&1)
echo "$r" | grep -q Success && ok "update over v1.0.15 accepted (no uninstall)" || bad "update over v1.0.15 refused: $r"
FIRST2=$(sh dumpsys package $PKG | grep -m1 firstInstallTime)
[ "$FIRST1" = "$FIRST2" ] && ok "same install kept (firstInstallTime unchanged) → data not wiped" || bad "install time changed: $FIRST1 / $FIRST2"
sh cat /data/data/$PKG/shared_prefs/jujukeys_settings.xml | grep -q ci_marker && ok "user settings still there after update" || bad "settings lost after update"

# 4) open the app once → old plain data is moved into encrypted form
sh am start -W -n $PKG/.MainActivity >/dev/null; sleep 8; sh am force-stop $PKG
CLIP=$(sh cat /data/data/$PKG/shared_prefs/jujukeys_clipboard.xml)
SET=$(sh cat /data/data/$PKG/shared_prefs/jujukeys_settings.xml)
SEC=$(sh cat /data/data/$PKG/shared_prefs/jujukeys_secret.xml)
echo "$CLIP" | grep -q 'name="items_enc"' && ! echo "$CLIP" | grep -q 'CI clip' && ! echo "$CLIP" | grep -q 'name="items"' \
  && ok "clipboard history migrated: encrypted, plain copy removed" || bad "clipboard migration: $CLIP"
echo "$SEC" | grep -q 'cloud_key_enc' && ! echo "$SEC$SET" | grep -q 'CI-KEY-777' \
  && ok "API key migrated: encrypted, plain copies removed" || bad "API key migration: SEC=[$SEC]"
echo "$SET" | grep -q 'ci_marker' && echo "$SET" | grep -q '>deep<' && ok "other settings untouched by migration" || bad "settings changed by migration"

# 5) which signing key the phone now trusts
SIG=$(sh dumpsys package $PKG | grep -E -m3 "signatures=|pastSigningCertificates|Signing" | tr -s ' ')
info "package signature info: $SIG"

# 6) an update signed ONLY with the old (public) key
r=$(adb install -r "$D/t-new-oldkey-only.apk" 2>&1)
if [ "$API" -ge 28 ]; then
  echo "$r" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" && ok "old-key-only update REFUSED on Android $API (rotation works)" \
    || bad "old-key-only update was not refused on API $API: $r"
else
  if echo "$r" | grep -q Success; then info "Android 7-8 (API $API): old-key-only update is still ACCEPTED — key rotation needs Android 9+ (known limit)"
  else info "API $API old-key-only result: $r"; fi
  # put the proper version back for the backup check
  adb install -r "$D/t-new-rotated.apk" >/dev/null 2>&1
fi

# 7) Google backup (local test transport): what goes in, what stays out
# (the app must not be in the "stopped" state, or Android skips it — so start it and leave it)
sh am start -W -n $PKG/.MainActivity >/dev/null; sleep 4; sh input keyevent KEYCODE_HOME
sh bmgr enable true >/dev/null
sh bmgr list transports > transports.txt
TR=$(grep -o '[^ *]*LocalTransport' transports.txt | head -1)
if [ -n "$TR" ]; then
  sh bmgr transport "$TR" > tr.txt
  sh bmgr backupnow $PKG > bmgr.txt 2>&1
  sleep 5
  sh "find /data -name '$PKG' -path '*backup*' 2>/dev/null; find /data -path '*_full*' 2>/dev/null | head -20" > found.txt
  F=$(grep -m1 '_full.*'"$PKG"'$' found.txt)
  if [ -n "$F" ]; then
    adb exec-out cat "$F" > fb.bin
    # the local transport stores a tar stream; list the file names inside
    L=$(tar -tf fb.bin 2>/dev/null || strings fb.bin | grep -E '^apps/|shared_prefs|learned' )
    echo "$L" | grep -q "jujukeys_settings.xml" && ok "backup includes settings" || info "settings not seen in backup listing"
    if echo "$L" | grep -qE "jujukeys_clipboard.xml|jujukeys_secret.xml|learned_words.txt"; then bad "backup contains private files: $(echo "$L" | grep -E 'clipboard|secret|learned' | tr '\n' ' ')"
    else ok "backup leaves out clipboard, API key and learned words (files in backup: $(echo "$L" | grep -c .))"; fi
  else info "backup file not found — transport=$TR; bmgr: $(tr '\n' ' ' < bmgr.txt | head -c 250); found: $(tr '\n' ' ' < found.txt | head -c 250)"; fi
else info "no local backup transport — $(tr '\n' ' ' < transports.txt | head -c 200)"; fi

# 8) speed on this EMULATOR (not a phone): turn the keyboard on so it loads the dictionaries
sh ime enable $PKG/.JuJuKeysInputMethodService >/dev/null; sh ime set $PKG/.JuJuKeysInputMethodService >/dev/null
sleep 25
B=$(sh cat /data/data/$PKG/shared_prefs/jujukeys_settings.xml | grep -E 'bench_' | tr -s ' ' | tr '\n' ' ')
M=$(sh dumpsys meminfo $PKG | grep -E 'TOTAL PSS|TOTAL:' | head -1 | tr -s ' ')
info "EMULATOR benchmark (x86_64, not a real phone): $B | memory: $M"

# GitHub shows only a few notes per step — so also put everything into ONE note
echo "::notice title=API $API all results::$(tr '\n' '‖' < "results-$API.txt")"
exit $FAILED
