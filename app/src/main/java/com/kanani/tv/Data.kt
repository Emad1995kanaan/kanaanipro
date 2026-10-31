package com.kanani.tv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * تحميل سريع: نسخة محفوظة على الجهاز لكل قسم (قنوات/أفلام/مسلسلات).
 * أول ما تفتح التطبيق بيعرض المحتوى فوراً من النسخة المحفوظة، وبنفس الوقت بيحدّثها من السيرفر بالخلفية.
 */
object Store {
    data class Snap(val cats: List<Category> = emptyList(), val items: List<Item> = emptyList())

    private var dir: File? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(c: Context) { dir = File(c.applicationContext.filesDir, "snap2").apply { mkdirs() } }

    private fun file(tab: Tab, quick: Boolean = false) =
        dir?.let { File(it, "${tab.name}_${Session.username}${if (quick) "_q" else ""}.tsv") }

    fun exists(tab: Tab) = file(tab)?.let { it.exists() && it.length() > 8 } == true

    // صيغة نصية بسيطة (سطر لكل عنصر، الحقول مفصولة بـ Tab): قراءتها أسرع بكتير من JSON
    private fun clean(s: String?) = s?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ') ?: ""

    private fun writeFile(f: File?, s: Snap) {
        if (f == null) return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write("v2\n")
                for (c in s.cats) { w.write("C\t${clean(c.id)}\t${clean(c.name)}\n") }
                for (i in s.items) {
                    w.write("I\t${i.streamId ?: ""}\t${i.seriesId ?: ""}\t${clean(i.categoryId)}\t${clean(i.name)}\t" +
                        "${clean(i.icon)}\t${clean(i.cover)}\t${clean(i.rating)}\t${clean(i.added)}\t${clean(i.ext)}\n")
                }
            }
            tmp.renameTo(f)
        }
    }

    private fun readFile(f: File?, tab: Tab): Snap? = runCatching {
        if (f == null || !f.exists()) return null
        val cats = ArrayList<Category>(); val items = ArrayList<Item>(4096)
        f.bufferedReader().use { r ->
            if (r.readLine() != "v2") return null
            while (true) {
                val line = r.readLine() ?: break
                val p = line.split('\t')
                when (p[0]) {
                    "C" -> if (p.size >= 3) cats.add(Category(p[1], p[2]))
                    "I" -> if (p.size >= 10) items.add(
                        Item(
                            streamId = p[1].toIntOrNull(), seriesId = p[2].toIntOrNull(),
                            categoryId = p[3].ifEmpty { null }, name = p[4],
                            icon = p[5].ifEmpty { null }, cover = p[6].ifEmpty { null },
                            rating = p[7].ifEmpty { null }, added = p[8].ifEmpty { null },
                            ext = p[9].ifEmpty { null }
                        ).also { it.kind = tab }
                    )
                }
            }
        }
        Snap(cats, items).takeIf { it.items.isNotEmpty() }
    }.getOrNull()

    fun read(tab: Tab): Snap? = readFile(file(tab), tab)

    /** نسخة صغيرة (آخر 14 عنصر) عشان الرئيسية تظهر فوراً قبل ما تنقرأ القائمة الكاملة */
    fun readQuick(tab: Tab): List<Item> = readFile(file(tab, true), tab)?.items.orEmpty()
    fun writeQuick(tab: Tab, items: List<Item>) = writeFile(file(tab, true), Snap(emptyList(), items))

    private fun write(tab: Tab, s: Snap) {
        writeFile(file(tab), s)
        if (tab != Tab.LIVE) writeQuick(tab, latestItems(s.items))
    }

    /** من السيرفر (التصنيفات والعناصر بنفس الوقت) + حفظ */
    suspend fun fetch(tab: Tab): Snap = coroutineScope {
        val api = Api.get(); val u = Session.username; val p = Session.password
        val cats = async { runCatching { api.categories(u, p, tab.catAction) }.getOrDefault(emptyList()) }
        val items = api.streams(u, p, tab.action)
        items.forEach { it.kind = tab }
        Snap(cats.await(), items).also { write(tab, it) }
    }

    fun rows(tab: Tab, s: Snap): List<ContentRow> {
        val byCat = s.items.groupBy { it.categoryId }
        val rows = s.cats.mapNotNull { c -> byCat[c.id]?.let { ContentRow(c.name, it) } }
        return when {
            rows.isNotEmpty() -> rows
            s.items.isEmpty() -> emptyList()
            else -> listOf(ContentRow(tab.title, s.items))
        }
    }

    /** تحديث صامت بالخلفية */
    fun refresh(tab: Tab) {
        scope.launch {
            runCatching {
                val s = fetch(tab)
                if (tab == Tab.LIVE) { LiveData.cats = s.cats; LiveData.items = s.items }
                else HomeCache.rows[tab] = rows(tab, s)
            }
        }
    }
}

suspend fun cachedRows(tab: Tab): List<ContentRow> {
    HomeCache.rows[tab]?.let { return it }
    val snap = withContext(Dispatchers.IO) { Store.read(tab) }
    if (snap != null) {
        val r = withContext(Dispatchers.Default) { Store.rows(tab, snap) }
        HomeCache.rows[tab] = r
        Store.refresh(tab)
        return r
    }
    val r = withContext(Dispatchers.Default) { Store.rows(tab, Store.fetch(tab)) }
    HomeCache.rows[tab] = r
    return r
}

/** القنوات: من النسخة المحفوظة فوراً، وتحديث بالخلفية */
suspend fun ensureLive() {
    if (LiveData.items.isNotEmpty()) return
    val snap = withContext(Dispatchers.IO) { Store.read(Tab.LIVE) }
    if (snap != null) {
        LiveData.cats = snap.cats; LiveData.items = snap.items
        Store.refresh(Tab.LIVE)
        return
    }
    val s = Store.fetch(Tab.LIVE)
    LiveData.cats = s.cats; LiveData.items = s.items
}
