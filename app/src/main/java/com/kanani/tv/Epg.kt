package com.kanani.tv

import android.util.Base64
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class EpgItem(
    val title: String? = null,
    @SerializedName("start_timestamp") val start: String? = null,
    @SerializedName("stop_timestamp") val stop: String? = null
)
data class EpgResponse(@SerializedName("epg_listings") val list: List<EpgItem>? = null)

/** برنامج على القناة: العنوان + البداية والنهاية (ثواني) */
data class Program(val title: String, val start: Long, val stop: Long)

/** دليل البث: البرنامج الحالي والجاي لكل قناة (كاش 3 دقايق، وأقصى 4 طلبات بنفس الوقت) */
object Epg {
    private val cache = HashMap<Int, Pair<Long, List<Program>>>()
    private val gate = Semaphore(4)

    private fun dec(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val d = runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
        return if (d != null && d.isNotBlank() && d.none { it == '�' || (it.isISOControl() && it != '\n') }) d.trim() else s
    }

    suspend fun get(id: Int): List<Program> {
        synchronized(cache) {
            cache[id]?.let { if (System.currentTimeMillis() - it.first < 180_000) return it.second }
        }
        val list = try {
            gate.withPermit {
                Api.get().epg(Session.username, Session.password, "get_short_epg", id, 3).list.orEmpty()
                    .mapNotNull { e ->
                        val t = dec(e.title)
                        val a = e.start?.toLongOrNull(); val b = e.stop?.toLongOrNull()
                        if (t.isBlank() || a == null || b == null) null else Program(t, a, b)
                    }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        synchronized(cache) { cache[id] = System.currentTimeMillis() to list }
        return list
    }

    fun current(l: List<Program>): Program? {
        val n = System.currentTimeMillis() / 1000
        return l.firstOrNull { n >= it.start && n < it.stop }
    }
    fun next(l: List<Program>, cur: Program?): Program? =
        if (cur == null) null else l.firstOrNull { it.start >= cur.stop }
    fun progress(p: Program): Float {
        val n = System.currentTimeMillis() / 1000
        return if (p.stop > p.start) ((n - p.start).toFloat() / (p.stop - p.start)).coerceIn(0f, 1f) else 0f
    }
}
