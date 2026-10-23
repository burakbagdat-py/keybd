package com.keybd.pro

import java.util.Locale
import kotlin.math.hypot

enum class KeyType { CHAR, TEXT, SHIFT, DELETE, ENTER, SPACE, TO_SYMBOLS, TO_SYMBOLS2, TO_ALPHA, LANG, EMOJI }

class Key(
    val type: KeyType,
    val label: String,
    val output: String = label,
    val width: Float = 1f,
    val popup: List<String> = emptyList(),
    val hint: String = "",
) {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val isLetter get() = type == KeyType.CHAR && label.length == 1 && label[0].isLetter()
    val isFunctional
        get() = type != KeyType.CHAR && type != KeyType.SPACE && type != KeyType.TEXT
}

class KeyRow(val keys: List<Key>, val heightFactor: Float = 1f)

class KeyboardLayout(val rows: List<KeyRow>, val units: Float, val isAlpha: Boolean)

object Layouts {
    const val TR_Q = "tr_q"
    const val TR_F = "tr_f"
    const val EN = "en"
    val ALL = listOf(TR_Q, TR_F, EN)

    enum class Mode { NORMAL, EMAIL, URL }

    class Opts(
        val numberRow: Boolean = false,
        val hints: Boolean = true,
        val multiLang: Boolean = true,
        val emojiKey: Boolean = true,
        val mode: Mode = Mode.NORMAL,
        val phone: Boolean = false,
    )

    fun name(id: String) = when (id) {
        TR_Q -> "Türkçe Q"
        TR_F -> "Türkçe F"
        else -> "English"
    }

    fun lang(id: String) = if (id == EN) "en" else "tr"
    fun locale(id: String): Locale = if (id == EN) Locale.US else Locale("tr", "TR")
    fun speechTag(id: String) = if (id == EN) "en-US" else "tr-TR"

    private val letterRows = mapOf(
        TR_Q to listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"),
        TR_F to listOf("fgğıodrnhpqw", "uieaütkmlyşx", "jövcçzsb.,"),
        EN to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    )

    private val letterPopups = mapOf(
        'a' to "âàáäãåāæª", 'e' to "éèêëēę€", 'i' to "îıïíìīİ", 'ı' to "îiïíì",
        'o' to "öôòóõøōœº", 'ö' to "oôòóõø", 'u' to "üûùúū", 'ü' to "uûùú",
        'c' to "çćč©", 'ç' to "cćč", 's' to "şśšß§", 'ş' to "sśš", 'g' to "ğ", 'ğ' to "g",
        'n' to "ñń", 'y' to "ýÿ¥", 'z' to "žźż", 'l' to "ł", 'r' to "ř®", 't' to "ţ™",
        'd' to "ď", 'k' to "", 'p' to "¶", 'x' to "×", '.' to "…!?", ',' to ";:",
    )

    private val digitPopups = mapOf(
        "1" to "¹½⅓¼⅛", "2" to "²⅔", "3" to "³¾⅜", "4" to "⁴", "5" to "⁵⅝", "6" to "⁶",
        "7" to "⁷⅞", "8" to "⁸", "9" to "⁹", "0" to "⁰ⁿ∅°",
    )

    private val symbolPopups = mapOf(
        "₺" to "$€£¥₹¢₽", "$" to "₺€£¥¢", "-" to "–—_·", "+" to "±", "(" to "<{[", ")" to ">}]",
        "/" to "\\÷", "\"" to "“”„«»", "'" to "‘’‚‹›`", "*" to "★†‡•", "!" to "¡‼", "?" to "¿‽",
        "&" to "§", "%" to "‰℅", "#" to "№", "=" to "≠≈∞", "_" to "‗", "@" to "©®",
        ":" to "…", ";" to "", "<" to "≤«‹", ">" to "≥»›", "π" to "Ωμ∑", "°" to "′″",
    )

    private fun chars(s: String) = s.map { it.toString() }

    private fun charKey(c: String, width: Float = 1f, extraFront: String? = null, popupMap: Map<String, String> = emptyMap()): Key {
        val base = if (c.length == 1) letterPopups[c[0]] ?: popupMap[c] ?: "" else popupMap[c] ?: ""
        val pop = ArrayList<String>()
        if (extraFront != null) pop += extraFront
        pop += chars(base)
        return Key(KeyType.CHAR, c, c, width, pop, extraFront ?: "")
    }

    private fun numberRow(units: Float): KeyRow {
        val w = units / 10f
        return KeyRow(chars("1234567890").map { Key(KeyType.CHAR, it, it, w, chars(digitPopups[it] ?: "")) }, 0.82f)
    }

    private fun bottomRow(units: Float, o: Opts, switchType: KeyType, switchLabel: String, alpha: Boolean): KeyRow {
        val keys = ArrayList<Key>()
        keys += Key(switchType, switchLabel, width = 1.5f)
        val comma = when (o.mode) {
            Mode.EMAIL -> Key(KeyType.CHAR, "@", "@", 1f, chars("_-+"))
            Mode.URL -> Key(KeyType.CHAR, "/", "/", 1f, chars(":_-~#?&="))
            else -> Key(KeyType.CHAR, ",", ",", 1f, chars(";:'\"-_"))
        }
        keys += comma
        if (o.multiLang && alpha) keys += Key(KeyType.LANG, "🌐")
        if (o.emojiKey) keys += Key(KeyType.EMOJI, "☺")
        val period = when (o.mode) {
            Mode.EMAIL, Mode.URL -> Key(KeyType.CHAR, ".", ".", 1f, listOf(".com", ".com.tr", ".net", ".org", ".edu.tr", ".io"))
            else -> Key(KeyType.CHAR, ".", ".", 1f, chars("…!?,;:-'\"#%&*"))
        }
        val enterW = 1.5f
        val used = keys.sumOf { it.width.toDouble() }.toFloat() + period.width + enterW
        keys += Key(KeyType.SPACE, " ", " ", (units - used).coerceAtLeast(2f))
        keys += period
        keys += Key(KeyType.ENTER, "↵", width = enterW)
        return KeyRow(keys)
    }

    fun alpha(id: String, o: Opts): KeyboardLayout {
        val rows = letterRows[id] ?: letterRows.getValue(TR_Q)
        val units = rows.maxOf { it.length }.toFloat()
        val list = ArrayList<KeyRow>()
        if (o.numberRow) list += numberRow(units)
        val digits = "1234567890"
        list += KeyRow(rows[0].mapIndexed { i, ch ->
            val d = if (!o.numberRow && i < 10) digits[i].toString() else null
            val k = charKey(ch.toString(), extraFront = d)
            if (!o.hints && d != null) Key(k.type, k.label, k.output, k.width, k.popup, "") else k
        })
        list += KeyRow(rows[1].map { charKey(it.toString()) })
        val sideW = ((units - rows[2].length) / 2f).coerceAtLeast(1f)
        val r3 = ArrayList<Key>()
        r3 += Key(KeyType.SHIFT, "⇧", width = sideW)
        rows[2].forEach { r3 += charKey(it.toString()) }
        r3 += Key(KeyType.DELETE, "⌫", width = sideW)
        list += KeyRow(r3)
        list += bottomRow(units, o, KeyType.TO_SYMBOLS, "?123", true)
        return KeyboardLayout(list, units, true)
    }

    fun symbols(o: Opts): KeyboardLayout {
        val u = 10f
        val r1 = KeyRow(chars("1234567890").map { Key(KeyType.CHAR, it, it, 1f, chars(digitPopups[it] ?: "")) })
        val r2 = KeyRow(chars("@#₺_&-+()/").map { charKey(it, popupMap = symbolPopups) })
        val r3 = ArrayList<Key>()
        r3 += Key(KeyType.TO_SYMBOLS2, "=\\<", width = 1.5f)
        chars("*\"':;!?").forEach { r3 += charKey(it, popupMap = symbolPopups) }
        r3 += Key(KeyType.DELETE, "⌫", width = 1.5f)
        return KeyboardLayout(listOf(r1, r2, KeyRow(r3), bottomRow(u, o, KeyType.TO_ALPHA, "ABC", false)), u, false)
    }

    fun symbols2(o: Opts): KeyboardLayout {
        val u = 10f
        val r1 = KeyRow(chars("~`|•√π÷×¶∆").map { charKey(it, popupMap = symbolPopups) })
        val r2 = KeyRow(chars("£€$¢^°={}\\").map { charKey(it, popupMap = symbolPopups) })
        val r3 = ArrayList<Key>()
        r3 += Key(KeyType.TO_SYMBOLS, "?123", width = 1.5f)
        chars("%©®™✓[]").forEach { r3 += charKey(it, popupMap = symbolPopups) }
        r3 += Key(KeyType.DELETE, "⌫", width = 1.5f)
        val r4 = ArrayList<Key>()
        r4 += Key(KeyType.TO_ALPHA, "ABC", width = 1.5f)
        r4 += charKey("<", popupMap = symbolPopups)
        r4 += Key(KeyType.SPACE, " ", " ", 5f)
        r4 += charKey(">", popupMap = symbolPopups)
        r4 += Key(KeyType.ENTER, "↵", width = 1.5f)
        return KeyboardLayout(listOf(r1, r2, KeyRow(r3), KeyRow(r4)), u, false)
    }

    fun numpad(o: Opts): KeyboardLayout {
        fun d(s: String, pop: String = "") = Key(KeyType.CHAR, s, s, 1f, chars(pop))
        val r1 = KeyRow(listOf(d("1"), d("2"), d("3"), Key(KeyType.DELETE, "⌫")))
        val r2 = KeyRow(listOf(d("4"), d("5"), d("6"), if (o.phone) d("+", "()") else d(".", ",")))
        val r3 = KeyRow(listOf(d("7"), d("8"), d("9"), if (o.phone) d("*", "#;") else d("-", "+/*")))
        val r4 = KeyRow(listOf(
            Key(KeyType.TO_SYMBOLS, "?123"), d("0", if (o.phone) "+" else ""),
            Key(KeyType.SPACE, " ", " ", 1f), Key(KeyType.ENTER, "↵"),
        ))
        return KeyboardLayout(listOf(r1, r2, r3, r4), 4f, false)
    }

    /** Düzeltme motoru için harflerin komşuları (yakın tuşlar). */
    fun neighbors(layout: KeyboardLayout): Map<Char, String> {
        val pos = HashMap<Char, Pair<Float, Float>>()
        var rowIdx = 0
        for (row in layout.rows) {
            val total = row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = (layout.units - total) / 2f
            for (k in row.keys) {
                if (k.isLetter) pos[k.label[0]] = Pair(x + k.width / 2f, rowIdx.toFloat())
                x += k.width
            }
            rowIdx++
        }
        val out = HashMap<Char, String>()
        for ((c, p) in pos) {
            val sb = StringBuilder()
            for ((c2, p2) in pos) if (c != c2 && hypot(p.first - p2.first, (p.second - p2.second) * 1.1f) < 1.45f) sb.append(c2)
            out[c] = sb.toString()
        }
        return out
    }
}
