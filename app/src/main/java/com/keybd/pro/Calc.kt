package com.keybd.pro

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/** Küçük hesap makinesi: + - * / × ÷ % ^ ve parantez; ondalık için virgül veya nokta. */
object Calc {
    private const val OPS = "+-*/×÷%^−"

    /** Metnin sonundaki matematik ifadesini bulur ("Fiyat 125*4" → "125*4"); yoksa null. */
    fun trailingExpression(text: String): String? {
        var i = text.length
        while (i > 0 && (text[i - 1].isDigit() || text[i - 1] in "$OPS()., ")) i--
        val e = text.substring(i).trim()
        if (e.none { it.isDigit() }) return null
        // En az bir işlem olmalı (baştaki eksi işareti sayılmaz)
        val body = e.trimStart('-', '−', '(', ' ')
        if (body.none { it in OPS }) return null
        return e
    }

    private val timesX = Regex("(?<=[\\d)])\\s*[xX]\\s*(?=[\\d(])")

    /**
     * Yazarken canlı sonuç: metnin sonunda "1x1", "12/4", "7-2", "3+4*2" gibi bir işlem varsa
     * sonucunu döndürür. Tarih (12/05/2026), telefon (0555-123-4567) gibi girdileri atlar.
     */
    fun live(textBefore: String): String? {
        if (textBefore.isEmpty()) return null
        val norm = timesX.replace(textBefore, "×")
        val last = norm.last()
        if (!last.isDigit() && last != ')' && last != '%') return null
        val e = trailingExpression(norm)?.trim() ?: return null
        if (e.count { it.isDigit() } > 15) return null
        val strong = e.count { it in "+*×÷%^" }
        val minus = e.count { it == '-' || it == '−' }
        val slash = e.count { it == '/' }
        if (strong == 0) {
            if (minus + slash >= 2) return null                       // tarih / telefon
            if (Regex("\\d{5,}").containsMatchIn(e)) return null     // uzun numaralar
            if (minus == 1 && e.trimStart().startsWith("-")) return null
        }
        if (e.startsWith("0") && e.length > 1 && e[1].isDigit()) return null
        return compute(e)
    }

    /** İfadeyi hesaplar; geçersizse null. */
    fun eval(expr: String): Double? {
        val p = Parser(normalize(expr))
        return try {
            val v = p.expression()
            if (p.pos != p.s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (_: Exception) {
            null
        }
    }

    /** Sonucu okunaklı biçimlendirir; virgül kullanıldıysa Türkçe ondalık ayırıcıyla. */
    fun format(v: Double, comma: Boolean): String {
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        var s = if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
        if (s == "-0") s = "0"
        return if (comma) s.replace('.', ',') else s
    }

    /** "125*4" → "500" gibi tek adımda hesaplayıp biçimlendirir. */
    fun compute(expr: String): String? {
        val v = eval(expr) ?: return null
        return format(v, usesComma(expr))
    }

    fun usesComma(expr: String) = expr.contains(',') && !expr.contains('.')

    private fun normalize(e: String): String {
        val comma = usesComma(e)
        val sb = StringBuilder()
        for (c in e) {
            when (c) {
                ' ' -> {}
                '×' -> sb.append('*')
                '÷' -> sb.append('/')
                '−' -> sb.append('-')
                ',' -> sb.append(if (comma) '.' else ',')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private class Parser(val s: String) {
        var pos = 0

        private fun peek(): Char? = s.getOrNull(pos)

        fun expression(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += term() }
                    '-' -> { pos++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; v *= power() }
                    '/' -> { pos++; val d = power(); if (d == 0.0) throw ArithmeticException(); v /= d }
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val b = unary()
            if (peek() == '^') {
                pos++
                return b.pow(power())
            }
            return b
        }

        private fun unary(): Double {
            if (peek() == '-') { pos++; return -unary() }
            if (peek() == '+') { pos++; return unary() }
            var v = primary()
            // Yüzde: "200*15%" → 200*0.15
            while (peek() == '%') { pos++; v /= 100.0 }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                pos++
                val v = expression()
                if (peek() == ')') pos++
                return v
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            if (start == pos) throw IllegalArgumentException()
            return s.substring(start, pos).toDouble()
        }
    }
}

/** Karar çarkı için metinden seçenek ayıklama. */
object WheelOptions {
    private val splitter = Regex(
        """\s*(?:,|;|/|\||\n|\s+veya\s+|\s+yoksa\s+|\s+ya\s+da\s+|\s+or\s+|\s+vs\.?\s+|\s+mı\s+|\s+mi\s+|\s+mu\s+|\s+mü\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val marker = Regex("""^\s*(?:[-•*·▪►➤✓✔☐☑]+|\(?\d{1,2}[.)\-:]|\(?[a-zA-Z][.)])\s*""")
    private val trailingQ = Regex("""(?:\s+(?:mı|mi|mu|mü))?\s*[?!.…]*\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val raw = if (lines.size >= 2) lines else splitter.split(text.trim())
        val out = ArrayList<String>()
        for (r in raw) {
            var o = marker.replace(r, "")
            o = trailingQ.replace(o, "").trim().trim('"', '\'', '“', '”')
            if (o.isEmpty() || o.length > 60) continue
            if (out.none { it.equals(o, ignoreCase = true) }) out += o
            if (out.size >= 12) break
        }
        return out
    }
}
