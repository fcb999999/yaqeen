package app.yaqeen.feature.prayer.adhan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import app.yaqeen.feature.prayer.reminders.PrayerNotifPrefs
import app.yaqeen.feature.prayer.times.PrayerFormat
import app.yaqeen.feature.prayer.times.PrayerRoutes
import app.yaqeen.feature.prayer.times.PrayerScreenFrame
import app.yaqeen.feature.prayer.times.findActivity
import app.yaqeen.feature.prayer.times.openAppSettings
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdhanSettingsViewModel(private val c: AppContainer) : ViewModel() {
    val prefs: StateFlow<Map<Prayer, PrayerNotifPrefs>> = combine(
        Prayer.entries.map { p ->
            combine(
                c.settings.observe(Settings.notifAdhan(p.name)),
                c.settings.observe(Settings.notifPreMinutes(p.name)),
                c.settings.observe(Settings.notifIqamaMinutes(p.name)),
                c.settings.observe(Settings.adhanVoice(p.name)),
            ) { a, pre, iq, v -> p to PrayerNotifPrefs(a, pre, iq, v) }
        },
    ) { arr -> arr.toMap() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Prayer.entries.associateWith { PrayerNotifPrefs() })

    val hasLocation: StateFlow<Boolean?> = c.prayerTimes.observeLocation().map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val voices = MutableStateFlow(AdhanVoices.list(c.context))
    val busy = MutableStateFlow(false)
    /** رسالة داخل نافذة اختيار الصوت (نجاح/خطأ الاستيراد). */
    val dialogMessage = MutableStateFlow<Pair<String, Boolean>?>(null)
    /** مفتاح التجربة الجارية (null = لا شيء يعمل). */
    val previewKey = MutableStateFlow<String?>(null)
    private val toasts = Channel<String>(Channel.BUFFERED)
    val toastFlow = toasts.receiveAsFlow()

    private val preview = AdhanPreview(c.context)
    private var previewJob: Job? = null

    fun canScheduleExact(): Boolean = c.reminders.canScheduleExact()

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            c.requestReschedule()
        }
    }

    fun setAdhan(p: Prayer, on: Boolean) = save { c.settings.set(Settings.notifAdhan(p.name), on) }
    fun setPre(p: Prayer, minutes: Int) = save { c.settings.set(Settings.notifPreMinutes(p.name), minutes) }
    fun setIqama(p: Prayer, minutes: Int) = save { c.settings.set(Settings.notifIqamaMinutes(p.name), minutes) }

    fun setVoice(p: Prayer, value: String, applyToAll: Boolean) = save {
        val targets = if (applyToAll) Prayer.entries else listOf(p)
        targets.forEach { c.settings.set(Settings.adhanVoice(it.name), value) }
    }

    fun clearDialogMessage() { dialogMessage.value = null }

    fun importUri(uri: Uri, onImported: (String) -> Unit) {
        runImport({ AdhanVoices.importFromUri(c.context, uri) }, onImported)
    }

    fun importUrl(url: String, onImported: (String) -> Unit) {
        runImport({ AdhanVoices.importFromUrl(c.context, url) }, onImported)
    }

    private fun runImport(op: suspend () -> Result<String>, onImported: (String) -> Unit) {
        if (busy.value) return
        busy.value = true
        dialogMessage.value = null
        viewModelScope.launch {
            val r = op()
            busy.value = false
            voices.value = AdhanVoices.list(c.context)
            r.onSuccess {
                dialogMessage.value = "تمت إضافة الملف إلى مكتبتك ويعمل الآن دون إنترنت." to true
                onImported(AdhanVoices.fileValue(it))
            }.onFailure { dialogMessage.value = (it.message ?: "تعذّر الاستيراد.") to false }
        }
    }

    /** يحذف ملفًا مستوردًا ويعيد كل صلاة تستخدمه إلى الصوت الافتراضي. */
    fun deleteVoice(file: VoiceFile) {
        viewModelScope.launch {
            AdhanVoices.delete(c.context, file.name)
            voices.value = AdhanVoices.list(c.context)
            prefs.value.filter { it.value.voice == file.value }.keys.forEach { c.settings.set(Settings.adhanVoice(it.name), AdhanVoices.DEFAULT) }
            c.requestReschedule()
        }
    }

    fun refreshVoices() { voices.value = AdhanVoices.list(c.context) }

    fun stopPreview() {
        previewJob?.cancel()
        preview.stop()
        previewKey.value = null
    }

    /** يبدّل تجربة صوت: إن كانت تعمل بالمفتاح نفسه توقفها وإلا تبدأها. */
    fun togglePreview(key: String, voice: String) {
        if (previewKey.value == key) { stopPreview(); return }
        stopPreview()
        val playable = AdhanVoices.parse(voice) == VoiceRef.Default || AdhanVoices.isCustomPlayable(c.context, voice)
        val effective = if (playable) voice else AdhanVoices.DEFAULT
        if (!playable) toasts.trySend("ملف الصوت غير موجود، جرّبنا الصوت الافتراضي.")
        val ok = preview.play(effective) { previewKey.value = null }
        if (!ok) { toasts.trySend("تعذّر تشغيل الصوت."); return }
        previewKey.value = key
        if (AdhanVoices.parse(effective) == VoiceRef.Default) {
            previewJob = viewModelScope.launch {
                delay(400)
                while (preview.isRingtonePlaying) delay(300)
                previewKey.value = null
            }
        }
    }

    override fun onCleared() {
        preview.stop()
        super.onCleared()
    }
}

private val PRE_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 45, 60)
private val IQAMA_OPTIONS = listOf(0, 5, 10, 15, 20, 25, 30, 45)

private fun minutesLabel(m: Int, zeroLabel: String) = if (m == 0) zeroLabel else PrayerFormat.minutes(m)

@Composable
fun AdhanSettingsScreen(nav: NavHostController) {
    val vm = containerViewModel { AdhanSettingsViewModel(it) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val hasLocation by vm.hasLocation.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val previewKey by vm.previewKey.collectAsStateWithLifecycle()

    // حالة الأذونات تُعاد قراءتها كلما عدنا للشاشة (بعد زيارة إعدادات النظام).
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; vm.refreshVoices() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopPreview() }
    val notificationsOk = remember(tick) { notificationsAllowed(context) }
    val exactOk = remember(tick) { vm.canScheduleExact() }

    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (!granted) {
            val a = context.findActivity()
            val canAskAgain = a != null && Build.VERSION.SDK_INT >= 33 &&
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.POST_NOTIFICATIONS)
            if (!canAskAgain) openNotificationSettings(context)
        }
    }

    var voiceDialogFor by rememberSaveable { mutableStateOf<String?>(null) }
    var minutesDialog by remember { mutableStateOf<Triple<Prayer, Boolean, Int>?>(null) } // (prayer, isPre, current)

    LaunchedEffect(Unit) { vm.toastFlow.collect { snackbar.showSnackbar(it) } }

    PrayerScreenFrame(title = "الأذان والتنبيهات", onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasLocation == false) {
                WarningCard(
                    "لم تُحدَّد مدينتك بعد",
                    "تُجدول التنبيهات وفق موقعك. حدّد موقعك أو اختر مدينتك لتبدأ التنبيهات بالعمل.",
                    "تحديد الموقع",
                ) { nav.navigate(PrayerRoutes.LOCATION) }
            }
            if (!notificationsOk) {
                WarningCard(
                    "الإشعارات غير مسموح بها",
                    "لن تصلك تنبيهات الأذان والصلاة حتى تسمح للتطبيق بإظهار الإشعارات.",
                    if (Build.VERSION.SDK_INT >= 33 && !askedNotifications) "السماح بالإشعارات" else "فتح إعدادات الإشعارات",
                ) {
                    if (Build.VERSION.SDK_INT >= 33 && !askedNotifications) {
                        askedNotifications = true
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }
            }
            if (!exactOk) {
                WarningCard(
                    "المنبهات الدقيقة غير مسموح بها",
                    "بدونها قد يتأخر الأذان والتنبيه عن وقته بدقائق. اسمح للتطبيق بجدولة المنبهات والتذكيرات من إعدادات النظام.",
                    "فتح الإعدادات",
                ) { openExactAlarmSettings(context) }
            }

            YaqeenCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "لا يتضمن التطبيق تسجيلات أذان. الصوت الافتراضي هو نغمة إشعارات جهازك، ويمكنك لكل صلاة استيراد ملف صوتي تملك حق استخدامه ليُشغَّل دون إنترنت.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Prayer.entries.forEach { p ->
                val pr = prefs[p] ?: PrayerNotifPrefs()
                val missing = AdhanVoices.parse(pr.voice).let { it != VoiceRef.Default } && !AdhanVoices.isCustomPlayable(context, pr.voice)
                SectionTitle(p.ar)
                YaqeenCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    SwitchRow("الأذان عند دخول الوقت", pr.adhan, { vm.setAdhan(p, it) })
                    SettingRow("تنبيه قبل الصلاة", minutesLabel(pr.preMinutes, "معطّل")) { minutesDialog = Triple(p, true, pr.preMinutes) }
                    SettingRow("تنبيه الإقامة بعد الأذان", minutesLabel(pr.iqamaMinutes, "معطّل")) { minutesDialog = Triple(p, false, pr.iqamaMinutes) }
                    Row(Modifier.fillMaxWidth().clickable { voiceDialogFor = p.name }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("صوت الأذان", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (missing) "الملف غير موجود — سيُستخدم الصوت الافتراضي" else AdhanVoices.displayName(pr.voice),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val key = "prayer:${p.name}"
                        IconButton(onClick = { vm.togglePreview(key, pr.voice) }) {
                            if (previewKey == key) Icon(Icons.Filled.Stop, "إيقاف التجربة") else Icon(Icons.Filled.PlayArrow, "تجربة الصوت")
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Text(
                "عند وضع الجهاز على الصامت أو الاهتزاز يظهر إشعار الأذان دون تشغيل الصوت المخصص.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    minutesDialog?.let { (p, isPre, current) ->
        val options = if (isPre) PRE_OPTIONS else IQAMA_OPTIONS
        ChoiceDialog(
            title = if (isPre) "التنبيه قبل صلاة ${p.ar}" else "تنبيه إقامة صلاة ${p.ar}",
            options = options.map { it to minutesLabel(it, "معطّل") },
            selected = current,
            onSelect = { if (isPre) vm.setPre(p, it) else vm.setIqama(p, it) },
            onDismiss = { minutesDialog = null },
        )
    }

    voiceDialogFor?.let { name ->
        val p = Prayer.valueOf(name)
        VoiceDialog(
            prayer = p,
            current = prefs[p]?.voice ?: AdhanVoices.DEFAULT,
            vm = vm,
            onDismiss = { vm.stopPreview(); vm.clearDialogMessage(); voiceDialogFor = null },
        )
    }
}

private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }.onFailure { context.openAppSettings() }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        val i = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure { context.openAppSettings() }
    } else {
        context.openAppSettings()
    }
}

@Composable
private fun WarningCard(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    YaqeenCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = YaqeenTheme.extra.warning)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value); onDismiss() }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun VoiceDialog(
    prayer: Prayer,
    current: String,
    vm: AdhanSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val voices by vm.voices.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.dialogMessage.collectAsStateWithLifecycle()
    val previewKey by vm.previewKey.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(current) }
    var applyAll by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VoiceFile?>(null) }

    // اختيار ملف عبر Storage Access Framework؛ يُنسخ فورًا إلى تخزين التطبيق ثم يُحدَّد تلقائيًا.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri) { selected = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صوت أذان ${prayer.ar}") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                VoiceOption("الافتراضي (نغمة إشعارات الجهاز)", selected == AdhanVoices.DEFAULT, onSelect = { selected = AdhanVoices.DEFAULT }, onPreview = { vm.togglePreview("dlg", AdhanVoices.DEFAULT) }, previewing = previewKey == "dlg" && selected == AdhanVoices.DEFAULT, onDelete = null)
                voices.forEach { f ->
                    VoiceOption(
                        f.title, selected == f.value,
                        onSelect = { selected = f.value },
                        onPreview = { vm.togglePreview("dlg:${f.name}", f.value) },
                        previewing = previewKey == "dlg:${f.name}",
                        onDelete = { deleting = f },
                    )
                }
                if (voices.isEmpty()) {
                    Text("لا توجد ملفات مستوردة بعد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("استيراد ملف صوتي من الجهاز")
                }
                OutlinedButton(onClick = { showUrl = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Link, null); Spacer(Modifier.width(8.dp)); Text("إضافة من رابط مباشر")
                }
                message?.let { (text, ok) ->
                    Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) YaqeenTheme.extra.success else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "استورد فقط ملفات تملك حق استخدامها. تُنسخ الملفات إلى تخزين التطبيق مرة واحدة ثم تعمل دون إنترنت (الحد الأقصى ٢٥ ميغابايت و١٠ دقائق).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().clickable { applyAll = !applyAll }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text("تطبيق على جميع الصلوات", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.setVoice(prayer, selected, applyAll); onDismiss() }, enabled = !busy) { Text("اعتماد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (showUrl) {
        UrlDialog(
            onDismiss = { showUrl = false },
            onConfirm = { url ->
                showUrl = false
                vm.importUrl(url) { selected = it }
            },
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف الملف") },
            text = { Text("سيُحذف «${f.title}» من جهازك، وتعود أي صلاة تستخدمه إلى الصوت الافتراضي.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVoice(f)
                    if (selected == f.value) selected = AdhanVoices.DEFAULT
                    deleting = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun VoiceOption(title: String, selected: Boolean, onSelect: () -> Unit, onPreview: () -> Unit, previewing: Boolean, onDelete: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onPreview) { if (previewing) Icon(Icons.Filled.Stop, "إيقاف") else Icon(Icons.Filled.PlayArrow, "تجربة") }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف الملف", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var rights by remember { mutableStateOf(false) }
    val error = if (url.isBlank()) null else AdhanVoices.validateUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صوت من رابط") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "أدخل رابطًا مباشرًا (https) لملف صوتي. يُنزَّل مرة واحدة ثم يعمل دون إنترنت. روابط يوتيوب وما شابهها لا تُدعم.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("رابط الملف الصوتي") }, isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(Modifier.fillMaxWidth().clickable { rights = !rights }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rights, onCheckedChange = { rights = it })
                    Text("أؤكد أنني أملك حق استخدام هذا الملف الصوتي.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url.trim()) }, enabled = rights && url.isNotBlank() && error == null) { Text("تنزيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
