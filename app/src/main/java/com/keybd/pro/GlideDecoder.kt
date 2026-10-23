package com.keybd.pro

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/**
 * Parmağın klavye üzerinde çizdiği yolu kelimeye çevirir.
 * Başlangıç/bitiş tuşlarına göre adayları süzer, sonra her adayın "ideal" yolunu
 * kullanıcının yoluyla şekil olarak karşılaştırır ve kelime sıklığıyla birleştirir.
 */
/** Basit nokta (Android'den bağımsız, test edilebilir). */
class Pt(@JvmField val x: Float, @JvmField val y: Float)

object GlideDecoder {
    private const val N = 40

    fun decode(
        path: List<Pt>,
        centers: Map<Char, Pt>,
        keyW: Float,
        dict: Dictionary,
        prev: String?,
    ): List<String> {
        if (path.size < 2 || centers.isEmpty() || !dict.ready) return emptyList()
        val start = path.first()
        val end = path.last()
        val startKeys = nearKeys(start, centers, keyW * 0.95f)
        val endKeys = nearKeys(end, centers, keyW * 0.95f)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val userPath = resample(path, N)
        val pathLen = length(path)

        data class Scored(val word: String, val score: Float)
        val results = ArrayList<Scored>()
        val seen = HashSet<String>()

        for (s in startKeys) {
            for (e in dict.glideCandidates(s)) {
                val w = e.word
                if (w.length < 2 || !seen.add(w)) continue
                if (e.freq == 0 && e.user < 2) continue
                val keys = ArrayList<Pt>(w.length)
                var last: Char? = null
                var ok = true
                for (ch in w) {
                    val k = keyChar(ch, centers)
                    if (k == null) { ok = false; break }
                    if (k != last) keys += centers.getValue(k)
                    last = k
                }
                if (!ok || keys.isEmpty()) continue
                val firstK = keyChar(w[0], centers)
                val lastK = keyChar(w[w.length - 1], centers)
                if (firstK != s || lastK !in endKeys) continue
                // İdeal yol uzunluğu çok farklıysa ele
                val idealLen = lengthPts(keys)
                if (idealLen > pathLen * 1.9f + keyW || idealLen < pathLen * 0.45f - keyW) continue
                // Her harfin yakınından geçilmiş olmalı
                var miss = 0f
                for (kp in keys) {
                    var best = Float.MAX_VALUE
                    for (p in path) {
                        val d = hypot(p.x - kp.x, p.y - kp.y)
                        if (d < best) best = d
                    }
                    if (best > keyW * 0.9f) miss += (best / keyW)
                }
                if (miss > 1.6f) continue
                val ideal = if (keys.size == 1) List(N) { keys[0] } else resample(keys, N)
                var sum = 0f
                for (i in 0 until N) sum += hypot(ideal[i].x - userPath[i].x, ideal[i].y - userPath[i].y)
                val shape = sum / N / keyW
                val score = -shape * 3.2f - miss * 2f + dict.base(e) * 0.55f + lenBonus(w.length)
                results += Scored(w, score)
            }
        }
        val bigramBoosted = if (prev != null) {
            val preds = dict.predict(prev).map { dict.lower(it) }.toSet()
            results.map { if (it.word in preds) it.copy(score = it.score + 0.8f) else it }
        } else results
        return bigramBoosted.sortedByDescending { it.score }.take(5).map { dict.display(it.word) }
    }

    private fun lenBonus(n: Int) = ln(max(n, 2).toFloat()) * 0.3f

    private fun keyChar(c: Char, centers: Map<Char, Pt>): Char? {
        if (centers.containsKey(c)) return c
        val f = Dictionary.foldChar(c)
        return if (centers.containsKey(f)) f else null
    }

    private fun nearKeys(p: Pt, centers: Map<Char, Pt>, radius: Float): List<Char> {
        val list = centers.entries.map { it.key to hypot(it.value.x - p.x, it.value.y - p.y) }.sortedBy { it.second }
        val out = list.filter { it.second <= radius }.map { it.first }.take(3)
        return out.ifEmpty { listOfNotNull(list.firstOrNull()?.first) }
    }

    private fun length(p: List<Pt>): Float {
        var l = 0f
        for (i in 1 until p.size) l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        return l
    }

    private fun lengthPts(p: List<Pt>) = length(p)

    fun resample(pts: List<Pt>, n: Int): List<Pt> {
        val total = length(pts)
        if (total <= 0f) return List(n) { pts[0] }
        val step = total / (n - 1)
        val out = ArrayList<Pt>(n)
        out += Pt(pts[0].x, pts[0].y)
        var acc = 0f
        var prev = Pt(pts[0].x, pts[0].y)
        var i = 1
        while (i < pts.size && out.size < n) {
            val cur = pts[i]
            val d = hypot(cur.x - prev.x, cur.y - prev.y)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val np = Pt(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out += np
                prev = np
                acc = 0f
            } else {
                acc += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out += Pt(pts.last().x, pts.last().y)
        return out
    }
}
