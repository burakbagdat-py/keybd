package com.keybd.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class EngineTest {

    companion object {
        private val assets = File("src/main/assets")
        lateinit var tr: Dictionary
        lateinit var en: Dictionary
        lateinit var trLayout: KeyboardLayout
        lateinit var enLayout: KeyboardLayout

        fun dict(lang: String, layout: KeyboardLayout): Dictionary {
            val dir = Files.createTempDirectory("kb").toFile()
            val d = Dictionary(lang, dir) { File(assets, "dict_$lang.txt").inputStream() }
            d.load()
            d.neighbors = Layouts.neighbors(layout)
            return d
        }

        @BeforeClass
        @JvmStatic
        fun setup() {
            trLayout = Layouts.alpha(Layouts.TR_Q, Layouts.Opts())
            enLayout = Layouts.alpha(Layouts.EN, Layouts.Opts())
            tr = dict("tr", trLayout)
            en = dict("en", enLayout)
        }

        /** KeyboardView.layoutKeys ile aynı yerleşim (1080px genişlik). */
        fun centers(l: KeyboardLayout): Pair<Map<Char, Pt>, Float> {
            val w = 1080f
            val rowH = 150f
            val unit = w / l.units
            var y = 0f
            val m = HashMap<Char, Pt>()
            for (row in l.rows) {
                val h = rowH * row.heightFactor
                val total = row.keys.sumOf { it.width.toDouble() }.toFloat()
                var x = (w - total * unit) / 2f
                for (k in row.keys) {
                    if (k.isLetter) m[k.label[0]] = Pt(x + k.width * unit / 2f, y + h / 2f)
                    x += k.width * unit
                }
                y += h
            }
            return m to unit
        }

        /** Bir kelime için insan benzeri (gürültülü) kaydırma yolu üret. */
        fun swipe(word: String, c: Map<Char, Pt>, unit: Float, seed: Int): List<Pt> {
            val r = Random(seed)
            val pts = ArrayList<Pt>()
            val keys = word.map { c.getValue(it) }
            fun jitter(p: Pt) = Pt(p.x + (r.nextFloat() - 0.5f) * unit * 0.5f, p.y + (r.nextFloat() - 0.5f) * unit * 0.5f)
            var prev = jitter(keys[0])
            pts += prev
            for (i in 1 until keys.size) {
                val nx = jitter(keys[i])
                for (s in 1..12) {
                    val t = s / 12f
                    pts += Pt(prev.x + (nx.x - prev.x) * t + (r.nextFloat() - 0.5f) * 6f, prev.y + (nx.y - prev.y) * t + (r.nextFloat() - 0.5f) * 6f)
                }
                prev = nx
            }
            return pts
        }
    }

    private fun auto(d: Dictionary, s: String) = d.suggest(s, null, true).autoCorrect

    @Test fun turkishCharacterCorrection() {
        assertEquals("görüşürüz", auto(tr, "gorusuruz"))
        assertEquals("teşekkürler", auto(tr, "tesekkurler"))
        assertEquals("nasılsın", auto(tr, "nasilsin"))
        assertEquals("çok", auto(tr, "cok"))
        assertEquals("şimdi", auto(tr, "simdi"))
        assertEquals("değil", auto(tr, "degil"))
    }

    @Test fun turkishTypoCorrection() {
        assertEquals("merhaba", auto(tr, "merhba"))
        assertEquals("kitap", auto(tr, "kitp"))
        assertEquals("geliyorum", auto(tr, "geliyoeum"))
    }

    @Test fun validWordsAreNotCorrected() {
        for (w in listOf("ve", "bir", "kus", "ama", "gel", "evet", "araba", "kim", "sen", "bak", "yani")) {
            val r = tr.suggest(w, null, true)
            assertTrue("$w geçerli olmalı", r.typedValid)
            assertNull("$w düzeltilmemeli", r.autoCorrect)
        }
        for (w in listOf("hello", "its", "ill", "were", "well", "hell", "shell", "id", "lets")) assertNull(w, auto(en, w))
    }

    @Test fun englishCorrection() {
        assertEquals("the", auto(en, "teh"))
        assertEquals("hello", auto(en, "helo"))
        assertEquals("don't", auto(en, "dont"))
        assertEquals("I", auto(en, "i"))
        assertEquals("because", auto(en, "becuase"))
    }

    @Test fun completions() {
        val r = tr.suggest("merh", null, true)
        assertTrue(r.words.toString(), r.words.contains("merhaba"))
        val e = en.suggest("tomo", null, true)
        assertTrue(e.words.toString(), e.words.contains("tomorrow"))
    }

    @Test fun learningAndPrediction() {
        val d = dict("tr", trLayout)
        assertFalse(d.isValid("keybdcik"))
        d.learn("keybdcik", null)
        d.learn("keybdcik", null)
        assertTrue(d.isValid("keybdcik"))
        assertNull("öğrenilen kelime düzeltilmemeli", d.suggest("keybdcik", null, true).autoCorrect)
        d.learn("nasılsın", "merhaba")
        d.learn("nasılsın", "merhaba")
        assertEquals("nasılsın", d.predict("merhaba").first())
        d.forget("keybdcik")
        assertFalse(d.isValid("keybdcik"))
    }

    @Test fun glideTurkish() {
        val (c, unit) = centers(trLayout)
        var ok = 0
        val words = listOf("merhaba", "selam", "nasılsın", "tamam", "evet", "teşekkürler", "bugün", "güzel", "geliyorum", "arkadaş")
        for ((i, w) in words.withIndex()) {
            val res = GlideDecoder.decode(swipe(w, c, unit, i), c, unit, tr, null)
            if (res.firstOrNull() == w) ok++ else println("glide tr: $w -> $res")
        }
        println("glide tr: $ok/${words.size}")
        assertTrue("glide doğruluğu düşük: $ok", ok >= 8)
    }

    @Test fun glideEnglish() {
        val (c, unit) = centers(enLayout)
        var ok = 0
        val words = listOf("hello", "world", "keyboard", "thanks", "people", "today", "really", "phone", "great", "think")
        for ((i, w) in words.withIndex()) {
            val res = GlideDecoder.decode(swipe(w, c, unit, i + 100), c, unit, en, null)
            if (res.firstOrNull() == w) ok++ else println("glide en: $w -> $res")
        }
        println("glide en: $ok/${words.size}")
        assertTrue("glide doğruluğu düşük: $ok", ok >= 8)
    }

    @Test fun speed() {
        val words = listOf("gorusuruz", "merhba", "kitp", "nasilsin", "tesekkurler", "a", "ge", "yapiyorsun")
        val t0 = System.nanoTime()
        repeat(20) { for (w in words) tr.suggest(w, "ben", true) }
        val ms = (System.nanoTime() - t0) / 1e6 / (20 * words.size)
        println("ortalama öneri süresi: %.2f ms".format(ms))
        assertTrue(ms < 30)
        val (c, unit) = centers(trLayout)
        val path = swipe("geliyorum", c, unit, 1)
        val g0 = System.nanoTime()
        repeat(10) { GlideDecoder.decode(path, c, unit, tr, null) }
        println("ortalama glide süresi: %.1f ms".format((System.nanoTime() - g0) / 1e6 / 10))
    }

    @Test fun fontStyles() {
        assertEquals("𝐀𝐛𝟏", FontStyles.apply(1, "Ab1"))
        assertEquals("ℎ", FontStyles.apply(2, "h"))
        assertEquals("ℂ", FontStyles.apply(6, "C"))
        assertEquals("ⓐ①", FontStyles.apply(9, "a1"))
        assertEquals("Ａ", FontStyles.apply(11, "A"))
    }

    @Test fun layouts() {
        assertEquals(12f, trLayout.units)
        val f = Layouts.alpha(Layouts.TR_F, Layouts.Opts(numberRow = true))
        assertEquals(5, f.rows.size)
        assertTrue(Layouts.neighbors(trLayout).getValue('ş').contains('i'))
        for (l in listOf(trLayout, enLayout, f, Layouts.symbols(Layouts.Opts()), Layouts.symbols2(Layouts.Opts()), Layouts.numpad(Layouts.Opts()))) {
            for (r in l.rows) assertTrue("satır taşıyor", r.keys.sumOf { it.width.toDouble() } <= l.units + 0.01)
        }
    }

    @Test fun updateVersionParsing() {
        assertEquals(6, Updater.versionCodeOf("v1.5"))
        assertEquals(43, Updater.versionCodeOf("1.42"))
        assertNull(Updater.versionCodeOf("v2-beta"))
    }
}
