package com.keybd.pro

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Ayarları, kısayolları, öğrenilen kelimeleri, pano sabitlerini, istatistikleri ve tema
 * fotoğrafını tek bir JSON dosyasına yedekler / geri yükler.
 */
object Backup {
    private val DATA_FILES = listOf("user_tr.txt", "user_en.txt", "bigram_tr.txt", "bigram_en.txt")
    private val SKIP = setOf("pending_update", "last_update_check", Prefs.DICT_RESET, Prefs.DICT_RELOAD)

    fun export(ctx: Context, out: OutputStream) {
        val sp = Prefs(ctx).sp
        val prefs = JSONObject()
        for ((k, v0) in sp.all) {
            if (k in SKIP || v0 == null) continue
            // Pano geçmişinden yalnızca sabitlenenler yedeklenir (gizlilik)
            val v: Any = if (k == Prefs.CLIPS && v0 is String) pinnedOnly(v0) else v0
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            prefs.put(k, o)
        }
        val files = JSONObject()
        for (f in DATA_FILES) {
            val file = File(ctx.filesDir, f)
            if (file.exists()) files.put(f, file.readText())
        }
        val root = JSONObject()
            .put("app", "keybd")
            .put("format", 1)
            .put("created", System.currentTimeMillis())
            .put("version", BuildConfig.VERSION_NAME)
            .put("prefs", prefs)
            .put("files", files)
        val bg = File(ctx.filesDir, Themes.BG_FILE)
        if (bg.exists() && bg.length() < 6_000_000) root.put("bg", Base64.encodeToString(bg.readBytes(), Base64.NO_WRAP))
        out.write(root.toString().toByteArray(Charsets.UTF_8))
    }

    private fun pinnedOnly(json: String): String = try {
        val arr = org.json.JSONArray(json)
        val out = org.json.JSONArray()
        for (i in 0 until arr.length()) arr.getJSONObject(i).let { if (it.optBoolean("p")) out.put(it) }
        out.toString()
    } catch (_: Exception) {
        "[]"
    }

    /** Geri yükler; dosya Keybd yedeği değilse false döner. */
    fun import(ctx: Context, inp: InputStream): Boolean {
        val root = JSONObject(inp.readBytes().toString(Charsets.UTF_8))
        if (root.optString("app") != "keybd") return false
        val prefs = root.optJSONObject("prefs") ?: return false
        val sp = Prefs(ctx).sp
        val e = sp.edit()
        for (k in sp.all.keys) if (k !in SKIP) e.remove(k)
        for (k in prefs.keys()) {
            if (k in SKIP) continue
            val o = prefs.getJSONObject(k)
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "s" -> e.putString(k, o.getString("v"))
            }
        }
        e.commit()
        val files = root.optJSONObject("files")
        for (f in DATA_FILES) {
            val file = File(ctx.filesDir, f)
            val text = files?.optString(f, null as String?)
            if (text != null) file.writeText(text) else file.delete()
        }
        val bg = root.optString("bg", "")
        if (bg.isNotEmpty()) File(ctx.filesDir, Themes.BG_FILE).writeBytes(Base64.decode(bg, Base64.DEFAULT))
        // Klavye servisine haber ver: sözlükleri ve görünümü yeniden yükle
        sp.edit().putLong(Prefs.DICT_RELOAD, System.currentTimeMillis())
            .putLong(Themes.CUSTOM_VER, System.currentTimeMillis()).apply()
        return true
    }
}
