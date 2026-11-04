package app.yaqeen.feature.adhkar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yaqeen.core.ArabicText
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.HeroCard
import app.yaqeen.ui.components.IconChip
import app.yaqeen.ui.components.LocalHero
import app.yaqeen.ui.components.YaqeenTile
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.Hue
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.theme.YaqeenTheme

/** لون كل قسم في نمط «حياة» (لا يؤثر في classic). */
internal fun categoryHue(id: String): Hue = when (id) {
    AdhkarCategoryId.MORNING -> Hue.AMBER
    AdhkarCategoryId.EVENING -> Hue.CORAL
    AdhkarCategoryId.SLEEP -> Hue.VIOLET
    AdhkarCategoryId.WAKING -> Hue.TEAL
    AdhkarCategoryId.AFTER_PRAYER -> Hue.GREEN
    else -> Hue.PINK
}

internal fun categoryIcon(id: String): ImageVector = when (id) {
    AdhkarCategoryId.MORNING -> Icons.Filled.WbSunny
    AdhkarCategoryId.EVENING -> Icons.Filled.WbTwilight
    AdhkarCategoryId.SLEEP -> Icons.Filled.Bedtime
    AdhkarCategoryId.WAKING -> Icons.Filled.Alarm
    AdhkarCategoryId.AFTER_PRAYER -> Icons.Filled.Mosque
    else -> Icons.Filled.AutoStories
}

/** جذر تبويب الأذكار: الأقسام مع تقدّم اليوم، "تابع"، والمفضلة والعلامات والبحث. */
@Composable
fun AdhkarHomeScreen(
    vm: AdhkarHomeViewModel,
    onOpenCategory: (categoryId: String, start: Int) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onBookmarks: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("الأذكار", actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "بحث في الأذكار") }
        })
        when (val s = ui) {
            HomeUi.Loading -> LoadingBox()
            is HomeUi.Error -> EmptyState(title = s.message, action = { Button(onClick = vm::retry) { Text("إعادة المحاولة") } })
            is HomeUi.Ready -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                s.cont?.let { c -> item(key = "continue") { ContinueCard(c, onOpenCategory) } }
                item(key = "quick") { QuickRow(s.favorites, s.bookmarks, onFavorites, onBookmarks, onSearch) }
                item(key = "title") { SectionTitle("الأقسام") }
                items(s.categories, key = { it.id }) { cat -> CategoryCard(cat) { onOpenCategory(cat.id, -1) } }
            }
        }
    }
}

@Composable
private fun ContinueCard(c: ContinueUi, onOpen: (String, Int) -> Unit) {
    HeroCard(
        Area.ADHKAR,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(c.categoryId, if (c.isResume) c.itemIndex else -1) },
    ) {
        val on = LocalHero.current.content
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (c.isResume) "تابع من حيث توقفت" else "ابدأ ${c.categoryTitle}",
                    style = MaterialTheme.typography.titleMedium,
                    color = on,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (c.isResume) "${c.categoryTitle} – الذكر ${ArabicText.indic(c.itemIndex + 1)} من ${ArabicText.indic(c.total)}"
                    else "اقتراح بحسب وقت اليوم",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = on)
        }
    }
}

@Composable
private fun QuickRow(favorites: Int, bookmarks: Int, onFavorites: () -> Unit, onBookmarks: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickTile("المفضلة", Icons.Filled.Star, if (favorites > 0) ArabicText.indic(favorites) else null, Hue.AMBER, onFavorites, Modifier.weight(1f))
        QuickTile("العلامات", Icons.Filled.Bookmark, if (bookmarks > 0) ArabicText.indic(bookmarks) else null, Hue.VIOLET, onBookmarks, Modifier.weight(1f))
        QuickTile("بحث", Icons.Filled.Search, null, Hue.TEAL, onSearch, Modifier.weight(1f))
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, badge: String?, hue: Hue, onClick: () -> Unit, modifier: Modifier) {
    YaqeenTile(
        label, icon, onClick, modifier,
        hue = hue,
        caption = badge ?: " ",
        iconSize = 24.dp,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
        labelStyle = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun CategoryCard(cat: CategoryCardUi, onClick: () -> Unit) {
    YaqeenCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (YaqeenTheme.isHayat) {
                IconChip(categoryIcon(cat.id), categoryHue(cat.id), size = 52.dp)
            } else {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryIcon(cat.id), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        cat.complete -> "أُنجز اليوم"
                        cat.done > 0 -> "أُنجز ${ArabicText.indic(cat.done)} من ${ArabicText.indic(cat.total)}"
                        else -> AdhkarLabels.adhkarCount(cat.total, ArabicText::indic)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cat.complete) YaqeenTheme.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cat.done > 0 && !cat.complete) {
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(cat.ratio)
                }
            }
            if (cat.complete) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, "مكتمل", tint = YaqeenTheme.extra.success)
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
