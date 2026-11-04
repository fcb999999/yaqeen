package app.yaqeen.feature.quran.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.Surah
import app.yaqeen.feature.quran.reader.HizbLabels
import app.yaqeen.feature.quran.reader.IndexFilter
import app.yaqeen.feature.quran.reader.JumpDialog
import app.yaqeen.feature.quran.reader.JumpTarget
import app.yaqeen.feature.quran.reader.QuarterEntry
import app.yaqeen.feature.quran.reader.QuranLoadGate
import app.yaqeen.feature.quran.reader.QuranText
import app.yaqeen.feature.quran.reader.rememberQuranLoad
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenFonts

private val TAB_TITLES = listOf("السور", "الأجزاء", "الأرباع")

/** فهرس المصحف: السور / الأجزاء / أرباع الأحزاب مع بحث فوري وانتقال بالسورة والآية أو الصفحة أو الجزء. */
@Composable
fun QuranIndexScreen(nav: NavHostController, c: AppContainer) {
    var retry by remember { mutableIntStateOf(0) }
    val load by rememberQuranLoad(c, retry)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showJump by remember { mutableStateOf(false) }

    fun open(surah: Int = -1, ayah: Int = -1, page: Int = -1) {
        nav.navigate(Routes.quranReader(surah, ayah, page)) {
            popUpTo(Routes.QURAN_READER_PATTERN) { inclusive = true }
        }
    }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(
            title = "الفهرس",
            onBack = { nav.popBackStack() },
            actions = {
                IconButton(onClick = { showJump = true }) { Icon(Icons.Filled.SwapVert, contentDescription = "الانتقال إلى") }
            },
        )
        QuranLoadGate(load, onRetry = { retry++ }) { quran ->
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (tab == 0) "ابحث باسم السورة أو رقمها" else if (tab == 1) "ابحث برقم الجزء أو اسم السورة" else "ابحث برقم الحزب أو اسم السورة") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "مسح") }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                TAB_TITLES.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> SurahList(quran, query) { s -> open(surah = s.number) }
                1 -> JuzList(quran, query) { a -> open(surah = a.surah, ayah = a.number) }
                else -> QuarterList(quran, query) { a -> open(surah = a.surah, ayah = a.number) }
            }
            if (showJump) {
                JumpDialog(
                    quran = quran,
                    initialSurah = 1,
                    onDismiss = { showJump = false },
                    onJump = { t ->
                        when (t) {
                            is JumpTarget.AyahTarget -> open(surah = t.surah, ayah = t.ayah)
                            is JumpTarget.PageTarget -> open(page = t.page)
                            is JumpTarget.JuzTarget -> quran.firstAyahOfJuz(t.juz).let { open(surah = it.surah, ayah = it.number) }
                        }
                    },
                )
            }
        }
    }
}

private val NameStyle = TextStyle(fontFamily = YaqeenFonts.Quran, fontSize = 22.sp, textDirection = TextDirection.Rtl)

@Composable
private fun NoResults() {
    EmptyState(
        title = "لا توجد نتائج",
        message = "جرّب اسمًا أو رقمًا آخر.",
        icon = Icons.Filled.SearchOff,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberBadge(text: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
private fun SurahList(quran: QuranData, query: String, onClick: (Surah) -> Unit) {
    val items = remember(quran, query) { IndexFilter.surahs(quran, query) }
    if (items.isEmpty()) {
        NoResults()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.number }) { s ->
            val first = quran.ayah(s.firstAyahId)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick(s) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBadge(ArabicText.indic(s.number))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.yaqeen.feature.quran.reader.QuranText.forHafsFont(s.nameAr), style = NameStyle, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${if (s.meccan) "مكية" else "مدنية"} – ${ArabicText.indic(s.ayahCount)} آية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("ص ${ArabicText.indic(first.page)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private val SnippetStyle = TextStyle(fontFamily = YaqeenFonts.Quran, fontSize = 16.sp, textDirection = TextDirection.Rtl)

@Composable
private fun JuzList(quran: QuranData, query: String, onClick: (Ayah) -> Unit) {
    val all = remember(quran) { IndexFilter.juzStarts(quran) }
    val items = remember(quran, query) { IndexFilter.juz(quran, all, query) }
    if (items.isEmpty()) {
        NoResults()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.juz }) { a ->
            StartRow(
                badge = ArabicText.indic(a.juz),
                title = "الجزء ${ArabicText.indic(a.juz)}",
                quran = quran,
                ayah = a,
                onClick = { onClick(a) },
            )
        }
    }
}

@Composable
private fun QuarterList(quran: QuranData, query: String, onClick: (Ayah) -> Unit) {
    val all = remember(quran) { IndexFilter.quarters(quran) }
    val items: List<QuarterEntry> = remember(quran, query) { IndexFilter.quarters(quran, all, query) }
    if (items.isEmpty()) {
        NoResults()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.hizbQuarter }) { q ->
            StartRow(
                badge = ArabicText.indic(HizbLabels.hizbOf(q.hizbQuarter)),
                title = HizbLabels.label(q.hizbQuarter),
                quran = quran,
                ayah = q.first,
                onClick = { onClick(q.first) },
            )
        }
    }
}

@Composable
private fun StartRow(badge: String, title: String, quran: QuranData, ayah: Ayah, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(badge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            // بداية المقطع: أول آية فيه كما هي (سطر واحد)
            Text(app.yaqeen.feature.quran.reader.QuranText.forHafsFont(ayah.text), style = SnippetStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "سورة ${QuranText.plainSurahName(quran.surah(ayah.surah).nameAr)} – الآية ${ArabicText.indic(ayah.number)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("ص ${ArabicText.indic(ayah.page)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
