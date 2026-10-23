package com.keybd.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeaturesTest {

    @Test fun calculator() {
        assertEquals("500", Calc.compute("125*4"))
        assertEquals("7", Calc.compute("1+2*3"))
        assertEquals("9", Calc.compute("(1+2)*3"))
        assertEquals("3,5", Calc.compute("1,5+2"))
        assertEquals("2.5", Calc.compute("5/2"))
        assertEquals("30", Calc.compute("200×15%"))
        assertEquals("4", Calc.compute("12÷3"))
        assertEquals("1024", Calc.compute("2^10"))
        assertEquals("-3", Calc.compute("2−5"))
        assertEquals("0.3333333333", Calc.compute("1/3")?.take(12))
        assertNull(Calc.compute("5/0"))
        assertNull(Calc.compute("abc"))
    }

    @Test fun trailingExpression() {
        assertEquals("125*4", Calc.trailingExpression("Toplam fiyat 125*4"))
        assertEquals("(2+3)*4", Calc.trailingExpression("sonuç: (2+3)*4"))
        assertNull(Calc.trailingExpression("2026 yılında"))
        assertNull(Calc.trailingExpression("-5"))
        assertNull(Calc.trailingExpression("merhaba"))
    }

    @Test fun wheelOptions() {
        assertEquals(listOf("pizza", "burger"), WheelOptions.parse("pizza mı burger mi?"))
        assertEquals(listOf("Sinema", "Tiyatro", "Konser"), WheelOptions.parse("Sinema, Tiyatro veya Konser"))
        assertEquals(listOf("Kırmızı", "Mavi", "Yeşil"), WheelOptions.parse("1. Kırmızı\n2. Mavi\n3. Yeşil"))
        assertEquals(listOf("evde kalalım", "dışarı çıkalım"), WheelOptions.parse("evde kalalım ya da dışarı çıkalım"))
        assertEquals(listOf("tea", "coffee"), WheelOptions.parse("tea or coffee"))
        assertEquals(listOf("A", "B"), WheelOptions.parse("- A\n- B\n- a"))
        assertTrue(WheelOptions.parse("sadece tek bir cümle").size < 2)
        assertTrue(WheelOptions.parse(null).isEmpty())
    }

    @Test fun emojiSearch() {
        val es = EmojiSearch({ File("src/main/assets/emoji_kw.txt").inputStream() }, listOf("🇹🇷", "🇩🇪"))
        assertEquals("❤️", es.search("kalp").first())
        assertTrue(es.search("love").take(3).contains("❤️"))
        assertEquals("🍕", es.search("pizza").first())
        assertTrue(es.search("gülmek").take(5).contains("😂"))
        assertTrue(es.search("kedi").take(5).any { it == "🐱" || it == "🐈" })
        assertEquals("🇹🇷", es.search("türkiye").first())
        assertEquals("🇹🇷", es.search("turkiye").first())
        assertTrue(es.search("zzqxw").isEmpty())
        assertTrue(es.search("").isEmpty())
    }

    @Test fun liveMath() {
        assertEquals("1", Calc.live("1x1"))
        assertEquals("12", Calc.live("3x4"))
        assertEquals("12", Calc.live("3 X 4"))
        assertEquals("1", Calc.live("1/1"))
        assertEquals("0", Calc.live("1-1"))
        assertEquals("11", Calc.live("Toplam 3+4*2"))
        assertEquals("2.5", Calc.live("5/2"))
        assertEquals("3", Calc.live("1,5x2"))
        assertNull(Calc.live("1x"))
        assertNull(Calc.live("12/05/2026"))      // tarih
        assertNull(Calc.live("0555-123-4567"))   // telefon
        assertNull(Calc.live("2024-01-01"))
        assertNull(Calc.live("-5"))
        assertNull(Calc.live("box"))
        assertNull(Calc.live("2026"))
    }

    @Test fun wheelWeights() {
        val st = Panels.WheelState(mutableListOf("A", "B", "C"), ArrayList(), "")
        st.equalize()
        assertEquals(100, st.weights.sum())
        assertEquals(listOf(34, 33, 33), st.weights)
        st.change(0, 20)
        assertEquals(100, st.weights.sum())
        assertEquals(54, st.weights[0])
        repeat(30) { st.change(1, -5) }
        assertEquals(100, st.weights.sum())
        assertEquals(1, st.weights[1])
        st.add("D")
        assertEquals(4, st.options.size)
        assertEquals(100, st.weights.sum())
        st.removeAt(0)
        assertEquals(100, st.weights.sum())
        assertEquals(listOf("B", "C", "D"), st.options)
    }
}
