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
)

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
        KbTheme("retro", "Retro", c("#D9D2C3"), c("#F4EFE4"), c("#B9B09C"), c("#A39A86"),
            c("#3B3226"), c("#7C6F5B"), c("#D35400"), c("#FFFFFF"), c("#9C937F"), c("#D9D2C3"), c("#3B3226"), false),
    )

    fun all(ctx: Context): List<KbTheme> {
        val list = ArrayList<KbTheme>()
        materialYou(ctx)?.let { list += it }
        list += presets
        return list
    }

    fun get(ctx: Context, id: String): KbTheme = all(ctx).firstOrNull { it.id == id } ?: presets[0]

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
