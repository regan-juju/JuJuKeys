# Third-party notices — JuJuKeys

| What | Where in the app | Source (URL, revision) | License |
|---|---|---|---|
| Word frequency lists (Bangla 50k, English) | `app/src/main/assets/dict_bn.txt`, `dict_en.txt` | https://github.com/hermitdave/FrequencyWords — commit `525f9b560de45753a5ea01069454e72e9aa541c6`, `content/2018/bn/bn_50k.txt`, `content/2018/en/` | Content: CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/) · code: MIT |
| Extra Bangla words (spelling dictionary) | `app/src/main/assets/dict_bn_avro.txt` (kept as a separate file) | https://github.com/sarim/ibus-avro — commit `dd521a139af0b4bb64eaeeca44fef03f5fb770cf`, file `avrodict.js` (Avro Phonetic dictionary, OmicronLab) | Mozilla Public License 2.0 — full text in `app/src/main/assets/licenses/MPL-2.0.txt`; the Source Code Form of this file is this repository |
| Bangla word counts and new frequent Bangla words | `app/src/main/assets/dict_bn_avro.txt` (count column and words with count ≥ 10) | Bangla Wikipedia and Bangla Wiktionary text dumps of 1 October 2026 — https://dumps.wikimedia.org/bnwiki/ and /bnwiktionary/ ; Bangla words counted (letters only), no article text is stored | Wikipedia/Wiktionary text: CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/) |
| Extra English words | `app/src/main/assets/dict_en_extra.txt` | Word list of a third-party keyboard dictionary that appears to be the stock Android Open Source Project keyboard dictionary (`main_en.dict`, 2012); not verified against the AOSP source | Apache License 2.0 (`assets/licenses/Apache-2.0.txt`) |
| Emoji list and order | `app/src/main/assets/emoji.txt` | https://github.com/googlefonts/emoji-metadata — commit `173b9b26e8fcbcbe64e1ecbf073a21a96b95c6b1`, `emoji_16_0_ordering.json` | Apache License 2.0 |
| Bangla font | `app/src/main/res/font/noto_bengali_*.ttf` | Noto Sans Bengali (Google Fonts) | SIL Open Font License 1.1 (`assets/NotoSansBengali-OFL.txt`) |
| Bangla font (optional, Settings → Bangla font) | `app/src/main/res/font/nikosh_ban_regular.ttf` | Nikosh BAN (Bangladesh Election Commission; Latin glyphs from DejaVu Serif), file unmodified | CC BY-NC-ND 3.0 (https://creativecommons.org/licenses/by-nc-nd/3.0/) — non-commercial use only; JuJuKeys is free, ad-free and not sold |
| Icons | `keyboard/Symbols.kt` | Material Symbols (Google) | Apache License 2.0 |
| Offline translation | Google ML Kit Translate | https://developers.google.com/ml-kit | Google APIs Terms |
| Sticker background removal | Google ML Kit Subject Segmentation (beta, via Google Play services) | https://developers.google.com/ml-kit/vision/subject-segmentation | Google APIs Terms |

Changes made by JuJuKeys: words normalised to Unicode NFC; duplicates removed (1,053 duplicate
lines in the original Bangla list); Avro words that were already in the frequency list were left
out of `dict_bn_avro.txt`; 78 Avro entries with broken characters were dropped; 48 Bangla and 28
English words (places, banking/office, everyday) were added by hand; `aliases_bn.txt` (common
Roman spellings → Bangla word) was written for JuJuKeys.

Version 1.0.53: about 50,000 more rare Bangla words were added to `dict_bn_avro.txt` (frequency 1, lowest priority) from the word list of a third-party keyboard dictionary whose licence is not stated; only well-formed Bangla words were kept. `dict_en_extra.txt` has about 90,000 rare English words (a–z only). If the owner of that Bangla list objects, those lines can be removed without affecting the rest.

Version 1.0.53 (update): `dict_bn_avro.txt` now has a count column (how often a word occurs in Bangla Wikipedia/Wiktionary; 1 = not seen) and about 142,000 more Bangla words that occur at least 10 times there. Digits and malformed words were removed.
