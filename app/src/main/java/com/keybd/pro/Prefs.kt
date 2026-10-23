package com.keybd.pro

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Tüm kullanıcı ayarları tek yerde. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d

    val layouts: List<String>
        get() = s(LAYOUTS, "${Layouts.TR_Q},${Layouts.EN}").split(',')
            .filter { it in Layouts.ALL }.ifEmpty { listOf(Layouts.TR_Q) }

    var currentLayout: String
        get() = s(CUR_LAYOUT, layouts.first()).let { if (it in layouts) it else layouts.first() }
        set(v) = sp.edit().putString(CUR_LAYOUT, v).apply()

    val theme get() = s(THEME, "dark")
    val heightPercent get() = i(HEIGHT, 100)
    val keyRadius get() = i(RADIUS, 8)
    val keyGap get() = i(GAP, 4)
    val keyBorders get() = b(BORDERS, true)
    val numberRow get() = b(NUMROW, false)
    val keyHints get() = b(HINTS, true)
    val keyPreview get() = b(PREVIEW, true)
    val fontScale get() = i(FONT_SCALE, 100)
    val emojiKey get() = b(EMOJI_KEY, true)

    val autoCap get() = b(AUTOCAP, true)
    val autoCorrect get() = b(AUTOCORRECT, true)
    val suggestions get() = b(SUGGEST, true)
    val nextWord get() = b(NEXTWORD, true)
    val emojiSuggest get() = b(EMOJI_SUGGEST, true)
    val doubleSpace get() = b(DOUBLESPACE, true)
    val smartPunct get() = b(SMART_PUNCT, true)
    val learn get() = b(LEARN, true)
    val glide get() = b(GLIDE, true)
    val glideTrail get() = b(GLIDE_TRAIL, true)
    val spaceCursor get() = b(SPACE_CURSOR, true)
    val deleteSwipe get() = b(DEL_SWIPE, true)

    val vibrate get() = b(VIBRATE, true)
    val vibrateMs get() = i(VIBRATE_MS, 12)
    val sound get() = b(SOUND, false)
    val soundVolume get() = i(SOUND_VOL, 40)
    val longPressMs get() = i(LONGPRESS, 300)

    val clipHistory get() = b(CLIP, true)
    val soundPack get() = s(SOUND_PACK, SoundPack.SYSTEM)
    val compact get() = b(COMPACT, false)
    val kbWidth get() = i(KB_WIDTH, 100)
    val stripHeight get() = i(STRIP_HEIGHT, 44)
    val toolbarPos get() = i(TOOLBAR_POS, 0)
    val swipeUp get() = b(SWIPE_UP, true)
    val touchFix get() = b(TOUCH_FIX, true)
    val bottomPad get() = i(BOTTOM_PAD, 0)

    var stripCollapsed: Boolean
        get() = b(STRIP_COLLAPSED, false)
        set(v) = sp.edit().putBoolean(STRIP_COLLAPSED, v).apply()

    var oneHand: Int
        get() = i(ONEHAND, 0)
        set(v) = sp.edit().putInt(ONEHAND, v).apply()

    var incognito: Boolean
        get() = b(INCOGNITO, false)
        set(v) = sp.edit().putBoolean(INCOGNITO, v).apply()

    var recentEmoji: List<String>
        get() = s(RECENT_EMOJI, "").split('\u0001').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString(RECENT_EMOJI, v.joinToString("\u0001")).apply()

    var shortcuts: Map<String, String>
        get() {
            val raw = sp.getString(SHORTCUTS, null) ?: return DEFAULT_SHORTCUTS
            return try {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { o.getString(it) }
            } catch (e: Exception) {
                emptyMap()
            }
        }
        set(v) = sp.edit().putString(SHORTCUTS, JSONObject(v).toString()).apply()

    companion object {
        const val FILE = "keybd"
        const val LAYOUTS = "layouts"
        const val CUR_LAYOUT = "cur_layout"
        const val THEME = "theme"
        const val HEIGHT = "height"
        const val RADIUS = "radius"
        const val GAP = "gap"
        const val BORDERS = "borders"
        const val NUMROW = "number_row"
        const val HINTS = "hints"
        const val PREVIEW = "preview"
        const val FONT_SCALE = "font_scale"
        const val EMOJI_KEY = "emoji_key"
        const val AUTOCAP = "autocap"
        const val AUTOCORRECT = "autocorrect"
        const val SUGGEST = "suggest"
        const val NEXTWORD = "next_word"
        const val EMOJI_SUGGEST = "emoji_suggest"
        const val DOUBLESPACE = "double_space"
        const val SMART_PUNCT = "smart_punct"
        const val LEARN = "learn"
        const val GLIDE = "glide"
        const val GLIDE_TRAIL = "glide_trail"
        const val SPACE_CURSOR = "space_cursor"
        const val DEL_SWIPE = "del_swipe"
        const val VIBRATE = "vibrate"
        const val VIBRATE_MS = "vibrate_ms"
        const val SOUND = "sound"
        const val SOUND_VOL = "sound_vol"
        const val LONGPRESS = "longpress"
        const val CLIP = "clip_history"
        const val ONEHAND = "one_hand"
        const val INCOGNITO = "incognito"
        const val RECENT_EMOJI = "recent_emoji"
        const val SHORTCUTS = "shortcuts"
        const val CLIPS = "clips"
        const val SOUND_PACK = "sound_pack"
        const val COMPACT = "compact"
        const val KB_WIDTH = "kb_width"
        const val STRIP_HEIGHT = "strip_height"
        const val STRIP_COLLAPSED = "strip_collapsed"
        const val TOOLBAR_POS = "toolbar_pos"
        const val ARCHIVE = "archive"
        const val SWIPE_UP = "swipe_up"
        const val TOUCH_FIX = "touch_fix"
        const val BOTTOM_PAD = "bottom_pad"

        /** "Boyutları sıfırla" ile varsayılana dönen anahtarlar. */
        val SIZE_KEYS = listOf(HEIGHT, KB_WIDTH, FONT_SCALE, RADIUS, GAP, STRIP_HEIGHT, COMPACT, STRIP_COLLAPSED,
            TOOLBAR_POS, ONEHAND, BOTTOM_PAD)

        /** "Görünümü sıfırla": boyutlar + tema ve görünüm seçenekleri. */
        val LOOK_KEYS = SIZE_KEYS + listOf(THEME, BORDERS, HINTS, PREVIEW, EMOJI_KEY, NUMROW,
            "custom_bg", "custom_key", "custom_fn", "custom_text", "custom_accent", "custom_image", "custom_key_alpha", "custom_dim")

        /** "Tüm ayarları sıfırla" sırasında korunan kullanıcı verileri. */
        val KEEP_ON_RESET = setOf(ARCHIVE, CLIPS, RECENT_EMOJI, SHORTCUTS, "pending_update", "last_update_check")
        const val DICT_RESET = "dict_reset"
        const val DICT_RELOAD = "dict_reload"

        /** Bu anahtarlar değişince klavye görünümü yeniden kurulur. */
        val VIEW_KEYS = setOf(
            LAYOUTS, THEME, HEIGHT, RADIUS, GAP, BORDERS, NUMROW, HINTS, PREVIEW, FONT_SCALE,
            EMOJI_KEY, GLIDE, GLIDE_TRAIL, SPACE_CURSOR, DEL_SWIPE, LONGPRESS, ONEHAND, Themes.CUSTOM_VER,
            COMPACT, KB_WIDTH, STRIP_HEIGHT, STRIP_COLLAPSED, TOOLBAR_POS, SWIPE_UP, TOUCH_FIX, BOTTOM_PAD
        )

        val DEFAULT_SHORTCUTS = mapOf(
            "slm" to "selam",
            "nbr" to "naber",
            "tşk" to "teşekkürler",
            "tsk" to "teşekkürler",
            "kib" to "kendine iyi bak",
            "trh" to "{tarih}",
            "sst" to "{saat}",
            "iyg" to "iyi geceler",
            "brb" to "be right back",
            "omw" to "on my way",
        )
    }
}
