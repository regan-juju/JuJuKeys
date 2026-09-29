# JuJuKeys — বাংলা (অভ্র) ও ENGLISH কীবোর্ড

Android সিস্টেম কীবোর্ড (InputMethodService, Kotlin + Jetpack Compose), ডার্ক iOS স্টাইল ডিজাইন।

## APK নামানো
প্রতিবার `main`-এ পুশ হলে GitHub Actions নিজে APK বানায় এবং **Releases**-এ রাখে:
https://github.com/regan-juju/JuJuKeys/releases/latest

সব APK একই কী দিয়ে সাইন করা, তাই নতুন ভার্সন পুরনোটার উপর আপডেট হিসেবে ইনস্টল হয়।

## ধাপ ১ (এখন আছে)
- ENGLISH: সবসময় বড় হাতের অক্ষর (CAPITAL)
- বাংলা: অভ্র ফোনেটিক, অফলাইন — `ami → আমি`, `bhalo → ভালো`
  - Shift ছাড়া ছোট হাতের (t → ত), Shift চেপে বড় হাতের (T → ট), দুবার চাপলে লক
- কী-তে সবসময় বড় হাতের লেবেল; লম্বা চাপলে কোণের সংখ্যা/চিহ্ন
- ?123 ও #+= চিহ্নের পাতা, গ্লোব বোতামে ভাষা বদল, টাইপ করার সময় `bhalo → ভালো` প্রিভিউ
- নীল অ্যাকশন কী (SEND / GO / SEARCH / DONE / নতুন লাইন)

## ধাপ ২ (পরবর্তী)
- ক্লিপবোর্ড ইতিহাস + এক ট্যাপে Google Keep-এ পাঠানো
- Google Translate: অফলাইন (ML Kit, বাংলা↔ইংলিশ মডেল একবার ডাউনলোড) ও অনলাইন
- ইমোজি, ভয়েস টাইপিং, সেটিংস

## কোডের গঠন
- `bengali/AvroPhonetic.kt` — অভ্র ইঞ্জিন (Android ছাড়া বিশুদ্ধ Kotlin; `app/src/test`-এ টেস্ট)
- `JuJuKeysInputMethodService.kt` — কীবোর্ড সার্ভিস, টেক্সট বসানো
- `keyboard/KeyboardView.kt` — ডিজাইন (Compose)
- `keyboard/KeyboardLayouts.kt`, `keyboard/KeyboardState.kt`
- `MainActivity.kt` — সেটআপ স্ক্রিন

গোপনীয়তা: কীবোর্ড কোনো লেখা জমা রাখে না, লগ করে না, কোথাও পাঠায় না।
