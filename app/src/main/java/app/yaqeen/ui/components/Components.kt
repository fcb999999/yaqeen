package app.yaqeen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.Hue
import app.yaqeen.ui.theme.YaqeenTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope

/** بطاقة موحّدة لكل صفحات التطبيق. */
@Composable
fun YaqeenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val shape = MaterialTheme.shapes.medium
    val x = YaqeenTheme.extras
    // حياة (فاتح): بلا إطار وبظل بنفسجي ناعم؛ حياة (داكن) وclassic: إطار رفيع كما كان.
    val softShadow = x.isHayat && !x.dark
    val border = if (softShadow) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val modifier = if (softShadow) {
        modifier.shadow(6.dp, shape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    } else modifier
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val hayat = YaqeenTheme.isHayat
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hayat) {
            Box(Modifier.size(width = 5.dp, height = 20.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        }
        action?.invoke()
    }
}

/** شريط علوي موحّد: عنوان في الوسط وزر رجوع اختياري. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YaqeenTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val x = YaqeenTheme.extras
    val hayat = x.isHayat
    val bg = MaterialTheme.colorScheme.background
    // حياة: الترويسة شفافة (التدرّج البنفسجي مرسوم في خلفية الشاشة كلها من AppRoot فيشمل شريط الحالة)،
    // وزر الرجوع داخل دائرة.
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = if (hayat) Modifier.padding(horizontal = 4.dp).background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = if (hayat) Color.Transparent else bg,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** حلقة تقدم دائرية بنص في الوسط. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    label: String? = null,
    /** داخل بطاقة بطل متدرّجة في حياة: ألوان بيضاء/داكنة حسب البطاقة. */
    onHero: Boolean = false,
) {
    val hero = LocalHero.current
    val useHero = onHero && YaqeenTheme.isHayat && hero.content != Color.Unspecified
    val track = if (useHero) hero.content.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
    val color = if (useHero) hero.content else MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = if (useHero) hero.content else Color.Unspecified)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val hayat = YaqeenTheme.isHayat
    val hero = LocalHero.current
    val useHero = onHero && hayat && hero.content != Color.Unspecified
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(if (hayat) 10.dp else 8.dp),
        trackColor = if (useHero) hero.content.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        color = if (useHero) hero.content else MaterialTheme.colorScheme.primary,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
fun EmptyState(title: String, message: String? = null, icon: ImageVector? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** صف إعداد بمفتاح تبديل. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** صف تنقل/إعداد قابل للنقر. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** لون شارة الأيقونة في «حياة» فقط. */
    hue: Hue? = null,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            if (YaqeenTheme.isHayat) {
                IconChip(leading, hue ?: Hue.VIOLET, size = 40.dp)
                Spacer(Modifier.width(14.dp))
            } else {
                Icon(leading, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد",
    dismissText: String = "إلغاء",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

/** شريحة صغيرة (حالة/تصنيف). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer, content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Box(
        modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = content) }
}

// ---------------------------------------------------------------- مكونات «حياة» (في classic تعود لشكلها القديم)

/** ألوان بطاقة البطل الحالية: لون المحتوى ولون التمييز (للأزرار). */
@Immutable
class HeroColors(val content: Color, val accent: Color)

val LocalHero = staticCompositionLocalOf { HeroColors(Color.Unspecified, Color.Unspecified) }

/**
 * بطاقة بطل للرأس/الصلاة القادمة/تابع القراءة…
 * classic: بطاقة بلون primaryContainer كما كانت. حياة: تدرّج لون المجال مع زخرفة دائرية خفيفة.
 * ألوان النص داخلها من [LocalHero].content.
 */
@Composable
fun HeroCard(
    area: Area,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val x = YaqeenTheme.extras
    val p = x.area(area)
    if (!x.isHayat) {
        CompositionLocalProvider(LocalHero provides HeroColors(p.onContainer, p.accent)) {
            YaqeenCard(modifier, onClick = onClick, containerColor = p.container, contentPadding = contentPadding ?: PaddingValues(16.dp), content = content)
        }
        return
    }
    val shape = RoundedCornerShape(32.dp)
    val base = modifier
        .shadow(10.dp, shape, ambientColor = p.accent.copy(alpha = 0.25f), spotColor = p.accent.copy(alpha = 0.40f))
        .clip(shape)
        .background(Brush.linearGradient(listOf(p.accent, p.gradientEnd)))
        .drawBehind {
            // زخرفة هندسية مظلِّلة خفيفة (لا تُضعف التباين).
            drawCircle(Color.Black.copy(alpha = 0.07f), radius = size.minDimension * 0.62f, center = Offset(size.width * 0.04f, -size.height * 0.10f))
            drawCircle(Color.Black.copy(alpha = 0.06f), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.96f, size.height * 1.02f))
        }
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    CompositionLocalProvider(
        LocalHero provides HeroColors(p.onAccent, p.accent),
        LocalContentColor provides p.onAccent,
    ) {
        Column(clickable.padding(contentPadding ?: PaddingValues(20.dp)), content = content)
    }
}

/** زر داخل بطاقة بطل: classic زر عادي، حياة زر بلون محتوى البطاقة. */
@Composable
fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = LocalHero.current
    if (YaqeenTheme.isHayat && hero.content != Color.Unspecified) {
        Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = hero.content, contentColor = hero.accent)) { Text(text) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

/** شارة أيقونة ملوّنة (مربع مدوّر بلون ناعم للمجال). تُستعمل في «حياة» فقط. */
@Composable
fun IconChip(icon: ImageVector, hue: Hue, modifier: Modifier = Modifier, size: Dp = 44.dp, contentDescription: String? = null) {
    val p = YaqeenTheme.extras.hue(hue)
    Box(modifier.size(size).background(p.container, RoundedCornerShape(size * 0.36f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, Modifier.size(size * 0.52f), tint = p.onContainer)
    }
}

/**
 * بلاطة إجراء: classic بطاقة بأيقونة بلون primary كما كانت؛ حياة بلاطة متدرّجة بلون المجال.
 * [caption] سطر صغير اختياري (عدّاد/وصف).
 */
@Composable
fun YaqeenTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hue: Hue = Hue.VIOLET,
    caption: String? = null,
    iconSize: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    labelStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val x = YaqeenTheme.extras
    if (!x.isHayat) {
        YaqeenCard(modifier, onClick = onClick, contentPadding = contentPadding) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
                Spacer(Modifier.height(6.dp))
                Text(label, style = labelStyle, textAlign = TextAlign.Center)
                if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val p = x.hue(hue)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .shadow(8.dp, shape, ambientColor = p.accent.copy(alpha = 0.20f), spotColor = p.accent.copy(alpha = 0.35f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.accent, p.gradientEnd)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).background(p.onAccent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = labelStyle, fontWeight = FontWeight.Bold, color = p.onAccent, textAlign = TextAlign.Center)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = p.onAccent, textAlign = TextAlign.Center)
    }
}
