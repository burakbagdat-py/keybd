package com.keybd.pro

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import java.text.BreakIterator

class KeybdService : InputMethodService(), SharedPreferences.OnSharedPreferenceChangeListener {

    enum class Page { ALPHA, SYM, SYM2, NUM }

    lateinit var prefs: Prefs
        private set
    private lateinit var clip: ClipHistory
    private val dicts = HashMap<String, Dictionary>()

    private var strip: SuggestionStrip? = null
    private var kbView: KeyboardView? = null
    private var content: FrameLayout? = null
    private var kbRow: LinearLayout? = null
    private var panel: View? = null
    private var theme: KbTheme? = null
    private var viewDirty = false

    private var alphaId = Layouts.TR_Q
    private var page = Page.ALPHA
    private var shift = 0
    private var manualShift = false
    private var lastShiftTap = 0L

    private var numericField = false
    private var phoneField = false
    private var isPassword = false
    private var allowSuggest = true
    private var allowAutocorrect = true
    private var allowAutoCap = true
    private var noLearning = false
    private var inputMode = Layouts.Mode.NORMAL

    private val composing = StringBuilder()
    private var composingFromGlide = false
    private var current: Dictionary.Result? = null
    private var currentAuto: String? = null
    private var lastAutoCorrect: Triple<String, String, String>? = null
    private val ignoreAutocorrect = HashSet<String>()
    private var justPicked = false
    private var lastSpace = 0L
    private var selStart = -1
    private var selEnd = -1
    /** Şeridin ortasında gösterilen tek eylem: pano, hesap sonucu veya çark. */
    private class Chip(val kind: Int, val label: String, val value: String)
    private var chip: Chip? = null
    private var clipChipShownFor: ClipHistory.Item? = null

    // Silineni geri getirme
    private val deleted = StringBuilder()
    private var deleting = false
    private var swipeLen = 0

    // Kelimeye dokununca öneri
    private var lastEdit = 0L
    private var tapWord: Triple<Int, Int, String>? = null

    // Yazarak giriş modu: yazılanlar uygulamaya değil şeritteki kutuya gider
    enum class Capture { EMOJI, ARCHIVE, WHEEL }
    private var capture: Capture? = null
    private val captureBuf = StringBuilder()
    private var emojiSearch: EmojiSearch? = null
    /** Cihazın çizebildiği emojiler (arama sonuçlarında kutu görünmesin). */
    private var supportedEmoji: Set<String> = emptySet()
    private var emojiGrid: android.widget.GridView? = null

    private lateinit var archive: Archive
    private var wheelState: Panels.WheelState? = null
    private var resizing = false

    lateinit var stats: Stats
        private set

    private var swipeAnchor = -1
    private var swipeText = ""
    private var swipeN = 0
    private var inDeleteSwipe = false

    var fontStyle = 0
        private set
    private var voice: VoiceInput? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }
    private val audio: AudioManager? by lazy { getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    private val sounds by lazy { SoundPack(this) }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            if (!prefs.clipHistory || prefs.incognito) return@OnPrimaryClipChangedListener
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val c = cm.primaryClip ?: return@OnPrimaryClipChangedListener
            if (c.itemCount == 0) return@OnPrimaryClipChangedListener
            val desc = c.description
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && desc?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@OnPrimaryClipChangedListener
            val text = c.getItemAt(0).coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
            clip.add(text)
        } catch (_: Exception) {
        }
    }

    // ====================================================================== yaşam döngüsü

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        clip = ClipHistory(prefs)
        stats = Stats(prefs.sp)
        archive = Archive(prefs)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        alphaId = prefs.currentLayout
        ensureDict(Layouts.lang(alphaId))
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
        voice?.stop()
        dicts.values.forEach { it.save() }
        stats.flush()
        sounds.release()
        super.onDestroy()
    }

    private fun ensureDict(lang: String): Dictionary {
        return dicts.getOrPut(lang) {
            Dictionary(lang, filesDir) { assets.open("dict_$lang.txt") }.also { d -> Thread { d.load() }.start() }
        }
    }

    private fun dict(): Dictionary? = dicts[Layouts.lang(alphaId)]?.takeIf { it.ready }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == null) return
        if (key == Prefs.DICT_RESET) {
            dicts.values.forEach { it.clearLearned() }
            return
        }
        if (key == Prefs.DICT_RELOAD) {
            // Yedekten geri yüklendi: sözlükleri dosyalardan yeniden oku
            dicts.clear()
            ensureDict(Layouts.lang(alphaId))
            return
        }
        if (key in Prefs.VIEW_KEYS) {
            viewDirty = true
            if (key == Prefs.LAYOUTS) alphaId = prefs.currentLayout
            if (isInputViewShown) {
                setInputView(onCreateInputView())
                setPage(page)
                updateSuggestions()
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        viewDirty = false
        val t = Themes.get(this, prefs.theme)
        theme = t
        panel = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(t.bg)
        }
        if (t.image) {
            val bmp = try {
                android.graphics.BitmapFactory.decodeFile(java.io.File(filesDir, Themes.BG_FILE).path)
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) root.background = CropDrawable(bmp, prefs.sp.getInt(Themes.CUSTOM_BG, android.graphics.Color.BLACK),
                prefs.sp.getInt(Themes.CUSTOM_DIM, 30) * 255 / 100)
        }
        val stripH = if (prefs.compact) minOf(prefs.stripHeight, 36) else prefs.stripHeight
        val sideTools = prefs.toolbarPos != 0
        val s = SuggestionStrip(this, t, sideTools, stripH)
        strip = s
        val collapsed = prefs.stripCollapsed
        root.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(if (collapsed) 18f else stripH.toFloat())))
        if (collapsed) s.setCollapsed(true)

        // Emoji arama sonuçları: klavyenin üstünde büyük ızgara
        val grid = android.widget.GridView(this).apply {
            numColumns = android.widget.GridView.AUTO_FIT
            columnWidth = dpi(46f)
            stretchMode = android.widget.GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            selector = roundRect(withAlpha(t.text, 30), dp(8f))
            setBackgroundColor(t.bg)
            visibility = View.GONE
        }
        emojiGrid = grid
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(150f)))
        s.setToolActive("incognito", prefs.incognito)
        s.setToolActive("font", fontStyle != 0)
        s.setToolActive("onehand", prefs.oneHand != 0)

        val kv = KeyboardView(this, this)
        kbView = kv
        applyViewPrefs(kv, t)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        kbRow = row
        val oh = prefs.oneHand
        if (prefs.toolbarPos == 1) row.addView(toolSideBar(t, stripH), LinearLayout.LayoutParams(dpi(stripH.toFloat()), ViewGroup.LayoutParams.MATCH_PARENT))
        if (oh == 2) row.addView(oneHandSide(t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))
        row.addView(kv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (oh == 0) 1f else 0.84f))
        if (oh == 1) row.addView(oneHandSide(t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))
        if (prefs.toolbarPos == 2) row.addView(toolSideBar(t, stripH), LinearLayout.LayoutParams(dpi(stripH.toFloat()), ViewGroup.LayoutParams.MATCH_PARENT))
        // Klavye genişliği (%): kenarlardan boşluk
        val wpad = (resources.displayMetrics.widthPixels * (100 - prefs.kbWidth) / 200f).toInt()
        row.setPadding(wpad, 0, wpad, 0)

        val c = FrameLayout(this)
        content = c
        c.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.setPadding(0, 0, 0, dpi(prefs.bottomPad.toFloat()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val nb = insets.getInsets(WindowInsets.Type.navigationBars())
                v.setPadding(nb.left, 0, nb.right, nb.bottom + dpi(prefs.bottomPad.toFloat()))
                insets
            }
        }
        kv.kb = buildLayout()
        // Ses paketini önceden yükle (ilk tuşta ses kaçmasın)
        if (prefs.sound && prefs.soundPack != SoundPack.SYSTEM) sounds.load(prefs.soundPack)
        if (resizing) s.showResize()
        if (capture != null) root.post { refreshCapture() }
        return root
    }

    /** Araç çubuğu yan menüdeyken klavyenin solunda/sağında dikey düğmeler. */
    private fun toolSideBar(t: KbTheme, stripH: Int): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(t.strip)
        }
        for (tool in Tools.all) {
            if (tool.id == "collapse") continue
            col.addView(SuggestionStrip.toolButton(this, t, tool, stripH.toFloat()),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(stripH * 0.95f)))
        }
        return android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(t.strip)
            addView(col)
        }
    }

    private fun applyViewPrefs(kv: KeyboardView, t: KbTheme) {
        kv.theme = t
        kv.rowHeight = dp(54f) * prefs.heightPercent / 100f * (if (prefs.compact) 0.84f else 1f)
        kv.radius = dp(prefs.keyRadius.toFloat())
        kv.gap = dp(prefs.keyGap.toFloat()).let { if (prefs.compact) minOf(it, dp(2.5f)) else it }
        kv.borders = prefs.keyBorders
        kv.showHints = prefs.keyHints
        kv.preview = prefs.keyPreview
        kv.glideEnabled = prefs.glide
        kv.glideTrail = prefs.glideTrail
        kv.spaceCursor = prefs.spaceCursor
        kv.deleteSwipe = prefs.deleteSwipe
        kv.longPressMs = prefs.longPressMs.toLong()
        kv.swipeUp = prefs.swipeUp
        kv.touchFix = prefs.touchFix
        kv.fontScale = prefs.fontScale / 100f
    }

    private fun oneHandSide(t: KbTheme): View {
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(t.bg)
        }
        val swap = flatButton("⇄", t, 22f).apply {
            setOnClickListener { prefs.oneHand = if (prefs.oneHand == 1) 2 else 1 }
        }
        val full = flatButton("⤢", t, 22f).apply { setOnClickListener { prefs.oneHand = 0 } }
        side.addView(swap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        side.addView(full, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return side
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing.clear()
        composingFromGlide = false
        lastAutoCorrect = null
        justPicked = false
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (viewDirty || kbView == null) setInputView(onCreateInputView())
        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        numericField = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        phoneField = cls == InputType.TYPE_CLASS_PHONE
        isPassword = (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val email = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val uri = cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI
        val noSuggest = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        allowSuggest = cls == InputType.TYPE_CLASS_TEXT && !isPassword
        allowAutocorrect = allowSuggest && !email && !uri && !noSuggest && variation != InputType.TYPE_TEXT_VARIATION_FILTER
        allowAutoCap = cls == InputType.TYPE_CLASS_TEXT && !isPassword && !email && !uri
        inputMode = when {
            email -> Layouts.Mode.EMAIL
            uri -> Layouts.Mode.URL
            else -> Layouts.Mode.NORMAL
        }
        noLearning = isPassword || (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        alphaId = prefs.currentLayout
        ensureDict(Layouts.lang(alphaId))
        composing.clear()
        composingFromGlide = false
        current = null
        currentAuto = null
        lastAutoCorrect = null
        justPicked = false
        manualShift = false
        shift = 0
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        tapWord = null
        if (capture != null) endCapture()
        clearRestore()
        closePanel()

        kbView?.enterLabel = enterLabel(info)
        setPage(if (numericField) Page.NUM else Page.ALPHA)
        updateShift()

        val last = clip.lastAdded
        chip = if (last != null && last !== clipChipShownFor && System.currentTimeMillis() - last.time < 90_000 && !isPassword) {
            clipChipShownFor = last
            Chip(CHIP_CLIP, "📋 " + last.text, last.text)
        } else null
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopVoice()
        if (capture != null) endCapture()
        if (resizing) stopResize()
        closePanel()
        stats.flush()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
        composingFromGlide = false
        dicts.values.forEach { it.save() }
        stats.flush()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        // İmleç ileri veya uzağa atladıysa silme serisi biter (geri getirme tamponu yenilenir)
        if (deleting && (newSelStart > oldSelStart || oldSelStart - newSelStart > 64)) deleting = false
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // Kendi işlemlerimizden gelen gecikmeli bir olay olabilir: kelime hâlâ imlecin
            // hemen önündeyse oluşturmaya devam et.
            if (candidatesStart == -1 && newSelStart == newSelEnd) {
                val tb = currentInputConnection?.getTextBeforeCursor(composing.length, 0)?.toString()
                if (tb == composing.toString()) return
            }
            composing.clear()
            composingFromGlide = false
            currentInputConnection?.finishComposingText()
            updateSuggestions()
        }
        if (composing.isEmpty() && !inDeleteSwipe) {
            updateShift()
            if (newSelStart != newSelEnd) {
                // Metin seçildi: içinde seçenekler varsa "Çark yap" çipi
                val sel = currentInputConnection?.getSelectedText(0)?.toString()
                val opts = WheelOptions.parse(sel)
                if (sel != null && opts.size >= 2) {
                    chip = Chip(CHIP_WHEEL, "🎡 Çark yap (${opts.size} seçenek)", sel)
                    showPredictions()
                }
            } else {
                if (chip?.kind == CHIP_WHEEL) {
                    chip = null
                    updateSuggestions()
                }
                // Kullanıcı imleci bir kelimeye taşıdıysa (dokunma) o kelimenin önerilerini göster
                if (candidatesStart == -1 && SystemClock.uptimeMillis() - lastEdit > 400) checkTapWord(newSelStart)
            }
        }
    }

    private fun enterLabel(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "➜"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            EditorInfo.IME_ACTION_PREVIOUS -> "⇤"
            else -> "↵"
        }
    }

    // ====================================================================== düzen / sayfa

    private fun opts() = Layouts.Opts(
        numberRow = prefs.numberRow || isPassword,
        hints = prefs.keyHints,
        multiLang = prefs.layouts.size > 1,
        emojiKey = prefs.emojiKey,
        mode = inputMode,
        phone = phoneField,
    )

    private fun buildLayout(): KeyboardLayout {
        val o = opts()
        val l = when (page) {
            Page.ALPHA -> Layouts.alpha(alphaId, o)
            Page.SYM -> Layouts.symbols(o)
            Page.SYM2 -> Layouts.symbols2(o)
            Page.NUM -> Layouts.numpad(o)
        }
        if (page == Page.ALPHA) {
            val nb = Layouts.neighbors(l)
            dicts[Layouts.lang(alphaId)]?.neighbors = nb
        }
        return l
    }

    private fun setPage(p: Page) {
        page = p
        val kv = kbView ?: return
        kv.spaceLabel = if (p == Page.ALPHA || p == Page.SYM) Layouts.name(alphaId) else ""
        kv.kb = buildLayout()
        kv.shiftState = if (p == Page.ALPHA) shift else 0
    }

    fun showNumpad() {
        commitComposing(false)
        setPage(Page.NUM)
    }

    private fun nextLanguage() {
        val list = prefs.layouts
        if (list.size < 2) {
            showImePicker()
            return
        }
        commitComposing(false)
        val idx = list.indexOf(alphaId)
        alphaId = list[(idx + 1) % list.size]
        prefs.currentLayout = alphaId
        ensureDict(Layouts.lang(alphaId))
        setPage(Page.ALPHA)
        updateShift()
        updateSuggestions()
        toast(Layouts.name(alphaId))
    }

    // ====================================================================== geri bildirim

    fun keyFeedback(k: Key?) {
        markEdit()
        stats.keyPressed(SystemClock.uptimeMillis())
        if (prefs.vibrate) {
            try {
                val ms = prefs.vibrateMs.toLong().coerceAtLeast(1)
                val v = vibrator
                if (theme?.style == KbTheme.STYLE_SYMBIOTE && v?.hasAmplitudeControl() == true) {
                    // Venom: yumuşak başlayıp sertleşen çift darbe ("şlap" hissi)
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, ms / 2 + 2, 18, ms + 4), intArrayOf(0, 70, 0, 255), -1))
                } else {
                    v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } catch (_: Exception) {
            }
        }
        if (prefs.sound && prefs.soundPack != SoundPack.SYSTEM) {
            sounds.load(prefs.soundPack)
            sounds.play(SoundPack.kindOf(k), prefs.soundVolume / 100f)
        } else if (prefs.sound) {
            val fx = when (k?.type) {
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, prefs.soundVolume / 100f)
        }
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ====================================================================== tuş işleme

    fun applyCase(s: String, st: Int = shift): String {
        if (st == 0 || page != Page.ALPHA) return s
        if (s.length != 1 || !s[0].isLetter()) return s
        val u = s.uppercase(Layouts.locale(alphaId))
        return if (u.length == 1) u else s
    }

    fun glideAllowed() = page == Page.ALPHA && fontStyle == 0 && allowSuggest && dict() != null && capture == null

    fun onKey(k: Key) {
        val cap = capture
        if (cap != null) {
            // Yazarak giriş modu: yazılanlar uygulamaya değil şeritteki kutuya gider
            when (k.type) {
                KeyType.CHAR, KeyType.TEXT -> onText(k.output)
                KeyType.SPACE -> {
                    if (cap != Capture.EMOJI || (captureBuf.isNotEmpty() && captureBuf.last() != ' ')) captureBuf.append(' ')
                    refreshCapture()
                }
                KeyType.DELETE -> handleDelete(0)
                KeyType.ENTER -> confirmCapture()
                KeyType.EMOJI -> { endCapture(); showEmojiPanel() }
                KeyType.SHIFT -> handleShift()
                KeyType.TO_SYMBOLS -> setPage(Page.SYM)
                KeyType.TO_SYMBOLS2 -> setPage(Page.SYM2)
                KeyType.TO_ALPHA -> setPage(Page.ALPHA)
                KeyType.LANG -> nextLanguage()
            }
            return
        }
        when (k.type) {
            KeyType.CHAR, KeyType.TEXT -> onText(k.output)
            KeyType.SPACE -> handleSpace()
            KeyType.DELETE -> handleDelete(0)
            KeyType.ENTER -> handleEnter()
            KeyType.SHIFT -> handleShift()
            KeyType.TO_SYMBOLS -> { commitComposing(false); setPage(Page.SYM) }
            KeyType.TO_SYMBOLS2 -> setPage(Page.SYM2)
            KeyType.TO_ALPHA -> { setPage(Page.ALPHA); updateShift() }
            KeyType.LANG -> nextLanguage()
            KeyType.EMOJI -> showEmojiPanel()
        }
    }

    private fun useComposing() = allowSuggest && prefs.suggestions && fontStyle == 0

    fun onText(s: String) {
        val ic = currentInputConnection ?: return
        lastAutoCorrect = null
        if (s.isEmpty()) return
        capture?.let { c ->
            if (c == Capture.EMOJI) {
                if (s.codePointCount(0, s.length) == 1 && isWordChar(s.codePointAt(0))) captureBuf.append(s.lowercase(Layouts.locale(alphaId)))
            } else captureBuf.append(applyCase(s))
            afterChar()
            refreshCapture()
            return
        }
        clearRestore()
        chip = null
        stats.add(Stats.CHARS)
        val cased = applyCase(s)
        val cp = s.codePointAt(0)
        val single = s.codePointCount(0, s.length) == 1
        val wordChar = single && (isWordChar(cp) || (s == "'" && composing.isNotEmpty() && Layouts.lang(alphaId) == "en"))

        if (fontStyle != 0) {
            commitComposing(false)
            ic.commitText(FontStyles.apply(fontStyle, cased), 1)
            afterChar()
            return
        }
        if (!single) {
            commitComposing(false)
            ic.commitText(s, 1)
            justPicked = false
            afterChar()
            return
        }
        if (wordChar && useComposing()) {
            if (composingFromGlide) {
                commitComposing(false)
                ic.commitText(" ", 1)
            }
            composing.append(cased)
            ic.setComposingText(composing, 1)
            justPicked = false
            afterChar()
            updateSuggestions()
            return
        }
        if (wordChar) {
            ic.commitText(cased, 1)
            justPicked = false
            afterChar()
            return
        }
        // Noktalama / sembol
        val sentencePunct = s in ".,!?;:"
        val typed = composing.toString()
        val out = commitComposing(sentencePunct && !composingFromGlide)
        if (out != null && out != typed) lastAutoCorrect = Triple(typed, out, s)
        if (justPicked && prefs.smartPunct && (sentencePunct || s == ")")) {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before != null && before.toString() == " ") {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (s == ")") s else "$s ", 1)
            } else ic.commitText(s, 1)
        } else {
            ic.commitText(s, 1)
        }
        justPicked = false
        if (s == "=") {
            // Satır içi hesap makinesi: "125*4=" → öneri olarak "500"
            val b = ic.getTextBeforeCursor(80, 0)?.toString()
            if (b != null && b.endsWith("=")) {
                val r = Calc.trailingExpression(b.dropLast(1))?.let { Calc.compute(it) }
                if (r != null) chip = Chip(CHIP_CALC, "= $r", r)
            }
        }
        afterChar()
        if (composing.isEmpty()) updateSuggestions()
    }

    private fun afterChar() {
        if (shift == 1) {
            shift = 0
            manualShift = false
            kbView?.shiftState = 0
        }
        if (page == Page.SYM || page == Page.SYM2) return
        if (composing.isEmpty()) updateShift()
    }

    private fun handleShift() {
        if (selStart >= 0 && selEnd > selStart && capture == null && cycleSelectionCase()) return
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 350 -> 2
            shift == 1 -> 0
            else -> 1
        }
        manualShift = shift != 0
        lastShiftTap = now
        kbView?.shiftState = shift
        if (composing.isNotEmpty()) updateSuggestions()
    }

    /** Seçili metin: küçük → Baş Harfler → BÜYÜK → küçük. */
    private fun cycleSelectionCase(): Boolean {
        val ic = currentInputConnection ?: return false
        val sel = ic.getSelectedText(0)?.toString() ?: return false
        if (sel.isBlank() || sel.length > 5000) return false
        val loc = Layouts.locale(alphaId)
        val lower = sel.lowercase(loc)
        val upper = sel.uppercase(loc)
        val title = lower.split(Regex("(?<=\\s)")).joinToString("") { w -> w.replaceFirstChar { it.titlecase(loc) } }
        val out = when (sel) {
            lower -> title
            title -> upper
            else -> lower
        }
        val start = selStart
        ic.beginBatchEdit()
        ic.commitText(out, 1)
        ic.setSelection(start, start + out.length)
        ic.endBatchEdit()
        keyFeedback(null)
        return true
    }

    fun setCapsLock() {
        shift = 2
        manualShift = true
        kbView?.shiftState = 2
    }

    private fun updateShift() {
        if (shift == 2 || manualShift) return
        capture?.let { c ->
            // Emoji aramada küçük harf; arşiv/çark girişinde yalnızca ilk harf büyük
            shift = if (c != Capture.EMOJI && captureBuf.isEmpty()) 1 else 0
            kbView?.shiftState = shift
            return
        }
        val kv = kbView ?: return
        var newShift = 0
        if (prefs.autoCap && allowAutoCap && page == Page.ALPHA && composing.isEmpty()) {
            val ic = currentInputConnection
            val info = currentInputEditorInfo
            if (ic != null && info != null) {
                var flags = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                flags = flags or (info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
                if (ic.getCursorCapsMode(flags) != 0) newShift = 1
            }
        }
        shift = newShift
        kv.shiftState = if (page == Page.ALPHA) shift else 0
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        clearRestore()
        chip = null
        if (fontStyle != 0) {
            ic.commitText(" ", 1)
            return
        }
        if (composing.isNotEmpty()) {
            val typed = composing.toString()
            val out = commitComposing(!composingFromGlide)
            ic.commitText(" ", 1)
            lastAutoCorrect = if (out != null && out != typed) Triple(typed, out, " ") else null
            justPicked = false
            lastSpace = now
            afterSeparator()
            return
        }
        lastAutoCorrect = null
        if (prefs.doubleSpace && !isPassword && now - lastSpace < 700) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString()
            if (b != null && b.length == 2 && b[1] == ' ' && (b[0].isLetterOrDigit() || b[0] in ")\"'”’")) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpace = 0
                justPicked = false
                afterSeparator()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        justPicked = false
        if (page == Page.SYM || page == Page.SYM2) setPage(Page.ALPHA)
        afterSeparator()
    }

    private fun afterSeparator() {
        if (shift == 1) {
            shift = 0
            manualShift = false
        }
        updateShift()
        updateSuggestions()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        clearRestore()
        chip = null
        commitComposing(false)
        lastAutoCorrect = null
        justPicked = false
        val info = currentInputEditorInfo
        if (info != null) {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
            if ((info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
                ic.commitText("\n", 1)
                afterSeparator()
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    fun deleteRepeat(count: Int) {
        if (count > 22) deleteWordBack() else handleDelete(count)
    }

    private fun handleDelete(count: Int) {
        val ic = currentInputConnection ?: return
        capture?.let { c ->
            if (captureBuf.isEmpty()) {
                if (c == Capture.EMOJI) cancelCapture()
            } else {
                val cp = captureBuf.codePointBefore(captureBuf.length)
                captureBuf.setLength(captureBuf.length - Character.charCount(cp))
                refreshCapture()
            }
            return
        }
        justPicked = false
        val lac = lastAutoCorrect
        lastAutoCorrect = null
        if (lac != null && count == 0 && composing.isEmpty()) {
            val (typed, out, sep) = lac
            val before = ic.getTextBeforeCursor(out.length + sep.length, 0)?.toString()
            if (before == out + sep) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(before.length, 0)
                composing.setLength(0)
                composing.append(typed)
                ic.setComposingText(composing, 1)
                ic.endBatchEdit()
                ignoreAutocorrect += typed.lowercase()
                updateSuggestions()
                return
            }
        }
        if (composing.isNotEmpty()) {
            if (composingFromGlide) {
                noteDeleted(composing.toString())
                composing.clear()
                composingFromGlide = false
                ic.commitText("", 1)
                updateShift()
                updateSuggestions()
                return
            }
            val cp = composing.codePointBefore(composing.length)
            noteDeleted(composing.substring(composing.length - Character.charCount(cp)))
            composing.setLength(composing.length - Character.charCount(cp))
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            updateSuggestions()
            if (composing.isEmpty()) updateShift()
            return
        }
        if (selStart >= 0 && selStart != selEnd) {
            noteDeleted(ic.getSelectedText(0)?.toString() ?: "")
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(32, 0)?.toString()
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val gl = lastGraphemeLength(before)
        noteDeleted(before.takeLast(gl))
        ic.deleteSurroundingText(gl, 0)
        if (count == 0) tryRecompose()
        updateShift()
        if (composing.isEmpty()) updateSuggestions()
    }

    private fun lastGraphemeLength(s: String): Int {
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(s)
        val end = bi.last()
        val start = bi.previous()
        return if (start == BreakIterator.DONE) 1 else (end - start).coerceAtLeast(1)
    }

    /** Silerek bir kelimenin sonuna gelindiğinde o kelimeyi tekrar düzenlenebilir yapar. */
    private fun tryRecompose() {
        if (!useComposing()) return
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(48, 0)?.toString() ?: return
        val a = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (a.isNotEmpty() && a[0].isLetterOrDigit()) return
        var i = b.length
        while (i > 0 && b[i - 1].isLetter()) i--
        val w = b.substring(i)
        if (w.isEmpty() || w.length >= 40 || (i == 0 && b.length >= 48)) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        composing.setLength(0)
        composing.append(w)
        ic.setComposingText(composing, 1)
        ic.endBatchEdit()
    }

    fun deleteWordBack() {
        val ic = currentInputConnection ?: return
        if (capture != null) { handleDelete(0); return }
        if (composing.isNotEmpty()) {
            noteDeleted(composing.toString())
            composing.clear()
            composingFromGlide = false
            ic.commitText("", 1)
            updateSuggestions()
            return
        }
        if (selStart >= 0 && selStart != selEnd) {
            noteDeleted(ic.getSelectedText(0)?.toString() ?: "")
            ic.commitText("", 1)
            return
        }
        val b = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (b.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        var i = b.length
        while (i > 0 && b[i - 1].isWhitespace()) i--
        if (i > 0 && !b[i - 1].isLetterOrDigit()) i-- else while (i > 0 && b[i - 1].isLetterOrDigit()) i--
        val n = (b.length - i).coerceAtLeast(1)
        noteDeleted(b.takeLast(n))
        ic.deleteSurroundingText(n, 0)
        updateShift()
        updateSuggestions()
    }

    // ====================================================================== kelime yönetimi

    private fun lastWord(s: String): String? {
        var i = s.length
        while (i > 0 && (s[i - 1].isLetterOrDigit() || s[i - 1] == '\'')) i--
        return s.substring(i).ifEmpty { null }
    }

    private fun prevWord(): String? {
        val ic = currentInputConnection ?: return null
        val t = ic.getTextBeforeCursor(64 + composing.length, 0)?.toString() ?: return null
        val base = if (composing.isNotEmpty() && t.endsWith(composing.toString())) t.dropLast(composing.length) else t
        if (!base.endsWith(" ")) return null
        return lastWord(base.trimEnd())
    }

    /** Oluşturulmakta olan kelimeyi metne yazar; gerekirse otomatik düzeltir. */
    private fun commitComposing(autocorrect: Boolean): String? {
        if (composing.isEmpty()) return null
        val ic = currentInputConnection ?: return null
        val typed = composing.toString()
        var out = typed
        if (autocorrect) {
            val sc = prefs.shortcuts[typed.lowercase()]?.let { expandTokens(it) }
            val auto = currentAuto
            if (sc != null) out = sc
            else if (auto != null && allowAutocorrect && prefs.autoCorrect && typed.lowercase() !in ignoreAutocorrect) out = auto
        }
        val prev = prevWord()
        ic.commitText(out, 1)
        learn(out, prev, false)
        stats.word()
        if (out != typed && autocorrect) stats.add(Stats.AUTOCORRECT)
        composing.clear()
        composingFromGlide = false
        current = null
        currentAuto = null
        return out
    }

    /** Kısayollardaki değişkenler: {tarih} {saat} {gün} {yarın} */
    private fun expandTokens(s: String): String {
        if (!s.contains('{')) return s
        val tr = java.util.Locale("tr", "TR")
        val now = java.util.Date()
        fun f(p: String, d: java.util.Date = now) = java.text.SimpleDateFormat(p, tr).format(d)
        return s.replace("{tarih}", f("dd.MM.yyyy"))
            .replace("{saat}", f("HH:mm"))
            .replace("{gün}", f("EEEE"))
            .replace("{yarın}", f("dd.MM.yyyy", java.util.Date(now.time + 86_400_000L)))
    }

    // E-posta tamamlama: "ali@g" → "@gmail.com"
    private val emailDomains = listOf("gmail.com", "hotmail.com", "outlook.com", "icloud.com", "yahoo.com", "yandex.com", "windowslive.com", "msn.com", "proton.me")
    private val emailRe = Regex("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]*)$")
    private var emailPick: Map<String, String> = emptyMap()

    private fun emailSuggestions(): List<String>? {
        if (isPassword || capture != null) return null
        val b = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: return null
        val m = emailRe.find(b) ?: return null
        val typed = m.groupValues[1].lowercase()
        val cands = emailDomains.filter { it.startsWith(typed) && it != typed }.take(3)
        if (cands.isEmpty()) return null
        emailPick = cands.associate { "@$it" to it.substring(typed.length) }
        return cands.map { "@$it" }
    }

    private fun learn(word: String, prev: String?, force: Boolean) {
        if (!prefs.learn || prefs.incognito || noLearning) return
        dict()?.learn(word, prev, force)
    }

    private fun matchCase(typed: String, w: String): String {
        if (typed.isEmpty() || w.isEmpty()) return w
        val loc = Layouts.locale(alphaId)
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(loc)
            typed[0].isUpperCase() -> w.substring(0, 1).uppercase(loc) + w.substring(1)
            else -> w
        }
    }

    /** İmlecin önündeki "1x1", "12/4", "7-2" gibi ifadenin sonucu (yoksa null). */
    private fun liveCalc(): String? {
        if (isPassword || fontStyle != 0 || capture != null) return null
        val b = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: return null
        return Calc.live(b)
    }

    private fun updateSuggestions() {
        val s = strip ?: return
        emailPick = emptyMap()
        emailSuggestions()?.let { es ->
            s.setWords(listOf(es[0], es.getOrNull(1), es.getOrNull(2)), true)
            return
        }
        val live = liveCalc()
        if (live != null) {
            chip = Chip(CHIP_CALC, "= $live", "=$live")
            s.showChip("= $live", "=$live")
            return
        } else if (chip?.kind == CHIP_CALC && chip?.value?.startsWith("=") == true) chip = null
        if (composing.isEmpty()) {
            showPredictions()
            return
        }
        if (composingFromGlide) return
        if (!prefs.suggestions || !allowSuggest) {
            s.clear()
            return
        }
        val typed = composing.toString()
        val d = dict()
        val allowAc = allowAutocorrect && prefs.autoCorrect && typed.lowercase() !in ignoreAutocorrect
        val res = d?.suggest(typed, prevWord(), allowAc)
        current = res
        currentAuto = res?.autoCorrect?.let { matchCase(typed, it) }
        val shortcut = prefs.shortcuts[typed.lowercase()]?.let { expandTokens(it) }
        if (shortcut != null) currentAuto = null

        val others = ArrayList<String>()
        res?.words?.forEach { w ->
            val c = matchCase(typed, w)
            if (c != typed && c != currentAuto && c !in others) others += c
        }
        val emoji = if (prefs.emojiSuggest) EmojiData.forWord(typed) else null
        val list = arrayOfNulls<String>(3)
        var quoted = -1
        var bold = false
        when {
            shortcut != null -> {
                list[0] = shortcut; list[1] = typed; list[2] = emoji ?: others.getOrNull(0)
                bold = true
            }
            currentAuto != null -> {
                list[0] = currentAuto; list[1] = typed; quoted = 1; list[2] = emoji ?: others.getOrNull(0)
                bold = true
            }
            res?.typedValid == true -> {
                list[0] = typed; list[1] = others.getOrNull(0); list[2] = emoji ?: others.getOrNull(1)
            }
            else -> {
                list[0] = others.getOrNull(0) ?: typed
                list[1] = if (others.isEmpty()) null else typed
                if (list[1] != null) quoted = 1
                list[2] = emoji ?: others.getOrNull(1)
            }
        }
        s.setWords(list.toList(), bold, quoted)
    }

    private fun showPredictions() {
        val s = strip ?: return
        val c = chip
        if (c != null) {
            s.showChip(c.label, c.value)
            return
        }
        if (!prefs.nextWord || !useComposing()) {
            s.clear()
            return
        }
        val preds = dict()?.predict(prevWord()) ?: emptyList()
        if (preds.isEmpty()) {
            s.clear()
            return
        }
        val l = preds.map { if (shift > 0) applyCase(it.substring(0, 1)) + it.substring(1) else it }
        s.setWords(l, false)
    }

    fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        emailPick[word]?.let { rest ->
            commitComposing(false)
            ic.commitText(rest, 1)
            emailPick = emptyMap()
            markEdit()
            updateSuggestions()
            return
        }
        val c = chip
        if (c != null && word == c.value && (composing.isEmpty() || c.kind == CHIP_CALC)) {
            if (composing.isNotEmpty()) commitComposing(false)
            chip = null
            when (c.kind) {
                CHIP_WHEEL -> {
                    showWheelFor(c.value, "Seçili metinden")
                    return
                }
                CHIP_CALC -> { ic.commitText(c.value, 1); stats.add(Stats.CALC) }
                else -> ic.commitText(c.value, 1)
            }
            markEdit()
            updateSuggestions()
            return
        }
        chip = null
        clearRestore()
        val tw = tapWord
        if (tw != null && composing.isEmpty()) {
            // Dokunulan kelimeyi seçilen öneriyle değiştir
            ic.beginBatchEdit()
            ic.setComposingRegion(tw.first, tw.second)
            ic.commitText(word, 1)
            ic.endBatchEdit()
            learn(word, null, true)
            stats.add(Stats.PICKS)
            markEdit()
            updateShift()
            updateSuggestions()
            return
        }
        markEdit()
        stats.add(Stats.PICKS)
        lastAutoCorrect = null
        val prev = prevWord()
        if (composing.isNotEmpty()) {
            ic.commitText(word, 1)
            if (!isEmoji(word)) learn(word, prev, true)
            if (isEmoji(word)) stats.add(Stats.EMOJI) else stats.word()
            composing.clear()
            composingFromGlide = false
            current = null
            currentAuto = null
        } else {
            ic.commitText(word, 1)
            learn(word, prev, false)
        }
        ic.commitText(" ", 1)
        justPicked = true
        if (shift == 1 && !manualShift) shift = 0
        updateShift()
        updateSuggestions()
    }

    fun forgetSuggestion(word: String) {
        val d = dict() ?: return
        if (isEmoji(word)) return
        d.forget(word)
        toast("“$word” öğrenilenlerden kaldırıldı")
        updateSuggestions()
    }

    fun onGlide(pts: List<Pt>) {
        val kv = kbView ?: return
        val d = dict() ?: return
        val ic = currentInputConnection ?: return
        val prevBefore = prevWord()
        val res = GlideDecoder.decode(pts, kv.letterCenters(), kv.unitW, d, if (composing.isEmpty()) prevBefore else null)
        if (res.isEmpty()) return
        chip = null
        clearRestore()
        markEdit()
        stats.add(Stats.GLIDE)
        if (composing.isNotEmpty()) commitComposing(false)
        val b = ic.getTextBeforeCursor(1, 0)?.toString()
        if (!b.isNullOrEmpty() && !b[0].isWhitespace() && b[0] !in "([{\"'«“‘/@#") ic.commitText(" ", 1)
        val loc = Layouts.locale(alphaId)
        val cased = res.map {
            when (shift) {
                2 -> it.uppercase(loc)
                1 -> it.substring(0, 1).uppercase(loc) + it.substring(1)
                else -> it
            }
        }
        composing.setLength(0)
        composing.append(cased[0])
        composingFromGlide = true
        ic.setComposingText(composing, 1)
        if (shift == 1) {
            shift = 0
            manualShift = false
            kv.shiftState = 0
        }
        strip?.setWords(listOf(cased[0], cased.getOrNull(1), cased.getOrNull(2)), true)
    }

    // ====================================================================== imleç / seçim

    fun beginCursorMove() {
        commitComposing(false)
        currentInputConnection?.finishComposingText()
    }

    fun endCursorMove() = updateSuggestions()

    fun moveCursor(d: Int) {
        sendDownUpKeyEvents(if (d > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    fun beginDeleteSwipe() {
        commitComposing(false)
        inDeleteSwipe = true
        swipeAnchor = selStart
        swipeN = 0
        swipeText = currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
    }

    fun selectWordsBack(n: Int) {
        if (!inDeleteSwipe || swipeAnchor < 0 || n == swipeN) return
        swipeN = n
        val ic = currentInputConnection ?: return
        var i = swipeText.length
        repeat(n) {
            while (i > 0 && swipeText[i - 1].isWhitespace()) i--
            if (i > 0 && !swipeText[i - 1].isLetterOrDigit()) i-- else while (i > 0 && swipeText[i - 1].isLetterOrDigit()) i--
        }
        val len = swipeText.length - i
        swipeLen = len
        ic.setSelection(swipeAnchor - len, swipeAnchor)
        keyFeedback(null)
    }

    fun endDeleteSwipe() {
        val ic = currentInputConnection
        if (inDeleteSwipe && swipeN > 0) {
            noteDeleted(swipeText.takeLast(swipeLen))
            ic?.commitText("", 1)
        }
        inDeleteSwipe = false
        swipeN = 0
        updateShift()
        updateSuggestions()
    }

    fun sendArrow(code: Int, select: Boolean) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    fun undoRedo(redo: Boolean) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or (if (redo) KeyEvent.META_SHIFT_ON else 0)
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    fun contextAction(id: Int) {
        commitComposing(false)
        currentInputConnection?.performContextMenuAction(id)
    }

    // ====================================================================== paneller

    private fun showPanel(v: View) {
        val c = content ?: return
        commitComposing(false)
        panel?.let { c.removeView(it) }
        panel = v
        // Panel, klavyeyle tam aynı yükseklikte açılır (pencere boyu zıplamaz).
        val h = kbRow?.height?.takeIf { it > 0 } ?: kbView?.desiredHeight() ?: ViewGroup.LayoutParams.MATCH_PARENT
        kbRow?.visibility = View.INVISIBLE
        c.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
    }

    fun closePanel() {
        val p = panel ?: return
        content?.removeView(p)
        panel = null
        kbRow?.visibility = View.VISIBLE
    }

    fun showEmojiPanel() = theme?.let { showPanel(Panels.emoji(this, it)) }
    fun showClipboardPanel() = theme?.let { showPanel(Panels.clipboard(this, it, clip)) }
    fun showEditPanel() = theme?.let { showPanel(Panels.edit(this, it)) }
    fun showFontPanel() = theme?.let { showPanel(Panels.fonts(this, it)) }
    fun showCalcPanel() = theme?.let { showPanel(Panels.calculator(this, it)) }

    /** Seçili metin → kopyalanan metin → yazılan satır sırasıyla seçenek arayıp çarkı açar. */
    fun showWheelPanel() {
        val ic = currentInputConnection
        val sel = ic?.getSelectedText(0)?.toString()
        var opts = WheelOptions.parse(sel)
        var note = "Seçili metinden"
        if (opts.size < 2) {
            opts = WheelOptions.parse(latestClipText())
            note = "Kopyalanan metinden"
        }
        if (opts.size < 2) {
            val line = (ic?.getTextBeforeCursor(300, 0)?.toString() ?: "").substringAfterLast('\n')
            opts = WheelOptions.parse(line)
            note = "Yazdığınız metinden"
        }
        if (opts.size < 2) {
            opts = listOf("Evet", "Hayır")
            note = "Seçenek bulunamadı: ＋ Ekle ile yazın ya da seçenekleri seçip/kopyalayıp tekrar açın."
        }
        openWheel(opts, note)
    }

    private fun showWheelFor(text: String, note: String) {
        val opts = WheelOptions.parse(text)
        if (opts.size < 2) return showWheelPanel()
        openWheel(opts, note)
    }

    private fun openWheel(opts: List<String>, note: String) {
        val st = Panels.WheelState(opts.toMutableList(), ArrayList(), note).also { it.equalize() }
        wheelState = st
        theme?.let { showPanel(Panels.wheel(this, it, st)) }
    }

    private fun reopenWheel() {
        val st = wheelState ?: return
        theme?.let { showPanel(Panels.wheel(this, it, st)) }
    }

    // ====================================================================== arşiv

    fun showArchivePanel() = theme?.let { showPanel(Panels.archive(this, it, archive)) }

    fun addToArchive(text: String): Boolean {
        val added = archive.add(text)
        toast(if (added) "Arşive eklendi 🗂" else "Zaten arşivde, başa alındı")
        return true
    }

    fun archiveSelection(): Boolean {
        val sel = currentInputConnection?.getSelectedText(0)?.toString()
        if (sel.isNullOrBlank()) { toast("Önce metni seçin"); return false }
        return addToArchive(sel)
    }

    fun archiveClipboard(): Boolean {
        val c = latestClipText()
        if (c.isNullOrBlank()) { toast("Pano boş"); return false }
        return addToArchive(c)
    }

    fun startArchiveTyping() = startCapture(Capture.ARCHIVE)
    fun startWheelTyping() = startCapture(Capture.WHEEL)

    // ====================================================================== boyut / menü

    fun startResize() {
        closePanel()
        commitComposing(false)
        resizing = true
        strip?.showResize()
        toast("Klavyeyi büyütüp küçültün, bitince ✓")
    }

    fun resizeStep(key: String, d: Int) {
        val (min, max) = when (key) {
            Prefs.HEIGHT -> 60 to 150
            Prefs.KB_WIDTH -> 50 to 100
            else -> 60 to 160
        }
        val v = (prefs.sp.getInt(key, 100) + d).coerceIn(min, max)
        prefs.sp.edit().putInt(key, v).apply()
        keyFeedback(null)
    }

    fun resizeReset() {
        prefs.sp.edit().putInt(Prefs.HEIGHT, 100).putInt(Prefs.KB_WIDTH, 100).putInt(Prefs.FONT_SCALE, 100).apply()
    }

    fun stopResize() {
        resizing = false
        strip?.endBusy()
        updateSuggestions()
    }

    fun setStripCollapsed(c: Boolean) {
        prefs.stripCollapsed = c
        if (c) toast("Menü gizlendi — açmak için üstteki ▴ çizgisine dokunun")
    }

    private fun latestClipText(): String? {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val c = cm.primaryClip
            if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(this)?.toString()?.let { if (it.isNotBlank()) return it }
        } catch (_: Exception) {
        }
        return clip.items.firstOrNull()?.text
    }

    fun onWheelSpun() = stats.add(Stats.WHEEL)

    /** Panelden (hesap makinesi, çark) metne ekleme; seçimi silmeden sonuna ekler. */
    fun insertFromPanel(text: String, statKey: String?) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        if (selStart >= 0 && selStart != selEnd) ic.setSelection(selEnd, selEnd)
        val b = ic.getTextBeforeCursor(1, 0)?.toString()
        ic.commitText(if (!b.isNullOrEmpty() && !b[0].isWhitespace()) " $text" else text, 1)
        statKey?.let { stats.add(it) }
        markEdit()
        closePanel()
        updateShift()
        updateSuggestions()
    }

    // ====================================================================== emoji arama

    fun startEmojiSearch() = startCapture(Capture.EMOJI)

    private fun startCapture(c: Capture) {
        closePanel()
        commitComposing(false)
        if (resizing) stopResize()
        if (c == Capture.EMOJI && emojiSearch == null) {
            val all = EmojiData.categories(this).flatMap { it.items }
            supportedEmoji = all.toHashSet()
            val flags = all.filter { EmojiSearch.countryCode(it) != null }
            emojiSearch = EmojiSearch({ assets.open("emoji_kw.txt") }, flags)
        }
        capture = c
        captureBuf.setLength(0)
        if (page != Page.ALPHA) setPage(Page.ALPHA)
        manualShift = false
        shift = if (c == Capture.EMOJI) 0 else 1
        kbView?.shiftState = shift
        refreshCapture()
    }

    private fun refreshCapture() {
        val c = capture ?: return
        val s = strip ?: return
        val q = captureBuf.toString()
        when (c) {
            Capture.EMOJI -> {
                s.showCapture("🔍 Emoji ara… (klavyeyle yaz)", if (q.isEmpty()) "" else "🔍 $q", "Tümü")
                showEmojiGrid(if (q.isBlank()) prefs.recentEmoji.take(60) else searchEmoji(q, 150))
            }
            Capture.ARCHIVE -> s.showCapture("🗂 Arşive eklenecek metni yazın…", q, "✓ Ekle")
            Capture.WHEEL -> s.showCapture("🎡 Çarka eklenecek seçeneği yazın…", q, "✓ Ekle")
        }
    }

    private fun searchEmoji(q: String, limit: Int): List<String> =
        (emojiSearch?.search(q, limit * 2) ?: emptyList()).filter { it in supportedEmoji }.take(limit)

    private fun showEmojiGrid(items: List<String>) {
        val g = emojiGrid ?: return
        val t = theme ?: return
        g.visibility = View.VISIBLE
        g.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val tv = (cv as? android.widget.TextView) ?: android.widget.TextView(this@KeybdService).apply {
                    gravity = android.view.Gravity.CENTER
                    textSize = 26f
                    setTextColor(t.text)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(48f))
                }
                tv.text = items[p]
                return tv
            }
        }
        g.setOnItemClickListener { _, _, p, _ -> commitEmoji(items[p]) }
    }

    /** Enter / ✓: girişi tamamla. */
    fun confirmCapture() {
        val c = capture ?: return
        val q = captureBuf.toString().trim()
        when (c) {
            Capture.EMOJI -> {
                val res = if (q.isBlank()) null else searchEmoji(q, 500)
                endCapture()
                theme?.let { showPanel(Panels.emoji(this, it, res, q)) }
            }
            Capture.ARCHIVE -> {
                if (q.isNotEmpty()) addToArchive(q)
                endCapture()
                showArchivePanel()
            }
            Capture.WHEEL -> {
                val st = wheelState
                if (q.isNotEmpty() && st != null) {
                    if (st.options.any { it.equals(q, ignoreCase = true) }) toast("Bu seçenek zaten var")
                    else if (st.options.size >= 16) toast("En fazla 16 seçenek")
                    else st.add(q)
                }
                endCapture()
                reopenWheel()
            }
        }
    }

    /** ✕: girişi iptal et, geldiği panele dön. */
    fun cancelCapture() {
        val c = capture
        endCapture()
        when (c) {
            Capture.ARCHIVE -> showArchivePanel()
            Capture.WHEEL -> reopenWheel()
            else -> {}
        }
    }

    private fun endCapture() {
        capture = null
        captureBuf.setLength(0)
        emojiGrid?.visibility = View.GONE
        strip?.endBusy()
        updateShift()
        updateSuggestions()
    }

    // ====================================================================== silineni geri getir / dokunma

    private fun markEdit() {
        lastEdit = SystemClock.uptimeMillis()
        tapWord = null
    }

    private fun noteDeleted(text: String) {
        if (text.isEmpty()) return
        if (!deleting) deleted.setLength(0)
        deleted.insert(0, text)
        if (deleted.length > 5000) deleted.setLength(5000)
        deleting = true
        val t = deleted.toString()
        strip?.setRestore(if (t.length >= 2 && t.any { it.isLetterOrDigit() }) t else null)
    }

    private fun clearRestore() {
        if (deleted.isEmpty() && !deleting) return
        deleted.setLength(0)
        deleting = false
        strip?.setRestore(null)
    }

    fun restoreDeleted() {
        val ic = currentInputConnection ?: return
        val t = deleted.toString()
        if (t.isEmpty()) return
        commitComposing(false)
        ic.commitText(t, 1)
        clearRestore()
        stats.add(Stats.RESTORE)
        markEdit()
        updateShift()
        updateSuggestions()
    }

    /** İmleç bir kelimenin içine/sonuna taşındıysa o kelime için düzeltmeleri gösterir. */
    private fun checkTapWord(pos: Int) {
        tapWord = null
        if (!useComposing() || panel != null || capture != null || pos < 0) return
        val ic = currentInputConnection ?: return
        val d = dict() ?: return
        val b = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
        val a = ic.getTextAfterCursor(40, 0)?.toString() ?: ""
        var i = b.length
        while (i > 0 && b[i - 1].isLetter()) i--
        var j = 0
        while (j < a.length && a[j].isLetter()) j++
        val left = b.substring(i)
        val right = a.substring(0, j)
        val w = left + right
        if (w.length < 2 || left.isEmpty()) return
        val res = d.suggest(w, null, true)
        val cands = ArrayList<String>()
        res.autoCorrect?.let { cands += matchCase(w, it) }
        for (x in res.words) {
            val c = matchCase(w, x)
            if (c != w && c !in cands) cands += c
            if (cands.size >= 3) break
        }
        if (cands.isEmpty()) return
        tapWord = Triple(pos - left.length, pos + right.length, w)
        strip?.setWords(cands, res.autoCorrect != null)
    }

    fun commitEmoji(e: String) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        ic.commitText(e, 1)
        markEdit()
        clearRestore()
        stats.add(Stats.EMOJI)
        val rec = prefs.recentEmoji.toMutableList()
        rec.remove(e)
        rec.add(0, e)
        prefs.recentEmoji = rec.take(40)
    }

    fun pasteText(text: String) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        ic.commitText(text, 1)
        closePanel()
    }

    fun setFontStyle(i: Int) {
        commitComposing(false)
        fontStyle = i
        strip?.setToolActive("font", i != 0)
        closePanel()
        if (i != 0) toast(FontStyles.names[i] + " stili açık")
        updateSuggestions()
    }

    fun toggleOneHand() {
        prefs.oneHand = if (prefs.oneHand == 0) 2 else 0
    }

    fun toggleIncognito() {
        prefs.incognito = !prefs.incognito
        strip?.setToolActive("incognito", prefs.incognito)
        toast(if (prefs.incognito) "Gizli mod açık: hiçbir şey öğrenilmez/kaydedilmez" else "Gizli mod kapalı")
    }

    fun cycleTheme() {
        val all = Themes.all(this)
        val idx = all.indexOfFirst { it.id == prefs.theme }
        val next = all[(idx + 1) % all.size]
        prefs.sp.edit().putString(Prefs.THEME, next.id).apply()
        toast("Tema: " + next.name)
    }

    fun showImePicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
    }

    // ====================================================================== ses

    fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Sesli yazma için mikrofon izni verin")
            startActivity(Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SettingsActivity.EXTRA_MIC, true))
            return
        }
        commitComposing(false)
        val v = voice ?: VoiceInput(this).also { voice = it }
        if (v.start(Layouts.speechTag(alphaId))) strip?.showMessage("🎤 Hazırlanıyor…")
        else toast("Bu cihazda konuşma tanıma servisi bulunamadı")
    }

    fun stopVoice() {
        if (voice?.listening == true) {
            voice?.stop()
            strip?.showMessage(null)
        }
    }

    fun onVoiceStatus(s: String) {
        strip?.showMessage(s)
    }

    companion object {
        private const val CHIP_CLIP = 0
        private const val CHIP_CALC = 1
        private const val CHIP_WHEEL = 2
    }

    fun onVoiceDone(text: String?, error: String?) {
        strip?.showMessage(null)
        if (error != null) toast(error)
        if (text.isNullOrBlank()) return
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(1, 0)?.toString()
        var t = text
        if (shift > 0 || b.isNullOrEmpty()) t = t.substring(0, 1).uppercase(Layouts.locale(alphaId)) + t.substring(1)
        if (!b.isNullOrEmpty() && !b[0].isWhitespace()) t = " $t"
        ic.commitText(t, 1)
        updateShift()
        updateSuggestions()
    }
}
