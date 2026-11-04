package app.yaqeen.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.data.local.YaqeenDatabase
import app.yaqeen.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** أحجام ما يشغله التطبيق على الجهاز (بايت). */
internal data class StorageUsage(
    val database: Long,
    val audio: Long,
    val tafsir: Long,
    val adhan: Long,
    val downloads: Long,
    val other: Long,
    val cache: Long,
) {
    val total: Long get() = database + audio + tafsir + adhan + downloads + other + cache
}

internal fun computeStorage(context: Context): StorageUsage {
    val dbFile = context.getDatabasePath(YaqeenDatabase.NAME)
    val db = listOf("", "-wal", "-shm", "-journal").sumOf { File(dbFile.path + it).let { f -> if (f.isFile) f.length() else 0L } }
    val files = context.filesDir
    val audio = DiskUsage.size(File(files, "audio"))
    val tafsir = DiskUsage.size(File(files, "tafsir"))
    val adhan = DiskUsage.size(File(files, "adhan"))
    val downloads = DiskUsage.size(File(files, "downloads"))
    val filesTotal = DiskUsage.size(files)
    val cache = DiskUsage.size(context.cacheDir) + (context.externalCacheDir?.let { DiskUsage.size(it) } ?: 0L)
    return StorageUsage(db, audio, tafsir, adhan, downloads, (filesTotal - audio - tafsir - adhan - downloads).coerceAtLeast(0), cache)
}

@Composable
internal fun StorageScreen(nav: NavHostController, c: AppContainer) {
    SettingsPage("مساحة التخزين", onBack = { nav.popBackStack() }) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val notify = rememberNotifier()
        var reload by remember { mutableIntStateOf(0) }
        var confirm by remember { mutableStateOf(false) }
        val usage by produceState<StorageUsage?>(null, reload) {
            value = withContext(Dispatchers.IO) { runCatching { computeStorage(context) }.getOrNull() }
        }

        val u = usage
        if (u == null) {
            CircularProgressIndicator(Modifier.padding(32.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
        } else {
            SettingsGroup("ما يشغله يقين على جهازك") {
                InfoRow("المجموع", ByteSize.format(u.total))
                RowDivider()
                InfoRow("قاعدة البيانات (بياناتك)", ByteSize.format(u.database))
                RowDivider()
                InfoRow("الصوتيات المحمّلة", ByteSize.format(u.audio))
                RowDivider()
                InfoRow("التفاسير المحمّلة", ByteSize.format(u.tafsir))
                RowDivider()
                InfoRow("أصوات الأذان", ByteSize.format(u.adhan))
                RowDivider()
                InfoRow("ملفات التحديث", ByteSize.format(u.downloads))
                RowDivider()
                InfoRow("ملفات أخرى", ByteSize.format(u.other))
                RowDivider()
                InfoRow("الذاكرة المؤقتة", ByteSize.format(u.cache))
            }
            Button(onClick = { confirm = true }, enabled = u.cache > 0, modifier = Modifier.fillMaxWidth()) { Text("مسح الذاكرة المؤقتة") }
            Hint("مسح الذاكرة المؤقتة آمن: لا يحذف بياناتك ولا ملفاتك المحمّلة، وقد يُعاد إنشاء بعضها عند الحاجة.")
            OutlinedButton(
                onClick = { if (!nav.tryNavigate(SettingsRoutes.QURAN_DOWNLOADS)) notify("هذه الشاشة غير متاحة في هذا الإصدار بعد.") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("إدارة الملفات المحمّلة") }
        }
        if (confirm) {
            ConfirmDialog(
                title = "مسح الذاكرة المؤقتة؟",
                message = "ستُحذف الملفات المؤقتة فقط. لن تتأثر بياناتك ولا الصوتيات والتفاسير المحمّلة.",
                confirmText = "مسح",
                onConfirm = {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) {
                            DiskUsage.clearContents(context.cacheDir) + (context.externalCacheDir?.let { DiskUsage.clearContents(it) } ?: 0L)
                        }
                        reload++
                        notify("تم تحرير ${ByteSize.format(freed)}.")
                    }
                },
                onDismiss = { confirm = false },
            )
        }
    }
}
