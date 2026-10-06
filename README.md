# JuJuKeys — বাংলা (অভ্র) ও ENGLISH কীবোর্ড

Android সিস্টেম কীবোর্ড (InputMethodService, Kotlin + Jetpack Compose), ডার্ক iOS স্টাইল ডিজাইন।

## APK নামানো
প্রতিবার `main`-এ পুশ হলে GitHub Actions নিজে APK বানায় এবং **Releases**-এ রাখে:
https://github.com/regan-juju/JuJuKeys/releases/latest

রিলিজ APK শুধু GitHub Secrets-এ রাখা গোপন নতুন চাবি দিয়ে সাইন হয় (key rotation, Android 9+ নতুন চাবি মানে)। repo-তে কোনো চাবি বা পাসওয়ার্ড নেই; পুরনো চাবি (আগে পাবলিক ছিল) শুধু Secret থেকে CI-তে আসে, পুরনো ফোনের সামঞ্জস্য ও rotation-এর প্রমাণের জন্য। তাই নতুন ভার্সন পুরনোটার উপর আপডেট হিসেবে ইনস্টল হয়।

Play Store-এ নয় — সরাসরি ইনস্টল (sideload) করার অ্যাপ; compileSdk/targetSdk 34।

## যা আছে
- iPhone 16 Pro Max-এর মতো লেআউট: সাজেশন বার (ক/A বোতাম), 123 · ইমোজি · স্পেস · রিটার্ন, নিচে 🌐 ও 🎤
- নিচের পট্টির মাঝখানে: Google Keep ক্লিপবোর্ড ও Google Translate
- কী-র লেখা সবসময় CAPITAL; ENGLISH টাইপ সাধারণ কীবোর্ডের মতো (ছোট হাতের, Shift/বাক্যের শুরুতে বড় হাতের); বাংলা অভ্র: Shift ছাড়া ছোট হাতের, Shift দিয়ে বড় হাতের
- 🌐 চাপলে Gboard-এর মতো সেটিংস; ভাষা বদল সাজেশন বারের ক / A বোতামে
- সবসময় ডার্ক থিম
- শব্দ সাজেশন (বাংলা শব্দ মিলিয়ে: amra → আমরা, dhonnobad → ধন্যবাদ)
- ক্লিপবোর্ড ইতিহাস: পিন, মুছুন, এক ট্যাপে পেস্ট, 💡 চাপলে Google Keep-এ সেভ
- অনুবাদ: অফলাইন (Google ML Kit) + অনলাইন (নিজের Google Cloud API key দিলে)
- স্টিকার: ইমোজি প্যানেলে নিজের স্টিকার (যত খুশি; গ্যালারি থেকে যোগ — পারিবারিক ছবি তাই রিপোজিটরিতে রাখা হয় না); চাপলে WhatsApp/Messenger-এ সরাসরি যায়, না নিলে শেয়ার / গ্যালারিতে সেভ (Facebook পোস্টের জন্য); সাধারণ ছবির পটভূমি ফোনেই সরিয়ে সাদা বর্ডার (Google ML Kit)
- ইমোজি, ভয়েস টাইপিং (Google ভয়েস), স্পেসে আঙুল টেনে কার্সর সরানো, দুবার স্পেসে দাঁড়ি

- ?123 → নম্বর প্যাড, !?# → চিহ্ন, ১২ ৩৪ → আবার নম্বর প্যাড
- নিচের ⌃ চাপলে অনেক সাজেশন; ফোনেই শিখে পরের শব্দ আন্দাজ
- ক্লিপবোর্ডের ✎ চাপলে সব লেখা Google Keep-এ এক নোটে

উৎস ও লাইসেন্স: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — FrequencyWords (CC BY-SA 4.0), Avro অভিধান (MPL 2.0, আলাদা ফাইল `dict_bn_avro.txt`), Google emoji-metadata (Apache 2.0), Noto Sans Bengali (SIL OFL)।

## কোডের গঠন
- `bengali/AvroPhonetic.kt` — অভ্র ইঞ্জিন (Android ছাড়া বিশুদ্ধ Kotlin; `app/src/test`-এ টেস্ট)
- `JuJuKeysInputMethodService.kt` — কীবোর্ড সার্ভিস, টেক্সট বসানো
- `keyboard/KeyboardView.kt`, `keyboard/Panels.kt` — ডিজাইন (Compose)
- `suggest/Suggester.kt`, `clipboard/ClipHistory.kt`, `translate/TranslateEngine.kt`
- `keyboard/KeyboardLayouts.kt`, `keyboard/KeyboardState.kt`
- `MainActivity.kt` — সেটআপ স্ক্রিন

গোপনীয়তা: কীবোর্ড কোনো লেখা জমা রাখে না, লগ করে না, কোথাও পাঠায় না।
