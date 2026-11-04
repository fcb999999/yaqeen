package app.yaqeen.feature.quran.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.ui.LocalAppContainer
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.NavRow
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.theme.YaqeenTextStyles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =============================================================== نافذة إجراءات الآية

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahActionsSheet(
    quran: QuranData,
    ayah: Ayah,
    onBookmark: () -> Unit,
    onPlayAyah: () -> Unit,
    onPlayFromHere: () -> Unit,
    onTafsir: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppContainer.current
    val bookmarked by produceState<Boolean?>(null, ayah.id) {
        value = c.db.bookmarks().findAt(ayah.surah, ayah.number) != null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "سورة ${QuranText.plainSurahName(quran.surah(ayah.surah).nameAr)} – الآية ${ArabicText.indic(ayah.number)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (ayah.sajda) Pill("موضع سجدة")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                QuranText.forHafsFont(ayah.text),
                style = YaqeenTextStyles.quran(22f, 1.8f).copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(
                title = if (bookmarked == true) "تعديل العلامة المرجعية" else "إضافة علامة مرجعية",
                onClick = onBookmark,
                leading = if (bookmarked == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            )
            NavRow("تشغيل الآية", onPlayAyah, leading = Icons.Filled.PlayArrow)
            NavRow("تشغيل السورة من هنا", onPlayFromHere, leading = Icons.Filled.PlayCircle)
            NavRow("التفسير", onTafsir, leading = Icons.Filled.AutoStories)
            NavRow("نسخ الآية", onCopy, subtitle = "مع اسم السورة ورقم الآية", leading = Icons.Filled.ContentCopy)
            NavRow("مشاركة كنص", onShareText, leading = Icons.Filled.Share)
            NavRow("مشاركة كصورة", onShareImage, subtitle = "بطاقة بهوية يقين", leading = Icons.Filled.Image)
        }
    }
}

// =============================================================== إعدادات القراءة

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(c: AppContainer, prefs: ReaderPrefs, bismillah: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("إعدادات القراءة", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            ReaderSettingsContent(c, prefs, bismillah)
        }
    }
}

/** عناصر التحكم في القراءة (تُستعمل في النافذة السفلية وفي شاشة الإعدادات). تُحفظ كل تغييرات في c.settings. */
@Composable
fun ReaderSettingsContent(c: AppContainer, prefs: ReaderPrefs, bismillah: String, modifier: Modifier = Modifier) {
    val appDark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    val noor = app.yaqeen.ui.theme.YaqeenTheme.isNoor
    val palette = ReaderPalettes.resolve(prefs.background, appDark, noor)
    var font by remember(prefs.fontSize) { mutableFloatStateOf(prefs.fontSize) }
    var line by remember(prefs.lineSpacing) { mutableFloatStateOf(prefs.lineSpacing) }
    var margin by remember(prefs.pageMargin) { mutableFloatStateOf(prefs.pageMargin.toFloat()) }
    var speed by remember(prefs.scrollSpeed) { mutableIntStateOf(prefs.scrollSpeed) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = c.appScope

    Column(modifier) {
        // معاينة حيّة بخلفية القراءة الحالية
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.bg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = margin.dp.coerceAtMost(24.dp), vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                QuranText.forHafsFont(bismillah),
                style = YaqeenTextStyles.quran(font, line).copy(color = palette.text, textAlign = TextAlign.Center),
            )
        }
        Spacer(Modifier.height(12.dp))

        SliderRow(
            label = "حجم الخط",
            valueText = ArabicText.indic(font.roundToInt()),
            value = font,
            range = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
            onChange = { font = it },
            onFinish = { scope.launch { c.settings.set(Settings.QURAN_FONT_SIZE, font.roundToInt().toFloat()) } },
        )
        SliderRow(
            label = "المسافة بين الأسطر",
            valueText = ArabicText.indic(String.format(java.util.Locale.ENGLISH, "%.1f", line)),
            value = line,
            range = ReaderPrefs.MIN_LINE..ReaderPrefs.MAX_LINE,
            onChange = { line = it },
            onFinish = { scope.launch { c.settings.set(Settings.QURAN_LINE_SPACING, (line * 10).roundToInt() / 10f) } },
        )
        SliderRow(
            label = "هوامش الصفحة",
            valueText = ArabicText.indic(margin.roundToInt()),
            value = margin,
            range = ReaderPrefs.MIN_MARGIN.toFloat()..ReaderPrefs.MAX_MARGIN.toFloat(),
            onChange = { margin = it },
            onFinish = { scope.launch { c.settings.set(QuranKeys.PAGE_MARGIN, margin.roundToInt()) } },
        )

        Spacer(Modifier.height(8.dp))
        Text("خلفية القراءة", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val selectedKey = if (ReaderPalettes.all.any { it.key == prefs.background }) prefs.background else "ivory"
            for (p in ReaderPalettes.all) {
                val shown = ReaderPalettes.resolve(p.key, appDark, noor)
                val selected = selectedKey == p.key
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { scope.launch { c.settings.set(Settings.QURAN_BACKGROUND, p.key) } }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(shown.bg)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("أ", color = shown.text, style = YaqeenTextStyles.quran(20f).copy(textAlign = TextAlign.Center))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ReaderPalettes.label(p.key),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("مظهر التطبيق", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((mode, label) in listOf("system" to "تلقائي", "light" to "فاتح", "dark" to "داكن")) {
                FilterChip(
                    selected = prefs.themeMode == mode,
                    onClick = { scope.launch { c.settings.set(Settings.THEME_MODE, mode) } },
                    label = { Text(label) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchRow(
            title = "إبقاء الشاشة مضاءة أثناء القراءة",
            checked = prefs.keepScreenOn,
            onCheckedChange = { scope.launch { c.settings.set(Settings.QURAN_KEEP_SCREEN_ON, it) } },
        )
        SwitchRow(
            title = "ملء الصفحة على الشاشات الكبيرة",
            subtitle = "يكبّر الخط تلقائيًا في الأجهزة اللوحية حتى لا يبقى فراغ أسفل الآيات",
            checked = prefs.fitPage,
            onCheckedChange = { scope.launch { c.settings.set(QuranKeys.FIT_PAGE, it) } },
        )
        SwitchRow(
            title = "فتح المصحف بوضع ملء الشاشة",
            subtitle = "تُخفى الأشرطة، ويُظهرها لمس هامش الصفحة أو أيقونة الأعلى",
            checked = prefs.startFullscreen,
            onCheckedChange = { scope.launch { c.settings.set(Settings.QURAN_FULLSCREEN, it) } },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(8.dp))
        SliderRow(
            label = "سرعة التمرير التلقائي",
            valueText = "${ArabicText.indic(speed)} – ${AutoScroll.levelName(speed)}",
            value = speed.toFloat(),
            range = AutoScroll.MIN_SPEED.toFloat()..AutoScroll.MAX_SPEED.toFloat(),
            steps = AutoScroll.MAX_SPEED - AutoScroll.MIN_SPEED - 1,
            onChange = { speed = it.roundToInt() },
            onFinish = { scope.launch { c.settings.set(Settings.QURAN_SCROLL_SPEED, speed) } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in AutoScroll.presets) {
                FilterChip(
                    selected = speed == p.speed,
                    onClick = {
                        speed = p.speed
                        scope.launch { c.settings.set(Settings.QURAN_SCROLL_SPEED, p.speed) }
                    },
                    label = { Text(p.label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { confirmReset = true }) { Text("استعادة الإعدادات الافتراضية") }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "استعادة الافتراضي",
            message = "سيُعاد حجم الخط والمسافات والخلفية وبقية خيارات القراءة إلى قيمها الأصلية.",
            confirmText = "استعادة",
            onConfirm = {
                scope.launch {
                    c.settings.reset(Settings.QURAN_FONT_SIZE)
                    c.settings.reset(Settings.QURAN_LINE_SPACING)
                    c.settings.reset(Settings.QURAN_BACKGROUND)
                    c.settings.reset(Settings.QURAN_KEEP_SCREEN_ON)
                    c.settings.reset(Settings.QURAN_FULLSCREEN)
                    c.settings.reset(Settings.QURAN_SCROLL_SPEED)
                    c.settings.reset(QuranKeys.PAGE_MARGIN)
                    c.settings.reset(QuranKeys.FIT_PAGE)
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinish, valueRange = range, steps = steps)
    }
}

// =============================================================== القفز إلى موضع

sealed interface JumpTarget {
    data class AyahTarget(val surah: Int, val ayah: Int) : JumpTarget
    data class PageTarget(val page: Int) : JumpTarget
    data class JuzTarget(val juz: Int) : JumpTarget
}

/** انتقال إلى سورة وآية، أو صفحة، أو جزء. */
@Composable
fun JumpDialog(quran: QuranData, initialSurah: Int, onDismiss: () -> Unit, onJump: (JumpTarget) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var surah by rememberSaveable { mutableIntStateOf(initialSurah.coerceIn(1, quran.surahs.size)) }
    var ayahText by rememberSaveable { mutableStateOf("") }
    var pageText by rememberSaveable { mutableStateOf("") }
    var juzText by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    val s = quran.surah(surah)
    val ayahNum = JumpParser.ayahNumber(ayahText, s.ayahCount)
    val pageNum = JumpParser.page(pageText, quran.pageCount)
    val juzNum = JumpParser.juz(juzText)
    val canJump = when (mode) {
        0 -> ayahText.isBlank() || ayahNum != null
        1 -> pageNum != null
        else -> juzNum != null
    }
    fun submit() {
        if (!canJump) return
        when (mode) {
            0 -> onJump(JumpTarget.AyahTarget(surah, ayahNum ?: 1))
            1 -> onJump(JumpTarget.PageTarget(pageNum!!))
            else -> onJump(JumpTarget.JuzTarget(juzNum!!))
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الانتقال إلى") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("سورة وآية", "صفحة", "جزء").forEachIndexed { i, label ->
                        FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (mode) {
                    0 -> {
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${ArabicText.indic(s.number)}. ${QuranText.plainSurahName(s.nameAr)}",
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                for (item in quran.surahs) {
                                    DropdownMenuItem(
                                        text = { Text("${ArabicText.indic(item.number)}. ${QuranText.plainSurahName(item.nameAr)}") },
                                        trailingIcon = if (item.number == surah) ({ Icon(Icons.Filled.Check, null) }) else null,
                                        onClick = {
                                            surah = item.number
                                            ayahText = ""
                                            menu = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ayahText,
                            onValueChange = { ayahText = it.take(5) },
                            label = { Text("رقم الآية (اختياري)") },
                            isError = ayahText.isNotBlank() && ayahNum == null,
                            supportingText = {
                                Text(
                                    if (ayahText.isNotBlank() && ayahNum == null) "أدخل رقمًا من ١ إلى ${ArabicText.indic(s.ayahCount)}"
                                    else "عدد آيات السورة ${ArabicText.indic(s.ayahCount)}",
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    1 -> OutlinedTextField(
                        value = pageText,
                        onValueChange = { pageText = it.take(5) },
                        label = { Text("رقم الصفحة") },
                        isError = pageText.isNotBlank() && pageNum == null,
                        supportingText = { Text("من ١ إلى ${ArabicText.indic(quran.pageCount)}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> OutlinedTextField(
                        value = juzText,
                        onValueChange = { juzText = it.take(3) },
                        label = { Text("رقم الجزء") },
                        isError = juzText.isNotBlank() && juzNum == null,
                        supportingText = { Text("من ١ إلى ٣٠") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = canJump) { Text("انتقال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
