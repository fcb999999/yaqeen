package app.yaqeen.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.core.ArabicText
import app.yaqeen.data.auth.AuthState
import app.yaqeen.feature.settings.data.BackupFormat
import app.yaqeen.feature.settings.data.DataDeletion
import app.yaqeen.feature.settings.data.DeletionOutcome
import app.yaqeen.feature.settings.data.UserDataManager
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.YaqeenCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024

@Composable
internal fun DataManagementScreen(nav: NavHostController, c: AppContainer) {
    SettingsPage("إدارة البيانات", onBack = { nav.popBackStack() }) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val notify = rememberNotifier()
        val auth by c.auth.state.collectAsState()
        val manager = remember { UserDataManager(c.db, c.writer) }

        var busy by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        var total by remember { mutableStateOf<Int?>(null) }
        var pendingImport by remember { mutableStateOf<BackupFormat.Parsed.Ok?>(null) }
        var showDelete by remember { mutableStateOf(false) }

        LaunchedEffect(refresh) { total = runCatching { withContext(Dispatchers.IO) { manager.totalRecords() } }.getOrNull() }

        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = "جارٍ تصدير بياناتك…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val account = (c.auth.state.value as? AuthState.SignedIn)?.email
                        val text = manager.exportJson(BuildConfig.VERSION_NAME, System.currentTimeMillis(), account)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: error("تعذّر فتح الملف للكتابة")
                    }
                }
                busy = null
                notify(if (result.isSuccess) "تم تصدير بياناتك بنجاح." else "تعذّر التصدير: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}")
            }
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = "جارٍ قراءة الملف…"
            scope.launch {
                val parsed = runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_IMPORT_BYTES) }
                            ?: error("تعذّر فتح الملف")
                        BackupFormat.parse(bytes.toString(Charsets.UTF_8))
                    }
                }.getOrElse { BackupFormat.Parsed.Invalid(it.message ?: "تعذّرت قراءة الملف.") }
                busy = null
                when (parsed) {
                    is BackupFormat.Parsed.Ok -> if (parsed.rowCount == 0) notify("الملف لا يحتوي على بيانات.") else pendingImport = parsed
                    is BackupFormat.Parsed.Invalid -> notify(parsed.reason)
                }
            }
        }

        // ---- ملخص
        YaqeenCard(Modifier.fillMaxWidth()) {
            Text("بياناتك", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                total?.let { if (it == 0) "لا توجد سجلات بعد." else "لديك ${ArabicText.indic(it)} سجلًا (قراءة، أذكار، صلوات، مهام، أحداث، إعدادات…)." } ?: "جارٍ الحساب…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        // ---- تصدير واستيراد
        SettingsGroup("النسخ الاحتياطي") {
            Hint("يُحفظ ملف JSON واحد يحوي كل بياناتك (بلا كلمات مرور أو مفاتيح) في المكان الذي تختاره، ويمكنك استيراده لاحقًا على هذا الجهاز أو غيره.")
            Button(
                onClick = { exportLauncher.launch("yaqeen-backup-${LocalDate.now()}.json") },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("تصدير بياناتي") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("استيراد نسخة احتياطية") }
            Hint("الاستيراد يدمج الملف مع بياناتك الحالية، وتغلب سجلات الملف عند تطابق السجل نفسه.")
            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(busy!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- الحذف
        SettingsGroup("منطقة الخطر") {
            Hint(
                if (auth is AuthState.SignedIn)
                    "يحذف كل بياناتك: تُعلَّم نسختك السحابية كمحذوفة أولًا فتتبعها بقية أجهزتك، ثم تُمسح بيانات هذا الجهاز. لا يمكن التراجع."
                else
                    "يحذف كل بيانات هذا الجهاز نهائيًا. لا يمكن التراجع.",
            )
            OutlinedButton(
                onClick = { showDelete = true },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف كل بياناتي") }
            Spacer(Modifier.height(8.dp))
        }

        pendingImport?.let { p ->
            ConfirmDialog(
                title = "استيراد النسخة الاحتياطية؟",
                message = "سيُدمج ${ArabicText.indic(p.rowCount)} سجلًا من الملف مع بياناتك الحالية، وتحلّ سجلات الملف محل السجلات المطابقة لها.",
                confirmText = "استيراد",
                onConfirm = {
                    busy = "جارٍ الاستيراد…"
                    c.appScope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { manager.import(p) } }
                        withContext(Dispatchers.Main) {
                            busy = null
                            refresh++
                            notify(r.fold({ "تم استيراد ${ArabicText.indic(it)} سجلًا." }, { "تعذّر الاستيراد: ${it.message ?: "خطأ غير معروف"}" }))
                        }
                        if (r.isSuccess) c.requestReschedule()
                    }
                },
                onDismiss = { pendingImport = null },
            )
        }

        if (showDelete) {
            DeleteEverythingDialog(
                signedIn = auth is AuthState.SignedIn,
                onDismiss = { showDelete = false },
                onConfirm = {
                    showDelete = false
                    busy = "جارٍ حذف بياناتك…"
                    c.appScope.launch {
                        val outcome = DataDeletion(c).deleteEverything()
                        withContext(Dispatchers.Main) {
                            busy = null
                            refresh++
                            notify(
                                when (outcome) {
                                    is DeletionOutcome.Done ->
                                        if (outcome.hadAccount) "حُذفت بياناتك من هذا الجهاز ومن نسختك السحابية." else "حُذفت كل بيانات هذا الجهاز."
                                    is DeletionOutcome.CloudPending ->
                                        "حُذفت بياناتك من التطبيق. تعذّر الوصول للسحابة الآن، وسيصلها الحذف تلقائيًا عند توفر الاتصال."
                                    is DeletionOutcome.Failed -> "تعذّر الحذف: ${outcome.message}"
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) error("الملف أكبر من الحد المسموح للاستيراد")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** تأكيد قوي: يجب كتابة كلمة "حذف" لتفعيل زر التأكيد. */
@Composable
private fun DeleteEverythingDialog(signedIn: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف كل بياناتي؟") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "سيُحذف نهائيًا كل ما سجّلته: القراءة والأوراد والعلامات، الأذكار، الصلوات والسنن، المهام، الأحداث، والإعدادات.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (signedIn)
                        "ستُعلَّم نسختك السحابية كمحذوفة أولًا عبر المزامنة (شواهد حذف) فتتبعها بقية أجهزتك، ثم تُمسح بيانات هذا الجهاز. " +
                            "تبقى الوثائق في خادم Firestore بحالة «محذوفة» ولا يعرضها أي جهاز، ولا يمكن محوها فعليًا من داخل التطبيق."
                    else
                        "هذا يحذف بيانات هذا الجهاز فقط. إن كانت لديك نسخة سحابية من حساب سابق فسجّل الدخول به أولًا ثم احذف لتُحذف سحابته أيضًا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("لا يمكن التراجع عن هذه العملية.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("اكتب كلمة «حذف» للتأكيد") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "حذف") {
                Text("حذف نهائيًا", color = if (typed.trim() == "حذف") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
