package app.yaqeen.feature.adhkar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yaqeen.core.ArabicText
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AdhkarCategoryScreen(vm: AdhkarCategoryViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val s = ui) {
        CategoryUi.Loading -> Column(Modifier.fillMaxSize()) {
            YaqeenTopBar("الأذكار", onBack)
            LoadingBox()
        }
        is CategoryUi.Error -> Column(Modifier.fillMaxSize()) {
            YaqeenTopBar("الأذكار", onBack)
            EmptyState(title = s.message, action = { Button(onClick = vm::retry) { Text("إعادة المحاولة") } })
        }
        is CategoryUi.Ready -> CategoryContent(s, vm, onBack)
    }
}

@Composable
private fun CategoryContent(s: CategoryUi.Ready, vm: AdhkarCategoryViewModel, onBack: () -> Unit) {
    val n = s.items.size
    val pagerState = rememberPagerState(initialPage = s.startPage.coerceIn(0, n)) { n + 1 }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val prefs by rememberUpdatedState(s.prefs)
    var touched by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ItemUi?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    // حفظ آخر موضع: بعد استقرار الصفحة بنصف ثانية، وعند مغادرة الشاشة. لا نحفظ لمجرد فتح القسم.
    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (page != s.startPage) touched = true
        if (touched) {
            delay(500)
            vm.savePosition(page)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (touched) vm.savePosition(pagerState.currentPage) }
    }

    fun onTap(item: ItemUi) {
        val r = vm.tap(item.item.id)
        if (!r.counted) return
        touched = true
        if (prefs.haptics) {
            haptic.performHapticFeedback(if (r.itemCompleted) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
        val page = item.index
        when {
            r.categoryCompleted -> scope.launch {
                delay(500)
                pagerState.animateScrollToPage(n)
            }
            r.itemCompleted && prefs.autoAdvance -> scope.launch {
                delay(350)
                if (pagerState.currentPage == page) pagerState.animateScrollToPage(page + 1)
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val palette = SharePalette(
        background = colors.background.toArgb(),
        card = colors.surface.toArgb(),
        border = colors.outlineVariant.toArgb(),
        text = colors.onSurface.toArgb(),
        muted = colors.onSurfaceVariant.toArgb(),
        accent = colors.primary.toArgb(),
    )

    fun actionsFor(item: ItemUi) = AdhkarCardActions(
        onTap = { onTap(item) },
        onFavorite = { vm.toggleFavorite(item.item) },
        onBookmark = { vm.toggleBookmark(item.item) },
        onShareText = { if (!AdhkarShare.shareText(context, item.item)) say("تعذّرت المشاركة") },
        onShareImage = {
            scope.launch { if (!AdhkarShare.shareImage(context, item.item, s.title, palette)) say("تعذّر إنشاء الصورة") }
        },
        onCopy = { say(if (AdhkarShare.copy(context, item.item)) "تم نسخ الذكر" else "تعذّر النسخ") },
        onEditTarget = { editing = item },
        onReset = { vm.resetItem(item.item.id) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            YaqeenTopBar(
                title = s.title,
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        vm.setAutoAdvance(!s.prefs.autoAdvance)
                        say(if (s.prefs.autoAdvance) "تم إيقاف الانتقال التلقائي" else "تم تفعيل الانتقال التلقائي بعد إتمام كل ذكر")
                    }) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = if (s.prefs.autoAdvance) "إيقاف الانتقال التلقائي" else "تفعيل الانتقال التلقائي",
                            tint = if (s.prefs.autoAdvance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = {
                        vm.setVertical(!s.prefs.vertical)
                        say(if (s.prefs.vertical) "التمرير الآن يمينًا ويسارًا" else "التمرير الآن للأعلى والأسفل")
                    }) {
                        Icon(
                            if (s.prefs.vertical) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
                            contentDescription = if (s.prefs.vertical) "التمرير يمينًا ويسارًا" else "التمرير للأعلى والأسفل",
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "المزيد") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("إعادة عدّادات القسم لليوم") },
                                leadingIcon = { Icon(Icons.Filled.Restore, null) },
                                enabled = s.items.any { it.progress.count > 0 },
                                onClick = { menu = false; confirmResetAll = true },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            ProgressHeader(
                position = (pagerState.currentPage + 1).coerceAtMost(n),
                total = n,
                completed = s.completedCount,
            )
            if (s.groups.isNotEmpty()) {
                val currentGroup = s.items.getOrNull(pagerState.currentPage)?.item?.group
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.groups, key = { it.first }) { (name, start) ->
                        FilterChip(
                            selected = currentGroup == name,
                            onClick = { scope.launch { pagerState.animateScrollToPage(start) } },
                            label = { Text(name) },
                        )
                    }
                }
            }
            val pageContent: @Composable (Int) -> Unit = { page ->
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (page < n) {
                        val item = s.items[page]
                        AdhkarCard(item, n, s.prefs, actionsFor(item))
                    } else {
                        CompletionPage(
                            s = s,
                            onReview = { scope.launch { pagerState.animateScrollToPage(0) } },
                            onJump = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                            onBack = onBack,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth()) {
                if (s.prefs.vertical) {
                    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { page -> pageContent(page) }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { page -> pageContent(page) }
                }
            }
            NavigationRow(pagerState, n)
        }
    }

    editing?.let { item ->
        TargetDialog(
            current = item.progress.target,
            catalogCount = item.item.count,
            custom = item.customTarget,
            onDismiss = { editing = null },
            onSave = { vm.setTarget(item.item.id, it) },
            onResetDefault = { vm.resetTarget(item.item.id) },
        )
    }
    if (confirmResetAll) {
        ConfirmDialog(
            title = "إعادة العدّادات",
            message = "ستُصفَّر عدّادات كل أذكار هذا القسم لليوم. هل تريد المتابعة؟",
            confirmText = "إعادة",
            onConfirm = { vm.resetCategory() },
            onDismiss = { confirmResetAll = false },
        )
    }
}

@Composable
private fun ProgressHeader(position: Int, total: Int, completed: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${ArabicText.indic(position)} / ${ArabicText.indic(total)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        ProgressBar(progress = if (total == 0) 0f else completed.toFloat() / total, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            "أُنجز ${ArabicText.indic(completed)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationRow(pagerState: PagerState, n: Int) {
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
            enabled = page > 0,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("السابق")
        }
        Button(
            onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
            enabled = page < n,
        ) {
            Text(if (page == n - 1) "الخلاصة" else "التالي")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

/** الصفحة الأخيرة: رسالة هادئة عند إتمام القسم، أو قائمة بما تبقّى. */
@Composable
private fun CompletionPage(s: CategoryUi.Ready, onReview: () -> Unit, onJump: (Int) -> Unit, onBack: () -> Unit) {
    YaqeenCard(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        if (s.allCompleted) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp), tint = YaqeenTheme.extra.success)
                Spacer(Modifier.height(16.dp))
                Text("تمّت ${s.title} لهذا اليوم", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "تقبّل الله منك",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onReview) { Text("مراجعة الأذكار") }
                TextButton(onClick = onBack) { Text("رجوع") }
            }
        } else {
            val remaining = s.items.filter { !it.progress.completed }
            Text(
                "بقي ${ArabicText.indic(remaining.size)} من ${ArabicText.indic(s.items.size)}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "اضغط على ذكر للانتقال إليه",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(remaining, key = { it.item.id }) { it ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(it.index) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ArabicText.indic(it.index + 1) + ".",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            it.item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "المتبقي " + ArabicText.indic(it.progress.remaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("العودة إلى البداية") }
        }
    }
}
