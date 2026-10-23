package com.keybd.pro

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/**
 * Tuş sesi paketleri (assets/sounds, tools/gen_sounds.py ile üretilir).
 * Her paketin key (birkaç çeşit), space, enter ve del sesleri vardır.
 */
class SoundPack(private val ctx: Context) {
    enum class Kind(val file: String) { KEY("key"), SPACE("space"), ENTER("enter"), DEL("del") }

    private var pool: SoundPool? = null
    private var loaded: String? = null
    private val ids = HashMap<Kind, MutableList<Int>>()
    private var pending = 0

    /** Paketteki tüm sesler yüklenince bir kez çağrılır. */
    var onReady: (() -> Unit)? = null

    fun load(pack: String) {
        if (pack == loaded) return
        release()
        loaded = pack
        if (pack == SYSTEM) return
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        pool = p
        p.setOnLoadCompleteListener { _, _, _ ->
            if (--pending == 0) {
                val r = onReady
                onReady = null
                r?.invoke()
            }
        }
        val files = try {
            ctx.assets.list("sounds")?.filter { it.startsWith(pack + "_") && it.endsWith(".wav") } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (f in files.sorted()) {
            val kind = Kind.entries.firstOrNull { f.startsWith("${pack}_${it.file}_") } ?: continue
            try {
                ctx.assets.openFd("sounds/$f").use { fd ->
                    pending++
                    ids.getOrPut(kind) { ArrayList() } += p.load(fd, 1)
                }
            } catch (_: Exception) {
            }
        }
    }

    /** volume 0..1 */
    fun play(kind: Kind, volume: Float) {
        val p = pool ?: return
        val list = ids[kind] ?: ids[Kind.KEY] ?: return
        if (list.isEmpty()) return
        val id = list[Random.nextInt(list.size)]
        // Hafif perde değişimi sesi doğal yapar (piyano notaları hariç)
        val rate = if (loaded == "piano" || loaded == "funny") 1f else 0.96f + Random.nextFloat() * 0.08f
        p.play(id, volume, volume, 1, 0, rate)
    }

    fun release() {
        pool?.release()
        pool = null
        ids.clear()
        pending = 0
        loaded = null
    }

    companion object {
        const val SYSTEM = "system"
        val PACKS = listOf(
            SYSTEM to "Sistem sesi",
            "mech" to "Mekanik (tıkırtılı, mavi switch)",
            "thock" to "Mekanik (tok, derin)",
            "typewriter" to "Daktilo (satır sonunda zil)",
            "soft" to "Yumuşak",
            "bubble" to "Baloncuk",
            "piano" to "Piyano notaları",
            "funny" to "Komik 🤡 (boing, ördek, düdük…)",
        )

        fun name(id: String) = PACKS.firstOrNull { it.first == id }?.second ?: PACKS[0].second

        fun kindOf(k: Key?): Kind = when (k?.type) {
            KeyType.SPACE -> Kind.SPACE
            KeyType.ENTER -> Kind.ENTER
            KeyType.DELETE -> Kind.DEL
            else -> Kind.KEY
        }
    }
}
