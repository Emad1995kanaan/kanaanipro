package com.kanani.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun latestItems(items: List<Item>): List<Item> =
    items.distinctBy { it.id }.sortedByDescending { it.added?.toLongOrNull() ?: 0L }.take(14)

fun latest(rows: List<ContentRow>?): List<Item> = latestItems(rows.orEmpty().flatMap { it.items })

private class HomeState(
    val movies: List<Item>, val series: List<Item>,
    val loading: Boolean, val failed: Boolean, val retry: () -> Unit,
    val firstFr: FocusRequester, val onItem: (Item) -> Unit,
    val pool: List<Item> = emptyList()
)

private val navItems = listOf(
    Triple("القنوات", KIconKind.LIVE, Tab.LIVE),
    Triple("أفلام", KIconKind.MOVIES, Tab.MOVIES),
    Triple("مسلسلات", KIconKind.SERIES, Tab.SERIES)
)

@Composable
fun HomeScreen(
    onPlay: (String, Item) -> Unit, onSeries: (Item) -> Unit,
    onOpen: (Tab) -> Unit, onSettings: () -> Unit
) {
    var movies by remember { mutableStateOf(HomeCache.rows[Tab.MOVIES]) }
    var series by remember { mutableStateOf(HomeCache.rows[Tab.SERIES]) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var focusDone by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }
    val style = UiPrefs.homeStyle

    LaunchedEffect(attempt) {
        if (movies == null || series == null) {
            failed = false
            // كل قسم بينعرض أول ما يجهز (بدون ما ينتظر التاني)
            coroutineScope {
                launch { if (movies == null) movies = runCatching { cachedRows(Tab.MOVIES) }.getOrNull() }
                launch { if (series == null) series = runCatching { cachedRows(Tab.SERIES) }.getOrNull() }
            }
            if (movies == null && series == null) failed = true
        }
    }

    // بعد ما تجهز الرئيسية: حمّل القنوات بالخلفية عشان اللايف يفتح فوراً
    LaunchedEffect(movies != null && series != null) {
        if (movies != null && series != null) runCatching { ensureLive() }
    }
    val ctxH = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(movies, series) {
        val m = movies; val sr = series
        if (m != null && sr != null && !Notices.newChecked) {
            Notices.newChecked = true
            Notices.newContent(ctxH, m, sr)?.let { delay(1500); Notices.toast = it; delay(5000); Notices.toast = null }
        }
    }
    // الصف الجاهز فوراً من النسخة الصغيرة، وبعدين بيتحدّث من القائمة الكاملة (كله بالخلفية)
    var movieItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var seriesItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val qm = Store.readQuick(Tab.MOVIES); val qs = Store.readQuick(Tab.SERIES)
            withContext(Dispatchers.Main) {
                if (movieItems.isEmpty()) movieItems = qm
                if (seriesItems.isEmpty()) seriesItems = qs
            }
        }
    }
    LaunchedEffect(movies) {
        val m = movies ?: return@LaunchedEffect
        val l = withContext(Dispatchers.Default) { latest(m) }
        if (l.isNotEmpty()) { movieItems = l; withContext(Dispatchers.IO) { Store.writeQuick(Tab.MOVIES, l) } }
    }
    LaunchedEffect(series) {
        val s = series ?: return@LaunchedEffect
        val l = withContext(Dispatchers.Default) { latest(s) }
        if (l.isNotEmpty()) { seriesItems = l; withContext(Dispatchers.IO) { Store.writeQuick(Tab.SERIES, l) } }
    }
    val ready = style == HomeStyle.TILES || style == HomeStyle.SHOWCASE || movieItems.isNotEmpty() || seriesItems.isNotEmpty()

    // التركيز الأولي (وعند تغيير شكل الرئيسية)
    LaunchedEffect(style, ready) {
        if (ready) {
            delay(150)
            runCatching { firstFr.requestFocus() }.onSuccess { focusDone = true }
        }
    }

    val pool = remember(movieItems, seriesItems) { (movieItems + seriesItems).filter { !it.poster.isNullOrBlank() } }
    val st = HomeState(
        movieItems, seriesItems,
        loading = movies == null && series == null && !failed && movieItems.isEmpty() && seriesItems.isEmpty(),
        failed = failed, retry = { attempt++ }, firstFr = firstFr,
        onItem = { item ->
            when (item.kind) {
                Tab.MOVIES -> onPlay(Api.movieUrl(Session.username, Session.password, item.id, item.ext), item)
                Tab.SERIES -> onSeries(item)
                else -> Unit
            }
        },
        pool = pool
    )

    when (style) {
        HomeStyle.CLASSIC -> ClassicHome(st, onOpen, onSettings)
        HomeStyle.RAIL -> RailHome(st, onOpen, onSettings)
        HomeStyle.TILES -> TilesHome(st, onOpen, onSettings)
        HomeStyle.SHOWCASE -> ShowcaseHome(st, onOpen, onSettings)
    }
}

// ───────────── مشترك ─────────────

@Composable
private fun HomeRow(
    title: String, items: List<Item>, pw: Dp, ph: Dp,
    firstFr: FocusRequester?, pad: Dp = 44.dp, onClick: (Item) -> Unit
) {
    if (items.isEmpty()) return
    Column {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = pad))
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items) { i, item ->
                PosterCard(
                    item, false,
                    if (i == 0 && firstFr != null) Modifier.focusRequester(firstFr) else Modifier,
                    pw, ph
                ) { onClick(item) }
            }
        }
    }
}

@Composable
private fun RowsState(st: HomeState, content: @Composable () -> Unit) {
    when {
        st.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = K.Blue) }
        st.failed -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("تعذّر تحميل المحتوى", color = K.Muted, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            FocusBox(Modifier.width(180.dp).height(48.dp), RoundedCornerShape(24.dp), onClick = st.retry) { f ->
                Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                    Text("إعادة المحاولة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> content()
    }
}

@Composable
private fun NavIcon(label: String, kind: KIconKind, firstFr: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(
        (if (firstFr != null) Modifier.focusRequester(firstFr) else Modifier).width(92.dp),
        RoundedCornerShape(16.dp), 1.1f, onClick = onClick
    ) { f ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(kind, f)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (f) Color.White else K.Silver)
        }
    }
}

// ───────────── 1) كلاسيكي: صفين بوسترات + شريط سفلي ─────────────

@Composable
private fun ClassicHome(st: HomeState, onOpen: (Tab) -> Unit, onSettings: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ph = ((maxHeight - 340.dp) / 2).coerceIn(80.dp, 230.dp)
        val pw = ph * 0.68f
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RowsState(st) {
                    Column {
                        HomeRow("أحدث الأفلام", st.movies, pw, ph, st.firstFr, onClick = st.onItem)
                        HomeRow("أحدث المسلسلات", st.series, pw, ph,
                            if (st.movies.isEmpty()) st.firstFr else null, onClick = st.onItem)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 6.dp)) {
                    Row(
                        Modifier.align(Alignment.Center).glass(RoundedCornerShape(34.dp))
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        navItems.forEach { (l, k, t) -> NavIcon(l, k) { onOpen(t) } }
                        NavIcon("إعدادات", KIconKind.SETTINGS) { onSettings() }
                    }
                    Image(painterResource(R.drawable.logo), null, Modifier.align(Alignment.CenterStart).height(72.dp))
                }
                Text(
                    "ينتهي الاشتراك خلال: ${formatExp(Session.expDate)}",
                    fontSize = 13.sp, color = K.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ───────────── 2) سينمائي: شريط جانبي + بانر كبير ─────────────

@Composable
private fun RailHome(st: HomeState, onOpen: (Tab) -> Unit, onSettings: () -> Unit) {
    val hero = st.movies.firstOrNull() ?: st.series.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ph = ((maxHeight * 0.68f - 205.dp) / 2).coerceIn(70.dp, 150.dp)
        val pw = ph * 0.68f
        val heroH = maxHeight * 0.32f
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // الشريط الجانبي (أول عنصر بالـRtl = يمين)
            Column(
                Modifier.width(104.dp).fillMaxHeight().glass(RoundedCornerShape(28.dp)).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(painterResource(R.drawable.logo), null, Modifier.height(58.dp))
                Spacer(Modifier.weight(1f))
                navItems.forEachIndexed { i, (l, k, t) ->
                    NavIcon(l, k, if (hero == null && i == 0) st.firstFr else null) { onOpen(t) }
                }
                NavIcon("إعدادات", KIconKind.SETTINGS) { onSettings() }
                Spacer(Modifier.weight(1f))
                Text("حتى ${formatExp(Session.expDate)}", fontSize = 10.sp, color = K.Muted, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                RowsState(st) {
                    Column {
                        if (hero != null) {
                            Box(
                                Modifier.fillMaxWidth().height(heroH)
                                    .glass(RoundedCornerShape(26.dp))
                            ) {
                                AsyncImage(hero.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                                    listOf(Color(0xE6000000), Color(0x33000000), Color(0xE6000000)))))
                                Column(
                                    Modifier.align(Alignment.CenterEnd).padding(26.dp).fillMaxWidth(0.6f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(hero.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    FocusBox(
                                        Modifier.width(150.dp).height(42.dp).focusRequester(st.firstFr),
                                        RoundedCornerShape(21.dp), 1.06f, onClick = { st.onItem(hero) }
                                    ) { f ->
                                        Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                                            Text(if (hero.kind == Tab.SERIES) "عرض المسلسل" else "تشغيل",
                                                color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        HomeRow("أحدث الأفلام", st.movies, pw, ph, null, 8.dp, st.onItem)
                        HomeRow("أحدث المسلسلات", st.series, pw, ph, null, 8.dp, st.onItem)
                    }
                }
            }
        }
    }
}

// ───────────── 3) بطاقات: أربع بطاقات كبيرة + صفين ─────────────

@Composable
private fun TilesHome(st: HomeState, onOpen: (Tab) -> Unit, onSettings: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ph = ((maxHeight - 385.dp) / 2).coerceIn(70.dp, 150.dp)
        val pw = ph * 0.68f
        Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.logo), null, Modifier.height(68.dp))
                Spacer(Modifier.weight(1f))
                Text("ينتهي الاشتراك: ${formatExp(Session.expDate)}", fontSize = 13.sp, color = K.Muted)
            }
            Spacer(Modifier.height(10.dp))
            val tiles = listOf(
                Triple("القنوات المباشرة", KIconKind.LIVE) { onOpen(Tab.LIVE) },
                Triple("أفلام", KIconKind.MOVIES) { onOpen(Tab.MOVIES) },
                Triple("مسلسلات", KIconKind.SERIES) { onOpen(Tab.SERIES) },
                Triple("الإعدادات", KIconKind.SETTINGS) { onSettings() }
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                tiles.forEachIndexed { i, (label, kind, act) ->
                    FocusBox(
                        (if (i == 0) Modifier.focusRequester(st.firstFr) else Modifier)
                            .weight(1f).height(110.dp),
                        RoundedCornerShape(24.dp), 1.06f, onClick = act
                    ) { f ->
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    if (f) listOf(K.Blue, K.BlueGlow.copy(alpha = 0.6f))
                                    else listOf(Color(0x33FFFFFF), K.Blue.copy(alpha = 0.18f))
                                )
                            )
                        ) {
                            Column(
                                Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                KIcon(kind, boxSize = 40.dp,
                                    colors = if (f) listOf(Color.White, Color.White) else listOf(K.BlueGlow, Color.White))
                                Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!st.loading && !st.failed) {
                    Column {
                        HomeRow("أحدث الأفلام", st.movies, pw, ph, null, onClick = st.onItem)
                        HomeRow("أحدث المسلسلات", st.series, pw, ph, null, onClick = st.onItem)
                    }
                } else RowsState(st) {}
            }
        }
    }
}

// ───────────── 4) عصري: بوستر مختلف كل تشغيل + شريط سفلي بتكبير ديناميكي ─────────────

/** بوستر الواجهة: بيتغيّر كل ما ينفتح التطبيق (ثابت طول مدة التشغيل) */
private object HeroPick { val seed = kotlin.random.Random.nextInt(Int.MAX_VALUE) }

@Composable
private fun DockItem(label: String, kind: KIconKind, fr: FocusRequester?, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val d = LocalDensity.current.density
    val bounce = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    val sc by animateFloatAsState(if (f) 1.55f else 1f, bounce, label = "dockScale")
    val lift by animateFloatAsState(if (f) -26f else 0f, bounce, label = "dockLift")
    val glow by animateFloatAsState(if (f) 1f else 0f, label = "dockGlow")
    val shape = RoundedCornerShape(24.dp)
    Column(
        (if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .graphicsLayer { scaleX = sc; scaleY = sc; translationY = lift * d }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(78.dp).clip(shape)
                .background(Brush.linearGradient(listOf(
                    K.BlueGlow.copy(alpha = 0.18f + 0.7f * glow), K.Blue.copy(alpha = 0.22f + 0.7f * glow))))
                .border(if (f) 2.dp else 1.dp, if (f) Color.White.copy(alpha = 0.85f) else K.GlassBorder, shape),
            Alignment.Center
        ) {
            KIcon(kind, boxSize = 42.dp,
                colors = if (f) listOf(Color.White, Color.White) else listOf(K.BlueGlow, Color.White))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (f) FontWeight.Bold else FontWeight.Normal,
            color = if (f) Color.White else K.Silver.copy(alpha = 0.8f))
        // انعكاس خفيف تحت الأيقونة
        Box(Modifier.width(60.dp).height(5.dp).clip(RoundedCornerShape(50))
            .background(K.BlueGlow.copy(alpha = 0.35f * glow)))
    }
}

@Composable
private fun ShowcaseHome(st: HomeState, onOpen: (Tab) -> Unit, onSettings: () -> Unit) {
    val hero = remember(st.pool.isEmpty()) { if (st.pool.isEmpty()) null else st.pool[HeroPick.seed % st.pool.size] }
    val recent = remember(st.movies, st.series) { (st.movies.take(5) + st.series.take(5)).shuffled(kotlin.random.Random(HeroPick.seed)) }
    Box(Modifier.fillMaxSize()) {
        // البوستر الكبير بالجهة اليسرى (الـEnd بالـRTL) وبيذوب باتجاه النص
        if (hero != null) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f)) {
                AsyncImage(hero.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(K.Bg.copy(alpha = 0.15f), K.Bg.copy(alpha = 0.55f), K.Bg))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, K.Bg.copy(alpha = 0.95f)))))
            }
        }
        Column(Modifier.fillMaxSize()) {
            // الرأس: لوجو كبير + تاريخ الاشتراك
            Row(Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.height(84.dp))
                Spacer(Modifier.weight(1f))
                Text("ينتهي الاشتراك: ${formatExp(Session.expDate)}", fontSize = 13.sp, color = K.Silver.copy(alpha = 0.8f))
            }
            // نص البوستر
            Column(Modifier.weight(1f).padding(horizontal = 56.dp), verticalArrangement = Arrangement.Center) {
                if (st.loading) CircularProgressIndicator(color = K.Blue)
                else if (st.failed) {
                    Text("تعذّر تحميل المحتوى", color = K.Muted, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    FocusBox(Modifier.width(170.dp).height(44.dp), RoundedCornerShape(22.dp), onClick = st.retry) { f ->
                        Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                            Text("إعادة المحاولة", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (hero != null) {
                    Text(if (hero.kind == Tab.SERIES) "مسلسل" else "فيلم", fontSize = 14.sp,
                        color = K.BlueGlow, fontWeight = FontWeight.Bold)
                    Text(hero.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.5f))
                    Spacer(Modifier.height(12.dp))
                    FocusBox(
                        Modifier.width(170.dp).height(46.dp).focusRequester(st.firstFr),
                        RoundedCornerShape(23.dp), 1.08f, onClick = { st.onItem(hero) }
                    ) { f ->
                        Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                            Text(if (hero.kind == Tab.SERIES) "عرض المسلسل" else "تشغيل",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            // أحدث الإضافات
            if (recent.isNotEmpty()) {
                Text("أُضيف حديثاً", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 56.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(recent) { _, item ->
                        FocusBox(Modifier.size(width = 68.dp, height = 98.dp), RoundedCornerShape(14.dp), 1.12f,
                            onClick = { st.onItem(item) }) { _ ->
                            AsyncImage(item.poster, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            // الشريط السفلي: التصنيفات الثلاثة + الإعدادات، والمحدّد بيكبر بنطّة
            Row(
                Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(46.dp, Alignment.CenterHorizontally)
            ) {
                navItems.forEachIndexed { i, (l, k, t) ->
                    DockItem(l, k, if (hero == null && i == 0) st.firstFr else null) { onOpen(t) }
                }
                DockItem("إعدادات", KIconKind.SETTINGS, null) { onSettings() }
            }
        }
    }
}
