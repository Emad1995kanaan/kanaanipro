package com.kanani.tv

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── الثيمات (ألوان) ─────────────────────────

class KTheme(
    val id: String, val name: String,
    val accent: Color, val glow: Color,       // لون التمييز + توهّجه
    val top: Color, val bg: Color,            // أعلى وأسفل الخلفية
    val surface: Color, val surfaceHi: Color  // الأسطح
)

object Themes {
    val all = listOf(
        KTheme("blue", "أزرق", Color(0xFF2F6BFF), Color(0xFF6C9BFF), Color(0xFF0B1B4D), Color(0xFF070B14), Color(0xFF0F1626), Color(0xFF18233D)),
        KTheme("violet", "بنفسجي", Color(0xFF7C4DFF), Color(0xFFA78BFF), Color(0xFF1E1250), Color(0xFF0A0716), Color(0xFF141026), Color(0xFF221A40)),
        KTheme("emerald", "زمردي", Color(0xFF14B8A6), Color(0xFF5EEAD4), Color(0xFF063B3A), Color(0xFF04100F), Color(0xFF0C1A1A), Color(0xFF143030)),
        KTheme("sunset", "غروب", Color(0xFFFF6B4A), Color(0xFFFFA07A), Color(0xFF4A1A22), Color(0xFF120708), Color(0xFF1C0F10), Color(0xFF301A1C)),
        KTheme("nebula", "فضائي", Color(0xFF6C5CFF), Color(0xFF9DB8FF), Color(0xFF1A1450), Color(0xFF05060F), Color(0xFF0D0E22), Color(0xFF1B1D40)),
        KTheme("rose", "وردي", Color(0xFFEC4899), Color(0xFFF9A8D4), Color(0xFF4A1238), Color(0xFF12060E), Color(0xFF1C0F18), Color(0xFF301A2A))
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]
}

/** أشكال الصفحة الرئيسية */
enum class HomeStyle(val id: String, val title: String) {
    CLASSIC("classic", "كلاسيكي"),
    RAIL("rail", "سينمائي"),
    TILES("tiles", "بطاقات"),
    SHOWCASE("showcase", "عصري");

    companion object { fun byId(id: String?) = values().firstOrNull { it.id == id } ?: CLASSIC }
}

/** إعدادات المظهر المحفوظة: اللون، شكل الرئيسية، وحجم العرض (زوم) للمباشر والأفلام والمسلسلات */
object UiPrefs {
    var theme by mutableStateOf(Themes.all[0])
        private set
    var homeStyle by mutableStateOf(HomeStyle.CLASSIC)
        private set
    var zoom by mutableFloatStateOf(0.85f)
        private set
    var lang by mutableStateOf(Lang.AR)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        theme = Themes.byId(p.getString("theme", null))
        homeStyle = HomeStyle.byId(p.getString("home", null))
        zoom = p.getFloat("zoom", 0.85f).coerceIn(0.6f, 1f)
        lang = Lang.byId(p.getString("lang", null))
    }
    fun setTheme(c: Context, t: KTheme) { theme = t; prefs(c).edit().putString("theme", t.id).apply() }
    fun setHomeStyle(c: Context, s: HomeStyle) { homeStyle = s; prefs(c).edit().putString("home", s.id).apply() }
    fun setLang(c: Context, l: Lang) { lang = l; prefs(c).edit().putString("lang", l.id).apply() }
    fun setZoom(c: Context, z: Float) { zoom = z; prefs(c).edit().putFloat("zoom", z).apply() }
}

/**
 * "زوم أوت": بيصغّر كل شي جوا (الأحجام والخطوط) بنسبة UiPrefs.zoom،
 * فبيظهر تصنيفات وقنوات وبوسترات أكثر بنفس الصفحة. بنستخدمه بالمباشر والأفلام والمسلسلات.
 */
@Composable
fun Zoomed(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * UiPrefs.zoom, d.fontScale),
        content = content
    )
}

// ───────────────────────── أيقونات عصرية (مرسومة بالكود، بتدرّج لوني) ─────────────────────────

enum class KIconKind { LIVE, MOVIES, SERIES, SETTINGS, SEARCH }

@Composable
fun KIcon(
    kind: KIconKind, modifier: Modifier = Modifier, boxSize: Dp = 28.dp,
    colors: List<Color> = listOf(K.BlueGlow, Color.White)
) {
    Canvas(modifier.size(boxSize)) {
        val s = this.size.minDimension / 24f
        val brush = Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(24f, 24f))
        val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(s, s, pivot = Offset.Zero) {
            val p = Path()
            when (kind) {
                KIconKind.LIVE -> {          // شاشة بث مع هوائي ومثلث تشغيل
                    p.addRoundRect(RoundRect(3f, 8f, 21f, 20f, 3f, 3f))
                    p.moveTo(8.5f, 3.5f); p.lineTo(12f, 8f); p.lineTo(15.5f, 3.5f)
                    p.moveTo(10.5f, 11.5f); p.lineTo(10.5f, 16.5f); p.lineTo(14.8f, 14f); p.close()
                }
                KIconKind.MOVIES -> {        // كلاكيت
                    p.addRoundRect(RoundRect(3f, 10f, 21f, 20.5f, 2.5f, 2.5f))
                    p.addRoundRect(RoundRect(3f, 4.5f, 21f, 10f, 1.5f, 1.5f))
                    p.moveTo(8f, 4.5f); p.lineTo(10.5f, 10f)
                    p.moveTo(13f, 4.5f); p.lineTo(15.5f, 10f)
                    p.moveTo(10f, 13f); p.lineTo(10f, 18f); p.lineTo(14.5f, 15.5f); p.close()
                }
                KIconKind.SERIES -> {        // شاشتين فوق بعض (مواسم) مع تشغيل
                    p.moveTo(7f, 4.5f); p.lineTo(18.5f, 4.5f)
                    p.quadraticBezierTo(21f, 4.5f, 21f, 7f); p.lineTo(21f, 15f)
                    p.addRoundRect(RoundRect(3f, 8f, 17f, 20f, 2.8f, 2.8f))
                    p.moveTo(8.3f, 11.5f); p.lineTo(8.3f, 16.5f); p.lineTo(12.7f, 14f); p.close()
                }
                KIconKind.SETTINGS -> {      // ثلاث منزلقات
                    val ys = floatArrayOf(6f, 12f, 18f)
                    val xs = floatArrayOf(9f, 15f, 8f)
                    for (i in 0..2) {
                        p.moveTo(4f, ys[i]); p.lineTo(xs[i] - 3f, ys[i])
                        p.moveTo(xs[i] + 3f, ys[i]); p.lineTo(20f, ys[i])
                        p.addOval(Rect(xs[i] - 2.2f, ys[i] - 2.2f, xs[i] + 2.2f, ys[i] + 2.2f))
                    }
                }
                KIconKind.SEARCH -> {
                    p.addOval(Rect(4.5f, 4.5f, 15.5f, 15.5f))
                    p.moveTo(14.2f, 14.2f); p.lineTo(19.5f, 19.5f)
                }
            }
            drawPath(p, brush, style = stroke)
        }
    }
}

/** دائرة زجاجية فيها الأيقونة؛ بتتلوّن بلون الثيم لما ينحط عليها التركيز */
@Composable
fun IconBadge(kind: KIconKind, focused: Boolean, badge: Dp = 52.dp) {
    Box(
        Modifier.size(badge).clip(CircleShape).background(if (focused) K.Blue else Color(0x33FFFFFF)),
        Alignment.Center
    ) {
        KIcon(
            kind, boxSize = badge * 0.55f,
            colors = if (focused) listOf(Color.White, Color.White) else listOf(K.BlueGlow, Color.White)
        )
    }
}

// ───────────────────────── الإعدادات: الحساب + المظهر ─────────────────────────

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { firstFr.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).fillMaxHeight(0.94f).card().verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.logo), null, Modifier.height(80.dp))
                Column {
                    Text("الحساب: ${Session.username}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("ينتهي الاشتراك: ${formatExp(Session.expDate)}", fontSize = 13.sp, color = K.Muted)
                }
            }

            SettingsLabel("لون التطبيق")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Themes.all.forEachIndexed { i, t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusBox(
                            if (i == 0) Modifier.size(58.dp).focusRequester(firstFr) else Modifier.size(58.dp),
                            CircleShape, 1.1f, onClick = { UiPrefs.setTheme(ctx, t) }
                        ) { _ ->
                            Box(
                                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.glow, t.accent))),
                                Alignment.Center
                            ) {
                                if (t.id == UiPrefs.theme.id) Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(t.name, fontSize = 12.sp, color = K.Silver)
                    }
                }
            }

            SettingsLabel("شكل الصفحة الرئيسية")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeStyle.values().forEach { st ->
                    SettingsChip(st.title, UiPrefs.homeStyle == st) { UiPrefs.setHomeStyle(ctx, st) }
                }
            }

            SettingsLabel("اللغة / שפה")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Lang.values().forEach { lg ->
                    SettingsChip(lg.title, UiPrefs.lang == lg) { UiPrefs.setLang(ctx, lg) }
                }
            }

            Spacer(Modifier.height(4.dp))
            FocusBox(Modifier.fillMaxWidth().height(50.dp), RoundedCornerShape(14.dp), 1.03f, onClick = onLogout) { f ->
                Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                    Text("تسجيل الخروج", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) =
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = K.BlueGlow)

@Composable
private fun SettingsChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(Modifier, RoundedCornerShape(20.dp), 1.06f, onClick = onClick) { f ->
        Box(
            Modifier.background(
                when { selected -> K.Blue; f -> Color(0x33FFFFFF); else -> Color(0x1AFFFFFF) }
            ).padding(horizontal = 20.dp, vertical = 10.dp)
        ) { Text(title, fontSize = 15.sp, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
    }
}
