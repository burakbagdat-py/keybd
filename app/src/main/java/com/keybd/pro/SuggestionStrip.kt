package com.keybd.pro

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Klavyenin üstündeki şerit. Modlar: kelime önerileri, araç çubuğu, yazarak giriş
 * (emoji arama / arşiv / çark seçeneği), boyut ayarı ve daraltılmış tutamaç.
 */
@SuppressLint("ViewConstructor")
class SuggestionStrip(
    private val svc: KeybdService,
    private val theme: KbTheme,
    /** Araçlar yan menüdeyse üst şeritte araç çubuğu gösterilmez. */
    private val sideTools: Boolean,
    heightDp: Int,
) : LinearLayout(svc) {

    private val h = heightDp.toFloat()
    private val menuBtn: TextView
    private val micBtn: ImageView
    private val center = FrameLayout(svc)
    private val sugBox = LinearLayout(svc)
    private val slots = ArrayList<TextView>()
    private val toolScroll = HorizontalScrollView(svc)
    private val toolBox = LinearLayout(svc)
    private val message = TextView(svc)
    private var words = arrayOfNulls<String>(3)
    private var toolbarOpen = false
    private val toolButtons = HashMap<String, View>()
    private val restoreBtn: TextView
    private val captureBar = LinearLayout(svc)
    private val captureText = TextView(svc)
    private val captureOk: TextView
    private val resizeBar = LinearLayout(svc)
    private val handle = TextView(svc)

    /** Yazarak giriş veya boyut modundayken öneriler gösterilmez. */
    var busy = false
        private set
    var collapsed = false
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.strip)
        val iconSize = (h * 0.43f).coerceIn(14f, 24f)

        menuBtn = svc.flatButton("⋯", theme, iconSize)
        menuBtn.setOnClickListener { toggleToolbar() }
        menuBtn.setOnLongClickListener { svc.setStripCollapsed(true); true }
        addView(menuBtn, LayoutParams(dpi(h), LayoutParams.MATCH_PARENT))
        if (sideTools) menuBtn.visibility = GONE

        // Yanlışlıkla silineni geri getir
        restoreBtn = svc.flatButton("↶", theme, 15f, withAlpha(theme.accent, 70), dp(16f))
        restoreBtn.setTextColor(theme.stripText)
        restoreBtn.maxLines = 1
        restoreBtn.ellipsize = TextUtils.TruncateAt.END
        restoreBtn.setPadding(dpi(10f), 0, dpi(10f), 0)
        restoreBtn.contentDescription = "Silineni geri getir"
        restoreBtn.setOnClickListener { svc.restoreDeleted() }
        restoreBtn.visibility = GONE
        addView(restoreBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(h * 0.76f)).apply { marginStart = dpi(4f); marginEnd = dpi(4f) })

        sugBox.orientation = HORIZONTAL
        sugBox.gravity = Gravity.CENTER_VERTICAL
        // Sıra: sol, orta, sağ  → words[1], words[0], words[2]
        for ((pos, idx) in intArrayOf(1, 0, 2).withIndex()) {
            if (pos > 0) sugBox.addView(View(svc).apply { setBackgroundColor(withAlpha(theme.stripText, 40)) }, LayoutParams(dpi(1f), dpi(h * 0.45f)))
            val tv = svc.flatButton("", theme, (h * 0.36f).coerceIn(13f, 19f))
            tv.maxLines = 1
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.setPadding(dpi(4f), 0, dpi(4f), 0)
            tv.tag = idx
            tv.setOnClickListener { words[idx]?.let { w -> svc.pickSuggestion(w) } }
            tv.setOnLongClickListener { words[idx]?.let { w -> svc.forgetSuggestion(w) }; true }
            slots += tv
            sugBox.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        center.addView(sugBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        toolBox.orientation = HORIZONTAL
        toolBox.gravity = Gravity.CENTER_VERTICAL
        if (!sideTools) for (t in Tools.all) {
            val b = toolButton(svc, theme, t, h)
            toolButtons[t.id] = b
            toolBox.addView(b, LayoutParams(dpi(h * 1.05f), dpi(h * 0.9f)).apply { marginEnd = dpi(2f) })
        }
        toolScroll.isHorizontalScrollBarEnabled = false
        toolScroll.addView(toolBox)
        toolScroll.visibility = GONE
        center.addView(toolScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        message.setTextColor(theme.stripText)
        message.textSize = 15f
        message.gravity = Gravity.CENTER
        message.maxLines = 1
        message.ellipsize = TextUtils.TruncateAt.START
        message.visibility = GONE
        message.setOnClickListener { svc.stopVoice() }
        center.addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Yazarak giriş çubuğu: [metin▏] [✓ Ekle] [✕]
        captureBar.orientation = HORIZONTAL
        captureBar.gravity = Gravity.CENTER_VERTICAL
        captureBar.visibility = GONE
        captureText.setTextColor(theme.stripText)
        captureText.textSize = 15f
        captureText.maxLines = 1
        captureText.ellipsize = TextUtils.TruncateAt.START
        captureText.setPadding(dpi(12f), 0, dpi(8f), 0)
        captureText.background = roundRect(withAlpha(theme.stripText, 28), dp(16f))
        captureText.gravity = Gravity.CENTER_VERTICAL
        captureBar.addView(captureText, LayoutParams(0, dpi(h * 0.74f), 1f).apply { marginStart = dpi(4f) })
        captureOk = svc.flatButton("✓", theme, 15f, theme.accent, dp(16f))
        captureOk.setTextColor(theme.accentText)
        captureOk.setPadding(dpi(12f), 0, dpi(12f), 0)
        captureOk.setOnClickListener { svc.confirmCapture() }
        captureBar.addView(captureOk, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(h * 0.74f)).apply { marginStart = dpi(6f) })
        val close = svc.flatButton("✕", theme, 16f)
        close.setOnClickListener { svc.cancelCapture() }
        captureBar.addView(close, LayoutParams(dpi(h * 0.85f), LayoutParams.MATCH_PARENT))
        center.addView(captureBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Boyut ayarı: klavye görünür kalırken canlı değiştirilir
        resizeBar.orientation = HORIZONTAL
        resizeBar.gravity = Gravity.CENTER_VERTICAL
        resizeBar.visibility = GONE
        fun rb(label: String, f: () -> Unit) = svc.flatButton(label, theme, 13f, withAlpha(theme.stripText, 25), dp(10f)).apply {
            setOnClickListener { f() }
            resizeBar.addView(this, LayoutParams(0, dpi(h * 0.76f), 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
        }
        rb("↕−") { svc.resizeStep(Prefs.HEIGHT, -5) }
        rb("↕+") { svc.resizeStep(Prefs.HEIGHT, 5) }
        rb("↔−") { svc.resizeStep(Prefs.KB_WIDTH, -5) }
        rb("↔+") { svc.resizeStep(Prefs.KB_WIDTH, 5) }
        rb("A−") { svc.resizeStep(Prefs.FONT_SCALE, -10) }
        rb("A+") { svc.resizeStep(Prefs.FONT_SCALE, 10) }
        rb("↺") { svc.resizeReset() }
        rb("✓") { svc.stopResize() }
        center.addView(resizeBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        addView(center, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        micBtn = iconButton(svc, theme, R.drawable.ic_mic, h)
        micBtn.contentDescription = "Sesle yaz"
        micBtn.setOnClickListener { svc.startVoice() }
        addView(micBtn, LayoutParams(dpi(h), LayoutParams.MATCH_PARENT))

        // Daraltılmış hali: ince tutamaç
        handle.text = "▴"
        handle.gravity = Gravity.CENTER
        handle.setTextColor(theme.hint)
        handle.textSize = 12f
        handle.visibility = GONE
        handle.setOnClickListener { svc.setStripCollapsed(false) }
        addView(handle, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        clear()
    }

    fun setToolActive(id: String, active: Boolean) {
        toolButtons[id]?.background = if (active) roundRect(withAlpha(theme.accent, 90), dp(8f)) else null
    }

    private fun toggleToolbar() {
        if (toolbarOpen && hasWords()) showWordsView() else showToolbar()
    }

    private fun hasWords() = words.any { it != null }

    private fun hideAllCenter() {
        sugBox.visibility = GONE
        toolScroll.visibility = GONE
        message.visibility = GONE
        captureBar.visibility = GONE
        resizeBar.visibility = GONE
    }

    fun showToolbar() {
        if (busy || collapsed) return
        if (sideTools) { showWordsView(); return }
        toolbarOpen = true
        hideAllCenter()
        toolScroll.visibility = VISIBLE
        menuBtn.text = "‹"
    }

    private fun showWordsView() {
        if (busy || collapsed) return
        toolbarOpen = false
        hideAllCenter()
        sugBox.visibility = VISIBLE
        menuBtn.text = "⋯"
    }

    /** words[0] = ortadaki (ana) öneri. */
    fun setWords(list: List<String?>, boldCenter: Boolean, quotedIndex: Int = -1) {
        if (busy) return
        words = arrayOfNulls(3)
        for (i in 0 until minOf(3, list.size)) words[i] = list[i]
        if (!hasWords()) {
            clear()
            return
        }
        for (tv in slots) {
            val idx = tv.tag as Int
            val w = words[idx]
            tv.text = when {
                w == null -> ""
                idx == quotedIndex -> "“$w”"
                else -> w
            }
            tv.typeface = if (idx == 0 && boldCenter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.setTextColor(if (idx == 0 && boldCenter) theme.accent else theme.stripText)
        }
        showWordsView()
    }

    /** Öneri yoksa araç çubuğunu göster (yan menü kullanılıyorsa boş öneri alanı). */
    fun clear() {
        words = arrayOfNulls(3)
        if (sideTools) {
            for (tv in slots) tv.text = ""
            showWordsView()
        } else showToolbar()
    }

    /** Ortada tek bir eylem çipi (pano, hesap sonucu, çark…). Dokununca [value] seçilir. */
    fun showChip(label: String, value: String) {
        if (busy) return
        words = arrayOfNulls(3)
        words[0] = value
        for (tv in slots) {
            val idx = tv.tag as Int
            tv.text = if (idx == 0) label.replace('\n', ' ').take(44) else ""
            tv.typeface = if (idx == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.setTextColor(if (idx == 0) theme.accent else theme.stripText)
        }
        showWordsView()
    }

    /** Silinen metin varsa şeridin solunda "↶ metin" düğmesini gösterir. */
    fun setRestore(text: String?) {
        if (text.isNullOrEmpty() || collapsed) {
            restoreBtn.visibility = GONE
            return
        }
        val t = text.replace('\n', ' ').trim()
        restoreBtn.text = "↶ " + if (t.length > 12) t.take(11) + "…" else t
        restoreBtn.visibility = VISIBLE
    }

    /** Yazarak giriş modu (emoji arama, arşive ekleme, çarka seçenek ekleme). */
    fun showCapture(prompt: String, text: String, okLabel: String) {
        busy = true
        hideAllCenter()
        captureBar.visibility = VISIBLE
        menuBtn.visibility = GONE
        micBtn.visibility = GONE
        restoreBtn.visibility = GONE
        captureText.text = if (text.isEmpty()) prompt else "$text▏"
        captureText.setTextColor(if (text.isEmpty()) theme.hint else theme.stripText)
        captureOk.text = okLabel
    }

    fun showResize() {
        busy = true
        hideAllCenter()
        resizeBar.visibility = VISIBLE
        menuBtn.visibility = GONE
        micBtn.visibility = GONE
        restoreBtn.visibility = GONE
    }

    /** Yazarak giriş / boyut modundan normal şeride dön. */
    fun endBusy() {
        busy = false
        if (!sideTools) menuBtn.visibility = VISIBLE
        micBtn.visibility = VISIBLE
        clear()
    }

    fun setCollapsed(c: Boolean) {
        collapsed = c
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            v.visibility = when {
                v === handle -> if (c) VISIBLE else GONE
                c -> GONE
                v === menuBtn -> if (sideTools) GONE else VISIBLE
                v === restoreBtn -> GONE
                else -> VISIBLE
            }
        }
        if (!c) clear()
    }

    fun showMessage(text: String?) {
        if (text == null) {
            message.visibility = GONE
            if (toolbarOpen) showToolbar() else showWordsView()
            return
        }
        hideAllCenter()
        message.text = text
        message.visibility = VISIBLE
    }

    companion object {
        fun toolButton(svc: KeybdService, theme: KbTheme, t: Tools.Tool, h: Float): TextView {
            val b = svc.flatButton(t.icon, theme, if (t.icon.length > 2) (h * 0.32f).coerceIn(11f, 16f) else (h * 0.43f).coerceIn(14f, 24f))
            b.contentDescription = t.desc
            b.setOnClickListener { t.action(svc) }
            b.setOnLongClickListener { svc.toast(t.desc); true }
            return b
        }

        /** Temaya göre renklendirilmiş vektör simgeli düğme (emoji değil). */
        fun iconButton(svc: KeybdService, theme: KbTheme, res: Int, h: Float): ImageView = ImageView(svc).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(theme.stripText)
            scaleType = ImageView.ScaleType.CENTER
            val pad = svc.dpi(h * 0.24f)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            background = RippleDrawable(ColorStateList.valueOf(withAlpha(theme.stripText, 50)), null, roundRect(android.graphics.Color.WHITE, svc.dp(8f)))
        }
    }
}
