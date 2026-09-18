# JuJuKeys — Android System Keyboard

A real Android IME (InputMethodService) with Bengali Avro-style offline phonetic typing
and an always-uppercase English QWERTY keyboard, built with Kotlin + Jetpack Compose.

## How to open and build

This sandbox has no internet access to Google's Maven repository, so the project could
**not** be compiled or run here — that part is honest, not glossed over. To build it:

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer).
2. Open this folder (`JuJuKeys/`) directly — **File → Open**, not "Import".
3. Android Studio will offer to generate the Gradle wrapper (`gradlew`) automatically on
   first sync, since this project only ships `gradle/wrapper/gradle-wrapper.properties`
   (pinned to Gradle 8.7) and not the `gradlew`/`gradlew.bat` scripts themselves — they
   need a real internet connection to fetch, which this sandbox didn't have either.
   If Android Studio doesn't prompt, run `gradle wrapper --gradle-version 8.7` once from
   a terminal with internet access.
4. Let Gradle sync (downloads AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, etc.).
5. Run the `app` configuration on a device or emulator (**minSdk 24**).
6. On the device: Settings → System → Languages & input → On-screen keyboard → turn
   JuJuKeys on → switch to it from any text field's keyboard/globe icon. The app's own
   launcher screen also has direct buttons for both steps.

## What was verified in this environment (and how)

- **Bengali phonetic engine** (`bengali/BengaliPhoneticEngine.kt`): the exact algorithm
  was ported to Python and run against all of the spec's test words —
  `ami→আমি, amar→আমার, bangla→বাংলা, bhalo→ভালো, tumi→তুমি, dhonnobad→ধন্যবাদ,
  kemon→কেমন, acho→আছো` — all passed. This is a rule-based engine (vowel/consonant
  tables + conjunct/anusvara rules), not a hardcoded word list; extend the tables in
  that file to widen coverage.
- **Resource references**: every `R.drawable.*` / `R.string.*` used in Kotlin code was
  cross-checked against what's actually declared in `res/` — no missing references.
- **XML validity**: every manifest/resource XML file was parsed and confirmed well-formed.
- **Gradle/AGP resolution**: attempted for real; failed only because this sandbox's
  network policy blocks `dl.google.com` / `services.gradle.org` / Maven Central for
  plugin resolution — a sandbox limitation, not a project defect. This could not be
  verified further here.

## What was NOT verified (be aware before you rely on it)

- **Full Kotlin compilation** — no Android SDK / Google Maven access here means `javac`/
  `kotlinc`-level type-checking across all 29 files never ran. I reviewed each file by
  hand and cross-checked imports/APIs against their real signatures, but a first build
  in Android Studio may still surface a small issue (an import, an API version nuance) —
  treat the first `./gradlew assembleDebug` as the real compile check.
- **On-device behavior** — text insertion, backspace, language switching, clipboard,
  emoji, voice input, and settings persistence are all implemented against real Android
  APIs (`InputConnection`, `ClipboardManager`, `SpeechRecognizer`, DataStore) but were
  never run on an emulator/device.

## Known simplifications (documented, not hidden)

- **Bengali phonetic rules** cover the general Avro-style grammar (vowels, consonants,
  conjuncts via hasant, anusvara for "ng") plus one documented exception ("nn"→ন্য).
  Real Avro has ~60 special-cased rules (reph, ya-phola/ba-phola placement, চন্দ্রবিন্দু,
  etc.) that aren't all modelled — uncommon words may transliterate slightly differently
  than the official Avro Keyboard app. Extend `BengaliPhoneticEngine.kt`'s tables to fix
  specific words as you find them.
- **Online translation**: no API key is bundled (that would leak a secret in the APK, and
  the spec explicitly forbids hardcoding one). `translation/TranslationEngine.kt` has a
  clear extension point (`OnlineTranslationProvider`) — wire in your own provider/key
  from Settings if you want live online translation; until then it runs fully offline on
  a small built-in dictionary and clearly reports "online unavailable" rather than faking it.
- **English is always uppercase** exactly as specified, including in password fields —
  the spec didn't carve out an exception for password fields, so none was added. If you
  want lowercase in password fields specifically, that's a one-line change in
  `JuJuKeysInputMethodService.onLetterKey`.
- Launcher icon is a simple generated placeholder (blue rounded key with "J"), not a
  designed brand mark — swap `res/mipmap-*/ic_launcher*.png` and the adaptive-icon
  vectors for real artwork.

## Project structure

```
app/src/main/java/com/reganbarua/jujukeys/
  JuJuKeysInputMethodService.kt   the IME service — the only place touching InputConnection
  MainActivity.kt                 onboarding (enable + switch to JuJuKeys)
  bengali/BengaliPhoneticEngine.kt
  keyboard/                       Compose UI: keys, toolbar, layouts, state, bottom row
  suggestions/                    offline EN/BN word lists + suggestion & autocorrect logic
  clipboard/                      ClipboardManager wrapper + history + Keep/Sharesheet
  translation/                    offline dictionary + online-provider extension point
  emoji/                          emoji data + picker grid
  voice/                          SpeechRecognizer wrapper + runtime-permission flow
  settings/                       DataStore-backed preferences + Settings screen
  theme/                          dark iPhone-style color palette matching the reference image
```
