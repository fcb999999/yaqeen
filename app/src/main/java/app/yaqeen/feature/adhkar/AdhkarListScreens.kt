package app.yaqeen.feature.adhkar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yaqeen.core.ArabicText
import app.yaqeen.domain.model.AdhkarMarkType
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.theme.YaqeenTextStyles

/** بطاقة ذكر مختصرة في القوائم: القسم + معاينة النص. */
@Composable
private fun AdhkarRowCard(row: AdhkarRow, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val style = (if (row.item.quranRef.isNotBlank()) YaqeenTextStyles.quranAmiri(18f, 1.7f) else YaqeenTextStyles.adhkar(18f, 1.7f))
        .copy(textAlign = TextAlign.Start)
    YaqeenCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(row.categoryTitle)
                    if (row.item.group.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.item.group,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    row.item.text,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

/** المفضلة أو العلامات المرجعية: قائمة تفتح الذكر في بطاقته. */
@Composable
fun AdhkarMarksScreen(vm: AdhkarMarksViewModel, onBack: () -> Unit, onOpen: (categoryId: String, index: Int) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val favorite = vm.type == AdhkarMarkType.FAVORITE
    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(if (favorite) "المفضلة" else "العلامات المرجعية", onBack)
        when (val s = ui) {
            RowsUi.Loading -> LoadingBox()
            is RowsUi.Error -> EmptyState(title = s.message, action = { Button(onClick = vm::retry) { Text("إعادة المحاولة") } })
            is RowsUi.Ready -> if (s.rows.isEmpty()) {
                EmptyState(
                    title = if (favorite) "لا توجد أذكار مفضلة بعد" else "لا توجد علامات مرجعية بعد",
                    message = if (favorite) "اضغط على النجمة في بطاقة أي ذكر لتجده هنا" else "اضغط على أيقونة العلامة في بطاقة أي ذكر لتجده هنا",
                    icon = if (favorite) Icons.Filled.Star else Icons.Filled.Bookmark,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.rows, key = { it.itemId }) { row ->
                        AdhkarRowCard(
                            row = row,
                            onClick = { onOpen(row.categoryId, row.index) },
                            trailing = {
                                IconButton(onClick = { vm.remove(row) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = if (favorite) "إزالة من المفضلة" else "إزالة العلامة المرجعية",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** البحث في كل الأذكار؛ النتيجة تفتح الذكر في بطاقته. */
@Composable
fun AdhkarSearchScreen(vm: AdhkarSearchViewModel, onBack: () -> Unit, onOpen: (categoryId: String, index: Int) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(vm.currentQuery) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        vm.setQuery(text)
        if (text.isBlank()) runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("بحث في الأذكار", onBack)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; vm.setQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("اكتب كلمة من الذكر أو فضله") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "مسح") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = MaterialTheme.shapes.medium,
        )
        when {
            ui.error != null -> EmptyState(title = ui.error.orEmpty(), action = { Button(onClick = vm::retry) { Text("إعادة المحاولة") } })
            ui.loading -> LoadingBox()
            text.isBlank() -> EmptyState(
                title = "ابحث في الأذكار",
                message = "يشمل البحث أذكار الصباح والمساء والنوم والاستيقاظ وبعد الصلاة والأذكار المتنوعة",
                icon = Icons.Filled.Search,
            )
            ui.rows.isEmpty() -> EmptyState(title = "لا توجد نتائج", message = "جرّب كلمة أخرى أو جزءًا أقصر من العبارة")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "count") {
                    Text(
                        if (ui.limitReached) "أول ${ArabicText.indic(ui.rows.size)} نتيجة" else "${ArabicText.indic(ui.rows.size)} نتيجة",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ui.rows, key = { it.itemId }) { row ->
                    AdhkarRowCard(row = row, onClick = { focusManager.clearFocus(); onOpen(row.categoryId, row.index) })
                }
            }
        }
    }
}
