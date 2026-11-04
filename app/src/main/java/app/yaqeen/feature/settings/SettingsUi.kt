package app.yaqeen.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.data.settings.SettingKey
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** مسارات شاشات الإعدادات الفرعية (إضافة إلى ما في Routes). */
object SettingsRoutes {
    const val PRIVACY = "settings/privacy"
    const val ABOUT = "settings/about"
    const val DATA = "settings/data"
    const val STORAGE = "settings/storage"

    /** شاشات تملكها ميزات أخرى (قد لا تكون مسجّلة في بعض البناءات؛ نتحقق عند الفتح). */
    const val QURAN_DOWNLOADS = "quran/downloads"
    const val ADHAN_SETTINGS = "prayer/adhan/settings"
}

internal val LocalSettingsSnack = staticCompositionLocalOf<SnackbarHostState> { error("SettingsPage مفقود") }

/** هيكل موحّد لشاشات الإعدادات: شريط علوي + محتوى قابل للتمرير + شريط رسائل. */
@Composable
internal fun SettingsPage(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val snack = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSettingsSnack provides snack) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                YaqeenTopBar(title, onBack)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    content()
                    Spacer(Modifier.height(32.dp))
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
internal fun rememberNotifier(): (String) -> Unit {
    val snack = LocalSettingsSnack.current
    val scope = rememberCoroutineScope()
    return remember(snack, scope) {
        { msg: String ->
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                snack.showSnackbar(msg)
            }
            Unit
        }
    }
}

/** مجموعة إعدادات داخل بطاقة بعنوان. */
@Composable
internal fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionTitle(title, Modifier.padding(top = 8.dp))
    YaqeenCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), content = content)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** فقرة شرح صغيرة. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** عنوان فرعي داخل البطاقة + اختيار من شرائح. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

/** منزلق يحفظ القيمة عند رفع الإصبع فقط (لا كتابة لكل خطوة سحب). */
@Composable
internal fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel(v), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = v, onValueChange = { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onCommit(v) })
    }
}

/** صف وقت: عنوان + الوقت الحالي، والنقر يفتح منتقي الوقت. */
@Composable
internal fun TimeRow(title: String, time: String, fallback: TimeText.HM, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
        Text(
            TimeText.display(time, fallback),
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

/** منتقي الوقت (Material3 TimePicker) داخل حوار. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(initial: TimeText.HM, onDismiss: () -> Unit, onConfirm: (hour: Int, minute: Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute); onDismiss() }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

// ---------------------------------------------------------------- إعدادات: قراءة وكتابة

/** يلاحظ إعدادًا مع قيمة ابتدائية افتراضية. */
@Composable
internal fun <T> AppContainer.rememberSetting(key: SettingKey<T>): State<T> {
    val flow = remember(key.id) { settings.observe(key) }
    return flow.collectAsState(initial = key.default)
}

/** يكتب إعدادًا في نطاق التطبيق (لا يُلغى بمغادرة الشاشة) ويعيد جدولة التذكيرات عند الحاجة. */
internal fun <T> AppContainer.putSetting(key: SettingKey<T>, value: T, reschedule: Boolean = false) {
    appScope.launch {
        settings.set(key, value)
        if (reschedule) requestReschedule()
    }
}

// ---------------------------------------------------------------- مساعدات النظام

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** يفتح مسارًا قد لا يكون مسجّلًا؛ يعيد false بدل رمي استثناء. */
internal fun NavHostController.tryNavigate(route: String): Boolean = try {
    navigate(route)
    true
} catch (e: IllegalArgumentException) {
    false
}

// ---------------------------------------------------------------- الصورة الشخصية

private object AvatarCache {
    private val map = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 8
    }

    @Synchronized fun get(url: String): Bitmap? = map[url]
    @Synchronized fun put(url: String, bmp: Bitmap) { map[url] = bmp }
}

private suspend fun loadAvatar(url: String): ImageBitmap? {
    if (!url.startsWith("https://")) return null
    AvatarCache.get(url)?.let { return it.asImageBitmap() }
    return withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                if (conn.responseCode != 200 || conn.contentLength > 2_000_000) return@runCatching null
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { AvatarCache.put(url, it) }?.asImageBitmap()
    }
}

/** صورة حساب Google (تُحمَّل من الرابط عند توفر الاتصال) أو حرف أول من الاسم عند تعذرها. */
@Composable
internal fun ProfileAvatar(photoUrl: String?, label: String?, size: Dp = 56.dp) {
    val image by produceState<ImageBitmap?>(null, photoUrl) { value = photoUrl?.let { loadAvatar(it) } }
    val shape = CircleShape
    Box(
        Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = "الصورة الشخصية", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                label?.trim()?.firstOrNull()?.toString() ?: "؟",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun HSpace(w: Dp) = Spacer(Modifier.width(w))
