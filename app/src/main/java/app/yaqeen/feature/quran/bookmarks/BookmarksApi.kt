package app.yaqeen.feature.quran.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.BookmarkCategoryEntity
import app.yaqeen.data.local.BookmarkEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.feature.quran.plans.SurahNames
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTextStyles
import kotlinx.coroutines.launch

/**
 * === العلامات المرجعية ===
 * شاشة مستقلة: قائمة مجمّعة حسب التصنيف، تصفية بالتصنيف، بحث في العنوان والملاحظة واسم السورة، انتقال مباشر للآية.
 */
@Composable
fun BookmarksScreen(nav: NavHostController, c: AppContainer) {
    val repo = remember { BookmarkRepository(c) }
    val scope = rememberCoroutineScope()
    val bookmarks by c.db.bookmarks().observeAll().collectAsState(initial = null)
    val categories by c.db.bookmarkCategories().observeAll().collectAsState(initial = emptyList())
    var quran by remember { mutableStateOf<QuranData?>(null) }
    LaunchedEffect(Unit) { quran = c.quran() }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) } // null الكل، "" بلا تصنيف، وإلا id
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
    var deleting by remember { mutableStateOf<BookmarkEntity?>(null) }
    var manageCategories by remember { mutableStateOf(false) }

    // إن حُذف التصنيف المحدد (من هذا الجهاز أو غيره) نعود إلى "الكل".
    LaunchedEffect(categories) {
        val f = filter
        if (f != null && f != BookmarkFilter.UNCATEGORIZED && categories.none { it.id == f }) filter = null
    }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("العلامات المرجعية", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { manageCategories = true }) { Icon(Icons.Filled.Folder, "إدارة التصنيفات") }
        })
        val q = quran
        val all = bookmarks
        if (q == null || all == null) {
            LoadingBox()
        } else {
            val catById = categories.associateBy { it.id }
            val visible = all.filter { b ->
                BookmarkFilter.matches(b, filter, query, q.surah(b.surah).nameAr, b.categoryId?.let { catById[it]?.name })
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("ابحث في العناوين والملاحظات") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item("all") { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("الكل (${ArabicText.indic(all.size)})") }) }
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = filter == cat.id, onClick = { filter = cat.id },
                            label = { Text("${cat.name} (${ArabicText.indic(all.count { it.categoryId == cat.id })})") },
                            leadingIcon = { Box(Modifier.size(12.dp).background(Color(cat.color), CircleShape)) },
                        )
                    }
                    if (categories.isNotEmpty() && all.any { it.categoryId == null || catById[it.categoryId] == null }) {
                        item("none") { FilterChip(selected = filter == BookmarkFilter.UNCATEGORIZED, onClick = { filter = BookmarkFilter.UNCATEGORIZED }, label = { Text("بلا تصنيف") }) }
                    }
                }
            }

            when {
                all.isEmpty() -> EmptyState(
                    title = "لا توجد علامات بعد",
                    message = "اضغط مطولًا على أي آية في المصحف واختر «علامة» لتحفظ موضعك مع عنوان وملاحظة ولون.",
                    icon = Icons.Filled.BookmarkBorder,
                )
                visible.isEmpty() -> EmptyState(title = "لا توجد نتائج", message = "جرّب كلمات أخرى أو غيّر التصنيف.")
                else -> {
                    // التجميع حسب التصنيف يظهر عند عرض "الكل" بلا بحث.
                    val grouped = filter == null && query.isBlank() && categories.isNotEmpty()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (grouped) {
                            val order: List<String?> = categories.map { it.id } + listOf(null)
                            order.forEach { cid ->
                                val group = if (cid == null) visible.filter { it.categoryId == null || catById[it.categoryId] == null }
                                else visible.filter { it.categoryId == cid }
                                if (group.isNotEmpty()) {
                                    item("h-$cid") { SectionTitle(cid?.let { catById[it]?.name } ?: "بلا تصنيف") }
                                    items(group, key = { "b-${it.id}" }) { b ->
                                        BookmarkCard(b, q, catById[b.categoryId], onOpen = { open(nav, b) }, onEdit = { editing = b }, onDelete = { deleting = b })
                                    }
                                }
                            }
                        } else {
                            items(visible, key = { it.id }) { b ->
                                BookmarkCard(b, q, catById[b.categoryId], onOpen = { open(nav, b) }, onEdit = { editing = b }, onDelete = { deleting = b })
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { b -> BookmarkEditorSheet(c, b.surah, b.ayah, onDismiss = { editing = null }) }
    deleting?.let { b ->
        ConfirmDialog(
            title = "حذف العلامة",
            message = "هل تريد حذف هذه العلامة؟",
            confirmText = "حذف",
            onConfirm = { scope.launch { repo.delete(b) } },
            onDismiss = { deleting = null },
        )
    }
    if (manageCategories) CategoryManagerDialog(repo, categories, onDismiss = { manageCategories = false })
}

private fun open(nav: NavHostController, b: BookmarkEntity) {
    nav.navigate(Routes.quranReader(surah = b.surah, ayah = b.ayah))
}

@Composable
private fun BookmarkCard(
    b: BookmarkEntity, q: QuranData, category: BookmarkCategoryEntity?,
    onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    val ayah = q.ayah(b.surah, b.ayah)
    val ref = "${SurahNames.plain(q.surah(b.surah).nameAr)} ${ArabicText.indic(b.ayah)} • ص ${ArabicText.indic(ayah.page)}"
    YaqeenCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Color(b.color)))
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 4.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title.ifBlank { ref }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (b.title.isNotBlank()) Text(ref, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تعديل العلامة") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف العلامة") }
                }
                Text(
                    app.yaqeen.feature.quran.reader.QuranText.forHafsFont(ayah.text), style = YaqeenTextStyles.quran(18f, 1.8f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                if (b.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(b.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
                }
                if (category != null) {
                    Spacer(Modifier.height(6.dp))
                    Pill(category.name)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ التصنيفات

@Composable
private fun ColorPalette(selected: Int, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(BookmarkPalette.colors) { col ->
            Box(
                Modifier.size(34.dp).background(Color(col), CircleShape).clickable { onPick(col) },
                contentAlignment = Alignment.Center,
            ) {
                if (col == selected) Icon(Icons.Filled.Check, "مختار", tint = if (BookmarkPalette.isLight(col)) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(initial: BookmarkCategoryEntity?, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: BookmarkPalette.colors[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "تصنيف جديد" else "تعديل التصنيف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("اسم التصنيف") }, modifier = Modifier.fillMaxWidth())
                ColorPalette(color) { color = it }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CategoryManagerDialog(repo: BookmarkRepository, categories: List<BookmarkCategoryEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editTarget by remember { mutableStateOf<BookmarkCategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BookmarkCategoryEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التصنيفات") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (categories.isEmpty()) {
                    Text("لا توجد تصنيفات بعد. أنشئ تصنيفًا لتنظيم علاماتك.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                categories.forEach { cat ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(Color(cat.color), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { editTarget = cat }) { Icon(Icons.Filled.Edit, "تعديل التصنيف") }
                        IconButton(onClick = { deleting = cat }) { Icon(Icons.Filled.Delete, "حذف التصنيف") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("تصنيف جديد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
    if (creating) CategoryEditDialog(null, onSave = { n, col -> scope.launch { repo.createCategory(n, col) } }, onDismiss = { creating = false })
    editTarget?.let { cat ->
        CategoryEditDialog(cat, onSave = { n, col -> scope.launch { repo.updateCategory(cat, n, col) } }, onDismiss = { editTarget = null })
    }
    deleting?.let { cat ->
        ConfirmDialog(
            title = "حذف التصنيف",
            message = "سيُحذف التصنيف «${cat.name}» وتبقى علاماته بلا تصنيف.",
            confirmText = "حذف",
            onConfirm = { scope.launch { repo.deleteCategory(cat) } },
            onDismiss = { deleting = null },
        )
    }
}

// ------------------------------------------------------------------ محرر العلامة

/**
 * نافذة سفلية لإضافة/تعديل/حذف علامة على آية (عنوان، ملاحظة، لون، تصنيف).
 * إن كانت للآية علامة موجودة تُعرض للتعديل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditorSheet(c: AppContainer, surah: Int, ayah: Int, onDismiss: () -> Unit) {
    val repo = remember { BookmarkRepository(c) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories by c.db.bookmarkCategories().observeAll().collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<BookmarkEntity?>(null) }
    var quran by remember { mutableStateOf<QuranData?>(null) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(BookmarkPalette.default) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(surah, ayah) {
        try {
            quran = c.quran()
            val e = repo.find(surah, ayah)
            existing = e
            if (e != null) { title = e.title; note = e.note; color = e.color; categoryId = e.categoryId }
            loaded = true
        } catch (t: Throwable) {
            failed = true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val q = quran
        val valid = q != null && surah in 1..114 && ayah in 1..q.surah(surah).ayahCount
        when {
            failed || (loaded && !valid) -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("تعذّر فتح العلامة لهذه الآية.", style = MaterialTheme.typography.bodyLarge)
            }
            !loaded || q == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val a = q.ayah(surah, ayah)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(if (existing == null) "علامة جديدة" else "تعديل العلامة", style = MaterialTheme.typography.titleLarge)
                    YaqeenCard(Modifier.fillMaxWidth()) {
                        Text(
                            "${SurahNames.plain(q.surah(surah).nameAr)} • الآية ${ArabicText.indic(ayah)} • صفحة ${ArabicText.indic(a.page)}",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(app.yaqeen.feature.quran.reader.QuranText.forHafsFont(a.text), style = YaqeenTextStyles.quran(20f, 1.8f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, singleLine = true, label = { Text("العنوان (اختياري)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it.take(1000) }, label = { Text("ملاحظة (اختيارية)") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())

                    Text("اللون", style = MaterialTheme.typography.titleSmall)
                    ColorPalette(color) { color = it }

                    Text("التصنيف", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item("none") { FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("بلا تصنيف") }) }
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(cat.name) },
                                leadingIcon = { Box(Modifier.size(12.dp).background(Color(cat.color), CircleShape)) },
                            )
                        }
                        item("new") { OutlinedButton(onClick = { newCategory = true }) { Text("+ تصنيف جديد") } }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.upsert(surah, ayah, a.page, title, note, color, categoryId)
                                    sheetState.hide()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (existing == null) "حفظ العلامة" else "حفظ التعديل") }
                        if (existing != null) {
                            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("حذف العلامة") }
                        } else {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                        }
                    }
                }
            }
        }
    }

    if (newCategory) {
        CategoryEditDialog(
            initial = null,
            onSave = { n, col -> scope.launch { categoryId = repo.createCategory(n, col).id } },
            onDismiss = { newCategory = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف العلامة",
            message = "هل تريد حذف هذه العلامة؟",
            confirmText = "حذف",
            onConfirm = { scope.launch { existing?.let { repo.delete(it) }; onDismiss() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
