package com.keybd.pro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

@SuppressLint("ViewConstructor")
class KeyboardView(ctx: Context, private val svc: KeybdService) : View(ctx) {

    var kb: KeyboardLayout? = null
        set(v) {
            field = v
            requestLayout()
            layoutKeys()
            invalidate()
        }
    var theme: KbTheme = Themes.get(ctx, "dark")
    var shiftState = 0
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    var enterLabel = "↵"
    var spaceLabel = ""
    var rowHeight = dp(54f)
    var radius = dp(8f)
    var gap = dp(4f)
    var borders = true
    var showHints = true
    var preview = true
    var glideEnabled = true
    var glideTrail = true
    var spaceCursor = true
    var deleteSwipe = true
    var longPressMs = 300L
    var fontScale = 1f

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private val padV get() = dp(4f)

    // --- dokunma durumu ---
    private enum class Mode { NONE, POPUP, CURSOR, DEL_SWIPE, GLIDE, CANCELLED }

    private var mode = Mode.NONE
    private var curKey: Key? = null
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var cursorAccum = 0f
    private var repeating = false
    private var repeatCount = 0
    private var longPressFired = false
    private val glidePts = ArrayList<Pt>()
    private val trail = Path()

    val unitW: Float get() = width / (kb?.units ?: 10f)

    fun desiredHeight(): Int {
        val l = kb ?: return (rowHeight * 4 + padV * 2).toInt()
        return (l.rows.sumOf { (rowHeight * it.heightFactor).toDouble() } + padV * 2).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desiredHeight())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
        buildTendrils(w, h)
    }

    // ---------------- simbiyot teması ----------------

    private val sym get() = theme.style == KbTheme.STYLE_SYMBIOTE
    private val tendrils = ArrayList<Path>()
    private val splashes = ArrayList<FloatArray>()
    private val symPath = Path()
    private val dripPath = Path()
    private val symPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Klavyenin kenarlarından içeri uzanan organik "simbiyot" dokunaçları. */
    private fun buildTendrils(w: Int, h: Int) {
        tendrils.clear()
        if (w <= 0 || h <= 0) return
        val r = java.util.Random(7)
        repeat(11) { i ->
            val path = Path()
            val sx: Float
            val sy: Float
            when (i % 4) {
                0 -> { sx = r.nextFloat() * w; sy = 0f }
                1 -> { sx = r.nextFloat() * w; sy = h.toFloat() }
                2 -> { sx = 0f; sy = r.nextFloat() * h }
                else -> { sx = w.toFloat(); sy = r.nextFloat() * h }
            }
            path.moveTo(sx, sy)
            var x = sx
            var y = sy
            repeat(2 + r.nextInt(2)) {
                val nx = (x + (r.nextFloat() - 0.5f) * w * 0.5f).coerceIn(0f, w.toFloat())
                val ny = (y + (r.nextFloat() - 0.5f) * h * 0.9f).coerceIn(0f, h.toFloat())
                path.cubicTo(
                    x + (r.nextFloat() - 0.5f) * w * 0.3f, y + (r.nextFloat() - 0.5f) * h * 0.6f,
                    nx + (r.nextFloat() - 0.5f) * w * 0.3f, ny + (r.nextFloat() - 0.5f) * h * 0.6f,
                    nx, ny,
                )
                x = nx; y = ny
            }
            tendrils += path
        }
    }

    private fun drawTendrils(c: Canvas) {
        symPaint.style = Paint.Style.STROKE
        for ((i, t) in tendrils.withIndex()) {
            val base = if (i % 3 == 0) 1.3f else 0.8f
            symPaint.color = withAlpha(theme.text, 20); symPaint.strokeWidth = dp(10f * base); c.drawPath(t, symPaint)
            symPaint.color = withAlpha(theme.text, 40); symPaint.strokeWidth = dp(4.5f * base); c.drawPath(t, symPaint)
            symPaint.color = withAlpha(theme.text, 95); symPaint.strokeWidth = dp(1.4f * base); c.drawPath(t, symPaint)
        }
    }

    /** Organik kenarlı, bazıları "damlayan" simbiyot tuşu. */
    private fun drawSymbioteKey(c: Canvas, r: RectF, k: Key, idx: Int, pressed: Boolean) {
        symPath.reset()
        val rad = radius.coerceAtLeast(dp(6f)) * 1.3f
        symPath.addRoundRect(r, rad, rad, Path.Direction.CW)
        if (k.type == KeyType.CHAR && (idx * 7919 + 3) % 5 == 0) {
            val dr = min(r.width(), r.height()) * 0.13f
            val dx = r.left + r.width() * (0.25f + ((idx * 31) % 50) / 100f)
            dripPath.reset()
            dripPath.addCircle(dx, r.bottom + dr * 0.8f, dr, Path.Direction.CW)
            dripPath.addRect(dx - dr * 0.55f, r.bottom - dr, dx + dr * 0.55f, r.bottom + dr * 0.6f, Path.Direction.CW)
            symPath.op(dripPath, Path.Op.UNION)
        }
        symPaint.style = Paint.Style.FILL
        symPaint.color = if (pressed) theme.keyPressed else if (k.type == KeyType.ENTER || (k.type == KeyType.SHIFT && shiftState == 2)) theme.accent else theme.key
        c.drawPath(symPath, symPaint)
        if (!pressed) {
            symPaint.style = Paint.Style.STROKE
            symPaint.strokeWidth = dp(1.4f)
            symPaint.color = withAlpha(theme.text, if (k.isFunctional) 150 else 220)
            c.drawPath(symPath, symPaint)
        }
    }

    /** Tuşa basınca etrafa saçılan simbiyot dikenleri. */
    private fun drawSplashes(c: Canvas) {
        if (splashes.isEmpty()) return
        val now = android.os.SystemClock.uptimeMillis().toFloat()
        val it = splashes.iterator()
        symPaint.style = Paint.Style.STROKE
        while (it.hasNext()) {
            val s = it.next()
            val f = (now - s[2]) / 380f
            if (f >= 1f) { it.remove(); continue }
            val r0 = rowHeight * 0.3f
            val len = rowHeight * (0.25f + 0.75f * f)
            symPaint.color = withAlpha(theme.text, ((1f - f) * 220).toInt())
            symPaint.strokeWidth = dp(3.5f) * (1f - f) + dp(0.5f)
            for (i in 0 until 8) {
                val a = (i * Math.PI * 2 / 8 + s[3]).toFloat()
                val ca = kotlin.math.cos(a)
                val sa = kotlin.math.sin(a)
                c.drawLine(s[0] + ca * r0, s[1] + sa * r0, s[0] + ca * (r0 + len * (if (i % 2 == 0) 1f else 0.6f)), s[1] + sa * (r0 + len * (if (i % 2 == 0) 1f else 0.6f)), symPaint)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun layoutKeys() {
        val l = kb ?: return
        val w = width.toFloat()
        if (w <= 0f) return
        val unit = w / l.units
        var y = padV
        for (row in l.rows) {
            val h = rowHeight * row.heightFactor
            val total = row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = (w - total * unit) / 2f
            for (k in row.keys) {
                k.left = x
                k.right = x + k.width * unit
                k.top = y
                k.bottom = y + h
                x = k.right
            }
            y += h
        }
    }

    /** Glide çözücü için harf tuşlarının merkezleri. */
    fun letterCenters(): Map<Char, Pt> {
        val m = HashMap<Char, Pt>()
        kb?.rows?.forEach { r -> r.keys.forEach { k -> if (k.isLetter) m[k.label[0]] = Pt(k.cx, k.cy) } }
        return m
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val l = kb ?: return null
        var row: KeyRow? = null
        for (r in l.rows) {
            val k0 = r.keys.firstOrNull() ?: continue
            if (y < k0.bottom || r === l.rows.last()) {
                row = r
                break
            }
        }
        val keys = row?.keys ?: return null
        for (k in keys) if (x < k.right) return k
        return keys.last()
    }

    // ---------------- çizim ----------------

    override fun onDraw(canvas: Canvas) {
        if (!theme.image) canvas.drawColor(theme.bg)
        val l = kb ?: return
        val hg = gap / 2f
        val vg = gap * 0.75f
        if (sym) drawTendrils(canvas)
        var idx = 0
        for (row in l.rows) for (k in row.keys) {
            rect.set(k.left + hg, k.top + vg, k.right - hg, k.bottom - vg)
            val pressed = k === curKey && mode != Mode.GLIDE
            idx++
            if (sym) {
                drawSymbioteKey(canvas, rect, k, idx, pressed)
                drawLabel(canvas, k, pressed)
                continue
            }
            val bg = when {
                pressed -> theme.keyPressed
                k.type == KeyType.ENTER -> theme.accent
                k.type == KeyType.SHIFT && shiftState == 2 -> theme.accent
                k.isFunctional -> theme.keyFn
                else -> theme.key
            }
            if (borders || pressed || k.type == KeyType.ENTER) {
                if (borders) {
                    keyPaint.color = theme.shadow
                    rect.offset(0f, dp(1.2f))
                    canvas.drawRoundRect(rect, radius, radius, keyPaint)
                    rect.offset(0f, -dp(1.2f))
                }
                keyPaint.color = bg
                canvas.drawRoundRect(rect, radius, radius, keyPaint)
            }
            drawLabel(canvas, k, pressed)
        }
        if (sym) drawSplashes(canvas)
        if (mode == Mode.GLIDE && glideTrail && glidePts.size > 1) {
            trailPaint.color = withAlpha(theme.accent, 200)
            trailPaint.strokeWidth = dp(5f)
            trail.reset()
            val start = (glidePts.size - 60).coerceAtLeast(0)
            trail.moveTo(glidePts[start].x, glidePts[start].y)
            for (i in start + 1 until glidePts.size) trail.lineTo(glidePts[i].x, glidePts[i].y)
            canvas.drawPath(trail, trailPaint)
        }
    }

    private fun labelFor(k: Key): String = when (k.type) {
        KeyType.CHAR -> svc.applyCase(k.label, shiftState)
        KeyType.SHIFT -> when (shiftState) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
        KeyType.ENTER -> enterLabel
        KeyType.SPACE -> spaceLabel
        else -> k.label
    }

    private fun drawLabel(canvas: Canvas, k: Key, pressed: Boolean = false) {
        val label = labelFor(k)
        if (label.isEmpty()) return
        val single = label.codePointCount(0, label.length) == 1
        val size = when {
            k.type == KeyType.SPACE -> 13f
            k.type == KeyType.SHIFT || k.type == KeyType.DELETE || k.type == KeyType.ENTER -> 20f
            k.type == KeyType.LANG || k.type == KeyType.EMOJI -> 19f
            single -> 22f
            else -> 15f
        } * fontScale
        textPaint.textSize = dp(size)
        textPaint.typeface = if (k.isFunctional && !single) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.color = when {
            pressed && sym -> theme.bg
            k.type == KeyType.ENTER -> theme.accentText
            k.type == KeyType.SHIFT && shiftState == 2 -> theme.accentText
            k.type == KeyType.SPACE -> theme.hint
            else -> theme.text
        }
        val y = k.cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, k.cx, y, textPaint)
        if (showHints && k.hint.isNotEmpty()) {
            hintPaint.textSize = dp(10f) * fontScale
            hintPaint.color = theme.hint
            canvas.drawText(k.hint, k.right - gap / 2f - dp(4f), k.top + gap * 0.75f + dp(12f), hintPaint)
        }
    }

    // ---------------- dokunma ----------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> startPointer(e, 0)
            MotionEvent.ACTION_POINTER_DOWN -> {
                val k = curKey
                if (k != null) {
                    if (mode == Mode.NONE) release() else return true
                }
                startPointer(e, e.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(pointerId)
                if (idx >= 0) {
                    if (mode == Mode.GLIDE) {
                        for (h in 0 until e.historySize) addGlidePoint(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
                    }
                    move(e.getX(idx), e.getY(idx))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointerId) release()
            MotionEvent.ACTION_UP -> {
                if (e.getPointerId(e.actionIndex) == pointerId) release()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun startPointer(e: MotionEvent, i: Int) {
        val x = e.getX(i)
        val y = e.getY(i)
        val k = keyAt(x, y) ?: return
        pointerId = e.getPointerId(i)
        curKey = k
        downX = x; downY = y; lastX = x
        mode = Mode.NONE
        longPressFired = false
        repeating = false
        repeatCount = 0
        cursorAccum = 0f
        svc.keyFeedback(k)
        if (sym) {
            splashes += floatArrayOf(k.cx, k.cy, android.os.SystemClock.uptimeMillis().toFloat(), (Math.random() * 6.28).toFloat())
            postInvalidateOnAnimation()
        }
        if (preview && k.type == KeyType.CHAR) showPreview(k)
        glidePts.clear()
        if (glideEnabled && k.isLetter && kb?.isAlpha == true && svc.glideAllowed()) glidePts += Pt(x, y)
        handler.removeCallbacks(longPress)
        handler.postDelayed(longPress, if (k.type == KeyType.DELETE) 400L else longPressMs)
        invalidate()
    }

    private fun addGlidePoint(x: Float, y: Float) {
        val lp = glidePts.lastOrNull()
        if (lp == null || hypot(lp.x - x, lp.y - y) > dp(2f)) glidePts += Pt(x, y)
    }

    private fun move(x: Float, y: Float) {
        val k = curKey ?: return
        when (mode) {
            Mode.POPUP -> { popupSelect(x, y); return }
            Mode.CURSOR -> {
                val step = unitW * 0.5f
                cursorAccum += x - lastX
                lastX = x
                while (cursorAccum >= step) { svc.moveCursor(1); cursorAccum -= step }
                while (cursorAccum <= -step) { svc.moveCursor(-1); cursorAccum += step }
                return
            }
            Mode.DEL_SWIPE -> {
                val n = ((downX - x) / (unitW * 0.8f)).toInt().coerceAtLeast(0)
                svc.selectWordsBack(n)
                return
            }
            Mode.GLIDE -> { addGlidePoint(x, y); invalidate(); return }
            Mode.CANCELLED -> return
            Mode.NONE -> {}
        }
        val dx = x - downX
        val dy = y - downY
        when (k.type) {
            KeyType.SPACE -> {
                if (spaceCursor && abs(dx) > unitW * 0.6f) {
                    mode = Mode.CURSOR
                    cancelTimers()
                    lastX = x
                    cursorAccum = dx
                    svc.beginCursorMove()
                    invalidate()
                } else if (dy > rowHeight * 1.3f && abs(dx) < unitW) {
                    mode = Mode.CANCELLED
                    cancelTimers()
                    svc.requestHideSelf(0)
                }
            }
            KeyType.DELETE -> if (deleteSwipe && !repeating && -dx > unitW * 0.7f) {
                mode = Mode.DEL_SWIPE
                cancelTimers()
                svc.beginDeleteSwipe()
                move(x, y)
            }
            else -> if (glidePts.isNotEmpty() && hypot(dx, dy) > unitW * 0.75f) {
                mode = Mode.GLIDE
                cancelTimers()
                hidePreview()
                addGlidePoint(x, y)
                invalidate()
            }
        }
    }

    private fun release() {
        cancelTimers()
        hidePreview()
        val k = curKey
        val m = mode
        curKey = null
        mode = Mode.NONE
        pointerId = -1
        if (k != null) {
            when (m) {
                Mode.POPUP -> {
                    val s = popupSelected()
                    dismissPopup()
                    if (s != null) svc.onText(s)
                }
                Mode.GLIDE -> svc.onGlide(ArrayList(glidePts))
                Mode.DEL_SWIPE -> svc.endDeleteSwipe()
                Mode.CURSOR -> svc.endCursorMove()
                Mode.CANCELLED -> {}
                Mode.NONE -> if (!longPressFired && !(k.type == KeyType.DELETE && repeating)) svc.onKey(k)
            }
        }
        glidePts.clear()
        invalidate()
    }

    private fun cancel() {
        cancelTimers()
        hidePreview()
        dismissPopup()
        if (mode == Mode.DEL_SWIPE) svc.endDeleteSwipe()
        curKey = null
        mode = Mode.NONE
        pointerId = -1
        glidePts.clear()
        invalidate()
    }

    private fun cancelTimers() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
    }

    private val longPress = Runnable {
        val k = curKey ?: return@Runnable
        if (mode != Mode.NONE) return@Runnable
        when {
            k.type == KeyType.DELETE -> {
                repeating = true
                svc.deleteRepeat(repeatCount++)
                handler.postDelayed(repeat, 60)
            }
            k.type == KeyType.CHAR && k.popup.isNotEmpty() -> {
                longPressFired = true
                hidePreview()
                showPopup(k)
                mode = Mode.POPUP
                glidePts.clear()
                svc.keyFeedback(null)
            }
            k.type == KeyType.SPACE || k.type == KeyType.LANG -> {
                longPressFired = true
                svc.showImePicker()
            }
            k.type == KeyType.SHIFT -> {
                longPressFired = true
                svc.setCapsLock()
            }
            k.type == KeyType.EMOJI || k.type == KeyType.TO_SYMBOLS -> {
                longPressFired = true
                svc.openSettings()
            }
            else -> {}
        }
    }

    private val repeat = object : Runnable {
        override fun run() {
            if (curKey?.type != KeyType.DELETE) return
            svc.deleteRepeat(repeatCount++)
            handler.postDelayed(this, if (repeatCount > 12) 35L else 60L)
        }
    }

    // ---------------- önizleme balonu ----------------

    private var previewWin: PopupWindow? = null
    private var previewText: TextView? = null
    private val loc = IntArray(2)

    private fun showPreview(k: Key) {
        val label = svc.applyCase(k.label, shiftState)
        val w = (k.right - k.left).coerceAtLeast(dp(40f)) * 1.15f
        val h = rowHeight * 1.35f
        val tv = previewText ?: TextView(context).apply {
            gravity = Gravity.CENTER
            previewText = this
        }
        tv.text = label
        tv.setTextColor(if (sym) theme.bg else theme.text)
        tv.textSize = 28f * fontScale
        tv.background = roundRect(theme.keyPressed, radius * 1.4f)
        val win = previewWin ?: PopupWindow(tv).apply {
            isClippingEnabled = false
            isTouchable = false
            animationStyle = 0
            previewWin = this
        }
        getLocationInWindow(loc)
        val px = (loc[0] + k.cx - w / 2f).toInt()
        val py = (loc[1] + k.top - h + gap).toInt()
        if (win.isShowing) win.update(px, py, w.toInt(), h.toInt())
        else {
            win.width = w.toInt()
            win.height = h.toInt()
            try {
                win.showAtLocation(this, Gravity.NO_GRAVITY, px, py)
            } catch (_: Exception) {
            }
        }
    }

    private fun hidePreview() {
        try {
            previewWin?.dismiss()
        } catch (_: Exception) {
        }
    }

    // ---------------- uzun basma menüsü ----------------

    private var popupWin: PopupWindow? = null
    private var mini: MiniKeys? = null
    private var popX = 0f
    private var popY = 0f

    private inner class MiniKeys(val items: List<String>, val cols: Int, val cellW: Float, val cellH: Float) : View(context) {
        val rows = ceil(items.size / cols.toFloat()).toInt()
        var sel = 0
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            p.color = theme.keyFn
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(r, radius * 1.3f, radius * 1.3f, p)
            for (i in items.indices) {
                val col = i % cols
                val row = rows - 1 - i / cols
                r.set(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH)
                if (i == sel) {
                    p.color = theme.accent
                    r.inset(dp(2f), dp(2f))
                    c.drawRoundRect(r, radius, radius, p)
                    r.inset(-dp(2f), -dp(2f))
                }
                p.color = if (i == sel) theme.accentText else theme.text
                val s = items[i]
                p.textSize = dp(if (s.length > 2) 15f else 22f) * fontScale
                c.drawText(s, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2f, p)
            }
        }
    }

    private fun showPopup(k: Key) {
        val items = k.popup.map { svc.applyCase(it, shiftState) }.distinct()
        if (items.isEmpty()) return
        val wide = items.any { it.length > 2 }
        val cellW = (k.right - k.left).coerceAtMost(unitW) * (if (wide) 1.9f else 1f)
        val cellH = rowHeight * 0.95f
        val maxCols = (width / cellW).toInt().coerceAtLeast(1)
        val cols = min(items.size, min(maxCols, if (wide) 3 else 8))
        val m = MiniKeys(items, cols, cellW, cellH)
        val w = cols * cellW
        val h = m.rows * cellH
        popX = (k.cx - cellW / 2f).coerceIn(0f, (width - w).coerceAtLeast(0f))
        popY = k.top - h - dp(2f)
        getLocationInWindow(loc)
        val win = PopupWindow(m, w.toInt(), h.toInt()).apply {
            isClippingEnabled = false
            isTouchable = false
            animationStyle = 0
        }
        try {
            win.showAtLocation(this, Gravity.NO_GRAVITY, (loc[0] + popX).toInt(), (loc[1] + popY).toInt())
        } catch (_: Exception) {
            return
        }
        popupWin = win
        mini = m
        popupSelect(downX, downY)
    }

    private fun popupSelect(x: Float, y: Float) {
        val m = mini ?: return
        val col = ((x - popX) / m.cellW).toInt().coerceIn(0, m.cols - 1)
        val visualRow = ((y - popY) / m.cellH).toInt().coerceIn(0, m.rows - 1)
        val idx = ((m.rows - 1 - visualRow) * m.cols + col).coerceIn(0, m.items.size - 1)
        if (idx != m.sel) {
            m.sel = idx
            m.invalidate()
        }
    }

    private fun popupSelected(): String? = mini?.let { it.items.getOrNull(it.sel) }

    private fun dismissPopup() {
        try {
            popupWin?.dismiss()
        } catch (_: Exception) {
        }
        popupWin = null
        mini = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancel()
    }
}
