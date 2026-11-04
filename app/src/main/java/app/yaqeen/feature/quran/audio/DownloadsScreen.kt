package app.yaqeen.feature.quran.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.quran.plans.SurahNames
import app.yaqeen.feature.quran.tafsir.EditionRow
import app.yaqeen.feature.quran.tafsir.TafsirCatalog
import app.yaqeen.feature.quran.tafsir.TafsirStore
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import kotlinx.coroutines.launch

/** شاشة التنزيلات: تنزيل سور التلاوة لقارئ، المحمَّل لكل قارئ بأحجامه، والتفاسير، والمساحة الإجمالية. */
@Composable
internal fun DownloadsScreenContent(nav: NavHostController, c: AppContainer) {
    val mgr = remember { AudioDownloadManager.of(c) }
    val tafsir = remember { TafsirStore.of(c) }
    val scope = rememberCoroutineScope()

    val reciterSetting by c.settings.observe(Settings.QURAN_RECITER).collectAsState(initial = Reciters.DEFAULT_ID)
    val reciter = Reciters.byId(reciterSetting)
    val active by mgr.active.collectAsState()
    val audioVersion by mgr.version.collectAsState()
    val tafsirVersion by tafsir.version.collectAsState()
    val tafsirDownloads by tafsir.downloads.collectAsState()

    var quran by remember { mutableStateOf<QuranData?>(null) }
    var scan by remember { mutableStateOf<ReciterScan?>(null) }
    var perReciter by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var pickReciter by remember { mutableStateOf(false) }
    var deleteSurah by remember { mutableStateOf<Int?>(null) }
    var deleteReciter by remember { mutableStateOf<String?>(null) }
    var deleteTafsir by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { quran = runCatching { c.quran() }.getOrNull() }
    LaunchedEffect(reciter.id, audioVersion) { scan = mgr.scan(reciter.id) }
    LaunchedEffect(audioVersion) { perReciter = mgr.bytesByReciter() }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("التنزيلات", onBack = { nav.popBackStack() })
        val q = quran
        val sc = scan
        if (q == null || sc == null) {
            LoadingBox()
            return@Column
        }
        val audioTotal = perReciter.values.sum()
        val tafsirTotal = tafsir.downloadedEditions().sumOf { tafsir.sizeBytes(it.id) } + (tafsirVersion * 0)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("storage") {
                YaqeenCard(Modifier.fillMaxWidth()) {
                    Text("المساحة المستخدمة", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(SizeFormat.bytes(audioTotal + tafsirTotal), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "التلاوات: ${SizeFormat.bytes(audioTotal)} • التفاسير المنزَّلة: ${SizeFormat.bytes(tafsirTotal)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "التفسير الميسر مضمَّن في التطبيق ولا يحتسب هنا. التنزيل يحتاج اتصالًا بالإنترنت، والاستماع بعده يعمل دونه.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------- تنزيل التلاوات
            item("t-audio") { SectionTitle("تنزيل التلاوات للاستماع دون إنترنت") }
            item("reciter") {
                OutlinedButton(onClick = { pickReciter = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("القارئ: ${reciter.nameAr}", modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ExpandMore, null)
                }
            }
            items((1..114).toList(), key = { "s$it" }) { n ->
                val s = q.surah(n)
                SurahRow(
                    name = SurahNames.plain(s.nameAr), ayahCount = s.ayahCount,
                    downloaded = sc.counts[n], bytes = sc.bytes[n],
                    progress = active["${reciter.id}:$n"],
                    onDownload = { mgr.clearError(reciter.id, n); mgr.start(reciter.id, n) },
                    onCancel = { mgr.cancel(reciter.id, n) },
                    onDelete = { deleteSurah = n },
                )
            }

            // ---------------- المحمَّل لكل قارئ
            item("t-per") { SectionTitle("المحمَّل حسب القارئ") }
            if (perReciter.isEmpty()) {
                item("per-empty") { Text("لا توجد تلاوات محمَّلة بعد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(perReciter.entries.toList(), key = { "pr-${it.key}" }) { (id, size) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Reciters.byId(id).nameAr, style = MaterialTheme.typography.bodyLarge)
                            Text(SizeFormat.bytes(size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteReciter = id }) { Icon(Icons.Filled.Delete, "حذف تلاوات ${Reciters.byId(id).nameAr}") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // ---------------- التفاسير
            item("t-tafsir") { SectionTitle("التفاسير") }
            items(TafsirCatalog.all, key = { "ed-${it.id}" }) { e ->
                val available = tafsir.isDownloaded(e.id)
                Column {
                    EditionRow(
                        e = e, available = available, selected = false, download = tafsirDownloads[e.id],
                        onSelect = { scope.launch { c.settings.set(Settings.QURAN_TAFSIR_EDITION, e.id) } },
                        onDownload = { tafsir.clearError(e.id); tafsir.download(e.id) },
                        onCancel = { tafsir.cancel(e.id) },
                    )
                    if (!e.bundled && available) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الحجم: ${SizeFormat.bytes(tafsir.sizeBytes(e.id))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { deleteTafsir = e.id }) { Text("حذف") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item("t-note") {
                Text(TafsirCatalog.SOURCE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (pickReciter) {
            AlertDialog(
                onDismissRequest = { pickReciter = false },
                title = { Text("اختر القارئ") },
                text = {
                    LazyColumn {
                        items(Reciters.all, key = { it.id }) { r ->
                            Row(Modifier.fillMaxWidth().clickable {
                                scope.launch { c.settings.set(Settings.QURAN_RECITER, r.id) }
                                pickReciter = false
                            }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = r.id == reciter.id, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(r.nameAr, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pickReciter = false }) { Text("إغلاق") } },
            )
        }
        deleteSurah?.let { n ->
            ConfirmDialog(
                title = "حذف التلاوة المحمَّلة",
                message = "ستُحذف تلاوة سورة ${SurahNames.plain(q.surah(n).nameAr)} بصوت ${reciter.nameAr} من هذا الجهاز.",
                confirmText = "حذف",
                onConfirm = { mgr.deleteSurah(reciter.id, n) },
                onDismiss = { deleteSurah = null },
            )
        }
        deleteReciter?.let { id ->
            ConfirmDialog(
                title = "حذف تلاوات القارئ",
                message = "ستُحذف كل تلاوات ${Reciters.byId(id).nameAr} المحمَّلة من هذا الجهاز.",
                confirmText = "حذف",
                onConfirm = { mgr.deleteReciter(id) },
                onDismiss = { deleteReciter = null },
            )
        }
        deleteTafsir?.let { id ->
            ConfirmDialog(
                title = "حذف التفسير",
                message = "سيُحذف ${TafsirCatalog.byId(id)?.name} من هذا الجهاز، ويمكنك تنزيله لاحقًا.",
                confirmText = "حذف",
                onConfirm = { tafsir.delete(id) },
                onDismiss = { deleteTafsir = null },
            )
        }
    }
}

@Composable
private fun SurahRow(
    name: String, ayahCount: Int, downloaded: Int, bytes: Long, progress: SurahDownload?,
    onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit,
) {
    val running = progress != null && progress.error == null
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("سورة $name", style = MaterialTheme.typography.bodyLarge)
                val sub = when {
                    running -> "جارٍ التنزيل: ${ArabicText.indic(progress!!.done)} من ${ArabicText.indic(progress.total)} آية"
                    downloaded >= ayahCount -> "محمَّلة كاملة • ${SizeFormat.bytes(bytes)}"
                    downloaded > 0 -> "محمَّلة جزئيًا: ${ArabicText.indic(downloaded)} من ${ArabicText.indic(ayahCount)} آية • ${SizeFormat.bytes(bytes)}"
                    else -> "${ArabicText.indic(ayahCount)} آية"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                running -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "إلغاء التنزيل") }
                downloaded >= ayahCount -> IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف التلاوة المحمَّلة") }
                else -> {
                    IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, if (downloaded > 0) "استكمال التنزيل" else "تنزيل السورة") }
                    if (downloaded > 0) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف الجزء المحمَّل") }
                }
            }
        }
        if (running) LinearProgressIndicator(progress = { progress!!.fraction }, modifier = Modifier.fillMaxWidth())
        if (progress?.error != null) Text(progress.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
