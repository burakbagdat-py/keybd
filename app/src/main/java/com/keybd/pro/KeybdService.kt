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
    private var clipChip: String? = null
    private var clipChipShownFor: ClipHistory.Item? = null

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
        val s = SuggestionStrip(this, t)
        strip = s
        root.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(44f)))
        s.setToolActive("incognito", prefs.incognito)
        s.setToolActive("font", fontStyle != 0)
        s.setToolActive("onehand", prefs.oneHand != 0)

        val kv = KeyboardView(this, this)
        kbView = kv
        applyViewPrefs(kv, t)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        kbRow = row
        val oh = prefs.oneHand
        if (oh == 2) row.addView(oneHandSide(t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))
        row.addView(kv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (oh == 0) 1f else 0.84f))
        if (oh == 1) row.addView(oneHandSide(t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))

        val c = FrameLayout(this)
        content = c
        c.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val nb = insets.getInsets(WindowInsets.Type.navigationBars())
                v.setPadding(nb.left, 0, nb.right, nb.bottom)
                insets
            }
        }
        kv.kb = buildLayout()
        return root
    }

    private fun applyViewPrefs(kv: KeyboardView, t: KbTheme) {
        kv.theme = t
        kv.rowHeight = dp(54f) * prefs.heightPercent / 100f
        kv.radius = dp(prefs.keyRadius.toFloat())
        kv.gap = dp(prefs.keyGap.toFloat())
        kv.borders = prefs.keyBorders
        kv.showHints = prefs.keyHints
        kv.preview = prefs.keyPreview
        kv.glideEnabled = prefs.glide
        kv.glideTrail = prefs.glideTrail
        kv.spaceCursor = prefs.spaceCursor
        kv.deleteSwipe = prefs.deleteSwipe
        kv.longPressMs = prefs.longPressMs.toLong()
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
        closePanel()

        kbView?.enterLabel = enterLabel(info)
        setPage(if (numericField) Page.NUM else Page.ALPHA)
        updateShift()

        val last = clip.lastAdded
        clipChip = if (last != null && last !== clipChipShownFor && System.currentTimeMillis() - last.time < 90_000 && !isPassword) {
            clipChipShownFor = last
            last.text
        } else null
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopVoice()
        closePanel()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
        composingFromGlide = false
        dicts.values.forEach { it.save() }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
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
        if (composing.isEmpty() && !inDeleteSwipe) updateShift()
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
        if (prefs.vibrate) {
            try {
                val ms = prefs.vibrateMs.toLong().coerceAtLeast(1)
                vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
        }
        if (prefs.sound) {
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
        return s.uppercase(Layouts.locale(alphaId))
    }

    fun glideAllowed() = page == Page.ALPHA && fontStyle == 0 && allowSuggest && dict() != null

    fun onKey(k: Key) {
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

    fun setCapsLock() {
        shift = 2
        manualShift = true
        kbView?.shiftState = 2
    }

    private fun updateShift() {
        if (shift == 2 || manualShift) return
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
                composing.clear()
                composingFromGlide = false
                ic.commitText("", 1)
                updateShift()
                updateSuggestions()
                return
            }
            val cp = composing.codePointBefore(composing.length)
            composing.setLength(composing.length - Character.charCount(cp))
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            updateSuggestions()
            if (composing.isEmpty()) updateShift()
            return
        }
        if (selStart >= 0 && selStart != selEnd) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(32, 0)?.toString()
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        ic.deleteSurroundingText(lastGraphemeLength(before), 0)
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
        if (composing.isNotEmpty()) {
            composing.clear()
            composingFromGlide = false
            ic.commitText("", 1)
            updateSuggestions()
            return
        }
        if (selStart >= 0 && selStart != selEnd) {
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
        ic.deleteSurroundingText((b.length - i).coerceAtLeast(1), 0)
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
            val sc = prefs.shortcuts[typed.lowercase()]
            val auto = currentAuto
            if (sc != null) out = sc
            else if (auto != null && allowAutocorrect && prefs.autoCorrect && typed.lowercase() !in ignoreAutocorrect) out = auto
        }
        val prev = prevWord()
        ic.commitText(out, 1)
        learn(out, prev, false)
        composing.clear()
        composingFromGlide = false
        current = null
        currentAuto = null
        return out
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

    private fun updateSuggestions() {
        val s = strip ?: return
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
        val shortcut = prefs.shortcuts[typed.lowercase()]
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
        val chip = clipChip
        if (chip != null) {
            s.showClipChip(chip)
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
        val chip = clipChip
        if (chip != null && composing.isEmpty() && word == chip) {
            clipChip = null
            ic.commitText(chip, 1)
            updateSuggestions()
            return
        }
        clipChip = null
        lastAutoCorrect = null
        val prev = prevWord()
        if (composing.isNotEmpty()) {
            ic.commitText(word, 1)
            if (!isEmoji(word)) learn(word, prev, true)
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
        clipChip = null
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
        ic.setSelection(swipeAnchor - len, swipeAnchor)
        keyFeedback(null)
    }

    fun endDeleteSwipe() {
        val ic = currentInputConnection
        if (inDeleteSwipe && swipeN > 0) ic?.commitText("", 1)
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
        kbRow?.visibility = View.INVISIBLE
        c.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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

    fun commitEmoji(e: String) {
        val ic = currentInputConnection ?: return
        commitComposing(false)
        ic.commitText(e, 1)
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
