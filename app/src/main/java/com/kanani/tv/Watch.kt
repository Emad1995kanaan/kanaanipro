package com.kanani.tv

import android.content.Context
import com.google.gson.Gson

/** عنصر "آخر المشاهدات": الفيلم/المسلسل + وين وصل + باقي الحلقات (للانتقال التلقائي) */
data class WatchEntry(
    val kind: String,                       // MOVIES | SERIES
    val item: Item,
    val url: String,                        // رابط الفيلم أو الحلقة الحالية
    val label: String?,                     // اسم الحلقة (للمسلسلات)
    val queue: List<Pair<String, String>>,  // الحلقات اللي بعد الحالية
    val pos: Long,
    val dur: Long,
    val ts: Long
)

/** معلومات العنصر اللي عم يتشغّل، لحفظ التقدّم */
data class Track(val tab: Tab, val item: Item, val label: String?)

object Watch {
    private val gson = Gson()
    private fun prefs(c: Context) = c.getSharedPreferences("watch", Context.MODE_PRIVATE)

    private fun readAll(c: Context): List<WatchEntry> = runCatching {
        gson.fromJson(prefs(c).getString("list", "[]"), Array<WatchEntry>::class.java).toList()
    }.getOrDefault(emptyList())

    private fun writeAll(c: Context, l: List<WatchEntry>) {
        prefs(c).edit().putString("list", gson.toJson(l)).apply()
    }

    fun all(c: Context, tab: Tab): List<WatchEntry> =
        readAll(c).filter { it.kind == tab.name }.sortedByDescending { it.ts }
            .onEach { it.item.kind = tab }

    @Synchronized
    fun save(c: Context, e: WatchEntry) {
        val rest = readAll(c).filterNot { it.kind == e.kind && it.item.id == e.item.id }
        val mine = (listOf(e) + rest.filter { it.kind == e.kind }).sortedByDescending { it.ts }.take(30)
        writeAll(c, mine + rest.filter { it.kind != e.kind })
    }

    @Synchronized
    fun remove(c: Context, kind: String, itemId: Int) {
        writeAll(c, readAll(c).filterNot { it.kind == kind && it.item.id == itemId })
    }
}

/** مفضلة الأفلام والمسلسلات (ضغطة مطوّلة OK على البوستر) */
object VodFavs {
    private fun prefs(c: Context) = c.getSharedPreferences("favvod", Context.MODE_PRIVATE)
    fun get(c: Context, tab: Tab): Set<Int> =
        (prefs(c).getStringSet(tab.name, emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    fun toggle(c: Context, tab: Tab, id: Int): Set<Int> {
        val cur = get(c, tab).toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        prefs(c).edit().putStringSet(tab.name, cur.map { it.toString() }.toSet()).apply()
        return cur
    }
}
