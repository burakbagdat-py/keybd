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
}
