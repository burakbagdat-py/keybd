package com.keybd.pro

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Klavyenin üstündeki şerit: kelime önerileri veya araç çubuğu. */
@SuppressLint("ViewConstructor")
class SuggestionStrip(private val svc: KeybdService, private val theme: KbTheme) : LinearLayout(svc) {

    private val menuBtn: TextView
    private val micBtn: TextView
    private val center = FrameLayout(svc)
    private val sugBox = LinearLayout(svc)
    private val slots = ArrayList<TextView>()
    private val dividers = ArrayList<View>()
    private val toolScroll = HorizontalScrollView(svc)
    private val toolBox = LinearLayout(svc)
    private val message = TextView(svc)
    private var words = arrayOfNulls<String>(3)
    private var toolbarOpen = false
    private val toolButtons = HashMap<String, TextView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.strip)

        menuBtn = svc.flatButton("⋯", theme, 20f)
        menuBtn.setOnClickListener { toggleToolbar() }
        addView(menuBtn, LayoutParams(dpi(44f), LayoutParams.MATCH_PARENT))

        sugBox.orientation = HORIZONTAL
        sugBox.gravity = Gravity.CENTER_VERTICAL
        // Sıra: sol, orta, sağ  → words[1], words[0], words[2]
        val order = intArrayOf(1, 0, 2)
        for ((pos, idx) in order.withIndex()) {
            if (pos > 0) {
                val d = View(svc).apply { setBackgroundColor(withAlpha(theme.stripText, 40)) }
                dividers += d
                sugBox.addView(d, LayoutParams(dpi(1f), dpi(20f)))
            }
            val tv = svc.flatButton("", theme, 16f)
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
        tool("emoji", "☺", "Emoji") { svc.showEmojiPanel() }
        tool("clip", "📋", "Pano") { svc.showClipboardPanel() }
        tool("edit", "✥", "Metin düzenleme") { svc.showEditPanel() }
        tool("font", "𝔸", "Yazı stilleri") { svc.showFontPanel() }
        tool("onehand", "◧", "Tek el modu") { svc.toggleOneHand() }
        tool("incognito", "🕶", "Gizli mod") { svc.toggleIncognito() }
        tool("theme", "◐", "Tema değiştir") { svc.cycleTheme() }
        tool("numpad", "123", "Sayı tuşları") { svc.showNumpad() }
        tool("settings", "⚙", "Ayarlar") { svc.openSettings() }
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

        addView(center, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        micBtn = svc.flatButton("🎤", theme, 18f)
        micBtn.setOnClickListener { svc.startVoice() }
        addView(micBtn, LayoutParams(dpi(44f), LayoutParams.MATCH_PARENT))
        clear()
    }

    private fun tool(id: String, icon: String, desc: String, action: () -> Unit) {
        val b = svc.flatButton(icon, theme, if (icon.length > 2) 14f else 19f)
        b.contentDescription = desc
        b.setOnClickListener { action() }
        b.setOnLongClickListener { svc.toast(desc); true }
        toolButtons[id] = b
        toolBox.addView(b, LayoutParams(dpi(46f), dpi(40f)).apply { marginEnd = dpi(2f) })
    }

    fun setToolActive(id: String, active: Boolean) {
        toolButtons[id]?.background = if (active) roundRect(withAlpha(theme.accent, 90), dp(8f)) else null
    }

    private fun toggleToolbar() {
        if (toolbarOpen && hasWords()) showWordsView() else showToolbar()
    }

    private fun hasWords() = words.any { it != null }

    fun showToolbar() {
        toolbarOpen = true
        message.visibility = GONE
        sugBox.visibility = GONE
        toolScroll.visibility = VISIBLE
        menuBtn.text = "‹"
    }

    private fun showWordsView() {
        toolbarOpen = false
        message.visibility = GONE
        toolScroll.visibility = GONE
        sugBox.visibility = VISIBLE
        menuBtn.text = "⋯"
    }

    /** words[0] = ortadaki (ana) öneri. */
    fun setWords(list: List<String?>, boldCenter: Boolean, quotedIndex: Int = -1) {
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

    /** Öneri yoksa araç çubuğunu göster. */
    fun clear() {
        words = arrayOfNulls(3)
        showToolbar()
    }

    fun showClipChip(text: String) {
        words = arrayOfNulls(3)
        words[0] = text
        for (tv in slots) {
            val idx = tv.tag as Int
            tv.text = if (idx == 0) "📋 " + text.replace('\n', ' ').take(40) else ""
            tv.typeface = Typeface.DEFAULT
            tv.setTextColor(theme.stripText)
        }
        showWordsView()
    }

    fun showMessage(text: String?) {
        if (text == null) {
            message.visibility = GONE
            if (toolbarOpen) showToolbar() else showWordsView()
            return
        }
        message.text = text
        message.visibility = VISIBLE
        sugBox.visibility = GONE
        toolScroll.visibility = GONE
    }
}
