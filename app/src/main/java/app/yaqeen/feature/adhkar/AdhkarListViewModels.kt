package app.yaqeen.feature.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.data.adhkar.AdhkarCatalog
import app.yaqeen.data.adhkar.AdhkarItem
import app.yaqeen.domain.model.AdhkarMarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** صف ذكر في قوائم المفضلة/العلامات/البحث: كل ما يلزم للعرض والانتقال إلى الذكر. */
data class AdhkarRow(
    val itemId: String,
    val categoryId: String,
    val categoryTitle: String,
    /** رقم الذكر داخل قسمه (لفتح البطاقة مباشرة). */
    val index: Int,
    val item: AdhkarItem,
)

sealed interface RowsUi {
    data object Loading : RowsUi
    data class Error(val message: String) : RowsUi
    data class Ready(val rows: List<AdhkarRow>) : RowsUi
}

private fun rowOf(catalog: AdhkarCatalog, itemId: String): AdhkarRow? {
    val category = catalog.categoryOf(itemId) ?: return null
    val idx = category.items.indexOfFirst { it.id == itemId }
    if (idx < 0) return null
    return AdhkarRow(itemId, category.id, category.title, idx, category.items[idx])
}

/** شاشة المفضلة أو العلامات المرجعية. */
class AdhkarMarksViewModel(private val c: AppContainer, val type: AdhkarMarkType) : ViewModel() {
    private val repo = AdhkarRepository(c)
    private val catalog = MutableStateFlow<Result<AdhkarCatalog>?>(null)

    val ui: StateFlow<RowsUi> = combine(catalog, repo.observeMarksOfType(type)) { cat, marks ->
        val catalogValue = cat?.getOrNull()
        when {
            catalogValue != null -> RowsUi.Ready(marks.mapNotNull { rowOf(catalogValue, it.itemId) })
            cat != null -> RowsUi.Error("تعذّر تحميل الأذكار")
            else -> RowsUi.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RowsUi.Loading)

    init { load() }

    fun retry() { catalog.value = null; load() }

    private fun load() {
        viewModelScope.launch { catalog.value = runCatching { c.adhkar() } }
    }

    fun remove(row: AdhkarRow) {
        c.appScope.launch { repo.removeMark(type, row.itemId) }
    }
}

data class SearchUi(
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<AdhkarRow> = emptyList(),
    val limitReached: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AdhkarSearchViewModel(private val c: AppContainer) : ViewModel() {
    private val catalog = MutableStateFlow<Result<AdhkarCatalog>?>(null)
    private val query = MutableStateFlow("")

    val ui: StateFlow<SearchUi> = combine(
        catalog,
        query,
        query.debounce(180).onStart { emit("") },
    ) { cat, typed, debounced -> Triple(cat, typed, debounced) }
        .mapLatest { (cat, typed, debounced) ->
            val catalogValue = cat?.getOrNull()
            when {
                catalogValue != null -> {
                    val rows = if (debounced.isBlank()) emptyList() else catalogValue.search(debounced, LIMIT).mapNotNull { (_, item) -> rowOf(catalogValue, item.id) }
                    SearchUi(query = typed, loading = false, rows = rows, limitReached = rows.size >= LIMIT)
                }
                cat != null -> SearchUi(query = typed, loading = false, error = "تعذّر تحميل الأذكار")
                else -> SearchUi(query = typed, loading = true)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    init { load() }

    fun retry() { catalog.value = null; load() }

    private fun load() {
        viewModelScope.launch { catalog.value = runCatching { c.adhkar() } }
    }

    fun setQuery(q: String) { query.value = q }

    val currentQuery: String get() = query.value

    private companion object { const val LIMIT = 100 }
}
