package app.yaqeen.feature.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.data.adhkar.AdhkarCategory
import app.yaqeen.data.adhkar.AdhkarItem
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.domain.model.AdhkarMarkType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ذكر في شاشة القسم مع تقدّمه وعلاماته. */
data class ItemUi(
    val item: AdhkarItem,
    val index: Int,
    val progress: ItemProgress,
    /** true إذا غيّر المستخدم عدد التكرار عن الكتالوج. */
    val customTarget: Boolean,
    val favorite: Boolean,
    val bookmark: Boolean,
)

sealed interface CategoryUi {
    data object Loading : CategoryUi
    data class Error(val message: String) : CategoryUi
    data class Ready(
        val categoryId: String,
        val title: String,
        val items: List<ItemUi>,
        val date: String,
        /** الصفحة الابتدائية (items.size = صفحة الإتمام). */
        val startPage: Int,
        val prefs: AdhkarPrefs,
        val groups: List<Pair<String, Int>>,
    ) : CategoryUi {
        val completedCount: Int get() = items.count { it.progress.completed }
        val allCompleted: Boolean get() = items.isNotEmpty() && completedCount == items.size
    }
}

/** نتيجة نقرة على البطاقة. */
data class TapResult(val counted: Boolean, val itemCompleted: Boolean, val categoryCompleted: Boolean) {
    companion object { val NONE = TapResult(false, false, false) }
}

/** يبني قائمة بطاقات القسم من الكتالوج وصفوف اليوم (الخالصة، قابلة للاختبار). */
internal fun buildItemUis(
    category: AdhkarCategory,
    date: String,
    dbRows: Map<String, AdhkarProgressEntity>,
    overlays: Map<String, ProgressOverlay>,
    targets: Map<String, Int>,
    marks: AdhkarMarks,
): List<ItemUi> = category.items.mapIndexed { i, item ->
    val target = AdhkarCounter.effectiveTarget(item.count, targets[item.id])
    val row = AdhkarMerge.effective(dbRows[item.id], overlays[item.id], date)
    ItemUi(
        item = item,
        index = i,
        progress = ItemProgress((row?.count ?: 0).coerceIn(0, target), target),
        customTarget = target != item.count,
        favorite = item.id in marks.favorites,
        bookmark = item.id in marks.bookmarks,
    )
}

class AdhkarCategoryViewModel(
    private val c: AppContainer,
    private val categoryId: String,
    private val startArg: Int,
) : ViewModel() {
    private val repo = AdhkarRepository(c)

    private class Loaded(val category: AdhkarCategory, val startPage: Int)
    private class DbDay(val date: String, val rows: Map<String, AdhkarProgressEntity>)

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val loadError = MutableStateFlow<String?>(null)
    private val dbDay = MutableStateFlow<DbDay?>(null)
    private val overlay = MutableStateFlow<Map<String, ProgressOverlay>>(emptyMap())
    private var seq = 0L
    private var lastSavedPositionItem: String? = null

    /** طابور كتابة مرتَّب: لا يضيع شيء إذا غادر المستخدم الشاشة (المستهلك يعمل في نطاق التطبيق). */
    private val writeQueue = Channel<ProgressOverlay>(Channel.UNLIMITED)

    private val targets: StateFlow<Map<String, Int>> = c.settings.observeRaw()
        .map { AdhkarKeys.targetsFrom(it) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val prefs: StateFlow<AdhkarPrefs> = repo.observePrefs().stateIn(viewModelScope, SharingStarted.Eagerly, AdhkarPrefs())
    private val marks: StateFlow<AdhkarMarks> = repo.observeMarks().stateIn(viewModelScope, SharingStarted.Eagerly, AdhkarMarks())

    val ui: StateFlow<CategoryUi> = combine(
        combine(loaded, loadError, dbDay) { l, err, d -> Triple(l, err, d) },
        overlay, targets, prefs, marks,
    ) { (l, err, d), ov, tg, pf, mk ->
        when {
            l != null && d != null -> CategoryUi.Ready(
                categoryId = categoryId,
                title = l.category.title,
                items = buildItemUis(l.category, d.date, d.rows, ov, tg, mk),
                date = d.date,
                startPage = l.startPage,
                prefs = pf,
                groups = AdhkarGroups.starts(l.category.items),
            )
            err != null -> CategoryUi.Error(err)
            else -> CategoryUi.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoryUi.Loading)

    init {
        c.appScope.launch {
            for (o in writeQueue) {
                val id = o.row.itemId
                runCatching { repo.saveProgress(o.row) }
                    .onSuccess { stamped ->
                        overlay.update { m ->
                            val cur = m[id]
                            if (cur != null && cur.seq == o.seq) m + (id to cur.copy(savedUpdatedAt = stamped.updatedAt)) else m
                        }
                    }
                    .onFailure {
                        // فشل الحفظ: نُسقط القيمة المتفائلة لتعكس الواجهة ما في قاعدة البيانات فعلًا.
                        overlay.update { m -> if (m[id]?.seq == o.seq) m - id else m }
                    }
            }
        }
        viewModelScope.launch {
            repo.todayFlow()
                .flatMapLatest { d -> repo.observeDayCategory(d, categoryId).map { rows -> DbDay(d, rows.associateBy { it.itemId }) } }
                .collect { dbDay.value = it }
        }
        load()
    }

    fun retry() { loadError.value = null; load() }

    private fun load() {
        viewModelScope.launch {
            val catalog = runCatching { c.adhkar() }.getOrElse {
                loadError.value = "تعذّر تحميل الأذكار"
                return@launch
            }
            val category = catalog.category(categoryId)
            if (category == null || category.items.isEmpty()) {
                loadError.value = "هذا القسم غير موجود"
                return@launch
            }
            val today = c.clock.today().toString()
            val rows = repo.dayCategoryRows(today, categoryId)
            val completed = AdhkarStats.isCategoryCompleted(category.items.map { it.id }, rows)
            val saved = AdhkarPosition.decode(c.settings.get(AdhkarKeys.position(categoryId)))
            loaded.value = Loaded(category, AdhkarResume.startPage(startArg, category.items.map { it.id }, saved, today, completed))
        }
    }

    // ------------------------------------------------------------------ تقدّم الذكر

    private fun currentProgress(item: AdhkarItem): ItemProgress {
        val d = dbDay.value
        val date = d?.date ?: c.clock.today().toString()
        val target = AdhkarCounter.effectiveTarget(item.count, targets.value[item.id])
        val row = AdhkarMerge.effective(d?.rows?.get(item.id), overlay.value[item.id], date)
        return ItemProgress((row?.count ?: 0).coerceIn(0, target), target)
    }

    private fun commit(item: AdhkarItem, count: Int, target: Int) {
        val date = dbDay.value?.date ?: c.clock.today().toString()
        val row = AdhkarProgressEntity(
            id = "${date}_${item.id}", date = date, categoryId = categoryId, itemId = item.id,
            count = count, target = target, completed = AdhkarCounter.isCompleted(count, target),
        )
        val o = ProgressOverlay(row, ++seq)
        overlay.update { it + (item.id to o) }
        writeQueue.trySend(o)
    }

    /** نقرة على بطاقة: تنقص المتبقي بواحد. تُستدعى من الواجهة (الخيط الرئيسي). */
    fun tap(itemId: String): TapResult {
        val category = loaded.value?.category ?: return TapResult.NONE
        val item = category.items.firstOrNull { it.id == itemId } ?: return TapResult.NONE
        val cur = currentProgress(item)
        if (cur.completed) return TapResult.NONE
        val newCount = AdhkarCounter.afterTap(cur.count, cur.target)
        val itemDone = AdhkarCounter.isCompleted(newCount, cur.target)
        commit(item, newCount, cur.target)
        val categoryDone = itemDone && category.items.all { it.id == itemId || currentProgress(it).completed }
        if (categoryDone) c.requestReschedule() // لا حاجة لتذكير قسم أُنجز اليوم
        return TapResult(true, itemDone, categoryDone)
    }

    fun resetItem(itemId: String) {
        val item = loaded.value?.category?.items?.firstOrNull { it.id == itemId } ?: return
        val cur = currentProgress(item)
        if (cur.count == 0) return
        commit(item, 0, cur.target)
        c.requestReschedule()
    }

    fun resetCategory() {
        val category = loaded.value?.category ?: return
        var changed = false
        for (item in category.items) {
            val cur = currentProgress(item)
            if (cur.count != 0) { commit(item, 0, cur.target); changed = true }
        }
        if (changed) c.requestReschedule()
    }

    /** يحفظ عدد التكرار المخصَّص ويطبّقه على تقدّم اليوم (إن وُجد). */
    fun setTarget(itemId: String, value: Int) {
        val item = loaded.value?.category?.items?.firstOrNull { it.id == itemId } ?: return
        val v = AdhkarCounter.clampTarget(value)
        val cur = currentProgress(item)
        if (cur.count > 0) commit(item, cur.count.coerceAtMost(v), v)
        c.appScope.launch { repo.setTarget(item.id, item.count, v) }
        c.requestReschedule()
    }

    fun resetTarget(itemId: String) {
        val item = loaded.value?.category?.items?.firstOrNull { it.id == itemId } ?: return
        setTarget(itemId, item.count)
    }

    // ------------------------------------------------------------------ العلامات والموضع

    fun toggleFavorite(item: AdhkarItem) {
        c.appScope.launch { repo.toggleMark(AdhkarMarkType.FAVORITE, item.id, categoryId) }
    }

    fun toggleBookmark(item: AdhkarItem) {
        c.appScope.launch { repo.toggleMark(AdhkarMarkType.BOOKMARK, item.id, categoryId) }
    }

    /** يحفظ آخر ذكر وصل إليه المستخدم (صفحة الإتمام لا تُحفَظ). */
    fun savePosition(page: Int) {
        val category = loaded.value?.category ?: return
        val item = category.items.getOrNull(page) ?: return
        if (lastSavedPositionItem == item.id) return
        lastSavedPositionItem = item.id
        val date = dbDay.value?.date ?: c.clock.today().toString()
        c.appScope.launch { repo.savePosition(categoryId, item.id, date) }
    }

    // ------------------------------------------------------------------ الإعدادات السريعة

    fun setAutoAdvance(enabled: Boolean) {
        c.appScope.launch { c.settings.set(app.yaqeen.data.settings.Settings.ADHKAR_AUTO_ADVANCE, enabled) }
    }

    fun setVertical(vertical: Boolean) {
        c.appScope.launch {
            c.settings.set(
                app.yaqeen.data.settings.Settings.ADHKAR_SWIPE_AXIS,
                if (vertical) AdhkarPrefs.AXIS_VERTICAL else AdhkarPrefs.AXIS_HORIZONTAL,
            )
        }
    }

    override fun onCleared() {
        writeQueue.close()
        super.onCleared()
    }
}
