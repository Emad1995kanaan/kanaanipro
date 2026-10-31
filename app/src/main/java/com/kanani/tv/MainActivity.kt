package com.kanani.tv

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiPrefs.load(this)
        Store.init(this)
        // كاش صور كبير (ذاكرة + قرص) وألوان أخف: البوسترات بتطلع فوراً وبتتحرك السكرول بسلاسة
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .crossfade(false)
                .allowRgb565(true)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(300L * 1024 * 1024).build() }
                .build()
        )
        // ملء الشاشة (مهم للجوال): بدون شريط الحالة والتنقل
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            KananiTheme {
                // الجوال: شاشة أقصر من التلفزيون، فبنصغّر المقاسات تلقائياً ليتّسع التصميم كامل
                val d = LocalDensity.current
                val scale = (LocalConfiguration.current.screenHeightDp / 540f).coerceIn(0.7f, 1f)
                CompositionLocalProvider(
                    LocalDensity provides Density(d.density * scale, d.fontScale),
                    // الواجهة عربية: من اليمين لليسار
                    LocalLayoutDirection provides (if (UiPrefs.lang == Lang.EN) LayoutDirection.Ltr else LayoutDirection.Rtl)
                ) { App() }
            }
        }
    }
}

sealed interface Nav {
    object Splash : Nav
    object Login : Nav
    object Home : Nav
    object Settings : Nav
    object Live : Nav
    data class Section(val tab: Tab) : Nav
    data class SeriesDetail(val item: Item) : Nav
    data class Play(
        val url: String, val queue: List<Pair<String, String>> = emptyList(),
        val track: Track? = null, val startPos: Long = 0L
    ) : Nav
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var stack by remember { mutableStateOf<List<Nav>>(listOf(Nav.Splash)) }
    val top = stack.last()
    fun push(n: Nav) { stack = stack + n }
    fun reset(n: Nav) { stack = listOf(n) }

    // زر الرجوع بيرجع خطوة، وبالرئيسية بيطلع من التطبيق
    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    LaunchedEffect(top is Nav.Home) {
        if (top is Nav.Home && !Notices.started) { Notices.started = true; delay(800); Notices.runStartup(ctx) }
    }

    LaunchedEffect(Unit) {
        Config.load(ctx)
        val saved = Auth.saved(ctx)
        if (saved != null) {
            Session.username = saved.first; Session.password = saved.second
            if (Store.exists(Tab.MOVIES)) {
                // في نسخة محفوظة: افتح الرئيسية فوراً، وتحقق من الحساب بالخلفية
                HomeCache.reset(); reset(Nav.Home)
                if (Auth.login(saved.first, saved.second) == LoginResult.BAD_CREDENTIALS) {
                    Auth.clear(ctx); HomeCache.reset(); reset(Nav.Login)
                }
            } else if (Auth.login(saved.first, saved.second) == LoginResult.OK) {
                HomeCache.reset(); reset(Nav.Home)
            } else reset(Nav.Login)
        } else reset(Nav.Login)
    }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = K.Silver) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(K.Navy, K.Bg, K.Bg)))
                .drawBehind {
                    // توهّج خفيف ثابت بالزاوية (بدون أنيميشن عشان التنقل يضل خفيف)
                    drawRect(
                        Brush.radialGradient(
                            listOf(K.Blue.copy(alpha = 0.27f), Color.Transparent),
                            center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.6f
                        )
                    )
                }
        ) {
            run {
                val n = top
                when (n) {
                    Nav.Splash -> SplashScreen()
                    Nav.Login -> LoginScreen { HomeCache.reset(); reset(Nav.Home) }
                    Nav.Home -> HomeScreen(
                        onPlay = { u, it -> push(Nav.Play(u, track = Track(Tab.MOVIES, it, null))) },
                        onSeries = { push(Nav.SeriesDetail(it)) },
                        onOpen = { if (it == Tab.LIVE) push(Nav.Live) else push(Nav.Section(it)) },
                        onSettings = { push(Nav.Settings) }
                    )
                    is Nav.Section -> SectionScreen(
                        n.tab,
                        onPlay = { u, it -> push(Nav.Play(u, track = Track(Tab.MOVIES, it, null))) },
                        onResume = { w ->
                            push(Nav.Play(w.url, w.queue,
                                Track(if (w.kind == "SERIES") Tab.SERIES else Tab.MOVIES, w.item, w.label), w.pos))
                        },
                        onSeries = { push(Nav.SeriesDetail(it)) }
                    )
                    Nav.Settings -> SettingsScreen {
                        Auth.clear(ctx); HomeCache.reset(); reset(Nav.Login)
                    }
                    Nav.Live -> LiveScreen()
                    is Nav.SeriesDetail -> SeriesScreen(n.item) { u, q, l -> push(Nav.Play(u, q, Track(Tab.SERIES, n.item, l))) }
                    is Nav.Play -> PlayerScreen(n.url, n.queue, n.track, n.startPos)
                }
            }
            if (top is Nav.Home) NoticeHost()
        }
    }
}

@Composable
fun SplashScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.height(140.dp))
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = K.Blue)
    }
}

// ───────────────────────── تسجيل الدخول ─────────────────────────

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val saved = remember { Auth.saved(ctx) }
    var user by remember { mutableStateOf(saved?.first ?: "") }
    var pass by remember { mutableStateOf(saved?.second ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val userFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { userFr.requestFocus() } }

    fun submit() {
        if (loading || user.isBlank() || pass.isBlank()) return
        loading = true; error = null
        scope.launch {
            when (Auth.login(user.trim(), pass.trim())) {
                LoginResult.OK -> { Auth.save(ctx, user.trim(), pass.trim()); onSuccess() }
                LoginResult.BAD_CREDENTIALS -> error = "اسم المستخدم أو كلمة المرور غير صحيحة"
                LoginResult.NETWORK -> error = "تعذّر الاتصال، تأكد من الإنترنت وحاول مرة ثانية"
            }
            loading = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = K.BlueGlow, unfocusedBorderColor = K.Muted,
        focusedLabelColor = K.BlueGlow, cursorColor = K.BlueGlow,
        focusedContainerColor = K.SurfaceHi, unfocusedContainerColor = K.Surface
    )
    val ltr = TextStyle(textDirection = TextDirection.Ltr, fontSize = 18.sp)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(480.dp).card().padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.height(110.dp))
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                user, { user = it }, label = { Text("اسم المستخدم") }, singleLine = true,
                textStyle = ltr, colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().focusRequester(userFr)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                pass, { pass = it }, label = { Text("كلمة المرور") }, singleLine = true,
                textStyle = ltr, colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(22.dp))
            FocusBox(Modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(14.dp), 1.04f, onClick = { submit() }) { f ->
                Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 3.dp)
                    else Text("دخول", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = K.Error, fontSize = 15.sp)
            }
        }
    }
}

fun Modifier.card() = this.glass(RoundedCornerShape(24.dp), strong = true)

// ───────────────────────── بيانات الأقسام ─────────────────────────

enum class Tab(val title: String, val action: String, val catAction: String, val live: Boolean = false) {
    LIVE("القنوات", "get_live_streams", "get_live_categories", true),
    MOVIES("أفلام", "get_vod_streams", "get_vod_categories"),
    SERIES("مسلسلات", "get_series", "get_series_categories")
}

const val RECENT = "آخر المشاهدات"
const val FAVS = "المفضلة"

data class ContentRow(val title: String, val items: List<Item>)

/** كاش بسيط: التنقل بين الأقسام بعد أول تحميل بيكون فوري */
object HomeCache {
    val rows = mutableMapOf<Tab, List<ContentRow>>()
    fun reset() { rows.clear(); LiveData.reset() }
}

// ───────────────────────── قسم كامل: التصنيفات بالجنب والمحتوى باليمين ─────────────────────────

/**
 * أفلام / مسلسلات:
 * - أول القائمة "الكل" (كل محتوى الأقسام)، وبعدها التصنيفات بنفس ترتيب السيرفر.
 * - فوق عالشمال خانة بحث: بتكتب وبيفلتر فوراً المحتوى اللي بالتصنيف المفتوح.
 * - لما تمرّ على تصنيف بيظهر محتواه باليمين، وOK عليه بينقلك لأول بوستر فيه.
 */
@Composable
fun SectionScreen(
    tab: Tab, onPlay: (String, Item) -> Unit, onResume: (WatchEntry) -> Unit, onSeries: (Item) -> Unit
) {
    val ctx = LocalContext.current
    val recents = remember(tab) { Watch.all(ctx, tab) }
    var vfavs by remember(tab) { mutableStateOf(VodFavs.get(ctx, tab)) }
    var rows by remember(tab) { mutableStateOf(HomeCache.rows[tab]) }
    var failed by remember(tab) { mutableStateOf(false) }
    var attempt by remember(tab) { mutableStateOf(0) }
    var selected by remember(tab) { mutableStateOf(0) }
    var query by remember(tab) { mutableStateOf("") }
    val catFr = remember { FocusRequester() }
    val gridFr = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    LaunchedEffect(tab, attempt) {
        if (rows == null) {
            failed = false
            try {
                rows = cachedRows(tab)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failed = true }
        }
    }
    // أول ما يفتح القسم، التركيز بيروح لأول تصنيف
    LaunchedEffect(rows) {
        if (rows?.isNotEmpty() == true) { delay(150); runCatching { catFr.requestFocus() } }
    }

    val raw = rows
    // "الكل" أول القائمة: كل عناصر كل التصنيفات (بدون تكرار)
    // تجهيز التصنيفات ("الكل" فيها كل العناصر) بالخلفية عشان ما يعلّق الواجهة
    val categoriesState = produceState<List<ContentRow>?>(null, raw, recents, vfavs) {
        value = raw?.let { r ->
            withContext(Dispatchers.Default) {
                val all = ContentRow("الكل", r.flatMap { it.items }.distinctBy { it.id })
                // "آخر المشاهدات" تحت "الكل" مباشرة (إذا في شي مبلّش فيه)
                val rec = if (recents.isEmpty()) emptyList() else listOf(ContentRow(RECENT, recents.map { it.item }))
                // "المفضلة" ثابتة بالقائمة (حتى لو فاضية) عشان ترتيب التصنيفات ما يتغيّر
                val fav = ContentRow(FAVS, all.items.filter { it.id in vfavs })
                listOf(all) + rec + fav + r
            }
        }
    }
    val categories = categoriesState.value
    val isRecent = categories?.getOrNull(selected.coerceIn(0, ((categories?.size ?: 1) - 1).coerceAtLeast(0)))?.title == RECENT
    val sel = selected.coerceIn(0, ((categories?.size ?: 1) - 1).coerceAtLeast(0))
    // الشبكة بتتحدّث بعد ما يوقف التركيز على التصنيف شوي (عشان التنقل بين التصنيفات يضل خفيف)
    var shownSel by remember(tab) { mutableStateOf(0) }
    LaunchedEffect(sel) { delay(160); shownSel = sel }
    val showSel = shownSel.coerceIn(0, ((categories?.size ?: 1) - 1).coerceAtLeast(0))
    val catItems = categories?.getOrNull(showSel)?.items.orEmpty()
    val items by produceState(catItems, catItems, query) {
        val q = query.trim()
        if (q.isEmpty()) value = catItems
        else {
            delay(150)
            value = withContext(Dispatchers.Default) { catItems.filter { it.name.contains(q, ignoreCase = true) } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.height(64.dp))
            Spacer(Modifier.width(24.dp))
            Text(tab.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            // البحث: فوق عالشمال، بيفلتر مباشرة وانت بتكتب
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("ابحث في ${categories?.getOrNull(sel)?.title ?: ""}", fontSize = 14.sp) },
                leadingIcon = { KIcon(KIconKind.SEARCH, boxSize = 22.dp, colors = listOf(K.BlueGlow, K.BlueGlow)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text("✕", fontSize = 16.sp, color = K.Muted,
                            modifier = Modifier.clickable { query = "" }.padding(10.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = K.BlueGlow, unfocusedBorderColor = K.GlassBorder,
                    focusedContainerColor = Color(0x33FFFFFF), unfocusedContainerColor = Color(0x1AFFFFFF),
                    cursorColor = K.BlueGlow,
                    focusedPlaceholderColor = K.Muted, unfocusedPlaceholderColor = K.Muted
                ),
                modifier = Modifier.width(320.dp)
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                raw == null && !failed ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = K.Blue)
                failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("تعذّر تحميل المحتوى", color = K.Muted, fontSize = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    FocusBox(Modifier.width(180.dp).height(48.dp), RoundedCornerShape(24.dp), onClick = { attempt++ }) { f ->
                        Box(Modifier.fillMaxSize().background(if (f) K.BlueGlow else K.Blue), Alignment.Center) {
                            Text("إعادة المحاولة", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                raw != null && raw.isEmpty() ->
                    Text("لا يوجد محتوى في هذا القسم", Modifier.align(Alignment.Center), color = K.Muted, fontSize = 18.sp)
                categories != null -> Row(
                    Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // المحتوى: باليمين (أول عنصر بالـ RTL)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        // 4 بطاقات بالصف و3 صفوف ظاهرة بنفس الشاشة
                        val ch = ((maxHeight - 20.dp - 32.dp - 44.dp * 3) / 3).coerceAtLeast(80.dp)
                        val cwFit = (maxWidth - 20.dp - 14.dp * 3) / 4
                        val cw = minOf(cwFit, ch * 0.68f)
                        key(tab, showSel, query) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                gridItemsIndexed(items) { i, item ->
                                    PosterCard(
                                        item, false,
                                        if (i == 0) Modifier.focusRequester(gridFr) else Modifier,
                                        cw, ch,
                                        fav = item.id in vfavs,
                                        onLongClick = { vfavs = VodFavs.toggle(ctx, item.kind ?: tab, item.id) },
                                        progress = if (isRecent) recents.firstOrNull { it.item.id == item.id }
                                            ?.let { if (it.dur > 0) (it.pos.toFloat() / it.dur).coerceIn(0f, 1f) else null } else null
                                    ) {
                                        val u = Session.username; val p = Session.password
                                        val w = if (isRecent) recents.firstOrNull { it.item.id == item.id } else null
                                        if (w != null) { onResume(w); return@PosterCard }
                                        when (item.kind ?: tab) {
                                            Tab.MOVIES -> onPlay(Api.movieUrl(u, p, item.id, item.ext), item)
                                            Tab.SERIES -> onSeries(item)
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                        if (items.isEmpty()) {
                            Text(
                                if (query.isNotBlank()) "لا توجد نتائج لـ \"${query.trim()}\""
                                else if (categories?.getOrNull(sel)?.title == FAVS)
                                    "ما في مفضلة بعد.\nاضغط مطوّلاً على OK فوق أي بوستر لإضافته"
                                else "لا يوجد محتوى",
                                Modifier.align(Alignment.Center), color = K.Muted, fontSize = 18.sp
                            )
                        }
                    }
                    // التصنيفات: بالجنب، فوق بعض، "الكل" أولاً ثم بنفس ترتيب السيرفر
                    LazyColumn(
                        Modifier.width(290.dp).fillMaxHeight().glass(RoundedCornerShape(22.dp), strong = true),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(categories) { i, row ->
                            GlassRow(
                                num(i + 1, 2), row.title, selected = i == sel,
                                modifier = if (i == 0) Modifier.focusRequester(catFr) else Modifier,
                                onFocus = { selected = i },
                                onClick = {
                                    selected = i
                                    scope.launch { delay(80); runCatching { gridFr.requestFocus() } }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    item: Item, live: Boolean, modifier: Modifier = Modifier,
    cw: Dp? = null, ch: Dp? = null, progress: Float? = null,
    fav: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    val w = cw ?: if (live) 190.dp else 150.dp
    val h = ch ?: if (live) 112.dp else 220.dp
    val stars = item.rating?.toDoubleOrNull()?.let { (it / 2).roundToInt().coerceIn(0, 5) } ?: 0
    Column(Modifier.width(w)) {
        FocusBox(modifier.size(w, h), onLongClick = onLongClick, onClick = onClick) { _ ->
            Box(Modifier.fillMaxSize().background(Color(0x22FFFFFF))) {
                AsyncImage(
                    item.poster, item.name,
                    Modifier.fillMaxSize().then(if (live) Modifier.padding(14.dp) else Modifier),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop
                )
                if (fav) {
                    Text("★", fontSize = 18.sp, color = Color(0xFFFFC857),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
                if (progress != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(Color(0x88000000))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(K.Blue))
                    }
                }
                if (stars > 0 && !live) {
                    Text(
                        "★".repeat(stars), fontSize = 11.sp, color = K.BlueGlow,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    )
                }
            }
        }
        Text(
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
    }
}

fun formatExp(exp: String?): String = exp?.toLongOrNull()?.let {
    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ENGLISH).format(java.util.Date(it * 1000))
} ?: "—"

// ───────────────────────── المسلسل: المواسم والحلقات ─────────────────────────

@Composable
fun SeriesScreen(item: Item, onPlay: (String, List<Pair<String, String>>, String?) -> Unit) {
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var season by remember { mutableStateOf<String?>(null) }
    val firstEp = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        info = try { Api.get().seriesInfo(Session.username, Session.password, "get_series_info", item.id) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        loading = false
    }
    val seasons = remember(info) {
        info?.episodes?.toSortedMap(compareBy<String> { it.toIntOrNull() ?: 0 })
            ?: java.util.TreeMap<String, List<Episode>>()
    }
    // كل الحلقات بالترتيب (موسم بعد موسم) عشان التشغيل التلقائي للحلقة التالية
    val allEps = remember(seasons) {
        seasons.entries.flatMap { (sn, eps) -> eps.mapIndexed { i, e ->
            Triple("الموسم $sn - الحلقة ${e.num ?: (i + 1)}" + (e.title?.let { "  •  $it" } ?: ""),
                Api.seriesUrl(Session.username, Session.password, e.id, e.ext), e.id)
        } }
    }
    val current = season ?: seasons.keys.firstOrNull()
    val episodes = current?.let { seasons[it] }.orEmpty()

    LaunchedEffect(current, episodes.size) {
        if (episodes.isNotEmpty()) { delay(150); runCatching { firstEp.requestFocus() } }
    }

    Row(Modifier.fillMaxSize().padding(44.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.width(220.dp)) {
            AsyncImage(
                item.poster, item.name,
                Modifier.fillMaxWidth().height(320.dp).background(K.Surface, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(14.dp))
            Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            when {
                loading -> CircularProgressIndicator(color = K.Blue)
                seasons.isEmpty() -> Text("لا توجد حلقات متاحة", color = K.Muted, fontSize = 18.sp)
                else -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(seasons.keys.toList()) { s ->
                            FocusBox(Modifier, RoundedCornerShape(20.dp), 1.06f, onFocus = { season = s }, onClick = { season = s }) { f ->
                                Box(
                                    Modifier.background(
                                        when { s == current -> K.Blue; f -> K.SurfaceHi; else -> K.Surface }
                                    ).padding(horizontal = 20.dp, vertical = 8.dp)
                                ) { Text("الموسم $s", fontSize = 16.sp, color = Color.White) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        itemsIndexed(episodes) { i, ep ->
                            FocusBox(
                                Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstEp) else Modifier),
                                RoundedCornerShape(12.dp), 1.02f,
                                onClick = {
                                    val pos = allEps.indexOfFirst { it.third == ep.id }.coerceAtLeast(0)
                                    onPlay(allEps[pos].second, allEps.drop(pos + 1).map { it.first to it.second }, allEps[pos].first)
                                }
                            ) { f ->
                                Box(Modifier.fillMaxWidth().background(if (f) K.SurfaceHi else K.Surface).padding(18.dp)) {
                                    Text(
                                        "الحلقة ${ep.num ?: (i + 1)}  •  ${ep.title ?: ""}",
                                        fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── المشغّل ─────────────────────────

/** خيار ترجمة بالمشغّل */
class SubOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

@Composable
fun PlayerScreen(
    url: String, queue: List<Pair<String, String>> = emptyList(),
    track: Track? = null, startPos: Long = 0L
) {
    val ctx = LocalContext.current
    var idx by remember { mutableStateOf(-1) }          // -1 = الحلقة الأصلية، وبعدها عناصر القائمة
    val curUrl = if (idx < 0) url else queue[idx].second
    val hasNext = idx + 1 < queue.size
    val nextLabel = queue.getOrNull(idx + 1)?.first.orEmpty()

    val player = remember { ExoPlayer.Builder(ctx).build() }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(-1L) }
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var cancelled by remember(curUrl) { mutableStateOf(false) }
    var countdown by remember(curUrl) { mutableStateOf(5) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableStateOf(0) }
    var uiTick by remember { mutableStateOf(0) }
    var showUi by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    var subMenu by remember { mutableStateOf(false) }
    var subOpts by remember { mutableStateOf<List<SubOpt>>(emptyList()) }
    val subFirst = remember { FocusRequester() }

    // حفظ "وين وصلت": كل 5 ثواني وعند الخروج
    fun persist() {
        val t = track ?: return
        if (duration <= 0) return
        val kind = t.tab.name
        val nearEnd = position >= duration - 60_000
        if (nearEnd) {
            if (t.tab == Tab.MOVIES || !hasNext) Store.scope.launch { Watch.remove(ctx, kind, t.item.id) }
            else { val e = WatchEntry(kind, t.item, queue[idx + 1].second, queue[idx + 1].first,
                queue.drop(idx + 2).take(60), 0L, 0L, System.currentTimeMillis()); Store.scope.launch { Watch.save(ctx, e) } }
        } else if (position > 15_000) {
            val e = WatchEntry(kind, t.item, curUrl,
                if (idx < 0) t.label else queue[idx].first, queue.drop(idx + 1).take(60), position, duration,
                System.currentTimeMillis())
            Store.scope.launch { Watch.save(ctx, e) }
        }
    }
    val persistNow by rememberUpdatedState(::persist)

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { ended = state == Player.STATE_ENDED }
            override fun onTracksChanged(t: Tracks) {
                val l = ArrayList<SubOpt>()
                for (g in t.groups) {
                    if (g.type != C.TRACK_TYPE_TEXT) continue
                    for (i in 0 until g.length) {
                        if (!g.isTrackSupported(i)) continue
                        val f = g.getTrackFormat(i)
                        val name = f.label ?: f.language?.let { java.util.Locale(it).displayLanguage }?.takeIf { it.isNotBlank() }
                            ?: "ترجمة ${l.size + 1}"
                        l.add(SubOpt(name, g, i, g.isTrackSelected(i)))
                    }
                }
                subOpts = l
            }
        }
        player.addListener(l)
        onDispose {
            runCatching { position = player.currentPosition; persistNow() }
            player.removeListener(l); player.release()
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(5000); persistNow() } }
    LaunchedEffect(curUrl) {
        position = 0; duration = -1; ended = false
        val item = MediaItem.fromUri(curUrl)
        player.setMediaItem(item); player.prepare(); player.playWhenReady = true
        if (idx < 0 && startPos > 0) player.seekTo(startPos)
        playing = true
    }
    LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
    // تحديث الموقع والمدة
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: -1L
            playing = player.playWhenReady
            delay(400)
        }
    }
    LaunchedEffect(toastTick) { if (toast != null) { delay(800); toast = null } }
    LaunchedEffect(uiTick) { if (uiTick > 0) { showUi = true; delay(3000); showUi = false } }

    // آخر دقيقة: بطاقة الحلقة التالية، وانتقال تلقائي بعد 5 ثواني
    val inLastMinute = hasNext && !cancelled && duration > 0 && position >= duration - 60_000
    LaunchedEffect(inLastMinute, curUrl) {
        countdown = 5
        if (inLastMinute) {
            while (countdown > 0) { delay(1000); countdown-- }
            idx++
        }
    }
    // انتهت الحلقة بدون ما تنتقل (مثلاً بعد إلغاء الانتقال) → ما بننتقل
    LaunchedEffect(ended, curUrl) { if (ended && hasNext && !cancelled) idx++ }

    fun seek(ms: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, max))
        toast = if (ms > 0) "+10 ث  ▶▶" else "◀◀  -10 ث"; toastTick++; uiTick++
    }
    fun openSubs() {
        if (subOpts.isEmpty()) { toast = "لا توجد ترجمة متاحة"; toastTick++; uiTick++ } else subMenu = true
    }
    fun pickSub(o: SubOpt?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (o == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
        player.trackSelectionParameters = b.build()
        subMenu = false
    }
    LaunchedEffect(subMenu) {
        if (subMenu) { delay(80); runCatching { subFirst.requestFocus() } } else runCatching { fr.requestFocus() }
    }
    BackHandler(enabled = subMenu) { subMenu = false }

    val canSkip = track?.tab == Tab.SERIES && duration > 0 && position < 240_000
    fun skipIntro() {
        player.seekTo(player.currentPosition + 90_000)
        toast = "تخطي المقدمة"; toastTick++; uiTick++
    }
    fun toggle() {
        player.playWhenReady = !player.playWhenReady; playing = player.playWhenReady; uiTick++
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (subMenu) return@onPreviewKeyEvent false
                val handled = when (e.key) {
                    Key.DirectionRight, Key.MediaFastForward, Key.DirectionLeft, Key.MediaRewind,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> true
                    Key.DirectionDown, Key.Menu -> true
                    Key.DirectionUp -> canSkip
                    else -> false
                }
                if (handled && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionRight, Key.MediaFastForward -> seek(10_000)
                        Key.DirectionLeft, Key.MediaRewind -> seek(-10_000)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                            if (inLastMinute) idx++ else toggle()
                        Key.MediaPlayPause -> toggle()
                        Key.MediaPlay -> { player.playWhenReady = true; playing = true; uiTick++ }
                        Key.MediaPause -> { player.playWhenReady = false; playing = false; uiTick++ }
                        Key.DirectionDown -> if (inLastMinute) cancelled = true else openSubs()
                        Key.Menu -> openSubs()
                        Key.DirectionUp -> skipIntro()
                        else -> Unit
                    }
                }
                handled
            }
            .focusable()
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    // الترجمة: كتابة بيضاء على خلفية سوداء (واضحة على أي مشهد)
                    subtitleView?.apply {
                        setStyle(CaptionStyleCompat(
                            android.graphics.Color.WHITE, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.WHITE, null
                        ))
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // لمس: ضغطتين يمين = +10 ث، ضغطتين شمال = -10 ث، ضغطة وحدة = إيقاف/تشغيل
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { o -> if (o.x > size.width / 2) seek(10_000) else seek(-10_000) },
                onTap = { toggle() }
            )
        })
        // مؤشر التقديم/الرجوع
        toast?.let {
            Text(
                it, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).glass(RoundedCornerShape(20.dp), true).padding(26.dp, 12.dp)
            )
        }
        // إيقاف مؤقت
        if (!playing) {
            Row(
                Modifier.align(Alignment.Center).size(84.dp).glass(CircleShape, true),
                horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { Box(Modifier.size(width = 10.dp, height = 34.dp).background(Color.White, RoundedCornerShape(4.dp))) }
            }
        }
        // شريط التقدّم
        if ((showUi || !playing) && duration > 0) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 44.dp, vertical = 24.dp)) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x44FFFFFF), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight()
                        .background(K.Blue, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(6.dp))
                Text("${fmtTime(position)} / ${fmtTime(duration)}", fontSize = 13.sp, color = K.Silver,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        // زر الترجمة (سهم لتحت أو Menu، أو لمس)
        if (showUi || !playing) {
            Text(
                "ترجمة  ▼", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .glass(RoundedCornerShape(22.dp), true).clickable { openSubs() }.padding(18.dp, 8.dp)
            )
        }
        if (subMenu) {
            Column(
                Modifier.align(Alignment.Center).width(300.dp).glass(RoundedCornerShape(20.dp), true).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("الترجمة", fontSize = 14.sp, color = K.BlueGlow, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
                val allOff = subOpts.none { it.selected }
                FocusBox(Modifier.fillMaxWidth().focusRequester(subFirst), RoundedCornerShape(12.dp), 1.02f,
                    onClick = { pickSub(null) }) { f ->
                    Box(Modifier.fillMaxWidth().background(if (f) K.Blue.copy(alpha = 0.6f) else Color.Transparent).padding(12.dp)) {
                        Text((if (allOff) "✓  " else "") + tr("بدون ترجمة"), fontSize = 16.sp)
                    }
                }
                subOpts.forEach { o ->
                    FocusBox(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), 1.02f, onClick = { pickSub(o) }) { f ->
                        Box(Modifier.fillMaxWidth().background(if (f) K.Blue.copy(alpha = 0.6f) else Color.Transparent).padding(12.dp)) {
                            Text((if (o.selected) "✓  " else "") + o.label, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        // زر تخطي المقدمة (مسلسلات، أول 4 دقايق): سهم لفوق أو لمس
        if (canSkip) {
            Text(
                "تخطي المقدمة  ▲", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 80.dp)
                    .glass(RoundedCornerShape(24.dp), true)
                    .clickable { skipIntro() }.padding(20.dp, 10.dp)
            )
        }
        // بطاقة الحلقة التالية (عاليمين)
        if (inLastMinute) {
            val frac by animateFloatAsState((5 - countdown) / 5f, tween(1000, easing = LinearEasing), label = "next")
            Column(
                Modifier.align(Alignment.TopStart).padding(28.dp).width(330.dp)
                    .glass(RoundedCornerShape(22.dp), true).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("الحلقة التالية", fontSize = 13.sp, color = K.BlueGlow, fontWeight = FontWeight.Bold)
                Text(nextLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x33FFFFFF), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(K.Blue, RoundedCornerShape(3.dp)))
                }
                Text("انتقال تلقائي بعد $countdown ث", fontSize = 14.sp)
                Text("OK للانتقال الآن  •  سهم لتحت للإلغاء", fontSize = 12.sp, color = K.Muted)
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
