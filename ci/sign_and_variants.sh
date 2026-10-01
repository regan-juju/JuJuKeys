#!/usr/bin/env bash
# Signs the release APK with KEY ROTATION (old public key → new private key) and makes the
# test variants used on the emulators. Never prints keys or passwords.
#   main branch : the real new key from GitHub Secrets is REQUIRED (build fails without it)
#   test branch : without Secrets, a throw-away test key is used (APK is NOT published)
set -euo pipefail
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
APK=app/build/outputs/apk/release/app-release.apk
OUT=signed; mkdir -p "$OUT"
OLD=(--ks keystore/jujukeys.jks --ks-key-alias jujukeys --ks-pass pass:jujukeys --key-pass pass:jujukeys)

if [ -n "${NEW_KS_B64:-}" ] && [ -n "${NEW_KS_PASS:-}" ]; then
  echo "$NEW_KS_B64" | base64 -d > "$RUNNER_TEMP/new.jks"
  ALIAS=jujukeys2; KIND=real
elif [ "${GITHUB_REF:-}" = "refs/heads/main" ]; then
  echo "::error title=Signing::The new signing key is not set. Add GitHub Secrets JUJU_KEYSTORE_B64 and JUJU_KEYSTORE_PASS. Release stopped — it will NOT fall back to the old public key."
  exit 1
else
  NEW_KS_PASS=$(openssl rand -hex 16); export NEW_KS_PASS
  keytool -genkeypair -keystore "$RUNNER_TEMP/new.jks" -storetype PKCS12 -storepass "$NEW_KS_PASS" -keypass "$NEW_KS_PASS" \
    -alias jujukeys2 -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=JuJuKeys CI TEST ONLY" >/dev/null 2>&1
  ALIAS=jujukeys2; KIND=test
  echo "::warning title=Signing::Test branch without Secrets — signed with a THROW-AWAY test key (for checking only, not published)."
fi
NEW=(--ks "$RUNNER_TEMP/new.jks" --ks-key-alias "$ALIAS" --ks-pass env:NEW_KS_PASS --key-pass env:NEW_KS_PASS)

"$BT/apksigner" rotate --out "$RUNNER_TEMP/lineage.bin" --old-signer "${OLD[@]}" --new-signer "${NEW[@]}"

sign_rotated() { "$BT/apksigner" sign "${OLD[@]}" --next-signer "${NEW[@]}" --lineage "$RUNNER_TEMP/lineage.bin" --rotation-min-sdk-version 28 --out "$2" "$1"; }
sign_old()     { "$BT/apksigner" sign "${OLD[@]}" --out "$2" "$1"; }
nolib()        { cp "$1" "$RUNNER_TEMP/x.zip"; zip -q -d "$RUNNER_TEMP/x.zip" 'lib/*' 'META-INF/*' >/dev/null 2>&1 || true
                 "$BT/zipalign" -f -p 4 "$RUNNER_TEMP/x.zip" "$2"; }

# the real APK
sign_rotated "$APK" "$OUT/app-release.apk"
echo "$KIND" > "$OUT/key-kind.txt"

# emulator test variants (native libs removed so they install on x86_64 emulators;
# the signing is exactly the same as the real APK)
curl -sSfL -o "$RUNNER_TEMP/v15.apk" https://github.com/regan-juju/JuJuKeys/releases/download/v1.0.15/JuJuKeys-v1.0.15.apk
nolib "$RUNNER_TEMP/v15.apk" "$RUNNER_TEMP/v15.zip";  sign_old "$RUNNER_TEMP/v15.zip" "$OUT/t-old-v15.apk"
nolib "$APK" "$RUNNER_TEMP/new.zip";                   sign_rotated "$RUNNER_TEMP/new.zip" "$OUT/t-new-rotated.apk"
sign_old "$RUNNER_TEMP/new.zip" "$OUT/t-new-oldkey-only.apk"     # what someone with only the OLD public key could make

rm -f "$RUNNER_TEMP/new.jks"

# ---- report what the signatures look like (certificate fingerprints only)
SUM=""
note() { while IFS= read -r l; do SUM="$SUM [$1] $l ‖"; done; }
note "verify" < <("$BT/apksigner" verify --verbose "$OUT/app-release.apk" | grep -E "Verified using v[23] |Verified using v1|Number of signers")
note "Android 7-8 sees" < <("$BT/apksigner" verify --print-certs --min-sdk-version 24 --max-sdk-version 27 "$OUT/app-release.apk" | grep -E "SHA-256")
note "Android 9+ sees" < <("$BT/apksigner" verify --print-certs --min-sdk-version 28 "$OUT/app-release.apk" | grep -E "SHA-256")
note "lineage" < <("$BT/apksigner" lineage --in "$OUT/app-release.apk" --print-certs -v 2>&1 | grep -E "Signer #|SHA-256|rollback|Has ")
echo "::notice title=signing summary ($KIND key)::$SUM"
