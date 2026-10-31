package com.kanani.tv

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** إشعارات الرئيسية: تنبيه الاشتراك (فوق)، محتوى جديد (فوق صغير) */
object Notices {
    var banner by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var started = false
    var newChecked = false

    /** تنبيه انتهاء الاشتراك */
    suspend fun runStartup(ctx: Context) {
        // لو الدخول لسا عم يتحقق بالخلفية، انتظر تاريخ الاشتراك (حد أقصى 6 ث)
        var w = 0
        while (Session.expDate == null && w < 20) { delay(300); w++ }
        val exp = Session.expDate?.toLongOrNull()
        if (exp != null && exp > 0) {
            val left = exp * 1000 - System.currentTimeMillis()
            val days = ((left + 86_399_999L) / 86_400_000L).toInt()
            val text = when {
                left < 0 -> "انتهى اشتراكك، تواصل معنا للتجديد"
                days <= 1 -> "ينتهي اشتراكك اليوم"
                days <= 7 -> "ينتهي اشتراكك خلال $days أيام"
                else -> null
            }
            if (text != null) { banner = text; delay(7000); banner = null }
        }
    }

    /** محتوى جديد من آخر زيارة (أول مرة بس بيحفظ نقطة البداية بدون إشعار) */
    fun newContent(c: Context, movies: List<ContentRow>, series: List<ContentRow>): String? {
        val p = c.getSharedPreferences("seen", Context.MODE_PRIVATE)
        fun cnt(rows: List<ContentRow>, key: String): Int {
            val items = rows.flatMap { it.items }.distinctBy { it.id }
            val base = p.getLong(key, -1L)
            val max = items.maxOfOrNull { it.added?.toLongOrNull() ?: 0L } ?: 0L
            val n = if (base < 0) 0 else items.count { (it.added?.toLongOrNull() ?: 0L) > base }
            p.edit().putLong(key, maxOf(max, base)).apply()
            return n
        }
        val m = cnt(movies, "m"); val s = cnt(series, "s")
        return when {
            m > 0 && s > 0 -> "أُضيف $m فيلم و $s مسلسل جديد"
            m > 0 -> "أُضيف $m فيلم جديد"
            s > 0 -> "أُضيف $s مسلسل جديد"
            else -> null
        }
    }
}

@Composable
fun NoticeHost() {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = Notices.banner != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Text(
                Notices.banner.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(top = 14.dp).glass(RoundedCornerShape(30.dp), true).padding(26.dp, 10.dp)
            )
        }
        AnimatedVisibility(
            visible = Notices.toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Text(
                Notices.toast.orEmpty(), fontSize = 15.sp, color = K.Silver,
                modifier = Modifier.padding(top = 72.dp).glass(RoundedCornerShape(30.dp)).padding(22.dp, 8.dp)
            )
        }
    }
}
