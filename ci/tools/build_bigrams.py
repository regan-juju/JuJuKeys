#!/usr/bin/env python3
"""Builds app/src/main/assets/bigram_bn.txt: for each common Bangla word, the words that most often follow it
in Bangla Wikipedia. Only counts are used; no article text is stored.
Usage: build_bigrams.py bnwiki-pages-articles.xml.bz2 app/src/main/assets  > out file written next to dictionaries
"""
import bz2, re, sys, unicodedata, collections, xml.etree.ElementTree as ET

dump, assets = sys.argv[1], sys.argv[2]
MAX_PREV, MAX_NEXT, MIN_PAIR = 25000, 5, 3

def words_of(path, col, min_count=0):
    out = set()
    for line in open(path, encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if len(p) > col and (len(p) < 2 or not p[1].isdigit() or int(p[1]) >= min_count):
            out.add(unicodedata.normalize("NFC", p[0]))
    return out

vocab = words_of(f"{assets}/dict_bn.txt", 0)
vocab |= words_of(f"{assets}/dict_bn_avro.txt", 0, 20)
print("vocab", len(vocab), file=sys.stderr)

TOKEN = re.compile(r"[ঀ-৿‌‍]+|[A-Za-z0-9]+|[^\s]")
BN = re.compile(r"^[ঀ-৿‌‍]+$")
DIGIT = re.compile(r"[০-৯]")
BREAK = re.compile(r"[।?!;:\n।॥]")

def clean(t):
    for _ in range(4):                                   # templates, innermost first
        t2 = re.sub(r"\{\{[^{}]*\}\}", " ", t)
        if t2 == t: break
        t = t2
    t = re.sub(r"\{\|.*?\|\}", " ", t, flags=re.S)       # tables
    t = re.sub(r"<ref[^>]*?/>|<ref[^>]*>.*?</ref>", " ", t, flags=re.S)
    t = re.sub(r"<!--.*?-->", " ", t, flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    t = re.sub(r"\[\[(?:ফাইল|File|Image|চিত্র|বিষয়শ্রেণী|Category|[a-z]{2,3}):[^\]]*\]\]", " ", t)
    t = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", t)
    t = re.sub(r"\[\[([^\]]*)\]\]", r"\1", t)
    t = re.sub(r"\[https?://[^\s\]]*\s*([^\]]*)\]", r"\1", t)
    t = re.sub(r"https?://\S+", " ", t)
    t = re.sub(r"'{2,}|^[=*#:;]+|={2,}", " ", t, flags=re.M)
    return t

pairs = collections.defaultdict(collections.Counter)
prev_total = collections.Counter()
pages = 0
with bz2.open(dump, "rb") as f:
    for ev, el in ET.iterparse(f, events=("end",)):
        tag = el.tag.rsplit("}", 1)[-1]
        if tag != "page": continue
        ns = el.find("{*}ns"); rev = el.find("{*}revision/{*}text")
        if ns is not None and ns.text == "0" and rev is not None and rev.text and not rev.text.startswith("#REDIRECT") and not rev.text.lstrip().lower().startswith("#পুনর্নির্দেশ"):
            text = unicodedata.normalize("NFC", clean(rev.text))
            for sent in BREAK.split(text):
                prev = None
                for tok in TOKEN.findall(sent):
                    if BN.match(tok) and not DIGIT.search(tok) and tok in vocab:
                        if prev is not None:
                            pairs[prev][tok] += 1
                            prev_total[prev] += 1
                        prev = tok
                    else:
                        prev = None
            pages += 1
            if pages % 20000 == 0: print("pages", pages, "prev words", len(pairs), file=sys.stderr, flush=True)
        el.clear()
print("pages", pages, file=sys.stderr)

lines = []
for w, _ in prev_total.most_common(MAX_PREV):
    nxt = [(n, c) for n, c in pairs[w].most_common(MAX_NEXT) if c >= MIN_PAIR and n != w]
    if nxt: lines.append(w + "\t" + " ".join(n for n, _ in nxt))
open(f"{assets}/bigram_bn.txt", "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("lines", len(lines), file=sys.stderr)
