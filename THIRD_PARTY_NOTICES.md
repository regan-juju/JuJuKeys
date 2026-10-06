# Third-party notices — JuJuKeys

| What | Where in the app | Source (URL, revision) | License |
|---|---|---|---|
| Word frequency lists (Bangla 50k, English) | `app/src/main/assets/dict_bn.txt`, `dict_en.txt` | https://github.com/hermitdave/FrequencyWords — commit `525f9b560de45753a5ea01069454e72e9aa541c6`, `content/2018/bn/bn_50k.txt`, `content/2018/en/` | Content: CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/) · code: MIT |
| Extra Bangla words (spelling dictionary) | `app/src/main/assets/dict_bn_avro.txt` (kept as a separate file) | https://github.com/sarim/ibus-avro — commit `dd521a139af0b4bb64eaeeca44fef03f5fb770cf`, file `avrodict.js` (Avro Phonetic dictionary, OmicronLab) | Mozilla Public License 2.0 — full text in `app/src/main/assets/licenses/MPL-2.0.txt`; the Source Code Form of this file is this repository |
| Emoji list and order | `app/src/main/assets/emoji.txt` | https://github.com/googlefonts/emoji-metadata — commit `173b9b26e8fcbcbe64e1ecbf073a21a96b95c6b1`, `emoji_16_0_ordering.json` | Apache License 2.0 |
| Bangla font | `app/src/main/res/font/noto_bengali_*.ttf` | Noto Sans Bengali (Google Fonts) | SIL Open Font License 1.1 (`assets/NotoSansBengali-OFL.txt`) |
| Icons | `keyboard/Symbols.kt` | Material Symbols (Google) | Apache License 2.0 |
| Offline translation | Google ML Kit Translate | https://developers.google.com/ml-kit | Google APIs Terms |
| Sticker background removal | Google ML Kit Subject Segmentation (beta, via Google Play services) | https://developers.google.com/ml-kit/vision/subject-segmentation | Google APIs Terms |

Changes made by JuJuKeys: words normalised to Unicode NFC; duplicates removed (1,053 duplicate
lines in the original Bangla list); Avro words that were already in the frequency list were left
out of `dict_bn_avro.txt`; 78 Avro entries with broken characters were dropped; 48 Bangla and 28
English words (places, banking/office, everyday) were added by hand; `aliases_bn.txt` (common
Roman spellings → Bangla word) was written for JuJuKeys.
