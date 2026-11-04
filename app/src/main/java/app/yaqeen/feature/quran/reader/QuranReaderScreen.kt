package app.yaqeen.feature.quran.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.journey.DayTicker
import app.yaqeen.feature.quran.QuranRoutes
import app.yaqeen.feature.quran.plans.PlanMath
import app.yaqeen.feature.quran.bookmarks.BookmarkEditorSheet
import app.yaqeen.feature.quran.audio.QuranMiniPlayer
import app.yaqeen.feature.quran.tafsir.TafsirSheet
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class ReaderSheet { None, Actions, Settings, Jump, Bookmark, Tafsir }

private enum class AutoMode { Off, Running, Paused }

/** شاشة قراءة المصحف. surah/ayah/page = -1 تعني غير محدد (استئناف آخر موضع). */
@Composable
fun QuranReaderScreen(nav: NavHostController, c: AppContainer, surah: Int, ayah: Int, page: Int) {
    val vm = containerViewModel { QuranReaderViewModel(it) }
    LaunchedEffect(Unit) { vm.start(surah, ayah, page) }
    val state by vm.state.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val st = state
    val p = prefs
    when {
        st is ReaderState.Failed -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = "تعذّر فتح المصحف",
                message = "حدثت مشكلة أثناء قراءة بيانات المصحف. جرّب مرة أخرى.",
                icon = Icons.Filled.ErrorOutline,
                action = { Button(onClick = { vm.retry() }) { Text("إعادة المحاولة") } },
            )
        }
        st is ReaderState.Ready && p != null -> ReaderContent(nav, c, vm, st, p)
        else -> LoadingBox()
    }
}

@Composable
private fun ReaderContent(nav: NavHostController, c: AppContainer, vm: QuranReaderViewModel, ready: ReaderState.Ready, prefs: ReaderPrefs) {
    val quran = ready.quran
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }
    val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = ReaderPalettes.resolve(prefs.background, appDark, app.yaqeen.ui.theme.YaqeenTheme.isNoor)

    // الصفحة الحالية تُحفظ عبر إعادة الإنشاء (تدوير/موت العملية)
    var savedPage by rememberSaveable { mutableIntStateOf(ready.startPage) }
    var autoMode by remember { mutableStateOf(AutoMode.Off) }
    // التمرير التلقائي = تدفّق متواصل (قائمة رأسية من الصفحات) ينساب فيه النص إلى الأعلى دون توقف عند حدود الصفحات؛
    // يُعاد المُمرِّر الأفقي (بحالة جديدة عند الصفحة الأخيرة المقروءة) حين ينتهي التمرير التلقائي.
    var pagerEpoch by remember { mutableIntStateOf(0) }
    val pagerState = key(pagerEpoch) {
        androidx.compose.foundation.pager.rememberPagerState(initialPage = (savedPage - 1).coerceIn(0, quran.pageCount - 1)) { quran.pageCount }
    }
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { savedPage = it + 1 } }
    val continuous = autoMode != AutoMode.Off
    var contStart by remember { mutableIntStateOf(1) }
    var contEpoch by remember { mutableIntStateOf(0) }
    var contOffset by remember { mutableIntStateOf(0) }
    var contPage by remember { mutableIntStateOf(savedPage) }
    val currentPage = if (continuous) contPage else pagerState.currentPage + 1
    val fitEnabled = prefs.fitPage && LocalConfiguration.current.screenWidthDp >= 600

    // ورد اليوم: كم بقي من مقدار اليوم ومن الورد كله (يتحدّث فور احتساب الصفحات المقروءة)
    val wirdMath = remember(quran) { PlanMath(quran) }
    val today by remember { DayTicker.today(c.clock) }.collectAsState(initial = c.clock.today())
    val activePlans by remember { c.db.quranPlans().observeActive() }.collectAsState(initial = emptyList())
    val planLogs by remember(today) { c.db.quranPlanLogs().observeRange(today.toString(), today.toString()) }.collectAsState(initial = emptyList())
    val wird = remember(wirdMath, activePlans, planLogs, today) { WirdBarLogic.build(wirdMath, activePlans, planLogs, today.toString()) }

    var immersive by rememberSaveable { mutableStateOf(prefs.startFullscreen) }
    var sheet by remember { mutableStateOf(ReaderSheet.None) }
    var selectedAyahId by remember { mutableStateOf<Int?>(null) }
    var focusId by remember { mutableStateOf(ready.focusAyahId) }
    var nonce by remember { mutableIntStateOf(0) }
    var scrollReq by remember { mutableStateOf(ready.focusAyahId?.let { ScrollRequest(it, 0) }) }
    var menuOpen by remember { mutableStateOf(false) }
    val audio by c.quranAudio.state.collectAsState()
    val scrollStates = remember { HashMap<Int, ScrollState>() }

    // ---- تنظيف حالات التمرير البعيدة (تبدأ الصفحة من أعلاها عند العودة إليها)
    LaunchedEffect(currentPage) { scrollStates.keys.removeAll { abs(it - currentPage) > 1 } }

    // ---- رسالة الاستئناف من جهاز آخر
    LaunchedEffect(Unit) { ready.resumeNote?.let { snackbar.showSnackbar(it) } }

    // ---- إزالة إبراز الهدف بعد قليل
    LaunchedEffect(focusId) {
        if (focusId != null) {
            delay(3500)
            focusId = null
        }
    }
    LaunchedEffect(scrollReq) {
        if (scrollReq != null) {
            delay(2000)
            scrollReq = null
        }
    }

    // ---- حفظ الموضع (بعد ثانيتين من البقاء) واحتساب القراءة (بعد ٨ ثوانٍ)، والتطبيق ظاهر فقط
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(currentPage) {
        var saved = false
        var tracked = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!saved) {
                delay(2000)
                val sel = selectedAyahId?.takeIf { quran.ayah(it).page == currentPage }
                val foc = focusId?.takeIf { quran.ayah(it).page == currentPage }
                vm.saveProgress(quran, sel ?: foc ?: quran.firstAyahOfPage(currentPage).id)
                saved = true
            }
            if (!tracked) {
                delay(6000)
                vm.pageRead(currentPage)
                tracked = true
            }
        }
    }

    // ---- شاشة مضاءة + ملء الشاشة
    val view = LocalView.current
    val keepOn = prefs.keepScreenOn || autoMode == AutoMode.Running
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    ImmersiveEffect(hidden = immersive, lightStatusIcons = palette.isLight)

    // ---- التنقل داخل المصحف
    fun goToPage(page: Int, focusAyah: Int?) {
        if (continuous) {
            // في التدفق المتواصل: نعيد بناء القائمة من الصفحة المطلوبة
            contStart = page.coerceIn(1, quran.pageCount)
            contOffset = 0
            contPage = contStart
            contEpoch++
            return
        }
        scope.launch {
            val idx = page.coerceIn(1, quran.pageCount) - 1
            if (abs(idx - pagerState.currentPage) <= 2) pagerState.animateScrollToPage(idx) else pagerState.scrollToPage(idx)
            if (focusAyah != null) {
                focusId = focusAyah
                nonce++
                scrollReq = ScrollRequest(focusAyah, nonce)
            }
        }
    }

    // ---- متابعة الآية الجارية في التلاوة (فقط إن كان المستخدم يتابعها)
    var lastPlayingPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(audio.currentAyahId) {
        if (autoMode != AutoMode.Off) return@LaunchedEffect
        val id = audio.currentAyahId ?: return@LaunchedEffect
        if (id !in 1..quran.totalAyahs) return@LaunchedEffect
        val target = quran.ayah(id).page
        val cur = pagerState.currentPage + 1
        if (cur == target || cur == lastPlayingPage) {
            val idx = target - 1
            if (idx != pagerState.currentPage) {
                if (abs(idx - pagerState.currentPage) <= 2) pagerState.animateScrollToPage(idx) else pagerState.scrollToPage(idx)
            }
            nonce++
            scrollReq = ScrollRequest(id, nonce)
        }
        lastPlayingPage = target
    }

    // ---- التمرير التلقائي (تدفّق متواصل)
    fun startAuto() {
        contStart = currentPage
        contOffset = scrollStates[currentPage]?.value ?: 0
        contPage = currentPage
        contEpoch++
        autoMode = AutoMode.Running
    }

    fun stopAuto(message: String? = null) {
        savedPage = contPage
        autoMode = AutoMode.Off
        pagerEpoch++
        if (message != null) scope.launch { snackbar.showSnackbar(message) }
    }

    // ---- أدوات مساعدة
    fun pauseAuto() {
        if (autoMode == AutoMode.Running) autoMode = AutoMode.Paused
    }

    fun openSheet(s: ReaderSheet) {
        pauseAuto()
        sheet = s
    }

    fun closeSheet() {
        sheet = ReaderSheet.None
        selectedAyahId = null
    }

    fun onAyahTap(a: Ayah) {
        selectedAyahId = a.id
        openSheet(ReaderSheet.Actions)
    }

    fun changeSpeed(delta: Int) {
        val next = AutoScroll.clampSpeed(prefs.scrollSpeed + delta)
        c.appScope.launch { c.settings.set(Settings.QURAN_SCROLL_SPEED, next) }
    }

    val dragCallback by rememberUpdatedState { pauseAuto() }
    val info = remember(currentPage, quran) { PageLayout.info(quran, currentPage) }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !immersive) {
                Column {
                    ReaderTopBar(
                        title = "سورة ${QuranText.plainSurahName(info.surah.nameAr)}",
                        subtitle = "صفحة ${ArabicText.indic(currentPage)} – الجزء ${ArabicText.indic(info.juz)}",
                        menuOpen = menuOpen,
                        onMenu = { menuOpen = it },
                        onBack = { nav.popBackStack() },
                        onSearch = { nav.navigate(QuranRoutes.SEARCH) },
                        onIndex = { nav.navigate(QuranRoutes.INDEX) },
                        onJump = { openSheet(ReaderSheet.Jump) },
                        onSettings = { openSheet(ReaderSheet.Settings) },
                        onBookmarks = { nav.navigate(QuranRoutes.BOOKMARKS) },
                        onPlans = { nav.navigate(Routes.quranPlans()) },
                        onDownloads = { nav.navigate(QuranRoutes.DOWNLOADS) },
                    )
                    wird?.let { w -> WirdStrip(w, onClick = { nav.navigate(Routes.quranPlans(w.planId)) }) }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onUserDrag { dragCallback() },
            ) {
                if (continuous) {
                    key(contStart, contEpoch) {
                        ContinuousReader(
                            quran = quran,
                            prefs = prefs,
                            palette = palette,
                            startPage = contStart,
                            startOffset = contOffset,
                            running = autoMode == AutoMode.Running,
                            speed = prefs.scrollSpeed,
                            selectedAyahId = selectedAyahId,
                            focusAyahId = focusId,
                            playingAyahId = audio.currentAyahId,
                            onAyahTap = { onAyahTap(it) },
                            onBackgroundTap = { immersive = !immersive },
                            onTopPage = { contPage = it },
                            onEnd = { stopAuto("وصلت إلى نهاية المصحف") },
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { index ->
                        val page = index + 1
                        val ss = remember(page) { scrollStates.getOrPut(page) { ScrollState(0) } }
                        ReaderPage(
                            page = page,
                            quran = quran,
                            prefs = prefs,
                            palette = palette,
                            scroll = ss,
                            selectedAyahId = selectedAyahId,
                            focusAyahId = focusId,
                            playingAyahId = audio.currentAyahId,
                            scrollRequest = scrollReq?.takeIf { quran.ayah(it.ayahId).page == page },
                            onAyahTap = { onAyahTap(it) },
                            onBackgroundTap = { immersive = !immersive },
                            modifier = Modifier.fillMaxSize(),
                            fitEnabled = fitEnabled,
                        )
                    }
                }
                if (immersive) {
                    IconButton(
                        onClick = { immersive = false },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .size(36.dp)
                            .alpha(0.55f)
                            .clip(CircleShape)
                            .background(palette.muted.copy(alpha = 0.25f)),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "إظهار الأشرطة", tint = palette.text)
                    }
                }
            }

            QuranMiniPlayer(c.quranAudio, Modifier.fillMaxWidth())

            AnimatedVisibility(visible = !immersive) {
                ReaderBottomBar(
                    page = currentPage,
                    autoMode = autoMode,
                    speed = prefs.scrollSpeed,
                    onStartAuto = { startAuto() },
                    onToggleAuto = { autoMode = if (autoMode == AutoMode.Running) AutoMode.Paused else AutoMode.Running },
                    onStopAuto = { stopAuto() },
                    onFaster = { changeSpeed(+1) },
                    onSlower = { changeSpeed(-1) },
                    onJump = { openSheet(ReaderSheet.Jump) },
                    onFullscreen = { immersive = true },
                    onSettings = { openSheet(ReaderSheet.Settings) },
                )
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (immersive) 16.dp else 80.dp),
        )
    }

    // ---- النوافذ
    val selAyah = selectedAyahId?.let { quran.ayah(it) }
    when (sheet) {
        ReaderSheet.None -> Unit
        ReaderSheet.Actions -> if (selAyah != null) {
            val text = QuranText.shareText(selAyah.text, quran.surah(selAyah.surah).nameAr, selAyah.number)
            AyahActionsSheet(
                quran = quran,
                ayah = selAyah,
                onBookmark = { sheet = ReaderSheet.Bookmark },
                onPlayAyah = {
                    c.quranAudio.playAyah(selAyah.id)
                    closeSheet()
                },
                onPlayFromHere = {
                    c.quranAudio.playSurah(selAyah.surah, selAyah.number)
                    closeSheet()
                },
                onTafsir = { sheet = ReaderSheet.Tafsir },
                onCopy = {
                    clipboard.setText(AnnotatedString(text))
                    closeSheet()
                    scope.launch { snackbar.showSnackbar("تم نسخ الآية") }
                },
                onShareText = {
                    closeSheet()
                    AyahShare.shareText(context, text)
                },
                onShareImage = {
                    val surah = quran.surah(selAyah.surah)
                    closeSheet()
                    scope.launch {
                        try {
                            AyahShare.shareImage(context, QuranText.forHafsFont(selAyah.text), surah.nameAr, surah.number, selAyah.number)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            snackbar.showSnackbar("تعذّر إنشاء صورة الآية")
                        }
                    }
                },
                onDismiss = { closeSheet() },
            )
        }
        ReaderSheet.Bookmark -> if (selAyah != null) {
            BookmarkEditorSheet(c, selAyah.surah, selAyah.number) { closeSheet() }
        }
        ReaderSheet.Tafsir -> if (selAyah != null) {
            TafsirSheet(c, selAyah.id) { closeSheet() }
        }
        ReaderSheet.Settings -> ReaderSettingsSheet(c, prefs, quran.bismillah) { sheet = ReaderSheet.None }
        ReaderSheet.Jump -> JumpDialog(
            quran = quran,
            initialSurah = info.surah.number,
            onDismiss = { sheet = ReaderSheet.None },
            onJump = { target ->
                when (target) {
                    is JumpTarget.AyahTarget -> {
                        val a = quran.ayah(target.surah, target.ayah)
                        goToPage(a.page, a.id)
                    }
                    is JumpTarget.PageTarget -> goToPage(target.page, null)
                    is JumpTarget.JuzTarget -> goToPage(quran.firstAyahOfJuz(target.juz).page, null)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- الأشرطة

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onIndex: () -> Unit,
    onJump: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onPlans: () -> Unit,
    onDownloads: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "بحث") }
                IconButton(onClick = onIndex) { Icon(Icons.Filled.FormatListNumbered, contentDescription = "الفهرس") }
                Box {
                    IconButton(onClick = { onMenu(true) }) { Icon(Icons.Filled.MoreVert, contentDescription = "المزيد") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                        DropdownMenuItem(text = { Text("الانتقال إلى…") }, onClick = { onMenu(false); onJump() })
                        DropdownMenuItem(text = { Text("إعدادات القراءة") }, onClick = { onMenu(false); onSettings() })
                        DropdownMenuItem(text = { Text("العلامات المرجعية") }, onClick = { onMenu(false); onBookmarks() })
                        DropdownMenuItem(text = { Text("الأوراد") }, onClick = { onMenu(false); onPlans() })
                        DropdownMenuItem(text = { Text("التنزيلات") }, onClick = { onMenu(false); onDownloads() })
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    page: Int,
    autoMode: AutoMode,
    speed: Int,
    onStartAuto: () -> Unit,
    onToggleAuto: () -> Unit,
    onStopAuto: () -> Unit,
    onFaster: () -> Unit,
    onSlower: () -> Unit,
    onJump: () -> Unit,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (autoMode == AutoMode.Off) {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    BarAction(Icons.Filled.PlayArrow, "تمرير تلقائي", onStartAuto)
                    BarAction(Icons.Filled.FormatListNumbered, "صفحة ${ArabicText.indic(page)}", onJump)
                    BarAction(Icons.Filled.Fullscreen, "ملء الشاشة", onFullscreen)
                    BarAction(Icons.Filled.TextFields, "القراءة", onSettings)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleAuto) {
                        Icon(
                            if (autoMode == AutoMode.Running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (autoMode == AutoMode.Running) "إيقاف مؤقت" else "متابعة",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onSlower, enabled = speed > AutoScroll.MIN_SPEED) { Icon(Icons.Filled.Remove, contentDescription = "أبطأ") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (autoMode == AutoMode.Paused) "متوقف مؤقتًا" else "السرعة ${ArabicText.indic(speed)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            if (autoMode == AutoMode.Paused) "اضغط ▶ للمتابعة" else AutoScroll.levelName(speed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onFaster, enabled = speed < AutoScroll.MAX_SPEED) { Icon(Icons.Filled.Add, contentDescription = "أسرع") }
                    IconButton(onClick = onStopAuto) { Icon(Icons.Filled.Close, contentDescription = "إنهاء التمرير التلقائي") }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---------------------------------------------------------------- التدفق المتواصل (التمرير التلقائي)

/**
 * قائمة رأسية من الصفحات تبدأ من [startPage]، تتحرك إلى الأعلى بسرعة ثابتة وتستمر عبر حدود الصفحات
 * حتى نهاية المصحف. عند الإيقاف المؤقت يبقى المكان ويمكن التمرير اليدوي. الحركة تتراكم كسورها
 * (نحرّك بأعداد صحيحة من البكسل) حتى لا تُفقد السرعات البطيئة.
 */
@Composable
private fun ContinuousReader(
    quran: QuranData,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    startPage: Int,
    startOffset: Int,
    running: Boolean,
    speed: Int,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    onAyahTap: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    onTopPage: (Int) -> Unit,
    onEnd: () -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = 0, initialFirstVisibleItemScrollOffset = startOffset)
    val density = LocalDensity.current
    val count = quran.pageCount - startPage + 1
    val speedNow by rememberUpdatedState(speed)
    val endNow by rememberUpdatedState(onEnd)
    val topNow by rememberUpdatedState(onTopPage)

    // الصفحة التي تقع في منتصف الشاشة هي "الصفحة الحالية" (للحفظ والعنوان)
    LaunchedEffect(state, startPage) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size > center }?.index
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { topNow(startPage + it) }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        var acc = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            acc += AutoScroll.dpPerSecond(speedNow) * density.density * dt
            val whole = acc.toInt()
            if (whole >= 1) {
                acc -= whole
                try {
                    state.scrollBy(whole.toFloat())
                } catch (e: CancellationException) {
                    // لمسة المستخدم تقاطع الحركة؛ إن لم يُلغَ عملنا نفسه نواصل (الإيقاف المؤقت يتولاه السحب)
                    currentCoroutineContext().ensureActive()
                }
                if (!state.canScrollForward) {
                    endNow()
                    return@LaunchedEffect
                }
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        items(count, key = { it }) { i ->
            ContinuousPageItem(
                page = startPage + i,
                quran = quran,
                prefs = prefs,
                palette = palette,
                selectedAyahId = selectedAyahId,
                focusAyahId = focusAyahId,
                playingAyahId = playingAyahId,
                onAyahTap = onAyahTap,
                onBackgroundTap = onBackgroundTap,
            )
        }
    }
}

// ---------------------------------------------------------------- أدوات النظام

/** يرصد سحب المستخدم (تجاوز عتبة اللمس) دون استهلاك الحدث. */
private fun Modifier.onUserDrag(onDrag: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var fired = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val ch = event.changes.firstOrNull { it.id == down.id }
            if (!fired && ch != null && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                fired = true
                onDrag()
            }
        } while (event.changes.any { it.pressed })
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** يخفي أشرطة النظام في وضع ملء الشاشة ويعيدها عند الخروج من القارئ. */
@Composable
private fun ImmersiveEffect(hidden: Boolean, lightStatusIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden, lightStatusIcons) {
        val window = view.context.findActivity()?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLight = ctrl?.isAppearanceLightStatusBars
        if (ctrl != null && hidden) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.isAppearanceLightStatusBars = lightStatusIcons
        }
        onDispose {
            if (ctrl != null) {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
                if (prevLight != null) ctrl.isAppearanceLightStatusBars = prevLight
            }
        }
    }
}
