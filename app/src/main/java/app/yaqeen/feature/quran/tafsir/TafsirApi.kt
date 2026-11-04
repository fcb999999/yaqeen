package app.yaqeen.feature.quran.tafsir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.quran.plans.SurahNames
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.theme.YaqeenTextStyles
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.launch

/** نتيجة البحث عن تفسير آية في كتاب. */
private sealed interface TafsirResult {
    data object Loading : TafsirResult
    /** [sourceAyah] هي الآية التي وُجد عندها النص (قد تسبق المطلوبة إن كان التفسير مجمَّعًا مع ما قبلها). */
    data class Found(val sourceAyah: Int, val text: String) : TafsirResult
    data object NoText : TafsirResult
    data object Failed : TafsirResult
}

/**
 * === التفسير ===
 * نافذة سفلية: نص الآية في الأعلى ثم تفسيرها من الكتاب المختار (الميسر مضمَّن، والبقية تُنزَّل وتعمل بلا إنترنت).
 * يمكن التنقل بين الآيات وتبديل الكتاب من داخل النافذة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TafsirSheet(c: AppContainer, ayahId: Int, onDismiss: () -> Unit) {
    val store = remember { TafsirStore.of(c) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val savedId by c.settings.observe(Settings.QURAN_TAFSIR_EDITION).collectAsState(initial = TafsirCatalog.DEFAULT)
    val version by store.version.collectAsState()
    val downloads by store.downloads.collectAsState()

    var current by remember { mutableIntStateOf(ayahId.coerceIn(1, TafsirJson.AYAH_COUNT)) }
    var quran by remember { mutableStateOf<QuranData?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TafsirResult>(TafsirResult.Loading) }

    LaunchedEffect(Unit) { quran = runCatching { c.quran() }.getOrNull() }

    // الكتاب الفعلي: المحفوظ في الإعدادات إن كان على هذا الجهاز، وإلا الميسر المضمَّن.
    val bundled = TafsirCatalog.byId(TafsirCatalog.DEFAULT)!!
    val edition = remember(savedId, version) { TafsirCatalog.byId(savedId)?.takeIf { store.isDownloaded(it.id) } ?: bundled }
    val savedButMissing = savedId != edition.id && TafsirCatalog.byId(savedId) != null

    LaunchedEffect(current, edition.id, version, quran) {
        val q = quran ?: return@LaunchedEffect
        result = TafsirResult.Loading
        val own = store.text(edition.id, current)
        result = when {
            own == null -> TafsirResult.Failed
            own.isNotBlank() -> TafsirResult.Found(current, own)
            else -> {
                // لا نص مستقل لهذه الآية: قد يكون تفسيرها مذكورًا مع ما قبلها في السورة نفسها.
                var found: TafsirResult = TafsirResult.NoText
                val first = q.surah(q.ayah(current).surah).firstAyahId
                var id = current - 1
                while (id >= first) {
                    val t = store.text(edition.id, id)
                    if (!t.isNullOrBlank()) { found = TafsirResult.Found(id, t); break }
                    id--
                }
                found
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val q = quran
        if (q == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        val a = q.ayah(current)
        SelectionContainer {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item("head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = current > 1, onClick = { current-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "الآية السابقة") }
                        Text(
                            "تفسير ${SurahNames.plain(q.surah(a.surah).nameAr)} • الآية ${ArabicText.indic(a.number)}",
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                        IconButton(enabled = current < TafsirJson.AYAH_COUNT, onClick = { current++ }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "الآية التالية") }
                    }
                }
                item("ayah") {
                    YaqeenCard(Modifier.fillMaxWidth()) {
                        Text(a.text, style = YaqeenTextStyles.quran(24f, 1.9f).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                    }
                }
                item("edition") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(edition.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(edition.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { showPicker = !showPicker }) { Text(if (showPicker) "إخفاء الكتب" else "تغيير الكتاب") }
                    }
                    if (savedButMissing) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "الكتاب المختار (${TafsirCatalog.byId(savedId)?.name}) غير محمَّل على هذا الجهاز، فيُعرض التفسير الميسر. نزّله من «تغيير الكتاب».",
                            style = MaterialTheme.typography.bodySmall, color = YaqeenTheme.extra.warning,
                        )
                    }
                }
                if (showPicker) {
                    items(TafsirCatalog.all, key = { "ed-${it.id}" }) { e ->
                        EditionRow(
                            e = e,
                            available = store.isDownloaded(e.id),
                            selected = e.id == edition.id,
                            download = downloads[e.id],
                            onSelect = { scope.launch { c.settings.set(Settings.QURAN_TAFSIR_EDITION, e.id) } },
                            onDownload = { store.clearError(e.id); store.download(e.id) },
                            onCancel = { store.cancel(e.id) },
                        )
                    }
                    item("picker-end") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
                when (val r = result) {
                    TafsirResult.Loading -> item("loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    TafsirResult.Failed -> item("failed") {
                        Text("تعذّر قراءة التفسير من هذا الكتاب. جرّب كتابًا آخر.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                    TafsirResult.NoText -> item("none") {
                        Text("لا يوجد في هذا الكتاب تفسير مستقل لهذه الآية.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is TafsirResult.Found -> {
                        if (r.sourceAyah != current) {
                            item("earlier") {
                                Text(
                                    "تفسير هذه الآية مذكور في هذا الكتاب مع الآية ${ArabicText.indic(q.ayah(r.sourceAyah).number)} وما بعدها:",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val paras = TafsirJson.paragraphs(r.text)
                        items(paras.size, key = { "p-${r.sourceAyah}-$it" }) { i ->
                            Text(
                                paras[i],
                                style = TextStyle(
                                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 17.sp, lineHeight = 30.sp,
                                    textDirection = TextDirection.Rtl, textAlign = TextAlign.Start,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item("attr") {
                    Column(Modifier.padding(top = 8.dp, bottom = 32.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("${edition.name} — ${edition.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!edition.bundled) Text(TafsirCatalog.SOURCE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** صف كتاب تفسير: اختيار (إن كان متاحًا) أو تنزيل مع التقدّم. */
@Composable
internal fun EditionRow(
    e: TafsirEdition, available: Boolean, selected: Boolean, download: TafsirDownload?,
    onSelect: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit,
) {
    val dl = download
    val downloading = dl != null && dl.error == null
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    e.author + if (e.bundled) " • مضمَّن في التطبيق" else if (available) " • محمَّل" else " • حجم التنزيل نحو ${ArabicText.indic(e.approxMb)} م.ب",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                downloading -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "إلغاء التنزيل") }
                available && selected -> Icon(Icons.Filled.CheckCircle, "الكتاب الحالي", tint = YaqeenTheme.extra.success)
                available -> TextButton(onClick = onSelect) { Text("اختيار") }
                else -> Button(onClick = onDownload) {
                    Icon(Icons.Filled.Download, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تنزيل")
                }
            }
        }
        if (dl != null && downloading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { dl.fraction }, modifier = Modifier.fillMaxWidth())
            Text("جارٍ التنزيل ${ArabicText.indic((dl.fraction * 100).toInt())}٪", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dl?.error != null) {
            Text(dl.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
