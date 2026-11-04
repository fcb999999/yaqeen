package app.yaqeen.feature.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.data.adhkar.AdhkarCatalog
import app.yaqeen.data.adhkar.AdhkarCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class CategoryCardUi(val id: String, val title: String, val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
    val ratio: Float get() = if (total == 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/** بطاقة "تابع": موضع محفوظ اليوم ([isResume] = true) أو اقتراح بحسب وقت اليوم. */
data class ContinueUi(
    val categoryId: String,
    val categoryTitle: String,
    val itemIndex: Int,
    val total: Int,
    val isResume: Boolean,
)

sealed interface HomeUi {
    data object Loading : HomeUi
    data class Error(val message: String) : HomeUi
    data class Ready(
        val categories: List<CategoryCardUi>,
        val cont: ContinueUi?,
        val favorites: Int,
        val bookmarks: Int,
    ) : HomeUi
}

/** يختار بطاقة "تابع" (خالصة وقابلة للاختبار). */
internal fun pickContinue(
    categories: List<AdhkarCategory>,
    positions: Map<String, AdhkarPosition>,
    completed: Set<String>,
    today: String,
    hour: Int,
): ContinueUi? {
    val resume = categories.mapNotNull { cat ->
        val pos = positions[cat.id] ?: return@mapNotNull null
        if (pos.date != today || cat.id in completed) return@mapNotNull null
        val idx = cat.items.indexOfFirst { it.id == pos.itemId }
        if (idx < 0) null else Triple(cat, idx, pos.savedAt)
    }.maxByOrNull { it.third }
    if (resume != null) {
        return ContinueUi(resume.first.id, resume.first.title, resume.second, resume.first.items.size, true)
    }
    val suggested = categories.firstOrNull { it.id == AdhkarSuggestion.categoryForHour(hour) } ?: return null
    if (suggested.id in completed || suggested.items.isEmpty()) return null
    return ContinueUi(suggested.id, suggested.title, 0, suggested.items.size, false)
}

class AdhkarHomeViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = AdhkarRepository(c)
    private val catalog = MutableStateFlow<Result<AdhkarCatalog>?>(null)

    val ui: StateFlow<HomeUi> = combine(
        catalog,
        repo.todayFlow().flatMapLatest { d -> repo.observeDay(d).map { rows -> d to rows } },
        c.settings.observeRaw().map { AdhkarKeys.positionsFrom(it) }.distinctUntilChanged(),
        repo.observeMarks(),
    ) { cat, (today, rows), positions, marks ->
        val catalogValue = cat?.getOrNull()
        when {
            catalogValue != null -> {
                val completed = AdhkarStats.completedCategoryIds(catalogValue.categories, rows)
                val hour = Instant.ofEpochMilli(c.clock.nowMillis()).atZone(c.clock.zone()).hour
                HomeUi.Ready(
                    categories = catalogValue.categories.map {
                        val ids = it.items.map { item -> item.id }
                        CategoryCardUi(it.id, it.title, AdhkarStats.completedItems(ids, rows), ids.size)
                    },
                    cont = pickContinue(catalogValue.categories, positions, completed, today, hour),
                    favorites = marks.favorites.size,
                    bookmarks = marks.bookmarks.size,
                )
            }
            cat != null -> HomeUi.Error("تعذّر تحميل الأذكار")
            else -> HomeUi.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi.Loading)

    init { load() }

    fun retry() { catalog.value = null; load() }

    private fun load() {
        viewModelScope.launch { catalog.value = runCatching { c.adhkar() } }
    }
}
