package com.kanani.tv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.Locale

/** المفضلة وآخر قناة انشافت (محفوظة على الجهاز) */
object Favs {
    private fun prefs(c: Context) = c.getSharedPreferences("fav", Context.MODE_PRIVATE)

    fun get(c: Context): Set<Int> =
        (prefs(c).getStringSet("ids", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    fun toggle(c: Context, id: Int): Set<Int> {
        val cur = get(c).toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        prefs(c).edit().putStringSet("ids", cur.map { it.toString() }.toSet()).apply()
        return cur
    }

    fun lastChannel(c: Context): Int = prefs(c).getInt("last", -1)
    fun saveLast(c: Context, id: Int) = prefs(c).edit().putInt("last", id).apply()
}

/** كاش القنوات: الدخول مرة ثانية على اللايف بيكون فوري */
object LiveData {
    var cats: List<Category> = emptyList()
    var items: List<Item> = emptyList()
    fun reset() { cats = emptyList(); items = emptyList() }
}

private data class Group(val id: String, val name: String, val items: List<Item>)

fun num(n: Int, digits: Int) = "%0${digits}d".format(Locale.ENGLISH, n)

/**
 * شاشة البث المباشر:
 * - الفيديو بملء الشاشة، وفوقه قائمة زجاجية (تصنيفات + قنوات مرقّمة) تظهر أول ما تدخل.
 * - OK على قناة = تشغيلها وإخفاء القائمة.
 * - وانت بتشاهد: السهم فوق = القناة التالية، السهم تحت = السابقة، و OK أو يمين/يسار = فتح القائمة.
 * - Back بيخفي القائمة، وبعدها بيرجع للرئيسية.
 * - ضغطة مطوّلة على OK فوق قناة = إضافة/إزالة من المفضلة.
 */
@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(LiveData.items) }
    var cats by remember { mutableStateOf(LiveData.cats) }
    var loading by remember { mutableStateOf(LiveData.items.isEmpty()) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var favs by remember { mutableStateOf(Favs.get(ctx)) }

    LaunchedEffect(attempt) {
        if (items.isEmpty()) {
            loading = true; failed = false
            try {
                ensureLive()
                items = LiveData.items; cats = LiveData.cats
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failed = true }
            loading = false
        }
    }

    // 01 الكل، 02 المفضلة، ثم التصنيفات بنفس ترتيب السيرفر
    val groups = remember(items, cats, favs) {
        val byCat = items.groupBy { it.categoryId }
        val all = Group("all", "الكل", items)
        val fav = Group("fav", "المفضلة", items.filter { it.id in favs })
        val real = cats.mapNotNull { c -> byCat[c.id]?.let { Group(c.id, c.name, it) } }
        listOf(all, fav) + real
    }

    var selectedCat by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf<Item?>(null) }
    var activeList by remember { mutableStateOf<List<Item>>(emptyList()) }
    var activeIndex by remember { mutableStateOf(0) }
    var panelVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var failCount by remember { mutableStateOf(0) }   // محاولات فاشلة متتالية للقناة الحالية
    var retryTick by remember { mutableStateOf(0) }   // كل زيادة = إعادة تحميل البث تلقائياً

    fun play(list: List<Item>, idx: Int) {
        if (list.isEmpty()) return
        val i = idx.mod(list.size)
        activeList = list; activeIndex = i; current = list[i]
        Favs.saveLast(ctx, list[i].id)
    }
    fun step(d: Int) = play(activeList, activeIndex + d)

    // أول دخول: بيشغّل آخر قناة انشافت (أو أول قناة) والقائمة ظاهرة فوقها
    LaunchedEffect(items.size, groups.size) {
        if (current == null && items.isNotEmpty()) {
            val last = Favs.lastChannel(ctx)
            // آخر قناة بتصنيفها الحقيقي، وإلا "الكل"
            val gi = groups.indexOfFirst { g -> g.id != "all" && g.id != "fav" && g.items.any { it.id == last } }
                .takeIf { it >= 0 }
                ?: groups.indexOfFirst { it.id != "fav" && it.items.isNotEmpty() }.coerceAtLeast(0)
            selectedCat = gi
            val list = groups[gi].items
            play(list, list.indexOfFirst { it.id == last }.coerceAtLeast(0))
        }
    }

    // المشغّل
    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ctx).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            ).build()
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // إعادة محاولة تلقائية بصمت؛ الرسالة بتظهر بس لو تكرر الفشل
                buffering = true; failCount++
                if (failCount > 3) playError = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { playError = false; failCount = 0 }
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }
    // تحميل القناة (وإعادة التحميل عند كل retryTick)
    LaunchedEffect(current?.id, retryTick) {
        val c = current ?: return@LaunchedEffect
        exo.setMediaItem(MediaItem.fromUri(Api.liveUrl(Session.username, Session.password, c.id)))
        exo.prepare(); exo.playWhenReady = true
    }
    // قناة جديدة: صفّر العدّاد
    LaunchedEffect(current?.id) {
        failCount = 0; playError = false
        // شريط معلومات القناة بيظهر ٤ ثواني بعد كل تبديل
        infoVisible = true; delay(4000); infoVisible = false
    }
    // بعد كل فشل: انتظر شوي (ثانية، ثانيتين… حد أقصى 5) وأعد المحاولة تلقائياً
    LaunchedEffect(failCount) {
        if (failCount > 0) { delay(minOf(1000L * failCount, 5000L)); retryTick++ }
    }
    // حارس التعليق: لو البث ظل يحمّل أكتر من 10 ثواني، اعتبره فاشل وأعد المحاولة
    LaunchedEffect(buffering, current?.id, retryTick) {
        if (buffering) { delay(10_000); if (buffering) failCount++ }
    }

    val catIdx = selectedCat.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val channels = groups.getOrNull(catIdx)?.items.orEmpty()
    val focusIndex = channels.indexOfFirst { it.id == current?.id }.let { if (it >= 0) it else 0 }
    val rootFr = remember { FocusRequester() }
    val chFr = remember { FocusRequester() }
    val retryFr = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // القائمة ظاهرة: التركيز على القناة الحالية. مخفية: التركيز للشاشة عشان الأسهم تبدّل القنوات
    LaunchedEffect(panelVisible, groups.size) {
        if (panelVisible) {
            delay(120)
            runCatching { listState.scrollToItem(focusIndex) }
            delay(60)
            runCatching { chFr.requestFocus() }
        } else runCatching { rootFr.requestFocus() }
    }
    LaunchedEffect(failed) { if (failed) { delay(150); runCatching { retryFr.requestFocus() } } }

    val curEpg by produceState(emptyList<Program>(), current?.id) {
        value = current?.let { Epg.get(it.id) }.orEmpty()
    }
    BackHandler(enabled = panelVisible && current != null) { panelVisible = false }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFr).focusable()
            .onKeyEvent { ev ->
                if (panelVisible || ev.type != KeyEventType.KeyDown) false
                else when (ev.key) {
                    Key.DirectionUp -> { step(+1); true }     // القناة التالية
                    Key.DirectionDown -> { step(-1); true }   // القناة السابقة
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft,
                    Key.DirectionRight, Key.Menu -> { panelVisible = true; true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = exo
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // لمس (جوال): ضغطة = إظهار/إخفاء القائمة، سحب لفوق/تحت = تبديل القناة
        Box(Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { panelVisible = !panelVisible }) }
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = { if (acc < -80f) step(+1) else if (acc > 80f) step(-1) }
                ) { _, dy -> acc += dy }
            })
        if (buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(42.dp), color = K.BlueGlow)
        }
        if (playError) {
            Text(
                "القناة مش متوفرة حالياً… بنعيد المحاولة تلقائياً", color = K.Error, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).glass(RoundedCornerShape(16.dp), true).padding(20.dp, 12.dp)
            )
        }

        when {
            loading && items.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = K.Blue)
            failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تعذّر تحميل القنوات", color = K.Muted, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                FocusBox(
                    Modifier.width(180.dp).height(48.dp).focusRequester(retryFr),
                    RoundedCornerShape(24.dp), onClick = { attempt++ }
                ) { f ->
                    Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                        Text("إعادة المحاولة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ───────── القائمة الزجاجية: تصنيفات + قنوات ─────────
        AnimatedVisibility(
            visible = panelVisible && items.isNotEmpty(),
            enter = fadeIn(tween(150)), exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row(
                Modifier.padding(end = 8.dp, top = 24.dp, bottom = 124.dp)
                    .width(560.dp).fillMaxHeight()
                    .glass(RoundedCornerShape(22.dp), strong = true)
            ) {
                if (channels.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp), Alignment.Center) {
                        Text(
                            if (groups.getOrNull(catIdx)?.id == "fav")
                                "ما في قنوات مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي قناة لإضافتها."
                            else "لا توجد قنوات",
                            color = K.Muted, fontSize = 15.sp, textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(), state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(channels) { i, ch ->
                            val epgList by produceState(emptyList<Program>(), ch.id) { delay(250); value = Epg.get(ch.id) }
                            val prog = Epg.current(epgList)
                            GlassRow(
                                num(i + 1, 3), ch.name, selected = ch.id == current?.id, compact = true,
                                star = ch.id in favs,
                                sub = prog?.let { "الآن: ${it.title}" }, progress = prog?.let { Epg.progress(it) },
                                modifier = if (i == focusIndex) Modifier.focusRequester(chFr) else Modifier,
                                onLongClick = { favs = Favs.toggle(ctx, ch.id) },
                                onClick = { play(channels, i); panelVisible = false }
                            )
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(K.GlassBorder))
                LazyColumn(
                    Modifier.width(190.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(groups) { i, g ->
                        GlassRow(
                            num(i + 1, 2), g.name, selected = i == catIdx, compact = true,
                            onFocus = { selectedCat = i }, onClick = { selectedCat = i }
                        )
                    }
                }
            }
        }

        // ───────── شريط معلومات القناة ─────────
        AnimatedVisibility(
            visible = (panelVisible || infoVisible) && current != null,
            enter = fadeIn(tween(150)), exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                Modifier.padding(horizontal = 28.dp, vertical = 22.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp), strong = true)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    current?.poster, null,
                    Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "${num(activeIndex + 1, 3)} | ${current?.name ?: ""}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(groups.getOrNull(catIdx)?.name ?: "", fontSize = 13.sp, color = K.Muted, maxLines = 1)
                    val cp = Epg.current(curEpg); val np = Epg.next(curEpg, cp)
                    if (cp != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "الآن: ${cp.title}" + (np?.let { "   •   التالي: ${it.title}" } ?: ""),
                            fontSize = 14.sp, color = K.Silver, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(Epg.progress(cp)).fillMaxHeight().background(K.Blue, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                Text("▲ ▼ تغيير القناة   •   OK القائمة", fontSize = 12.sp, color = K.Muted)
            }
        }
    }
}

@Composable
fun GlassRow(
    number: String, text: String, selected: Boolean, star: Boolean = false,
    modifier: Modifier = Modifier, sub: String? = null, progress: Float? = null, compact: Boolean = false,
    onFocus: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    FocusBox(
        modifier.fillMaxWidth(), RoundedCornerShape(12.dp), 1.02f,
        onFocus = onFocus, onLongClick = onLongClick, onClick = onClick
    ) { f ->
        Row(
            Modifier.fillMaxWidth()
                .background(
                    when {
                        f -> K.Blue.copy(alpha = 0.55f)
                        selected -> Color(0x33FFFFFF)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(number, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Bold, color = K.BlueGlow,
                modifier = Modifier.width(if (compact) 36.dp else 44.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text, fontSize = if (compact) 13.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (f) Color.White else K.Silver
                )
                if (sub != null) {
                    Text(sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (f) Color(0xCCFFFFFF) else K.Muted)
                    if (progress != null) {
                        Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(K.BlueGlow))
                        }
                    }
                }
            }
            if (star) Text("★", fontSize = if (compact) 12.sp else 14.sp, color = K.BlueGlow)
        }
    }
}
