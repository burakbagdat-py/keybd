package com.keybd.pro

import java.io.InputStream
import java.util.Locale

/**
 * Emojileri Türkçe ve İngilizce anahtar kelimelerle arar ("kalp", "gül", "pizza", "love").
 * Veriler Unicode CLDR açıklamalarından üretilmiştir (assets/emoji_kw.txt).
 */
class EmojiSearch(open: () -> InputStream, flags: List<String>) {
    private class Entry(val emoji: String, val words: List<String>, val order: Int)

    private val entries = ArrayList<Entry>()
    private val tr = Locale("tr", "TR")

    init {
        try {
            open().bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.isEmpty() || p[0].isEmpty()) continue
                    val words = ArrayList<String>()
                    for (i in 1 until p.size) p[i].split('|').forEach { if (it.isNotBlank()) words += norm(it) }
                    entries += Entry(p[0], words, entries.size)
                }
            }
        } catch (_: Exception) {
        }
        // Bayraklar: ülke adlarını sistemden al ("türkiye", "germany"…)
        for (f in flags) {
            val cc = countryCode(f) ?: continue
            val loc = Locale("", cc)
            val words = listOf(loc.getDisplayCountry(tr), loc.getDisplayCountry(Locale.ENGLISH), "bayrak", "flag", cc)
                .filter { it.isNotBlank() }.map { norm(it) }
            entries += Entry(f, words, entries.size)
        }
    }

    private fun norm(s: String) = Dictionary.fold(s.trim().lowercase(tr))

    /** En uygun emojiler; sorgu boşsa boş liste. */
    fun search(query: String, limit: Int = 40): List<String> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        val scored = ArrayList<Pair<Entry, Int>>()
        for (e in entries) {
            var total = 0
            var ok = true
            for (t in tokens) {
                var best = 0
                for ((i, w) in e.words.withIndex()) {
                    val s = when {
                        w == t -> 100
                        w.startsWith(t) -> 60
                        w.split(' ').any { it.startsWith(t) } -> 40
                        t.length >= 3 && w.contains(t) -> 15
                        else -> 0
                    }
                    // Ana ad (ilk kelimeler) daha değerli
                    val v = if (s > 0 && i == 0) s + 20 else s
                    if (v > best) best = v
                }
                if (best == 0) { ok = false; break }
                total += best
            }
            if (ok) scored += e to total + (POPULAR[e.emoji]?.let { 45 - it } ?: 0)
        }
        return scored.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.order })
            .take(limit).map { it.first.emoji }
    }

    companion object {
        /** En sık kullanılan emojiler aramada öne çıkar (ilk sıradaki en fazla). */
        private val POPULAR: Map<String, Int> = listOf(
            "❤️", "😂", "😍", "🥰", "😊", "👍", "🙏", "😭", "😘", "🔥", "✨", "🎉", "😁", "💕", "🤣", "😎",
            "👌", "💯", "😢", "💙", "🥺", "😅", "🤔", "🙄", "😡", "💔", "🌹", "🎂", "👏", "💪", "😴", "🤗",
            "😱", "🤩", "😇", "🐱", "🐶", "☕", "🍕", "⚽", "🇹🇷", "🌸", "⭐", "🎁", "💋",
        ).withIndex().associate { it.value to it.index }

        /** 🇹🇷 → "TR" */
        fun countryCode(flag: String): String? {
            val cps = flag.codePoints().toArray()
            if (cps.size != 2 || cps.any { it !in 0x1F1E6..0x1F1FF }) return null
            return String(charArrayOf('A' + (cps[0] - 0x1F1E6), 'A' + (cps[1] - 0x1F1E6)))
        }
    }
}
