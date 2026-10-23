package com.keybd.pro

import android.content.Context
import android.graphics.Color
import android.os.Build

data class KbTheme(
    val id: String,
    val name: String,
    val bg: Int,
    val key: Int,
    val keyFn: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
    val shadow: Int,
    val strip: Int,
    val stripText: Int,
    val dark: Boolean,
    /** Özel çizim stili (ör. simbiyot). */
    val style: Int = STYLE_NORMAL,
    /** Arka planda kullanıcının fotoğrafı var mı. */
    val image: Boolean = false,
) {
    companion object {
        const val STYLE_NORMAL = 0
        const val STYLE_SYMBIOTE = 1
    }
}

object Themes {
    private fun c(s: String) = Color.parseColor(s)

    private val presets = listOf(
        KbTheme("dark", "Koyu", c("#1B1C1F"), c("#3A3B3F"), c("#2A2B2F"), c("#55575C"),
            c("#F1F3F4"), c("#9AA0A6"), c("#8AB4F8"), c("#10223F"), c("#0E0F11"), c("#1B1C1F"), c("#E8EAED"), true),
        KbTheme("light", "Açık", c("#E8EAED"), c("#FFFFFF"), c("#CDD1D6"), c("#B8BDC4"),
            c("#202124"), c("#5F6368"), c("#1A73E8"), c("#FFFFFF"), c("#A7ABB1"), c("#E8EAED"), c("#202124"), false),
        KbTheme("amoled", "AMOLED Siyah", c("#000000"), c("#161616"), c("#0B0B0B"), c("#333333"),
            c("#FFFFFF"), c("#8C8C8C"), c("#00E5FF"), c("#000000"), c("#000000"), c("#000000"), c("#FFFFFF"), true),
        KbTheme("ocean", "Okyanus", c("#0B2239"), c("#16395C"), c("#102C49"), c("#24527F"),
            c("#E3F2FD"), c("#90CAF9"), c("#4FC3F7"), c("#06243B"), c("#061525"), c("#0B2239"), c("#E3F2FD"), true),
        KbTheme("sunset", "Gün Batımı", c("#2D1B2E"), c("#4A2C46"), c("#3A2238"), c("#6B3F63"),
            c("#FFE8D6"), c("#E6A57E"), c("#FF8A65"), c("#2D1B2E"), c("#1A0F1A"), c("#2D1B2E"), c("#FFE8D6"), true),
        KbTheme("forest", "Orman", c("#13221A"), c("#22392C"), c("#1A2D22"), c("#2F5140"),
            c("#E6F4EA"), c("#9CCFB0"), c("#66BB6A"), c("#0D1F13"), c("#0A140E"), c("#13221A"), c("#E6F4EA"), true),
        KbTheme("pink", "Pembe", c("#FCE4EC"), c("#FFFFFF"), c("#F8BBD0"), c("#F48FB1"),
            c("#4A1430"), c("#AD5C7F"), c("#E91E63"), c("#FFFFFF"), c("#E2A8BD"), c("#FCE4EC"), c("#4A1430"), false),
        KbTheme("purple", "Gece Moru", c("#1A1530"), c("#2D2550"), c("#221C40"), c("#41367A"),
            c("#EDE7F6"), c("#B39DDB"), c("#B388FF"), c("#1A1530"), c("#0F0C1E"), c("#1A1530"), c("#EDE7F6"), true),
        KbTheme("venom", "Venom Simbiyot", c("#000000"), c("#0B0B0B"), c("#050505"), c("#FFFFFF"),
            c("#FFFFFF"), c("#9E9E9E"), c("#FFFFFF"), c("#000000"), c("#000000"), c("#000000"), c("#FFFFFF"), true,
            style = KbTheme.STYLE_SYMBIOTE),
        KbTheme("retro", "Retro", c("#D9D2C3"), c("#F4EFE4"), c("#B9B09C"), c("#A39A86"),
            c("#3B3226"), c("#7C6F5B"), c("#D35400"), c("#FFFFFF"), c("#9C937F"), c("#D9D2C3"), c("#3B3226"), false),
    )

    fun all(ctx: Context): List<KbTheme> {
        val list = ArrayList<KbTheme>()
        materialYou(ctx)?.let { list += it }
        list += presets
        list += custom(ctx)
        return list
    }

    fun get(ctx: Context, id: String): KbTheme = all(ctx).firstOrNull { it.id == id } ?: presets[0]

    const val CUSTOM_BG = "custom_bg"
    const val CUSTOM_KEY = "custom_key"
    const val CUSTOM_FN = "custom_fn"
    const val CUSTOM_TEXT = "custom_text"
    const val CUSTOM_ACCENT = "custom_accent"
    const val CUSTOM_IMAGE = "custom_image"
    const val CUSTOM_KEY_ALPHA = "custom_key_alpha"
    const val CUSTOM_DIM = "custom_dim"
    const val CUSTOM_VER = "custom_ver"
    const val BG_FILE = "kb_bg.jpg"

    private fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    /** Kullanıcının kendi seçtiği renklerden (ve isteğe bağlı fotoğraftan) tema üretir. */
    fun custom(ctx: Context): KbTheme {
        val sp = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val bg = sp.getInt(CUSTOM_BG, c("#101820"))
        val key = sp.getInt(CUSTOM_KEY, c("#2A3A4A"))
        val fn = sp.getInt(CUSTOM_FN, mix(key, bg, 0.45f))
        val text = sp.getInt(CUSTOM_TEXT, c("#FFFFFF"))
        val accent = sp.getInt(CUSTOM_ACCENT, c("#FFB300"))
        val image = sp.getBoolean(CUSTOM_IMAGE, false) && java.io.File(ctx.filesDir, BG_FILE).exists()
        val alpha = if (image) sp.getInt(CUSTOM_KEY_ALPHA, 170) else 255
        val dark = lum(bg) < 0.5
        val pressed = if (lum(key) < 0.5) mix(key, Color.WHITE, 0.25f) else mix(key, Color.BLACK, 0.2f)
        return KbTheme(
            "custom", "Özel temam",
            if (image) Color.TRANSPARENT else bg,
            withAlpha(key, alpha), withAlpha(fn, alpha), pressed,
            text, withAlpha(text, 150), accent, if (lum(accent) > 0.6) Color.BLACK else Color.WHITE,
            if (image) Color.TRANSPARENT else mix(bg, Color.BLACK, 0.5f),
            if (image) Color.TRANSPARENT else bg, text, dark, image = image,
        )
    }

    /** Android 12+ duvar kağıdı renklerinden üretilen tema. */
    private fun materialYou(ctx: Context): KbTheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val r = ctx.resources
        val night = (r.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        fun col(id: Int) = ctx.getColor(id)
        return if (night) {
            KbTheme("you", "Material You", col(android.R.color.system_neutral1_900),
                col(android.R.color.system_neutral2_700), col(android.R.color.system_neutral2_800),
                col(android.R.color.system_accent2_600), col(android.R.color.system_neutral1_50),
                col(android.R.color.system_neutral2_300), col(android.R.color.system_accent1_200),
                col(android.R.color.system_accent1_900), Color.BLACK,
                col(android.R.color.system_neutral1_900), col(android.R.color.system_neutral1_50), true)
        } else {
            KbTheme("you", "Material You", col(android.R.color.system_neutral1_50),
                col(android.R.color.system_neutral1_10), col(android.R.color.system_accent2_100),
                col(android.R.color.system_accent2_200), col(android.R.color.system_neutral1_900),
                col(android.R.color.system_neutral2_600), col(android.R.color.system_accent1_600),
                Color.WHITE, col(android.R.color.system_neutral2_300),
                col(android.R.color.system_neutral1_50), col(android.R.color.system_neutral1_900), false)
        }
    }
}
