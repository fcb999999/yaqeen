package app.yaqeen.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.data.settings.BoolKey
import app.yaqeen.data.settings.IntKey
import app.yaqeen.data.settings.Settings
import app.yaqeen.data.settings.StringKey
import app.yaqeen.domain.model.Prayer
import app.yaqeen.feature.settings.updates.UpdateScheduler
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard

@Composable
internal fun NotificationsSettingsScreen(nav: NavHostController, c: AppContainer) {
    SettingsPage("الإشعارات", onBack = { nav.popBackStack() }) {
        val context = LocalContext.current
        val notify = rememberNotifier()
        var tick by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

        SystemPermissionCards(c, tick)

        SettingsGroup("الورد القرآني") {
            SwitchSetting(c, "تذكير الورد", Settings.NOTIF_WIRD, "يذكّرك في الوقت الذي حدّدته لكل ورد من صفحة الأوراد")
        }

        SettingsGroup("الأذكار") {
            SwitchSetting(c, "أذكار الصباح", Settings.NOTIF_MORNING)
            TimeSetting(c, "وقت أذكار الصباح", Settings.NOTIF_MORNING, Settings.NOTIF_MORNING_TIME, TimeText.HM(5, 30))
            RowDivider()
            SwitchSetting(c, "أذكار المساء", Settings.NOTIF_EVENING)
            TimeSetting(c, "وقت أذكار المساء", Settings.NOTIF_EVENING, Settings.NOTIF_EVENING_TIME, TimeText.HM(17, 0))
            RowDivider()
            SwitchSetting(c, "أذكار النوم", Settings.NOTIF_SLEEP)
            TimeSetting(c, "وقت أذكار النوم", Settings.NOTIF_SLEEP, Settings.NOTIF_SLEEP_TIME, TimeText.HM(22, 0))
        }

        Prayer.entries.forEach { p -> PrayerGroup(c, p) }
        Hint("تُجدول تنبيهات الصلاة حسب موقعك وطريقة الحساب المختارة في «مواقيت الصلاة». يمكنك اختيار صوت الأذان من «المؤذن».")

        SettingsGroup("المهام والمناسبات") {
            SwitchSetting(c, "تذكير المهام", Settings.NOTIF_TASKS, "تنبيه في وقت المهمة التي فعّلت تذكيرها")
            RowDivider()
            SwitchSetting(c, "ملخص نهاية اليوم", Settings.NOTIF_DAY_END, "تنبيه يلخّص إنجازك ويذكّرك بما لم يُسجَّل")
            TimeSetting(c, "وقت ملخص نهاية اليوم", Settings.NOTIF_DAY_END, Settings.NOTIF_DAY_END_TIME, TimeText.HM(23, 0))
            RowDivider()
            SwitchSetting(c, "المناسبات الإسلامية وأحداث التقويم", Settings.NOTIF_EVENTS, "تنبيه بالمناسبات القادمة وأحداثك الشخصية")
        }

        SettingsGroup("التحديثات") {
            val enabled by c.rememberSetting(Settings.NOTIF_UPDATES)
            SwitchRow(
                title = "إشعارات تحديثات التطبيق",
                checked = enabled,
                subtitle = "تنبيه عند صدور إصدار جديد من يقين",
                onCheckedChange = {
                    c.putSetting(Settings.NOTIF_UPDATES, it)
                    UpdateScheduler.apply(context.applicationContext, it)
                },
            )
        }

        SettingsGroup("إعدادات النظام") {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (!openAppNotificationSettings(context)) notify("تعذّر فتح إعدادات النظام.")
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("قنوات الإشعارات في النظام", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "تحكّم بصوت وأهمية كل قناة (الأذان، الورد، الأذكار…) من إعدادات أندرويد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------- الأذونات

private fun openAppNotificationSettings(context: android.content.Context): Boolean {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

@Composable
private fun SystemPermissionCards(c: AppContainer, tick: Int) {
    val context = LocalContext.current
    val notify = rememberNotifier()

    // ---- إذن الإشعارات (أندرويد 13+) وحالة تفعيلها عمومًا
    val enabled = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var denied by remember { mutableStateOf(false) }
    var granted by remember(tick) { mutableStateOf(enabled) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) denied = true else c.requestReschedule()
    }
    if (!granted) {
        YaqeenCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text("الإشعارات متوقفة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "لن تصلك تنبيهات الأذان والأذكار والورد حتى تسمح للتطبيق بإرسال الإشعارات.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(10.dp))
            val canAsk = Build.VERSION.SDK_INT >= 33 && !denied &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (canAsk) {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("السماح بالإشعارات") }
            } else {
                Button(
                    onClick = { if (!openAppNotificationSettings(context)) notify("تعذّر فتح إعدادات النظام.") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("فتح إعدادات الإشعارات") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // ---- إذن المنبهات الدقيقة
    val exact = remember(tick) { c.reminders.canScheduleExact() }
    // عند منح الإذن (بعد العودة من إعدادات النظام) تُعاد الجدولة لتصبح دقيقة.
    LaunchedEffect(exact) { if (exact) c.requestReschedule() }

    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("دقة التنبيهات", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (exact) Pill("دقيقة") else Pill("تقريبية", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (exact) "المنبهات الدقيقة مفعّلة: يصلك تنبيه الأذان والتذكيرات في الدقيقة المحددة."
            else "المنبهات الدقيقة غير مفعّلة: قد يتأخر وصول التنبيه عدة دقائق، وهذا لا يناسب الأذان. فعّلها من إعدادات النظام.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(intent) }.isFailure) notify("تعذّر فتح إعدادات المنبهات.")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("السماح بالمنبهات الدقيقة") }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- عناصر الإعدادات

/** مفتاح تبديل مرتبط بإعداد منطقي؛ كل تغيير يحفظ ثم يعيد جدولة التذكيرات. */
@Composable
private fun SwitchSetting(c: AppContainer, title: String, key: BoolKey, subtitle: String? = null) {
    val value by c.rememberSetting(key)
    SwitchRow(title = title, checked = value, subtitle = subtitle, onCheckedChange = { c.putSetting(key, it, reschedule = true) })
}

/** صف وقت يظهر عند تفعيل النوع فقط ويفتح منتقي الوقت. */
@Composable
private fun TimeSetting(c: AppContainer, title: String, enabledKey: BoolKey, timeKey: StringKey, fallback: TimeText.HM) {
    val enabled by c.rememberSetting(enabledKey)
    val time by c.rememberSetting(timeKey)
    var show by remember { mutableStateOf(false) }
    if (!enabled) return
    TimeRow(title, time, fallback) { show = true }
    if (show) {
        TimePickerDialog(
            initial = TimeText.parse(time, fallback),
            onDismiss = { show = false },
            onConfirm = { h, m -> c.putSetting(timeKey, TimeText.format24(h, m), reschedule = true) },
        )
    }
}

/** اختيار عدد دقائق من قائمة منسدلة (0 = معطّل). */
@Composable
private fun MinutesSetting(c: AppContainer, title: String, key: IntKey, options: List<Int>) {
    val value by c.rememberSetting(key)
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            Text(ArabicUnits.minutes(value), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (options + value).distinct().sorted().forEach { m ->
                    DropdownMenuItem(
                        text = { Text(ArabicUnits.minutes(m)) },
                        onClick = {
                            open = false
                            c.putSetting(key, m, reschedule = true)
                        },
                    )
                }
            }
        }
    }
}

private val PRE_MINUTES = listOf(0, 5, 10, 15, 20, 30)
private val IQAMA_MINUTES = listOf(0, 5, 10, 15, 20, 25, 30, 45)

@Composable
private fun PrayerGroup(c: AppContainer, p: Prayer) {
    SettingsGroup("صلاة ${p.ar}") {
        val adhan = remember(p) { Settings.notifAdhan(p.name) }
        val pre = remember(p) { Settings.notifPreMinutes(p.name) }
        val iqama = remember(p) { Settings.notifIqamaMinutes(p.name) }
        SwitchSetting(c, "تنبيه دخول الوقت (الأذان)", adhan)
        RowDivider()
        MinutesSetting(c, "تنبيه قبل الصلاة", pre, PRE_MINUTES)
        RowDivider()
        MinutesSetting(c, "تنبيه الإقامة بعد الأذان", iqama, IQAMA_MINUTES)
    }
}
