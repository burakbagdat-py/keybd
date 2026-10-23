package com.keybd.pro

/** Unicode tabanlı süslü yazı stilleri (her uygulamada çalışır). */
object FontStyles {
    val names = listOf(
        "Normal", "Kalın", "İtalik", "Kalın İtalik", "El Yazısı", "Gotik", "Çift Çizgi",
        "Daktilo", "Sans Kalın", "Daire", "Kare", "Geniş", "Küçük Büyük", "Üstü Çizili", "Altı Çizili",
    )

    private const val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"

    private val doubleStruckHoles = mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)

    private fun math(c: Char, upper: Int, lower: Int, digit: Int = -1, holes: Map<Char, Int> = emptyMap()): String {
        holes[c]?.let { return String(Character.toChars(it)) }
        val cp = when (c) {
            in 'A'..'Z' -> upper + (c - 'A')
            in 'a'..'z' -> lower + (c - 'a')
            in '0'..'9' -> if (digit >= 0) digit + (c - '0') else return c.toString()
            else -> return c.toString()
        }
        return String(Character.toChars(cp))
    }

    fun apply(style: Int, text: String): String {
        if (style == 0) return text
        val sb = StringBuilder()
        for (c in text) {
            sb.append(
                when (style) {
                    1 -> math(c, 0x1D400, 0x1D41A, 0x1D7CE)
                    2 -> if (c == 'h') "ℎ" else math(c, 0x1D434, 0x1D44E)
                    3 -> math(c, 0x1D468, 0x1D482)
                    4 -> math(c, 0x1D4D0, 0x1D4EA)
                    5 -> math(c, 0x1D56C, 0x1D586)
                    6 -> math(c, 0x1D538, 0x1D552, 0x1D7D8, doubleStruckHoles)
                    7 -> math(c, 0x1D670, 0x1D68A, 0x1D7F6)
                    8 -> math(c, 0x1D5D4, 0x1D5EE, 0x1D7EC)
                    9 -> when (c) {
                        in 'A'..'Z' -> String(Character.toChars(0x24B6 + (c - 'A')))
                        in 'a'..'z' -> String(Character.toChars(0x24D0 + (c - 'a')))
                        '0' -> "⓪"
                        in '1'..'9' -> String(Character.toChars(0x2460 + (c - '1')))
                        else -> c.toString()
                    }
                    10 -> when (c) {
                        in 'A'..'Z' -> String(Character.toChars(0x1F130 + (c - 'A')))
                        in 'a'..'z' -> String(Character.toChars(0x1F130 + (c - 'a')))
                        else -> c.toString()
                    }
                    11 -> if (c in '!'..'~') (c + 0xFEE0).toString() else if (c == ' ') "　" else c.toString()
                    12 -> if (c in 'a'..'z') SMALL_CAPS[c - 'a'].toString() else c.toString()
                    13 -> if (c == ' ') " " else c + "̶"
                    14 -> if (c == ' ') " " else c + "̲"
                    else -> c.toString()
                }
            )
        }
        return sb.toString()
    }
}
