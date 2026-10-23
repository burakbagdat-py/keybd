package com.keybd.pro

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Venom Simbiyot teması efektleri.
 * - Tuş şekilleri (organik köşeler + damlalar) bir kez hesaplanıp önbelleğe alınır (akıcılık).
 * - Arka plandaki dokunaçlar dokunuştan sonra birkaç saniye canlı dalgalanır, sonra durur (pil).
 * - Basınca: beyaz sıvı dokunulan noktadan tuşa yayılır, komşu tuşlara simbiyot iplikleri
 *   uzanır; bırakınca iplikler kopar, sıvı çekilir. Etrafa organik dikenler ve damlalar saçılır.
 */
class SymbioteFx(private val dp: (Float) -> Float) {

    private class KeyShape(val path: Path, val gloss: Path)

    private class Strand(val from: Key, val to: Key, val sag: Float, val t0: Long) {
        var released = -1L
    }

    private class Press(val key: Key, val x: Float, val y: Float, val t0: Long) {
        var released = -1L
    }

    private class Drop(var x: Float, var y: Float, val vx: Float, var vy: Float, val r: Float, val t0: Long)

    private class Spike(val x: Float, val y: Float, val angle: Float, val len: Float, val t0: Long)

    private class Tendril(val pts: FloatArray, val phase: Float, val speed: Float, val width: Float)

    private val shapes = HashMap<Key, KeyShape>()
    private val tendrils = ArrayList<Tendril>()
    private val presses = ArrayList<Press>()
    private val strands = ArrayList<Strand>()
    private val drops = ArrayList<Drop>()
    private val spikes = ArrayList<Spike>()
    private val keys = ArrayList<Key>()
    private var lastTouch = 0L
    private var keyH = 1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tmp = Path()
    private val rect = RectF()

    // ------------------------------------------------------------------ yerleşim

    fun layout(all: List<Key>, gap: Float, radius: Float, w: Int, h: Int) {
        shapes.clear()
        keys.clear()
        keys += all
        val hg = gap / 2f
        val vg = gap * 0.75f
        val rnd = Random(11)
        for ((idx, k) in all.withIndex()) {
            val r = RectF(k.left + hg, k.top + vg, k.right - hg, k.bottom - vg)
            keyH = r.height()
            val base = radius.coerceAtLeast(dp(6f)) * 1.35f
            // Her köşe biraz farklı yuvarlak: organik görünüm
            val radii = FloatArray(8) { base * (0.75f + rnd.nextFloat() * 0.6f) }
            val p = Path()
            p.addRoundRect(r, radii, Path.Direction.CW)
            if (k.type == KeyType.CHAR && (idx * 7919 + 3) % 4 == 0) {
                val dr = min(r.width(), r.height()) * (0.10f + rnd.nextFloat() * 0.06f)
                val dx = r.left + r.width() * (0.25f + rnd.nextFloat() * 0.5f)
                tmp.reset()
                tmp.addCircle(dx, r.bottom + dr * 0.9f, dr, Path.Direction.CW)
                tmp.addRect(dx - dr * 0.5f, r.bottom - dr, dx + dr * 0.5f, r.bottom + dr * 0.7f, Path.Direction.CW)
                p.op(tmp, Path.Op.UNION)
            }
            // Islak parlaklık: sol üstte ince yay
            val g = Path()
            val inset = RectF(r.left + dp(3f), r.top + dp(2.5f), r.right - dp(3f), r.bottom - dp(3f))
            g.addArc(inset, 195f, 70f)
            shapes[k] = KeyShape(p, g)
        }
        buildTendrils(w, h)
    }

    private fun buildTendrils(w: Int, h: Int) {
        tendrils.clear()
        if (w <= 0 || h <= 0) return
        val r = Random(7)
        repeat(12) { i ->
            val segs = 3
            val pts = FloatArray((segs + 1) * 2)
            var x: Float
            var y: Float
            when (i % 4) {
                0 -> { x = r.nextFloat() * w; y = 0f }
                1 -> { x = r.nextFloat() * w; y = h.toFloat() }
                2 -> { x = 0f; y = r.nextFloat() * h }
                else -> { x = w.toFloat(); y = r.nextFloat() * h }
            }
            pts[0] = x; pts[1] = y
            for (s in 1..segs) {
                x = (x + (r.nextFloat() - 0.5f) * w * 0.45f).coerceIn(0f, w.toFloat())
                y = (y + (r.nextFloat() - 0.5f) * h * 0.8f).coerceIn(0f, h.toFloat())
                pts[s * 2] = x; pts[s * 2 + 1] = y
            }
            tendrils += Tendril(pts, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.9f, if (i % 3 == 0) 1.4f else 0.85f)
        }
    }

    // ------------------------------------------------------------------ olaylar

    fun onPress(k: Key, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        lastTouch = now
        presses.removeAll { it.key === k }
        presses += Press(k, x, y, now)
        // En yakın 2-3 komşuya iplik
        val near = keys.filter { it !== k }.sortedBy { hypot(it.cx - k.cx, it.cy - k.cy) }.take(5).shuffled().take(2 + Random.nextInt(2))
        for (n in near) strands += Strand(k, n, (Random.nextFloat() - 0.5f) * keyH * 0.8f, now)
        // Organik dikenler + damlalar
        val base = Random.nextFloat() * 6.28f
        for (i in 0 until 7) {
            val a = base + i * 6.28f / 7 + (Random.nextFloat() - 0.5f) * 0.4f
            spikes += Spike(x, y, a, keyH * (0.45f + Random.nextFloat() * 0.5f), now)
        }
        repeat(3 + Random.nextInt(3)) {
            val a = Random.nextFloat() * 6.28f
            val v = dp(0.25f + Random.nextFloat() * 0.35f)
            drops += Drop(x, y, cos(a) * v, sin(a) * v - dp(0.25f), dp(1.5f + Random.nextFloat() * 2.5f), now)
        }
        if (strands.size > 24) strands.subList(0, strands.size - 24).clear()
        if (spikes.size > 60) spikes.subList(0, spikes.size - 60).clear()
    }

    fun onRelease(k: Key?) {
        val now = SystemClock.uptimeMillis()
        lastTouch = now
        for (p in presses) if ((k == null || p.key === k) && p.released < 0) p.released = now
        for (s in strands) if ((k == null || s.from === k) && s.released < 0) s.released = now
    }

    /** Sıvının tuşu kaplama oranı (0..1); etiket rengini tersine çevirmek için. */
    fun coverage(k: Key, now: Long): Float {
        val p = presses.lastOrNull { it.key === k } ?: return 0f
        val grow = ((now - p.t0) / 90f).coerceIn(0f, 1f)
        return if (p.released < 0) grow else (grow * (1f - (now - p.released) / 150f)).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------ çizim

    fun drawBackground(c: Canvas, theme: KbTheme, now: Long) {
        val time = (if (animating(now)) now else lastTouch) / 1000f
        stroke.style = Paint.Style.STROKE
        for (t in tendrils) {
            tmp.reset()
            val p = t.pts
            val amp = dp(10f)
            fun px(i: Int) = p[i * 2] + sin(time * t.speed + t.phase + i * 1.7f) * amp * (if (i == 0) 0f else 1f)
            fun py(i: Int) = p[i * 2 + 1] + cos(time * t.speed * 0.8f + t.phase + i * 1.3f) * amp * (if (i == 0) 0f else 1f)
            tmp.moveTo(px(0), py(0))
            val n = p.size / 2
            for (i in 1 until n) {
                val mx = (px(i - 1) + px(i)) / 2f
                val my = (py(i - 1) + py(i)) / 2f
                tmp.quadTo(px(i - 1), py(i - 1), mx, my)
            }
            tmp.lineTo(px(n - 1), py(n - 1))
            val breathe = 0.8f + 0.2f * sin(time * 1.3f + t.phase)
            stroke.color = withAlpha(theme.text, (18 * breathe).toInt()); stroke.strokeWidth = dp(11f * t.width); c.drawPath(tmp, stroke)
            stroke.color = withAlpha(theme.text, (38 * breathe).toInt()); stroke.strokeWidth = dp(5f * t.width); c.drawPath(tmp, stroke)
            stroke.color = withAlpha(theme.text, (95 * breathe).toInt()); stroke.strokeWidth = dp(1.4f * t.width); c.drawPath(tmp, stroke)
        }
    }

    /** Tuşu çizer; dönen değer etiketin ters renkle (siyah) çizilmesi gerekip gerekmediği. */
    fun drawKey(c: Canvas, k: Key, theme: KbTheme, highlighted: Boolean, now: Long): Boolean {
        val s = shapes[k] ?: return false
        fill.color = if (highlighted) theme.accent else theme.key
        c.drawPath(s.path, fill)
        val cov = coverage(k, now)
        if (cov > 0f) {
            // Dokunulan noktadan yayılan beyaz sıvı (tuş şekliyle kırpılmış)
            val p = presses.last { it.key === k }
            c.save()
            c.clipPath(s.path)
            fill.color = theme.keyPressed
            val maxR = hypot(k.right - k.left, k.bottom - k.top)
            c.drawCircle(p.x, p.y, maxR * cov, fill)
            c.restore()
        }
        if (!highlighted) {
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = dp(1.4f)
            stroke.color = withAlpha(theme.text, if (k.isFunctional) 150 else 225)
            c.drawPath(s.path, stroke)
            stroke.strokeWidth = dp(1.6f)
            stroke.color = withAlpha(theme.text, 45)
            c.drawPath(s.gloss, stroke)
        }
        return cov > 0.5f || highlighted
    }

    fun drawEffects(c: Canvas, theme: KbTheme, now: Long) {
        // İplikler: uzanır, basılıyken titrer, bırakınca incelip kopar
        val si = strands.iterator()
        while (si.hasNext()) {
            val s = si.next()
            val ext = ((now - s.t0) / 120f).coerceIn(0f, 1f)
            var thick = 1f
            if (s.released >= 0) {
                val f = (now - s.released) / 170f
                if (f >= 1f) { si.remove(); continue }
                thick = 1f - f
            }
            val ax = s.from.cx; val ay = s.from.cy
            val bx = ax + (s.to.cx - ax) * ext; val by = ay + (s.to.cy - ay) * ext
            val wob = sin(now / 60f + s.sag) * dp(2f)
            tmp.reset()
            tmp.moveTo(ax, ay)
            tmp.quadTo((ax + bx) / 2f + wob, (ay + by) / 2f + s.sag * ext + dp(6f) * ext, bx, by)
            stroke.style = Paint.Style.STROKE
            stroke.color = withAlpha(theme.text, (200 * thick).toInt())
            stroke.strokeWidth = dp(3.2f) * thick + dp(0.4f)
            c.drawPath(tmp, stroke)
            stroke.color = withAlpha(theme.bg, (230 * thick).toInt())
            stroke.strokeWidth = dp(1.4f) * thick
            c.drawPath(tmp, stroke)
            fill.color = withAlpha(theme.text, (220 * thick).toInt())
            c.drawCircle(bx, by, dp(2.4f) * thick, fill)
        }
        // Organik dikenler: sivri, kavisli, büyüyüp küçülen damla şekilleri
        val sp = spikes.iterator()
        while (sp.hasNext()) {
            val s = sp.next()
            val f = (now - s.t0) / 320f
            if (f >= 1f) { sp.remove(); continue }
            val grow = if (f < 0.35f) f / 0.35f else 1f - (f - 0.35f) / 0.65f
            val len = s.len * grow
            val r0 = keyH * 0.32f
            val ca = cos(s.angle); val sa = sin(s.angle)
            val bx = s.x + ca * r0; val by = s.y + sa * r0
            val tx = s.x + ca * (r0 + len); val ty = s.y + sa * (r0 + len)
            val w = dp(4.5f) * grow
            val curl = dp(5f) * grow
            tmp.reset()
            tmp.moveTo(bx - sa * w, by + ca * w)
            tmp.quadTo((bx + tx) / 2f - sa * (w * 0.4f + curl), (by + ty) / 2f + ca * (w * 0.4f + curl), tx, ty)
            tmp.quadTo((bx + tx) / 2f + sa * (w * 0.4f - curl), (by + ty) / 2f - ca * (w * 0.4f - curl), bx + sa * w, by - ca * w)
            tmp.close()
            fill.color = withAlpha(theme.text, (235 * (1f - f * 0.6f)).toInt())
            c.drawPath(tmp, fill)
        }
        // Damlalar: yerçekimiyle düşer, solar
        val di = drops.iterator()
        while (di.hasNext()) {
            val d = di.next()
            val age = now - d.t0
            if (age > 520) { di.remove(); continue }
            val tt = age.toFloat()
            val x = d.x + d.vx * tt
            val y = d.y + d.vy * tt + 0.5f * dp(0.0016f) * tt * tt
            fill.color = withAlpha(theme.text, (230 * (1f - tt / 520f)).toInt())
            c.drawCircle(x, y, d.r * (1f - tt / 900f), fill)
        }
        presses.removeAll { it.released >= 0 && now - it.released > 160 }
    }

    /** Glide izi: siyah çekirdekli, beyaz kenarlı simbiyot ipliği. */
    fun drawTrail(c: Canvas, path: Path, theme: KbTheme) {
        stroke.style = Paint.Style.STROKE
        stroke.color = withAlpha(theme.text, 230)
        stroke.strokeWidth = dp(8f)
        c.drawPath(path, stroke)
        stroke.color = theme.bg
        stroke.strokeWidth = dp(4f)
        c.drawPath(path, stroke)
    }

    private fun animating(now: Long) = now - lastTouch < 3500

    /** Bir sonraki kare gerekli mi (efekt varken veya dokunaçlar canlıyken). */
    fun needsFrame(now: Long) = animating(now) || presses.isNotEmpty() || strands.isNotEmpty() || spikes.isNotEmpty() || drops.isNotEmpty()
}
