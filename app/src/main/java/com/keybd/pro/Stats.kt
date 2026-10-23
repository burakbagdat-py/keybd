package com.keybd.pro

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Yazma istatistikleri. Sayaçlar bellekte tutulur ve klavye kapanınca kaydedilir
 * (her tuşta diske yazmamak için). Hiçbir veri cihazdan çıkmaz.
 */
class Stats(private val sp: SharedPreferences) {
    private val pending = HashMap<String, Long>()
    private var lastKey = 0L

    fun add(key: String, n: Long = 1) {
        pending[key] = (pending[key] ?: 0L) + n
    }

    /** Her tuş basışında çağrılır; 5 sn'den kısa aralıklar "aktif yazma süresi" sayılır. */
    fun keyPressed(now: Long) {
        val d = now - lastKey
        if (lastKey != 0L && d in 1..4999) add(ACTIVE_MS, d)
        lastKey = now
    }

    fun word(n: Long = 1) {
        add(WORDS, n)
        add(todayKey(), n)
    }

    fun flush() {
        if (pending.isEmpty()) return
        val e = sp.edit()
        if (!sp.contains(SINCE)) e.putLong(SINCE, System.currentTimeMillis())
        // Yalnızca bugünün sayacı tutulur
        val today = todayKey()
        for (k in sp.all.keys) if (k.startsWith(DAY_PREFIX) && k != today) e.remove(k)
        for ((k, v) in pending) e.putLong(k, sp.getLong(k, 0L) + v)
        e.apply()
        pending.clear()
    }

    companion object {
        const val WORDS = "st_words"
        const val CHARS = "st_chars"
        const val AUTOCORRECT = "st_autocorrect"
        const val GLIDE = "st_glide"
        const val EMOJI = "st_emoji"
        const val PICKS = "st_picks"
        const val RESTORE = "st_restore"
        const val CALC = "st_calc"
        const val WHEEL = "st_wheel"
        const val ACTIVE_MS = "st_active_ms"
        const val SINCE = "st_since"
        const val DAY_PREFIX = "st_day_"

        fun todayKey(): String = DAY_PREFIX + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        fun reset(sp: SharedPreferences) {
            val e = sp.edit()
            for (k in sp.all.keys) if (k.startsWith("st_")) e.remove(k)
            e.apply()
        }

        /** Dakikadaki kelime (aktif yazma süresine göre). */
        fun wpm(sp: SharedPreferences): Int {
            val ms = sp.getLong(ACTIVE_MS, 0L)
            if (ms < 30_000) return 0
            return (sp.getLong(WORDS, 0L) * 60_000.0 / ms).toInt()
        }
    }
}
