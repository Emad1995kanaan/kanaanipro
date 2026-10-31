package com.kanani.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.LocalTextStyle

/** لغات التطبيق */
enum class Lang(val id: String, val title: String) {
    AR("ar", "العربية"), HE("he", "עברית"), EN("en", "English");
    companion object { fun byId(id: String?) = values().firstOrNull { it.id == id } ?: AR }
}

/**
 * الترجمة: كل نص واجهة مكتوب بالعربي بيمرّ من هون. بالعربي بيضل متل ما هو، وبالعبري بيتحوّل.
 * بس النصوص المعروفة (حرفياً أو بالقوالب) بتتترجم، فأسماء القنوات والأفلام اللي من السيرفر ما بتتأثر.
 */
private val he = mapOf(
    "القنوات" to "ערוצים", "القنوات المباشرة" to "שידורים חיים", "أفلام" to "סרטים", "مسلسلات" to "סדרות",
    "إعدادات" to "הגדרות", "الإعدادات" to "הגדרות",
    "تعذّر تحميل المحتوى" to "לא ניתן לטעון את התוכן", "إعادة المحاولة" to "נסה שוב",
    "أحدث الأفلام" to "סרטים אחרונים", "أحدث المسلسلات" to "סדרות אחרונות",
    "عرض المسلسل" to "צפה בסדרה", "تشغيل" to "נגן", "مسلسل" to "סדרה", "فيلم" to "סרט",
    "أُضيف حديثاً" to "נוסף לאחרונה",
    "تعذّر تحميل القنوات" to "לא ניתן לטעון את הערוצים",
    "القناة مش متوفرة حالياً… بنعيد المحاولة تلقائياً" to "הערוץ אינו זמין כרגע… מנסה שוב אוטומטית",
    "ما في قنوات مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي قناة لإضافتها." to
        "אין ערוצים מועדפים עדיין.\nלחץ לחיצה ארוכה על OK מעל ערוץ כדי להוסיף.",
    "لا توجد قنوات" to "אין ערוצים",
    "▲ ▼ تغيير القناة   •   OK القائمة" to "▲ ▼ החלפת ערוץ   •   OK תפריט",
    "الكل" to "הכל", "المفضلة" to "מועדפים", "آخر المشاهدات" to "נצפו לאחרונה",
    "أزرق" to "כחול", "بنفسجي" to "סגול", "زمردي" to "אזמרגד", "غروب" to "שקיעה", "فضائي" to "חללי", "وردي" to "ורוד",
    "كلاسيكي" to "קלאסי", "سينمائي" to "קולנועי", "بطاقات" to "כרטיסים", "عصري" to "מודרני",
    "لون التطبيق" to "צבע האפליקציה", "شكل الصفحة الرئيسية" to "מראה מסך הבית", "تسجيل الخروج" to "התנתקות",
    "اسم المستخدم أو كلمة المرور غير صحيحة" to "שם משתמש או סיסמה שגויים",
    "تعذّر الاتصال، تأكد من الإنترنت وحاول مرة ثانية" to "החיבור נכשל, בדוק את האינטרנט ונסה שוב",
    "اسم المستخدم" to "שם משתמש", "كلمة المرور" to "סיסמה", "دخول" to "כניסה",
    "لا يوجد محتوى في هذا القسم" to "אין תוכן במדור זה", "لا يوجد محتوى" to "אין תוכן",
    "ما في مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي بوستر لإضافته" to
        "אין מועדפים עדיין.\nלחץ לחיצה ארוכה על OK מעל כרזה כדי להוסיף",
    "لا توجد حلقات متاحة" to "אין פרקים זמינים",
    "تخطي المقدمة" to "דלג על הפתיח", "تخطي المقدمة  ▲" to "דלג על הפתיח  ▲",
    "الحلقة التالية" to "הפרק הבא",
    "OK للانتقال الآن  •  سهم لتحت للإلغاء" to "OK למעבר מיידי  •  חץ למטה לביטול",
    "+10 ث  ▶▶" to "+10 שנ׳  ▶▶", "◀◀  -10 ث" to "◀◀  -10 שנ׳",
    "ترجمة  ▼" to "כתוביות  ▼", "الترجمة" to "כתוביות", "بدون ترجمة" to "ללא כתוביות", "لا توجد ترجمة متاحة" to "אין כתוביות זמינות",
    "انتهى اشتراكك، تواصل معنا للتجديد" to "המנוי שלך הסתיים, צור קשר לחידוש",
    "ينتهي اشتراكك اليوم" to "המנוי שלך מסתיים היום"
)

private fun t(s: String) = he[s] ?: s

private val heRx: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^ينتهي الاشتراك خلال: (.*)$") to { m -> "המנוי מסתיים בתאריך: ${m.groupValues[1]}" },
    Regex("^ينتهي الاشتراك: (.*)$") to { m -> "המנוי מסתיים: ${m.groupValues[1]}" },
    Regex("^حتى (\\d{2}-\\d{2}-\\d{4}|—)$") to { m -> "עד ${m.groupValues[1]}" },
    Regex("^الحساب: (.*)$") to { m -> "חשבון: ${m.groupValues[1]}" },
    Regex("^الآن: (.*)$", RegexOption.DOT_MATCHES_ALL) to { m ->
        "עכשיו: " + m.groupValues[1].replace("   •   التالي: ", "   •   הבא: ") },
    Regex("^ينتهي اشتراكك خلال (\\d+) أيام$") to { m -> "המנוי שלך מסתיים בעוד ${m.groupValues[1]} ימים" },
    Regex("^أُضيف (\\d+) فيلم و (\\d+) مسلسل جديد$") to { m -> "נוספו ${m.groupValues[1]} סרטים ו-${m.groupValues[2]} סדרות חדשות" },
    Regex("^أُضيف (\\d+) فيلم جديد$") to { m -> "נוספו ${m.groupValues[1]} סרטים חדשים" },
    Regex("^أُضيف (\\d+) مسلسل جديد$") to { m -> "נוספו ${m.groupValues[1]} סדרות חדשות" },
    Regex("^ابحث في (.*)$") to { m -> "חפש ב: ${t(m.groupValues[1])}" },
    Regex("^لا توجد نتائج لـ \"(.*)\"$") to { m -> "אין תוצאות עבור \"${m.groupValues[1]}\"" },
    Regex("^الموسم (\\d+)$") to { m -> "עונה ${m.groupValues[1]}" },
    Regex("^الموسم (\\S+) - الحلقة (\\S+)(.*)$", RegexOption.DOT_MATCHES_ALL) to { m ->
        "עונה ${m.groupValues[1]} - פרק ${m.groupValues[2]}${m.groupValues[3]}" },
    Regex("^الحلقة (\\S+)  •  (.*)$") to { m -> "פרק ${m.groupValues[1]}  •  ${m.groupValues[2]}" },
    Regex("^انتقال تلقائي بعد (\\d+) ث$") to { m -> "מעבר אוטומטי בעוד ${m.groupValues[1]} שנ׳" }
)

private val en = mapOf(
    "القنوات" to "Channels", "القنوات المباشرة" to "Live TV", "أفلام" to "Movies", "مسلسلات" to "Series",
    "إعدادات" to "Settings", "الإعدادات" to "Settings",
    "تعذّر تحميل المحتوى" to "Couldn't load content", "إعادة المحاولة" to "Try again",
    "أحدث الأفلام" to "Latest movies", "أحدث المسلسلات" to "Latest series",
    "عرض المسلسل" to "View series", "تشغيل" to "Play", "مسلسل" to "Series", "فيلم" to "Movie",
    "أُضيف حديثاً" to "Recently added",
    "تعذّر تحميل القنوات" to "Couldn't load channels",
    "القناة مش متوفرة حالياً… بنعيد المحاولة تلقائياً" to "Channel unavailable right now… retrying automatically",
    "ما في قنوات مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي قناة لإضافتها." to
        "No favorite channels yet.\nLong-press OK on a channel to add it.",
    "لا توجد قنوات" to "No channels",
    "▲ ▼ تغيير القناة   •   OK القائمة" to "▲ ▼ Change channel   •   OK Menu",
    "الكل" to "All", "المفضلة" to "Favorites", "آخر المشاهدات" to "Continue watching",
    "أزرق" to "Blue", "بنفسجي" to "Violet", "زمردي" to "Emerald", "غروب" to "Sunset", "فضائي" to "Nebula", "وردي" to "Rose",
    "كلاسيكي" to "Classic", "سينمائي" to "Cinematic", "بطاقات" to "Tiles", "عصري" to "Modern",
    "لون التطبيق" to "App color", "شكل الصفحة الرئيسية" to "Home layout", "تسجيل الخروج" to "Log out",
    "اسم المستخدم أو كلمة المرور غير صحيحة" to "Incorrect username or password",
    "تعذّر الاتصال، تأكد من الإنترنت وحاول مرة ثانية" to "Connection failed. Check your internet and try again",
    "اسم المستخدم" to "Username", "كلمة المرور" to "Password", "دخول" to "Sign in",
    "لا يوجد محتوى في هذا القسم" to "No content in this section", "لا يوجد محتوى" to "No content",
    "ما في مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي بوستر لإضافته" to
        "No favorites yet.\nLong-press OK on a poster to add it",
    "لا توجد حلقات متاحة" to "No episodes available",
    "تخطي المقدمة" to "Skip intro", "تخطي المقدمة  ▲" to "Skip intro  ▲",
    "الحلقة التالية" to "Next episode",
    "OK للانتقال الآن  •  سهم لتحت للإلغاء" to "OK to play now  •  Down to cancel",
    "+10 ث  ▶▶" to "+10 s  ▶▶", "◀◀  -10 ث" to "◀◀  -10 s",
    "ترجمة  ▼" to "Subtitles  ▼", "الترجمة" to "Subtitles", "بدون ترجمة" to "Off", "لا توجد ترجمة متاحة" to "No subtitles available",
    "انتهى اشتراكك، تواصل معنا للتجديد" to "Your subscription has ended. Contact us to renew",
    "ينتهي اشتراكك اليوم" to "Your subscription ends today"
)

private val enRx: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^ينتهي الاشتراك خلال: (.*)$") to { m -> "Subscription ends on: ${m.groupValues[1]}" },
    Regex("^ينتهي الاشتراك: (.*)$") to { m -> "Subscription ends: ${m.groupValues[1]}" },
    Regex("^حتى (\\d{2}-\\d{2}-\\d{4}|—)$") to { m -> "Until ${m.groupValues[1]}" },
    Regex("^الحساب: (.*)$") to { m -> "Account: ${m.groupValues[1]}" },
    Regex("^الآن: (.*)$", RegexOption.DOT_MATCHES_ALL) to { m ->
        "Now: " + m.groupValues[1].replace("   •   التالي: ", "   •   Next: ") },
    Regex("^ينتهي اشتراكك خلال (\\d+) أيام$") to { m -> "Your subscription ends in ${m.groupValues[1]} days" },
    Regex("^أُضيف (\\d+) فيلم و (\\d+) مسلسل جديد$") to { m -> "${m.groupValues[1]} new movies and ${m.groupValues[2]} new series added" },
    Regex("^أُضيف (\\d+) فيلم جديد$") to { m -> "${m.groupValues[1]} new movies added" },
    Regex("^أُضيف (\\d+) مسلسل جديد$") to { m -> "${m.groupValues[1]} new series added" },
    Regex("^ابحث في (.*)$") to { m -> "Search in ${ten(m.groupValues[1])}" },
    Regex("^لا توجد نتائج لـ \"(.*)\"$") to { m -> "No results for \"${m.groupValues[1]}\"" },
    Regex("^الموسم (\\d+)$") to { m -> "Season ${m.groupValues[1]}" },
    Regex("^الموسم (\\S+) - الحلقة (\\S+)(.*)$", RegexOption.DOT_MATCHES_ALL) to { m ->
        "Season ${m.groupValues[1]} - Episode ${m.groupValues[2]}${m.groupValues[3]}" },
    Regex("^الحلقة (\\S+)  •  (.*)$") to { m -> "Episode ${m.groupValues[1]}  •  ${m.groupValues[2]}" },
    Regex("^انتقال تلقائي بعد (\\d+) ث$") to { m -> "Playing next in ${m.groupValues[1]} s" }
)
private fun ten(s: String) = en[s] ?: s

fun tr(s: String): String {
    val lang = UiPrefs.lang
    if (lang == Lang.AR) return s
    val map = if (lang == Lang.HE) he else en
    val rxs = if (lang == Lang.HE) heRx else enRx
    map[s]?.let { return it }
    for ((rx, f) in rxs) rx.matchEntire(s)?.let { return f(it) }
    return s
}

/** بديل Text: بيترجم النص حسب لغة التطبيق (بنفس معاملات material3.Text) */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textDecoration: TextDecoration? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, textDecoration = textDecoration, style = style
    )
}
