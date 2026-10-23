package com.keybd.pro

import org.json.JSONArray
import org.json.JSONObject

/** Panoya kopyalanan metinlerin geçmişi (sabitleme destekli). */
class ClipHistory(private val prefs: Prefs) {
    class Item(val text: String, var pinned: Boolean, val time: Long)

    val items = ArrayList<Item>()
    var lastAdded: Item? = null
        private set

    init {
        try {
            val arr = JSONArray(prefs.sp.getString(Prefs.CLIPS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Item(o.getString("t"), o.optBoolean("p"), o.optLong("d"))
            }
        } catch (_: Exception) {
        }
    }

    fun add(text: String) {
        if (text.isBlank() || text.length > 5000) return
        val existing = items.firstOrNull { it.text == text }
        val pinned = existing?.pinned ?: false
        if (existing != null) items.remove(existing)
        val item = Item(text, pinned, System.currentTimeMillis())
        items.add(0, item)
        lastAdded = item
        var unpinned = 0
        val it = items.iterator()
        while (it.hasNext()) {
            val x = it.next()
            if (!x.pinned && ++unpinned > MAX) it.remove()
        }
        save()
    }

    fun remove(item: Item) {
        items.remove(item)
        if (lastAdded === item) lastAdded = null
        save()
    }

    fun togglePin(item: Item) {
        item.pinned = !item.pinned
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        lastAdded = null
        save()
    }

    /** Pano öğeleri: sabitlenenler önce. */
    fun sorted(): List<Item> = items.filter { it.pinned } + items.filter { !it.pinned }

    private fun save() {
        val arr = JSONArray()
        for (i in items) arr.put(JSONObject().put("t", i.text).put("p", i.pinned).put("d", i.time))
        prefs.sp.edit().putString(Prefs.CLIPS, arr.toString()).apply()
    }

    companion object {
        const val MAX = 40
    }
}
