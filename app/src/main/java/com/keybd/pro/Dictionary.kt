package com.keybd.pro

import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/**
 * Kelime sözlüğü: önek tamamlama, yazım hatası düzeltme (klavye yakınlığına ve Türkçe
 * karakterlere duyarlı), kullanıcı kelimelerini öğrenme ve sonraki kelime tahmini.
 */
class Dictionary(val lang: String, private val dir: File, private val openAsset: () -> InputStream) {

    class Entry(val word: String, val folded: String, var freq: Int, var user: Int = 0)

    class Result(val words: List<String>, val autoCorrect: String?, val typedValid: Boolean)

    private val locale: Locale = if (lang == "tr") Locale("tr", "TR") else Locale.US
    private val byWord = HashMap<String, Entry>(70000)
    private val sorted = ArrayList<Entry>(70000)
    private val byFirst = HashMap<Char, ArrayList<Entry>>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    @Volatile var ready = false
        private set
    private var dirty = false

    var neighbors: Map<Char, String> = emptyMap()

    fun lower(s: String): String = s.lowercase(locale)

    fun load() {
        try {
            openAsset().bufferedReader().useLines { lines ->
                for (line in lines) {
                    val sp = line.indexOf(' ')
                    if (sp <= 0) continue
                    val w = line.substring(0, sp)
                    val f = line.substring(sp + 1).trim().toIntOrNull() ?: 1
                    if (byWord.containsKey(w)) continue
                    val e = Entry(w, fold(w), f)
                    byWord[w] = e
                    sorted += e
                }
            }
        } catch (_: Exception) {
        }
        try {
            userFile().takeIf { it.exists() }?.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 2) {
                    val w = p[0]
                    val n = p[1].toIntOrNull() ?: return@forEachLine
                    val e = byWord[w] ?: Entry(w, fold(w), 0).also { byWord[w] = it; sorted += it }
                    e.user = n
                }
            }
            bigramFile().takeIf { it.exists() }?.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 3) bigrams.getOrPut(p[0]) { HashMap() }[p[1]] = p[2].toIntOrNull() ?: 1
            }
        } catch (_: Exception) {
        }
        sorted.sortBy { it.folded }
        for (e in sorted) if (e.folded.isNotEmpty()) byFirst.getOrPut(e.folded[0]) { ArrayList() } += e
        ready = true
    }

    private fun userFile() = File(dir, "user_$lang.txt")
    private fun bigramFile() = File(dir, "bigram_$lang.txt")

    fun base(e: Entry): Float = ln((e.freq + 1).toFloat()) * 0.5f + ln((1 + e.user).toFloat()) * 1.3f

    private fun usable(e: Entry) = e.freq > 0 || e.user >= 2

    fun isValid(word: String): Boolean = byWord[lower(word)]?.let { usable(it) } ?: false

    /** Ana öneri fonksiyonu. */
    fun suggest(typed: String, prev: String?, allowCorrect: Boolean): Result {
        if (!ready || typed.isEmpty()) return Result(emptyList(), null, false)
        val t = lower(typed)
        val tf = fold(t)
        val exact = byWord[t]
        val typedValid = exact != null && usable(exact)
        val bg = prev?.let { bigrams[lower(it)] }
        val cand = HashMap<String, Float>()

        fun consider(e: Entry, s: Float) {
            if (!usable(e)) return
            val boost = bg?.get(e.word)?.let { ln((1 + it).toFloat()) * 1.5f } ?: 0f
            val sc = s + boost
            val old = cand[e.word]
            if (old == null || old < sc) cand[e.word] = sc
        }

        // 1) Önek tamamlama (Türkçe karaktersiz yazıma da duyarlı)
        var i = lowerBound(tf)
        while (i < sorted.size) {
            val e = sorted[i]
            if (!e.folded.startsWith(tf)) break
            var mismatch = 0
            for (k in t.indices) if (k < e.word.length && e.word[k] != t[k]) mismatch++
            val extra = e.word.length - t.length
            val s = base(e) - extra * 0.28f - mismatch * 0.5f + (if (extra == 0) 0.6f else 0f)
            consider(e, s)
            i++
        }

        // 2) Yazım hatası düzeltme
        var bestCorr: Entry? = null
        var bestCorrScore = -1e9f
        var bestCorrD = 0f
        if (t.length >= 2) {
            val maxD = when {
                t.length <= 2 -> 1f
                t.length <= 4 -> 1.3f
                t.length <= 7 -> 2f
                else -> 2.6f
            }
            val firsts = LinkedHashSet<Char>()
            firsts += tf[0]
            if (t.length >= 4) neighbors[t[0]]?.forEach { firsts += fold(it.toString()).firstOrNull() ?: it }
            for (f in firsts) {
                val bucket = byFirst[f] ?: continue
                for (e in bucket) {
                    if (abs(e.word.length - t.length) > maxD) continue
                    if (!usable(e)) continue
                    val d = distance(t, e.word, maxD)
                    if (d > maxD) continue
                    val s = base(e) - d * 2.4f + (if (e.word.length == t.length) 0.2f else 0f)
                    consider(e, s)
                    if (e.word != t && s > bestCorrScore) {
                        bestCorrScore = s
                        bestCorr = e
                        bestCorrD = d
                    }
                }
            }
        }

        val ranked = cand.entries.sortedByDescending { it.value }.take(6).map { display(it.key) }
        // Sözlükteki çok nadir kelime, çok daha yaygın yakın bir kelimenin yazım hatası olabilir
        // (örn. "tesekkurler" → "teşekkürler", "helo" → "hello").
        val bc = bestCorr
        val rareTypo = typedValid && exact != null && exact.user < 2 && bc != null && (t.length >= 4 || bc.folded == tf) &&
            bestCorrD <= 0.8f && bc.freq >= exact.freq.toLong() * 60
        val valid = typedValid && !rareTypo
        var auto: String? = null
        if (allowCorrect && !valid && t.length >= 2 && t.none { it.isDigit() } && bc != null && bestCorrScore > 0.8f) {
            auto = display(bc.word)
        }
        if (lang == "en" && t == "i") auto = "I"
        return Result(ranked, auto, valid)
    }

    /** Önceki kelimeye göre sonraki kelime tahmini (kullanıcının yazdıklarından öğrenilir). */
    fun predict(prev: String?): List<String> {
        if (!ready || prev.isNullOrEmpty()) return emptyList()
        val m = bigrams[lower(prev)] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(3).map { display(it.key) }
    }

    fun display(w: String): String {
        if (lang == "en" && (w == "i" || w.startsWith("i'"))) return "I" + w.substring(1)
        return w
    }

    fun learn(word: String, prev: String?, force: Boolean = false) {
        if (!ready) return
        val w = lower(word.trim())
        if (w.isEmpty() || w.length > 32 || w.any { it.isDigit() } || !w[0].isLetter()) return
        var e = byWord[w]
        if (e == null) {
            e = Entry(w, fold(w), 0)
            byWord[w] = e
            val idx = lowerBound(e.folded)
            sorted.add(idx, e)
            byFirst.getOrPut(e.folded[0]) { ArrayList() } += e
        }
        e.user += if (force) 2 else 1
        if (!prev.isNullOrEmpty()) {
            val p = lower(prev)
            if (p.length <= 32 && p[0].isLetter()) {
                val m = bigrams.getOrPut(p) { HashMap() }
                m[w] = (m[w] ?: 0) + 1
            }
        }
        dirty = true
    }

    fun forget(word: String) {
        val w = lower(word)
        val e = byWord[w] ?: return
        e.user = 0
        if (e.freq == 0) {
            byWord.remove(w)
            sorted.remove(e)
            byFirst[e.folded.firstOrNull() ?: ' ']?.remove(e)
        }
        for (m in bigrams.values) m.remove(w)
        dirty = true
    }

    fun clearLearned() {
        for (e in byWord.values) e.user = 0
        bigrams.clear()
        userFile().delete()
        bigramFile().delete()
        dirty = false
    }

    fun save() {
        if (!ready || !dirty) return
        dirty = false
        val users = byWord.values.filter { it.user > 0 }.map { "${it.word}\t${it.user}" }
        if (bigrams.size > 6000) {
            val it = bigrams.entries.iterator()
            while (it.hasNext()) {
                val m = it.next().value
                m.values.removeAll { v -> v <= 1 }
                if (m.isEmpty()) it.remove()
            }
        }
        val bgs = ArrayList<String>()
        for ((p, m) in bigrams) for ((w, n) in m) bgs += "$p\t$w\t$n"
        Thread {
            try {
                userFile().writeText(users.joinToString("\n"))
                bigramFile().writeText(bgs.joinToString("\n"))
            } catch (_: Exception) {
            }
        }.start()
    }

    fun glideCandidates(first: Char): List<Entry> = byFirst[fold(first.toString()).firstOrNull() ?: first] ?: emptyList()

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].folded < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // --- Ağırlıklı Damerau-Levenshtein mesafesi ---
    private var r0 = FloatArray(64)
    private var r1 = FloatArray(64)
    private var r2 = FloatArray(64)

    fun distance(a: String, b: String, max: Float): Float {
        val n = a.length
        val m = b.length
        if (m + 1 > r0.size) {
            r0 = FloatArray(m + 16); r1 = FloatArray(m + 16); r2 = FloatArray(m + 16)
        }
        var prev2 = r0
        var prev = r1
        var cur = r2
        for (j in 0..m) prev[j] = j.toFloat()
        for (i in 1..n) {
            cur[0] = i.toFloat()
            var rowMin = cur[0]
            val ca = a[i - 1]
            for (j in 1..m) {
                val cb = b[j - 1]
                var v = prev[j - 1] + subCost(ca, cb)
                val del = prev[j] + (if (i >= 2 && a[i - 2] == ca) 0.4f else 0.9f)
                val ins = cur[j - 1] + (if (cb == '\'') 0.2f else if (j >= 2 && b[j - 2] == cb) 0.35f else 1f)
                v = min(v, min(del, ins))
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) v = min(v, prev2[j - 2] + 0.7f)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return Float.MAX_VALUE
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    private fun subCost(a: Char, b: Char): Float {
        if (a == b) return 0f
        if (foldChar(a) == foldChar(b)) return 0.25f
        val nb = neighbors[a]
        if (nb != null && nb.indexOf(b) >= 0) return 0.6f
        return 1f
    }

    companion object {
        fun foldChar(c: Char): Char = when (c) {
            'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
            'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'; 'é', 'è', 'ê', 'ë' -> 'e'; 'á', 'à', 'ä', 'ã' -> 'a'
            'ó', 'ò', 'õ' -> 'o'; 'ú', 'ù' -> 'u'; 'í', 'ì', 'ï' -> 'i'; 'ñ' -> 'n'; '\'' -> '\''
            else -> c
        }

        fun fold(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) sb.append(foldChar(c))
            return sb.toString()
        }
    }
}
