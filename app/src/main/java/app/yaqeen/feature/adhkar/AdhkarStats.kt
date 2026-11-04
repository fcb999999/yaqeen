package app.yaqeen.feature.adhkar

import app.yaqeen.data.adhkar.AdhkarCategory
import app.yaqeen.data.local.AdhkarProgressEntity

/**
 * دوال خالصة لإحصاءات الأذكار (لا تعتمد على أندرويد). تستعملها شاشات الرئيسية و"رحلتي".
 *
 * قاعدة: القسم "مكتمل" في يوم ما إذا وُجد لكل ذكر من أذكاره سجل تقدّم غير محذوف وحالته completed=true.
 * قسم بلا أذكار لا يُعدّ مكتملًا أبدًا.
 */
object AdhkarStats {

    data class CategoryProgress(val done: Int, val total: Int) {
        val ratio: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        val isComplete: Boolean get() = total > 0 && done >= total
    }

    /** هل أُتمّ القسم في اليوم الذي تمثّله [dayRows] (سجلات يوم واحد؛ قد تحوي أقسامًا أخرى فتُتجاهل). */
    fun isCategoryCompleted(categoryItemIds: List<String>, dayRows: List<AdhkarProgressEntity>): Boolean {
        if (categoryItemIds.isEmpty()) return false
        val done = completedIds(dayRows)
        return categoryItemIds.all { it in done }
    }

    /** عدد الأذكار المكتملة (كل الأقسام) في سجلات يوم واحد. */
    fun completedItems(dayRows: List<AdhkarProgressEntity>): Int = completedIds(dayRows).size

    /** عدد الأذكار المكتملة من قائمة معرّفات قسم معيّن. */
    fun completedItems(categoryItemIds: List<String>, dayRows: List<AdhkarProgressEntity>): Int {
        val done = completedIds(dayRows)
        return categoryItemIds.count { it in done }
    }

    fun categoryProgress(categoryItemIds: List<String>, dayRows: List<AdhkarProgressEntity>): CategoryProgress =
        CategoryProgress(completedItems(categoryItemIds, dayRows), categoryItemIds.size)

    /** معرّفات الأقسام المكتملة في سجلات يوم واحد. */
    fun completedCategoryIds(categories: List<AdhkarCategory>, dayRows: List<AdhkarProgressEntity>): Set<String> {
        val done = completedIds(dayRows)
        return categories.filter { c -> c.items.isNotEmpty() && c.items.all { it.id in done } }.map { it.id }.toSet()
    }

    /**
     * التواريخ (ISO) التي أُتمّ فيها القسم، من سجلات مدى زمني. مفيد للسلاسل والإحصاءات.
     */
    fun completedDays(categoryItemIds: List<String>, rows: List<AdhkarProgressEntity>): Set<String> {
        if (categoryItemIds.isEmpty()) return emptySet()
        return rows.groupBy { it.date }
            .filter { (_, dayRows) -> isCategoryCompleted(categoryItemIds, dayRows) }
            .keys
    }

    private fun completedIds(dayRows: List<AdhkarProgressEntity>): Set<String> =
        dayRows.asSequence().filter { !it.deleted && it.completed }.map { it.itemId }.toSet()
}
