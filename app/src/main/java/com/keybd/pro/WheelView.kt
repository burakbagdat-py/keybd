package com.keybd.pro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.random.Random

/** Dönen karar çarkı. Dokununca döner; durduğunda üstteki ok kazananı gösterir. */
@SuppressLint("ViewConstructor")
class WheelView(ctx: Context, private val theme: KbTheme) : View(ctx) {
    var options: List<String> = emptyList()
        set(v) {
            field = v
            winner = -1
            invalidate()
        }
    private var weights: List<Int> = emptyList()

    /** Seçenekler ve yüzde ağırlıkları: dilim büyüklüğü = kazanma olasılığı. */
    fun setData(o: List<String>, w: List<Int>) {
        weights = w
        options = o
    }

    private fun sweepOf(i: Int): Float {
        val n = options.size
        if (weights.size != n) return 360f / n
        val sum = weights.sum().coerceAtLeast(1)
        return 360f * weights[i] / sum
    }

    var onResult: ((String) -> Unit)? = null
    var onTick: (() -> Unit)? = null
    var winner = -1
        private set
    val spinning get() = anim?.isRunning == true

    private var angle = 0f
    private var anim: ValueAnimator? = null
    private var lastSeg = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val oval = RectF()
    private val pointer = Path()

    private val colors: IntArray = if (theme.style == KbTheme.STYLE_SYMBIOTE) intArrayOf(Color.BLACK, Color.WHITE, 0xFF5A5A5A.toInt())
    else intArrayOf(
        0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF8E24AA.toInt(), 0xFFFB8C00.toInt(), 0xFF00ACC1.toInt(), 0xFFD81B60.toInt(),
        0xFF7CB342.toInt(), 0xFF3949AB.toInt(), 0xFF6D4C41.toInt(), 0xFF00897B.toInt(),
    )

    private fun segColor(i: Int): Int {
        val n = options.size
        // Tek sayıda dilimde ilk ve son aynı renk olmasın
        val c = colors[i % colors.size]
        return if (i == n - 1 && n > 1 && n % colors.size == 1) colors[(i + 1) % colors.size] else c
    }

    private fun contrast(c: Int): Int {
        val l = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255
        return if (l > 0.6) Color.BLACK else Color.WHITE
    }

    override fun onDraw(c: Canvas) {
        val n = options.size
        val size = min(width, height).toFloat()
        val r = size / 2f - dp(8f)
        val cx = width / 2f
        val cy = height / 2f + dp(4f)
        oval.set(cx - r, cy - r, cx + r, cy + r)
        if (n == 0) return
        var acc = 0f
        for (i in 0 until n) {
            val sweep = sweepOf(i)
            val start = -90f + angle + acc
            acc += sweep
            val win = if (theme.style == KbTheme.STYLE_SYMBIOTE) 0xFFB0B0B0.toInt() else theme.accent
            val fill = if (i == winner && !spinning) win else segColor(i)
            p.style = Paint.Style.FILL
            p.color = fill
            c.drawArc(oval, start, sweep, true, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.5f)
            p.color = if (theme.style == KbTheme.STYLE_SYMBIOTE) Color.WHITE else withAlpha(Color.WHITE, 160)
            c.drawArc(oval, start, sweep, true, p)
            // Etiket: dilimin ortasından dışa doğru; sol yarıda ters görünmesin diye çevrilir
            val mid = ((start + sweep / 2f) % 360f + 360f) % 360f
            val flip = mid > 90f && mid < 270f
            c.save()
            c.rotate(if (flip) mid + 180f else mid, cx, cy)
            tp.color = contrast(fill)
            tp.textSize = dp(if (n > 8) 11f else 13f)
            tp.isFakeBoldText = true
            val avail = r - dp(30f)
            val label = TextUtils.ellipsize(options[i], tp, avail, TextUtils.TruncateAt.END).toString()
            val ty = cy - (tp.descent() + tp.ascent()) / 2f
            if (flip) {
                tp.textAlign = Paint.Align.RIGHT
                c.drawText(label, cx - dp(24f), ty, tp)
                tp.textAlign = Paint.Align.LEFT
            } else c.drawText(label, cx + dp(24f), ty, tp)
            c.restore()
        }
        // Merkez düğme
        p.style = Paint.Style.FILL
        p.color = theme.keyFn
        c.drawCircle(cx, cy, dp(20f), p)
        p.style = Paint.Style.STROKE
        p.color = theme.accent
        p.strokeWidth = dp(2f)
        c.drawCircle(cx, cy, dp(20f), p)
        tp.color = theme.text
        tp.textSize = dp(10f)
        tp.textAlign = Paint.Align.CENTER
        c.drawText("ÇEVİR", cx, cy - (tp.descent() + tp.ascent()) / 2f, tp)
        tp.textAlign = Paint.Align.LEFT
        // Üstteki ok
        pointer.reset()
        pointer.moveTo(cx - dp(11f), cy - r - dp(8f))
        pointer.lineTo(cx + dp(11f), cy - r - dp(8f))
        pointer.lineTo(cx, cy - r + dp(14f))
        pointer.close()
        p.style = Paint.Style.FILL
        p.color = theme.accent
        c.drawPath(pointer, p)
        p.style = Paint.Style.STROKE
        p.color = theme.bg
        p.strokeWidth = dp(1.5f)
        c.drawPath(pointer, p)
    }

    private fun segmentAtPointer(): Int {
        val n = options.size
        if (n == 0) return -1
        val rel = ((-angle) % 360f + 360f) % 360f
        var acc = 0f
        for (i in 0 until n) {
            acc += sweepOf(i)
            if (rel < acc) return i
        }
        return n - 1
    }

    fun spin() {
        if (options.size < 2 || spinning) return
        winner = -1
        val target = angle + 360f * (5 + Random.nextInt(3)) + Random.nextFloat() * 360f
        anim = ValueAnimator.ofFloat(angle, target).apply {
            duration = 4200
            interpolator = DecelerateInterpolator(2.4f)
            addUpdateListener {
                angle = it.animatedValue as Float
                val s = segmentAtPointer()
                if (s != lastSeg) { lastSeg = s; onTick?.invoke() }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    angle %= 360f
                    winner = segmentAtPointer()
                    invalidate()
                    options.getOrNull(winner)?.let { onResult?.invoke(it) }
                }
            })
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) spin()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
    }
}
