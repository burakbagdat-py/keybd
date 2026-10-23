#!/usr/bin/env python3
"""Emoji varlık dosyalarını üretir.

Girdi (indirip bir klasöre koyun, yolu argüman olarak verin):
  emoji-test.txt         https://unicode.org/Public/emoji/latest/emoji-test.txt
  annotations-tr.json    https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations/tr/annotations.json
  annotations-en.json    https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations/en/annotations.json
Çıktı:
  assets/emoji_all.txt   "#Grup" satırları + emojiler (ten rengi varyantları hariç)
  assets/emoji_tones.txt "temel<TAB>varyant1 varyant2 …"
  assets/emoji_kw.txt    "emoji<TAB>tr anahtar kelimeler<TAB>en anahtar kelimeler"
Kullanım: python3 tools/gen_emoji.py <girdi-klasörü>
"""
import json
import os
import sys

src = sys.argv[1]
assets = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
TONES = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}
GROUPS = {
    "Smileys & Emotion": "😀 İfadeler", "People & Body": "👋 İnsanlar", "Animals & Nature": "🐶 Doğa",
    "Food & Drink": "🍔 Yiyecek", "Travel & Places": "🚗 Seyahat", "Activities": "⚽ Aktivite",
    "Objects": "💡 Nesneler", "Symbols": "❤️ Semboller", "Flags": "🏁 Bayraklar",
}


def strip_tones(e):
    return "".join(c for c in e if ord(c) not in TONES)


groups, order, tones = {}, [], {}
group = None
for line in open(os.path.join(src, "emoji-test.txt"), encoding="utf-8"):
    if line.startswith("# group:"):
        g = line.split(":", 1)[1].strip()
        group = GROUPS.get(g)
        if group and group not in groups:
            groups[group] = []
            order.append(group)
        continue
    if not group or "; fully-qualified" not in line:
        continue
    e = line.split("#", 1)[1].strip().split(" ")[0]
    if any(ord(c) in TONES for c in e):
        base = strip_tones(e)
        tones.setdefault(base, []).append(e)
        continue
    groups[group].append(e)

with open(os.path.join(assets, "emoji_all.txt"), "w", encoding="utf-8") as f:
    for g in order:
        f.write("#" + g + "\n" + " ".join(groups[g]) + "\n")
with open(os.path.join(assets, "emoji_tones.txt"), "w", encoding="utf-8") as f:
    for b, vs in tones.items():
        # Yalnızca tek ten varyantlı (5 renk) olanlar; çiftli kombinasyonlar çok uzun
        single = [v for v in vs if sum(ord(c) in TONES for c in v) == 1][:5]
        if single:
            f.write(b + "\t" + " ".join(single) + "\n")


def load(lang):
    return json.load(open(os.path.join(src, f"annotations-{lang}.json"), encoding="utf-8"))["annotations"]["annotations"]


tr, en = load("tr"), load("en")


def kw(x):
    if not x:
        return ""
    seen = []
    for w in list(x.get("tts", [])) + list(x.get("default", [])):
        w = w.strip().lower()
        if w and w not in seen:
            seen.append(w)
    return "|".join(seen)


n = 0
with open(os.path.join(assets, "emoji_kw.txt"), "w", encoding="utf-8") as f:
    for g in order:
        for e in groups[g]:
            k = e.replace("️", "")
            a = tr.get(e) or tr.get(k)
            b = en.get(e) or en.get(k)
            if a or b:
                f.write(f"{e}\t{kw(a)}\t{kw(b)}\n")
                n += 1
print(sum(len(v) for v in groups.values()), "emoji,", len(tones), "ten grubu,", n, "anahtar kelimeli")
