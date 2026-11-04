package app.yaqeen.feature.adhkar

import app.yaqeen.AppContainer
import app.yaqeen.core.Clock
import app.yaqeen.data.local.AdhkarMarkEntity
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.AdhkarMarkType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** تفضيلات العرض والسلوك للأذكار (من الإعدادات المتزامنة). */
data class AdhkarPrefs(
    val textSize: Float = Settings.ADHKAR_TEXT_SIZE.default,
    val showFadl: Boolean = Settings.ADHKAR_SHOW_FADL.default,
    val haptics: Boolean = Settings.ADHKAR_HAPTICS.default,
    val autoAdvance: Boolean = Settings.ADHKAR_AUTO_ADVANCE.default,
    val vertical: Boolean = Settings.ADHKAR_SWIPE_AXIS.default == AXIS_VERTICAL,
) {
    companion object {
        const val AXIS_HORIZONTAL = "horizontal"
        const val AXIS_VERTICAL = "vertical"
    }
}

/** الأذكار المفضلة والمعلَّمة بإشارة مرجعية (مجموعات معرّفات الأذكار). */
data class AdhkarMarks(val favorites: Set<String> = emptySet(), val bookmarks: Set<String> = emptySet())

/** وصول الأذكار إلى Room/الإعدادات. كل الكتابة تمر عبر c.writer. */
class AdhkarRepository(private val c: AppContainer) {
    private val progress get() = c.db.adhkarProgress()
    private val marks get() = c.db.adhkarMarks()

    /** تاريخ اليوم (ISO) ويُعاد إصداره عند منتصف الليل أثناء بقاء التطبيق مفتوحًا. */
    fun todayFlow(): Flow<String> = todayFlow(c.clock)

    fun observePrefs(): Flow<AdhkarPrefs> = combine(
        c.settings.observe(Settings.ADHKAR_TEXT_SIZE),
        c.settings.observe(Settings.ADHKAR_SHOW_FADL),
        c.settings.observe(Settings.ADHKAR_HAPTICS),
        c.settings.observe(Settings.ADHKAR_AUTO_ADVANCE),
        c.settings.observe(Settings.ADHKAR_SWIPE_AXIS),
    ) { size, fadl, haptics, auto, axis ->
        AdhkarPrefs(size.coerceIn(14f, 48f), fadl, haptics, auto, axis == AdhkarPrefs.AXIS_VERTICAL)
    }.distinctUntilChanged()

    fun observeMarks(): Flow<AdhkarMarks> = marks.observeAll().map { rows ->
        AdhkarMarks(
            favorites = rows.filter { it.type == AdhkarMarkType.FAVORITE.name }.map { it.itemId }.toSet(),
            bookmarks = rows.filter { it.type == AdhkarMarkType.BOOKMARK.name }.map { it.itemId }.toSet(),
        )
    }.distinctUntilChanged()

    /** علامات نوع معيّن مرتبة من الأحدث. */
    fun observeMarksOfType(type: AdhkarMarkType): Flow<List<AdhkarMarkEntity>> = marks.observeByType(type.name)

    suspend fun toggleMark(type: AdhkarMarkType, itemId: String, categoryId: String) {
        val id = markId(type, itemId)
        val existing = marks.byId(id)
        if (existing != null && !existing.deleted) {
            c.writer.delete(marks, existing)
        } else {
            c.writer.save(
                marks,
                (existing ?: AdhkarMarkEntity(id = id, itemId = itemId, categoryId = categoryId, type = type.name))
                    .copy(createdAt = c.clock.nowMillis(), deleted = false),
            )
        }
    }

    suspend fun removeMark(type: AdhkarMarkType, itemId: String) {
        val existing = marks.byId(markId(type, itemId)) ?: return
        if (!existing.deleted) c.writer.delete(marks, existing)
    }

    suspend fun saveProgress(row: AdhkarProgressEntity): AdhkarProgressEntity = c.writer.save(progress, row)

    suspend fun dayCategoryRows(date: String, categoryId: String): List<AdhkarProgressEntity> =
        progress.dayCategory(date, categoryId)

    fun observeDay(date: String): Flow<List<AdhkarProgressEntity>> = progress.observeDay(date)

    fun observeDayCategory(date: String, categoryId: String): Flow<List<AdhkarProgressEntity>> =
        progress.observeDayCategory(date, categoryId)

    /** يحفظ التكرار المخصَّص لذكر؛ القيمة المساوية للكتالوج تعني إزالة التخصيص. */
    suspend fun setTarget(itemId: String, catalogCount: Int, value: Int) {
        val v = AdhkarCounter.clampTarget(value)
        val key = AdhkarKeys.target(itemId, catalogCount)
        if (v == catalogCount) c.settings.reset(key) else c.settings.set(key, v)
    }

    suspend fun savePosition(categoryId: String, itemId: String, date: String) {
        c.settings.set(AdhkarKeys.position(categoryId), AdhkarPosition(date, itemId, c.clock.nowMillis()).encode())
    }

    companion object {
        fun markId(type: AdhkarMarkType, itemId: String) = "${type.name}_$itemId"

        fun todayFlow(clock: Clock): Flow<String> = flow {
            while (true) {
                val now = clock.nowMillis()
                val today = clock.today()
                emit(today.toString())
                val next = today.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
                // نصحو بعد منتصف الليل بقليل؛ حد أدنى لتفادي الدوران السريع إن اضطربت الساعة.
                delay((next - now).coerceIn(1_000L, 24 * 3_600_000L) + 500L)
            }
        }.distinctUntilChanged()
    }
}
