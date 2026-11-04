package app.yaqeen.feature.adhkar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.yaqeen.core.ArabicText
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.ProgressRing
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.theme.YaqeenTextStyles
import app.yaqeen.ui.theme.YaqeenTheme

/** إجراءات بطاقة الذكر. */
class AdhkarCardActions(
    val onTap: () -> Unit,
    val onFavorite: () -> Unit,
    val onBookmark: () -> Unit,
    val onShareText: () -> Unit,
    val onShareImage: () -> Unit,
    val onCopy: () -> Unit,
    val onEditTarget: () -> Unit,
    val onReset: () -> Unit,
)

/**
 * بطاقة ذكر واحد: النص، التكرار، الفضل، الملاحظة، المصدر، والعدّاد التفاعلي.
 * النقر على أي مكان في البطاقة يُنقص المتبقي بواحد.
 */
@Composable
fun AdhkarCard(
    ui: ItemUi,
    total: Int,
    prefs: AdhkarPrefs,
    actions: AdhkarCardActions,
    modifier: Modifier = Modifier,
) {
    val item = ui.item
    val progress = ui.progress
    val isQuran = item.quranRef.isNotBlank()
    val textStyle = if (isQuran) {
        YaqeenTextStyles.quranAmiri(prefs.textSize).copy(textAlign = TextAlign.Center)
    } else {
        YaqeenTextStyles.adhkar(prefs.textSize)
    }

    YaqeenCard(
        modifier = modifier.fillMaxSize(),
        onClick = if (progress.completed) null else actions.onTap,
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
    ) {
        CardHeader(ui, total, actions)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (item.group.isNotBlank()) {
                Text(item.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            Text(item.text, style = textStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            RepeatInfo(progress.target, ui.customTarget)
            if (prefs.showFadl && item.fadl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                InfoBlock("الفضل", item.fadl, YaqeenTheme.extra.goldContainer, YaqeenTheme.extra.gold)
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                InfoBlock("ملاحظة", item.note, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (item.source.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("المصدر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        CounterSection(progress, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun CardHeader(ui: ItemUi, total: Int, actions: AdhkarCardActions) {
    var menu by remember { mutableStateOf(false) }
    val gold = YaqeenTheme.extra.gold
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill("${ArabicText.indic(ui.index + 1)} / ${ArabicText.indic(total)}")
        Spacer(Modifier.weight(1f))
        IconButton(onClick = actions.onFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                if (ui.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (ui.favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                tint = if (ui.favorite) gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = actions.onBookmark, modifier = Modifier.size(40.dp)) {
            Icon(
                if (ui.bookmark) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (ui.bookmark) "إزالة العلامة المرجعية" else "إضافة علامة مرجعية",
                tint = if (ui.bookmark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = actions.onShareText, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "المزيد", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("نسخ النص") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; actions.onCopy() },
                )
                DropdownMenuItem(
                    text = { Text("مشاركة كصورة") },
                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                    onClick = { menu = false; actions.onShareImage() },
                )
                DropdownMenuItem(
                    text = { Text("تعديل عدد التكرار") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; actions.onEditTarget() },
                )
                DropdownMenuItem(
                    text = { Text("إعادة العدّاد") },
                    leadingIcon = { Icon(Icons.Filled.Restore, null) },
                    enabled = ui.progress.count > 0,
                    onClick = { menu = false; actions.onReset() },
                )
            }
        }
    }
}

@Composable
private fun RepeatInfo(target: Int, custom: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            "التكرار: " + AdhkarLabels.times(target, ArabicText::indic) + if (custom) " (معدّل)" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoBlock(label: String, body: String, container: androidx.compose.ui.graphics.Color, accent: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** العدّاد الدائري: يعرض المتبقي، وعند الاكتمال علامة صح. */
@Composable
private fun CounterSection(progress: ItemProgress, modifier: Modifier = Modifier) {
    val ratio by animateFloatAsState(progress.ratio, label = "adhkarRing")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            ProgressRing(ratio, size = 84.dp, stroke = 7.dp)
            if (progress.completed) {
                Icon(Icons.Filled.CheckCircle, "مكتمل", Modifier.size(36.dp), tint = YaqeenTheme.extra.success)
            } else {
                Text(ArabicText.indic(progress.remaining), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            progress.completed -> Text("تمّ هذا الذكر", style = MaterialTheme.typography.labelLarge, color = YaqeenTheme.extra.success)
            progress.count == 0 -> Text(
                "اضغط على البطاقة للعدّ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "المتبقي ${ArabicText.indic(progress.remaining)} من ${ArabicText.indic(progress.target)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** نافذة تعديل عدد التكرار (تقبل الأرقام العربية والهندية). */
@Composable
fun TargetDialog(
    current: Int,
    catalogCount: Int,
    custom: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onResetDefault: () -> Unit,
) {
    var text by remember { mutableStateOf(ArabicText.indic(current)) }
    val parsed = AdhkarCounter.parseTarget(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عدد التكرار") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(4) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            if (parsed == null) "أدخل رقمًا من ${ArabicText.indic(AdhkarCounter.MIN_TARGET)} إلى ${ArabicText.indic(AdhkarCounter.MAX_TARGET)}"
                            else "العدد الأصلي: " + AdhkarLabels.times(catalogCount, ArabicText::indic),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (custom) {
                    TextButton(onClick = { onResetDefault(); onDismiss() }) { Text("استعادة العدد الأصلي") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave); onDismiss() }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
