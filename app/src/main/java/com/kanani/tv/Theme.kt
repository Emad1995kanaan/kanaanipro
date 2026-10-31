package com.kanani.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** ألوان اللوجو: أزرق كهربائي + فضي + كحلي غامق، مع طبقات زجاجية شفافة */
object K {
    val Bg get() = UiPrefs.theme.bg
    val Navy get() = UiPrefs.theme.top
    val Surface get() = UiPrefs.theme.surface
    val SurfaceHi get() = UiPrefs.theme.surfaceHi
    val Blue get() = UiPrefs.theme.accent
    val BlueGlow get() = UiPrefs.theme.glow
    val Silver = Color(0xFFE3E7EE)
    val Muted = Color(0xFF8A94A8)
    val Error = Color(0xFFFF6B6B)
    val GlassBorder = Color(0x33FFFFFF)
}

@Composable
fun KananiTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = K.Blue, background = K.Bg, surface = K.Surface,
        onSurface = K.Silver, onBackground = K.Silver, onPrimary = Color.White
    ),
    content = content
)

/**
 * تأثير زجاجي خفيف: خلفية شفافة بتدرّج + حد أبيض رفيع (بدون blur، فما بيأثر على الأداء).
 * strong = طبقة أغمق للقوائم اللي فوق الفيديو.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(
            Brush.verticalGradient(
                if (strong) listOf(K.Surface.copy(alpha = 0.78f), K.Bg.copy(alpha = 0.64f))
                else listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF))
            ),
            shape
        )
        .border(1.dp, K.GlassBorder, shape)

/**
 * عنصر قابل للتركيز بالريموت: بيكبر شوي ويطلع له إطار أزرق مضيء لما ينحط عليه التركيز.
 * هاد هو أساس سلاسة التنقل؛ كل الأزرار والبوسترات بتستخدمه.
 * onLongClick: ضغطة مطوّلة على OK (مثلاً لإضافة قناة للمفضلة).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.08f,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, tween(140), label = "focusScale")
    Box(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) K.BlueGlow else K.GlassBorder, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) { content(focused) }
}
