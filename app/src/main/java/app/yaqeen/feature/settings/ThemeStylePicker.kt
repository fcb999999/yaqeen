package app.yaqeen.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.ThemeStyle
import app.yaqeen.ui.theme.YaqeenExtras
import app.yaqeen.ui.theme.YaqeenSchemes
import app.yaqeen.ui.theme.YaqeenTheme

/**
 * اختيار «نمط التطبيق»: بطاقتا معاينة مصغّرتان (كلاسيكي / حياة) برسم مصغّر لكل نمط.
 * المعاينة تستعمل ألوان النمط الحقيقية بحسب الوضع الحالي (فاتح/داكن)، والاختيار يُحفظ فورًا.
 */
@Composable
internal fun ThemeStylePicker(selected: String, onSelect: (String) -> Unit) {
    val dark = YaqeenTheme.extras.dark
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("نمط التطبيق", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StyleOption(
                style = ThemeStyle.CLASSIC,
                title = "كلاسيكي",
                subtitle = "هادئ: أزرق ملكي وعاجي",
                dark = dark,
                selected = selected == ThemeStyle.CLASSIC,
                modifier = Modifier.weight(1f),
                onSelect = onSelect,
            )
            StyleOption(
                style = ThemeStyle.HAYAT,
                title = "حياة",
                subtitle = "نابض: ألوان زاهية وبطاقات مدوّرة",
                dark = dark,
                selected = selected == ThemeStyle.HAYAT,
                modifier = Modifier.weight(1f),
                onSelect = onSelect,
            )
        }
        Hint("يتغير شكل التطبيق فورًا، وهو مستقل عن اختيار الفاتح والداكن أدناه.")
    }
}

@Composable
private fun StyleOption(
    style: String,
    title: String,
    subtitle: String,
    dark: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(style) })
            .padding(8.dp),
    ) {
        StyleMock(style, dark, Modifier.fillMaxWidth().height(124.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "النمط الحالي", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** رسم مصغّر زخرفي للنمط (لا يُقرأ بقارئ الشاشة؛ الاسم والوصف يكفيان). */
@Composable
private fun StyleMock(style: String, dark: Boolean, modifier: Modifier) {
    val scheme = remember(style, dark) { YaqeenSchemes.scheme(style, dark) }
    val ex = remember(style, dark) { YaqeenExtras.of(style, dark, scheme) }
    val hayat = ex.isHayat
    val outer = RoundedCornerShape(16.dp)
    Column(
        modifier.clearAndSetSemantics { }.clip(outer).background(scheme.background).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ترويسة/بطاقة بطل
        val heroShape = RoundedCornerShape(if (hayat) 14.dp else 8.dp)
        val hero = ex.area(Area.HOME)
        Box(
            Modifier.fillMaxWidth().height(32.dp).clip(heroShape).then(
                if (hayat) Modifier.background(Brush.linearGradient(listOf(hero.accent, hero.gradientEnd)))
                else Modifier.background(scheme.primaryContainer),
            ),
        ) {
            val bar = if (hayat) Color.White else scheme.onPrimaryContainer
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp).size(width = 34.dp, height = 5.dp).background(bar, CircleShape))
            Box(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 10.dp).size(width = 22.dp, height = 4.dp).background(bar.copy(alpha = 0.6f), CircleShape))
        }
        // بلاطتان
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Area.PRAYER, Area.ADHKAR).forEach { area ->
                val p = ex.area(area)
                val tileShape = RoundedCornerShape(if (hayat) 12.dp else 8.dp)
                Box(
                    Modifier.weight(1f).height(28.dp).clip(tileShape).then(
                        if (hayat) Modifier.background(Brush.linearGradient(listOf(p.accent, p.gradientEnd)))
                        else Modifier.background(scheme.surface).border(1.dp, scheme.outlineVariant, tileShape),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(10.dp).background(if (hayat) p.onAccent.copy(alpha = 0.85f) else scheme.primary, CircleShape))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // شريط التنقل: حبة عائمة (حياة) أو شريط مسطح (كلاسيكي)
        val navShape = RoundedCornerShape(if (hayat) 50 else 0)
        Row(
            Modifier.fillMaxWidth().height(18.dp).clip(navShape).background(if (hayat) ex.navContainer else scheme.surface).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                val on = i == 1
                Box(
                    Modifier.size(if (hayat && on) 11.dp else 7.dp).background(
                        when {
                            hayat && on -> ex.navSelected
                            hayat -> ex.onNav.copy(alpha = 0.8f)
                            on -> scheme.primary
                            else -> scheme.outline
                        },
                        CircleShape,
                    ),
                )
            }
        }
    }
}
