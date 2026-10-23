package com.keybd.pro

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Klavye alanında açılan paneller için ortak yardımcılar. */
object Panels {

    private fun bottomBar(svc: KeybdService, t: KbTheme, middle: View? = null): LinearLayout {
        val bar = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(svc.dpi(4f), svc.dpi(2f), svc.dpi(4f), svc.dpi(4f))
        }
        val abc = svc.flatButton("ABC", t, 15f, t.keyFn).apply { typeface = Typeface.DEFAULT_BOLD }
        abc.setOnClickListener { svc.closePanel() }
        bar.addView(abc, LinearLayout.LayoutParams(svc.dpi(64f), svc.dpi(42f)))
        if (middle != null) bar.addView(middle, LinearLayout.LayoutParams(0, svc.dpi(42f), 1f).apply {
            marginStart = svc.dpi(6f); marginEnd = svc.dpi(6f)
        }) else bar.addView(View(svc), LinearLayout.LayoutParams(0, 1, 1f))
        val del = repeatButton(svc, t, "⌫") { svc.deleteRepeat(0) }
        bar.addView(del, LinearLayout.LayoutParams(svc.dpi(64f), svc.dpi(42f)))
        return bar
    }

    @SuppressLint("ClickableViewAccessibility")
    fun repeatButton(svc: KeybdService, t: KbTheme, label: String, action: () -> Unit): TextView {
        val b = svc.flatButton(label, t, 18f, t.keyFn)
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                action(); h.postDelayed(this, 60)
            }
        }
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    svc.keyFeedback(null)
                    action()
                    h.postDelayed(r, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    h.removeCallbacks(r)
                }
            }
            true
        }
        return b
    }

    private fun root(svc: KeybdService, t: KbTheme) = LinearLayout(svc).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(t.bg)
        isClickable = true
    }

    // ------------------------------------------------------------- Emoji

    fun emoji(svc: KeybdService, t: KbTheme, results: List<String>? = null, query: String = ""): View {
        val root = root(svc, t)
        val tabs = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(svc).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        root.addView(tabScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(42f)))

        // Ten rengi seçimi (emojiye uzun basınca)
        val toneBar = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundRect(t.keyFn, svc.dp(12f))
            visibility = View.GONE
        }
        root.addView(toneBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(48f)).apply {
            setMargins(svc.dpi(6f), svc.dpi(2f), svc.dpi(6f), svc.dpi(2f))
        })

        val grid = GridView(svc).apply {
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = 0
            horizontalSpacing = 0
            isVerticalScrollBarEnabled = false
            selector = roundRect(withAlpha(t.text, 30), svc.dp(8f))
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(svc, t, svc.flatButton("boşluk", t, 13f, t.key).apply {
            setTextColor(t.hint)
            setOnClickListener { svc.onText(" ") }
        }))

        val search = svc.flatButton("🔍", t, 18f, withAlpha(t.accent, 60))
        search.contentDescription = "Emoji ara"
        search.setOnClickListener { svc.startEmojiSearch() }
        tabs.addView(search, LinearLayout.LayoutParams(svc.dpi(46f), svc.dpi(40f)))
        val cats = ArrayList<EmojiData.Category>()
        if (results != null) cats += EmojiData.Category("🔎", "“$query” sonuçları", results)
        cats += EmojiData.Category("🕘", "Son kullanılanlar", svc.prefs.recentEmoji)
        cats += EmojiData.categories(svc)
        val tones = EmojiData.tones(svc)
        val tabViews = ArrayList<TextView>()

        fun showTones(e: String): Boolean {
            val vs = tones[e] ?: tones[e.replace("\uFE0F", "")] ?: return false
            toneBar.removeAllViews()
            for (v in listOf(e) + vs) {
                val b = svc.flatButton(v, t, 26f)
                b.setOnClickListener { svc.commitEmoji(v); toneBar.visibility = View.GONE }
                toneBar.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
            val x = svc.flatButton("✕", t, 16f)
            x.setOnClickListener { toneBar.visibility = View.GONE }
            toneBar.addView(x, LinearLayout.LayoutParams(svc.dpi(40f), ViewGroup.LayoutParams.MATCH_PARENT))
            toneBar.visibility = View.VISIBLE
            svc.keyFeedback(null)
            return true
        }

        fun select(i: Int) {
            val c = cats[i]
            toneBar.visibility = View.GONE
            val kaomoji = c.items.any { it.length > 4 && !isEmoji(it) }
            grid.numColumns = if (kaomoji) 3 else GridView.AUTO_FIT
            grid.columnWidth = svc.dpi(if (kaomoji) 110f else 44f)
            val items = when {
                c.icon == "🕘" && c.items.isEmpty() -> listOf("😀", "😂", "❤️", "👍", "🙏", "😊", "😍", "🔥", "😭", "🥰")
                c.icon == "🔎" && c.items.isEmpty() -> listOf("🤷")
                else -> c.items
            }
            grid.adapter = object : BaseAdapter() {
                override fun getCount() = items.size
                override fun getItem(p: Int) = items[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                    val tv = (cv as? TextView) ?: TextView(svc).apply {
                        gravity = Gravity.CENTER
                        setTextColor(t.text)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    tv.text = items[p]
                    tv.textSize = if (kaomoji) 14f else 26f
                    tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(if (kaomoji) 46f else 48f))
                    return tv
                }
            }
            grid.setOnItemClickListener { _, _, p, _ -> svc.commitEmoji(items[p]) }
            grid.setOnItemLongClickListener { _, _, p, _ -> showTones(items[p]) }
            tabViews.forEachIndexed { j, tv -> tv.background = if (j == i) roundRect(withAlpha(t.accent, 80), svc.dp(8f)) else null }
        }

        cats.forEachIndexed { i, c ->
            val tv = svc.flatButton(c.icon, t, if (c.icon.length > 3) 11f else 20f)
            tv.contentDescription = c.name
            tv.setOnClickListener { select(i) }
            tabViews += tv
            tabs.addView(tv, LinearLayout.LayoutParams(svc.dpi(if (c.icon.length > 3) 58f else 46f), svc.dpi(40f)))
        }
        select(if (results != null || svc.prefs.recentEmoji.isNotEmpty()) 0 else 1)
        return root
    }

    // ------------------------------------------------------------- Arşiv

    fun archive(svc: KeybdService, t: KbTheme, archive: Archive): View {
        val root = root(svc, t)
        val actions = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(svc.dpi(6f), svc.dpi(2f), svc.dpi(6f), 0)
        }
        actions.addView(TextView(svc).apply {
            text = "🗂 Arşiv"
            setTextColor(t.text)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(svc.dpi(6f), 0, svc.dpi(8f), 0)
        })
        val list = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(svc.dpi(6f), 0, svc.dpi(6f), 0) }
        lateinit var fill: () -> Unit
        fun act(label: String, f: () -> Unit) {
            val b = svc.flatButton(label, t, 13f, t.keyFn, svc.dp(14f))
            b.setOnClickListener { f() }
            actions.addView(b, LinearLayout.LayoutParams(0, svc.dpi(34f), 1f).apply { setMargins(svc.dpi(3f), 0, svc.dpi(3f), 0) })
        }
        act("+ Seçili") { if (svc.archiveSelection()) fill() }
        act("+ Pano") { if (svc.archiveClipboard()) fill() }
        act("✎ Yaz") { svc.startArchiveTyping() }
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(42f)))

        fill = {
            list.removeAllViews()
            if (archive.items.isEmpty()) {
                list.addView(TextView(svc).apply {
                    text = "Arşiv boş.\nSık kullandığın metinleri (adres, IBAN, hazır cevaplar…) buraya ekle:\nseçili metni, panodakini ya da ✎ ile yazarak."
                    setTextColor(t.hint)
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setPadding(0, svc.dpi(18f), 0, 0)
                })
            }
            for (item in archive.items.toList()) {
                val row = LinearLayout(svc).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundRect(t.key, svc.dp(10f))
                }
                val txt = svc.flatButton(item.text.replace('\n', ' '), t, 14f).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(t.text)
                    setPadding(svc.dpi(10f), svc.dpi(8f), svc.dpi(4f), svc.dpi(8f))
                    setOnClickListener { svc.pasteText(item.text) }
                }
                row.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val up = svc.flatButton("▲", t, 13f).apply { setOnClickListener { archive.moveUp(item); fill() } }
                row.addView(up, LinearLayout.LayoutParams(svc.dpi(36f), svc.dpi(40f)))
                val del = svc.flatButton("✕", t, 16f).apply { setOnClickListener { archive.remove(item); fill() } }
                row.addView(del, LinearLayout.LayoutParams(svc.dpi(40f), svc.dpi(40f)))
                list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = svc.dpi(5f)
                })
            }
        }
        root.addView(ScrollView(svc).apply { addView(list); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(svc, t))
        fill()
        return root
    }

    // ------------------------------------------------------------- Pano

    fun clipboard(svc: KeybdService, t: KbTheme, clip: ClipHistory): View {
        val root = root(svc, t)
        val header = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(svc.dpi(12f), 0, svc.dpi(6f), 0)
        }
        header.addView(TextView(svc).apply {
            text = if (svc.prefs.clipHistory) "Pano geçmişi" else "Pano geçmişi kapalı (Ayarlar)"
            setTextColor(t.text)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val list = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(svc.dpi(6f), 0, svc.dpi(6f), 0) }

        fun fill() {
            list.removeAllViews()
            val items = clip.sorted()
            if (items.isEmpty()) {
                list.addView(TextView(svc).apply {
                    text = "Henüz kopyalanmış bir şey yok.\nKopyaladığınız metinler burada görünür."
                    setTextColor(t.hint)
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setPadding(0, svc.dpi(30f), 0, 0)
                })
            }
            for (item in items) {
                val row = LinearLayout(svc).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundRect(t.key, svc.dp(10f))
                }
                val txt = svc.flatButton(item.text.replace('\n', ' '), t, 14f).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(t.text)
                    setPadding(svc.dpi(10f), svc.dpi(8f), svc.dpi(4f), svc.dpi(8f))
                    setOnClickListener { svc.pasteText(item.text) }
                }
                row.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val arch = svc.flatButton("🗂", t, 15f).apply {
                    contentDescription = "Arşive ekle"
                    setOnClickListener { svc.addToArchive(item.text) }
                }
                row.addView(arch, LinearLayout.LayoutParams(svc.dpi(38f), svc.dpi(40f)))
                val pin = svc.flatButton(if (item.pinned) "📌" else "📍", t, 16f).apply {
                    alpha = if (item.pinned) 1f else 0.45f
                    setOnClickListener { clip.togglePin(item); fill() }
                }
                row.addView(pin, LinearLayout.LayoutParams(svc.dpi(40f), svc.dpi(40f)))
                val del = svc.flatButton("✕", t, 16f).apply { setOnClickListener { clip.remove(item); fill() } }
                row.addView(del, LinearLayout.LayoutParams(svc.dpi(40f), svc.dpi(40f)))
                list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = svc.dpi(5f)
                })
            }
        }

        val clear = svc.flatButton("Temizle", t, 13f).apply { setOnClickListener { clip.clearUnpinned(); fill() } }
        header.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, svc.dpi(38f)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(42f)))
        val scroll = ScrollView(svc).apply { addView(list); isVerticalScrollBarEnabled = false }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(svc, t))
        fill()
        return root
    }

    // ------------------------------------------------------------- Metin düzenleme

    fun edit(svc: KeybdService, t: KbTheme): View {
        val root = root(svc, t)
        var selecting = false
        val grid = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(svc.dpi(4f), svc.dpi(4f), svc.dpi(4f), 0)
        }
        lateinit var selBtn: TextView
        fun row(vararg views: Pair<View, Float>) {
            val r = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
            for ((v, w) in views) r.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w).apply {
                setMargins(svc.dpi(3f), svc.dpi(3f), svc.dpi(3f), svc.dpi(3f))
            })
            grid.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        fun arrow(label: String, code: Int) = repeatButton(svc, t, label) { svc.sendArrow(code, selecting) }.apply {
            background = roundRect(t.key, svc.dp(10f)); textSize = 22f
        }
        fun act(label: String, f: () -> Unit) = svc.flatButton(label, t, 14f, t.keyFn, svc.dp(10f)).apply { setOnClickListener { f() } }

        selBtn = act("Seç") {
            selecting = !selecting
            selBtn.background = roundRect(if (selecting) t.accent else t.keyFn, svc.dp(10f))
            selBtn.setTextColor(if (selecting) t.accentText else t.stripText)
        }
        val up = arrow("▲", android.view.KeyEvent.KEYCODE_DPAD_UP)
        val down = arrow("▼", android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        val left = arrow("◀", android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        val right = arrow("▶", android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        row(act("Baş") { svc.sendArrow(android.view.KeyEvent.KEYCODE_MOVE_HOME, selecting) } to 1f, up to 1.2f,
            act("Son") { svc.sendArrow(android.view.KeyEvent.KEYCODE_MOVE_END, selecting) } to 1f, act("Tümünü seç") { svc.contextAction(android.R.id.selectAll) } to 1.4f)
        row(left to 1f, selBtn to 1.2f, right to 1f, act("Kopyala") { svc.contextAction(android.R.id.copy) } to 1.4f)
        row(act("Geri al") { svc.undoRedo(false) } to 1f, down to 1.2f, act("Yinele") { svc.undoRedo(true) } to 1f, act("Kes") { svc.contextAction(android.R.id.cut) } to 1.4f)
        row(act("Kelime sil") { svc.deleteWordBack() } to 1f, act("Satır başı") { svc.onText("\n") } to 1.2f,
            act("İleri sil") { svc.sendArrow(android.view.KeyEvent.KEYCODE_FORWARD_DEL, false) } to 1f, act("Yapıştır") { svc.contextAction(android.R.id.paste) } to 1.4f)
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(svc, t))
        return root
    }

    // ------------------------------------------------------------- Yazı stilleri

    fun fonts(svc: KeybdService, t: KbTheme): View {
        val root = root(svc, t)
        root.addView(TextView(svc).apply {
            text = "Yazı stili seçin (önerilere ve otomatik düzeltmeye kapalıdır)"
            setTextColor(t.hint)
            textSize = 13f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(30f)))
        val list = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(svc.dpi(6f), 0, svc.dpi(6f), 0) }
        var r: LinearLayout? = null
        FontStyles.names.forEachIndexed { i, name ->
            if (i % 2 == 0) {
                r = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
                list.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(46f)))
            }
            val sample = if (i == 0) "Normal yazı" else FontStyles.apply(i, if (i == 12) "keybd yazı" else "Keybd Abc")
            val active = svc.fontStyle == i
            val b = svc.flatButton(sample, t, 16f, if (active) t.accent else t.key, svc.dp(10f)).apply {
                if (active) setTextColor(t.accentText) else setTextColor(t.text)
                contentDescription = name
                maxLines = 1
                setOnClickListener { svc.setFontStyle(i) }
            }
            r!!.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(svc.dpi(3f), svc.dpi(3f), svc.dpi(3f), svc.dpi(3f))
            })
        }
        root.addView(ScrollView(svc).apply { addView(list); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(svc, t))
        return root
    }

    // ------------------------------------------------------------- Hesap makinesi

    fun calculator(svc: KeybdService, t: KbTheme): View {
        val root = root(svc, t)
        val expr = StringBuilder()
        val display = TextView(svc).apply {
            setTextColor(t.text)
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setPadding(svc.dpi(12f), 0, svc.dpi(12f), 0)
        }
        val preview = TextView(svc).apply {
            setTextColor(t.hint)
            textSize = 14f
            gravity = Gravity.END
            setPadding(svc.dpi(12f), 0, svc.dpi(12f), 0)
        }
        val head = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(t.key, svc.dp(10f))
        }
        head.addView(display, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f))
        head.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f).apply {
            setMargins(svc.dpi(6f), svc.dpi(4f), svc.dpi(6f), svc.dpi(2f))
        })

        fun result(): String? = Calc.compute(expr.toString())
        fun refresh() {
            display.text = if (expr.isEmpty()) "0" else expr
            preview.text = if (Calc.trailingExpression(expr.toString()) != null) result()?.let { "= $it" } ?: "" else ""
        }
        fun press(k: String) {
            svc.keyFeedback(null)
            when (k) {
                "C" -> expr.setLength(0)
                "⌫" -> if (expr.isNotEmpty()) expr.setLength(expr.length - 1)
                "=" -> result()?.let { expr.setLength(0); expr.append(it) }
                "Ekle" -> {
                    val r = if (Calc.trailingExpression(expr.toString()) != null) result() else null
                    val text = r ?: expr.toString()
                    if (text.isNotEmpty()) svc.insertFromPanel(text, Stats.CALC)
                    return
                }
                "ABC" -> { svc.closePanel(); return }
                else -> expr.append(k)
            }
            refresh()
        }
        val rows = listOf(
            listOf("C", "(", ")", "÷", "⌫"),
            listOf("7", "8", "9", "×", "%"),
            listOf("4", "5", "6", "−", "^"),
            listOf("1", "2", "3", "+", "ABC"),
            listOf("0", ",", "=", "Ekle"),
        )
        for (r in rows) {
            val row = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                val fn = k !in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ",")
                val bg = when (k) {
                    "=", "Ekle" -> t.accent
                    else -> if (fn) t.keyFn else t.key
                }
                val b = svc.flatButton(k, t, if (k.length > 1) 14f else 19f, bg, svc.dp(8f))
                if (k == "=" || k == "Ekle") b.setTextColor(t.accentText) else b.setTextColor(t.text)
                b.setOnClickListener { press(k) }
                row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (k == "Ekle") 2f else 1f).apply {
                    setMargins(svc.dpi(3f), svc.dpi(2f), svc.dpi(3f), svc.dpi(2f))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        refresh()
        return root
    }

    // ------------------------------------------------------------- Karar çarkı

    /** Çarkın durumu servis tarafında tutulur (elle ekleme için klavyeye gidip dönülebilsin). */
    class WheelState(val options: MutableList<String>, val weights: MutableList<Int>, var note: String) {
        var winner: String? = null

        fun add(o: String) {
            options += o
            equalize()
        }

        fun removeAt(i: Int) {
            options.removeAt(i)
            weights.removeAt(i)
            normalize()
        }

        fun equalize() {
            weights.clear()
            val n = options.size
            for (i in 0 until n) weights += 100 / n + if (i < 100 % n) 1 else 0
        }

        /** i. seçeneğin yüzdesini değiştirir, diğerlerini orantılı ayarlar (toplam %100). */
        fun change(i: Int, delta: Int) {
            val n = options.size
            if (n < 2) return
            val nv = (weights[i] + delta).coerceIn(1, 100 - (n - 1))
            val rest = 100 - nv
            val others = (0 until n).filter { it != i }
            val sum = others.sumOf { weights[it] }.coerceAtLeast(1)
            for (j in others) weights[j] = (weights[j] * rest.toFloat() / sum).toInt().coerceAtLeast(1)
            weights[i] = nv
            // Yuvarlama farkını en büyük diğer seçeneğe ekle
            val diff = 100 - weights.sum()
            val big = others.maxByOrNull { weights[it] } ?: return
            weights[big] = (weights[big] + diff).coerceAtLeast(1)
        }

        private fun normalize() {
            if (options.isEmpty()) return
            val sum = weights.sum().coerceAtLeast(1)
            for (j in weights.indices) weights[j] = (weights[j] * 100f / sum).toInt().coerceAtLeast(1)
            weights[0] += 100 - weights.sum()
        }

        val equal get() = weights.distinct().size <= 2 && (weights.maxOrNull() ?: 0) - (weights.minOrNull() ?: 0) <= 1
    }

    fun wheel(svc: KeybdService, t: KbTheme, st: WheelState): View {
        val root = root(svc, t)
        val body = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        val wheel = WheelView(svc, t)
        fun syncWheel() = wheel.setData(st.options.toList(), st.weights.toList())
        syncWheel()
        body.addView(wheel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val side = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(svc.dpi(4f), svc.dpi(4f), svc.dpi(6f), svc.dpi(2f))
        }
        side.addView(TextView(svc).apply {
            text = st.note
            setTextColor(t.hint)
            textSize = 11f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        val result = TextView(svc).apply {
            text = st.winner?.let { "🎉 $it" } ?: "Çarka dokun ya da Çevir'e bas"
            setTextColor(t.text)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, svc.dpi(2f), 0, svc.dpi(4f))
        }
        side.addView(result)
        val rows = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        side.addView(ScrollView(svc).apply { addView(rows); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        lateinit var fill: () -> Unit
        fill = {
            rows.removeAllViews()
            for (i in st.options.indices) {
                val row = LinearLayout(svc).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundRect(t.key, svc.dp(12f))
                }
                row.addView(TextView(svc).apply {
                    text = st.options[i]
                    setTextColor(t.text)
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(svc.dpi(8f), 0, svc.dpi(2f), 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                fun small(label: String, f: () -> Unit) = svc.flatButton(label, t, 14f).apply {
                    setOnClickListener {
                        if (wheel.spinning) return@setOnClickListener
                        f(); syncWheel(); fill()
                    }
                    row.addView(this, LinearLayout.LayoutParams(svc.dpi(28f), svc.dpi(30f)))
                }
                small("−") { st.change(i, -5) }
                row.addView(TextView(svc).apply {
                    text = "%${st.weights[i]}"
                    setTextColor(t.accent)
                    textSize = 12f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(svc.dpi(36f), ViewGroup.LayoutParams.WRAP_CONTENT))
                small("+") { st.change(i, 5) }
                small("✕") {
                    if (st.options.size <= 2) svc.toast("En az 2 seçenek kalmalı") else st.removeAt(i)
                }
                rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svc.dpi(32f)).apply { bottomMargin = svc.dpi(3f) })
            }
        }
        fill()
        body.addView(side, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        wheel.onTick = { svc.keyFeedback(null) }
        wheel.onResult = { w ->
            st.winner = w
            result.text = "🎉 $w"
            svc.onWheelSpun()
        }
        val bar = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(svc.dpi(4f), svc.dpi(2f), svc.dpi(4f), svc.dpi(4f))
        }
        fun barBtn(label: String, w: Float, bg: Int, f: () -> Unit) = svc.flatButton(label, t, 13f, bg, svc.dp(10f)).apply {
            setOnClickListener { f() }
            bar.addView(this, LinearLayout.LayoutParams(0, svc.dpi(40f), w).apply { setMargins(svc.dpi(2f), 0, svc.dpi(2f), 0) })
        }
        barBtn("ABC", 0.8f, t.keyFn) { svc.closePanel() }
        barBtn("＋ Ekle", 1f, t.keyFn) { svc.startWheelTyping() }
        barBtn("⚖ Eşitle", 1f, t.keyFn) {
            if (!wheel.spinning) { st.equalize(); syncWheel(); fill() }
        }
        barBtn("🎡 Çevir", 1.3f, t.accent) {
            result.text = "Dönüyor…"
            wheel.spin()
        }.setTextColor(t.accentText)
        barBtn("Gönder", 1f, t.keyFn) {
            val w = st.winner
            if (w == null) svc.toast("Önce çarkı çevirin") else svc.insertFromPanel("🎡 Çark: $w", null)
        }
        root.addView(bar)
        return root
    }
}
