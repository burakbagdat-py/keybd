package com.keybd.pro

import org.json.JSONArray
import org.json.JSONObject

/** Kullanıcının kalıcı olarak sakladığı metinler (adresler, IBAN, hazır cevaplar…). */
class Archive(private val prefs: Prefs) {
    class Item(var text: String, val time: Long)

    val items = ArrayList<Item>()

    init {
        try {
            val arr = JSONArray(prefs.sp.getString(Prefs.ARCHIVE, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Item(o.getString("t"), o.optLong("d"))
            }
        } catch (_: Exception) {
        }
    }

    /** Eklendiyse true; boşsa veya zaten varsa false. */
    fun add(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 10000) return false
        val existing = items.firstOrNull { it.text == t }
        if (existing != null) {
            items.remove(existing)
            items.add(0, existing)
            save()
            return false
        }
        items.add(0, Item(t, System.currentTimeMillis()))
        save()
        return true
    }

    fun remove(item: Item) {
        items.remove(item)
        save()
    }

    fun moveUp(item: Item) {
        val i = items.indexOf(item)
        if (i > 0) {
            items.removeAt(i)
            items.add(i - 1, item)
            save()
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (i in items) arr.put(JSONObject().put("t", i.text).put("d", i.time))
        prefs.sp.edit().putString(Prefs.ARCHIVE, arr.toString()).apply()
    }
}
