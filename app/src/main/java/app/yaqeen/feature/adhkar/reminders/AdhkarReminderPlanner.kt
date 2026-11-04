package app.yaqeen.feature.adhkar.reminders

import app.yaqeen.domain.model.AdhkarCategoryId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** تخطيط تذكيرات الأذكار (خالص، قابل للاختبار): يقرر ماذا يُجدوَل ومتى دون لمس أندرويد. */
object AdhkarReminderPlanner {
    const val KEY_PREFIX = "adhkar:"
    const val DAYS_AHEAD = 3

    /** إعداد تذكير قسم واحد. */
    data class Slot(val categoryId: String, val enabled: Boolean, val time: String, val defaultTime: String)

    data class Planned(val key: String, val categoryId: String, val date: LocalDate, val triggerAtMillis: Long)

    fun key(categoryId: String, date: LocalDate) = "$KEY_PREFIX$categoryId:$date"

    /** "HH:mm" (يقبل أرقامًا عربية) أو الافتراضي إن كان النص غير صالح. */
    fun parseTime(raw: String, fallback: String): LocalTime {
        fun tryParse(s: String): LocalTime? {
            val latin = buildString {
                for (ch in s.trim()) append(if (ch in '٠'..'٩') '0' + (ch - '٠') else ch)
            }
            val parts = latin.split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return LocalTime.of(h, m)
        }
        return tryParse(raw) ?: tryParse(fallback) ?: LocalTime.of(9, 0)
    }

    /**
     * يخطط تذكيرات اليوم والأيام التالية (إجمالًا [DAYS_AHEAD] أيام).
     * @param completedByDate لكل تاريخ ISO مجموعة الأقسام المكتملة فيه؛ لا يُجدوَل تذكير لقسم أُنجز.
     */
    fun plan(
        nowMillis: Long,
        zone: ZoneId,
        today: LocalDate,
        slots: List<Slot>,
        completedByDate: Map<String, Set<String>>,
        daysAhead: Int = DAYS_AHEAD,
    ): List<Planned> {
        val out = ArrayList<Planned>()
        for (offset in 0 until daysAhead) {
            val date = today.plusDays(offset.toLong())
            for (slot in slots) {
                if (!slot.enabled) continue
                if (slot.categoryId in completedByDate[date.toString()].orEmpty()) continue
                val trigger = date.atTime(parseTime(slot.time, slot.defaultTime)).atZone(zone).toInstant().toEpochMilli()
                if (trigger <= nowMillis) continue
                out += Planned(key(slot.categoryId, date), slot.categoryId, date, trigger)
            }
        }
        return out
    }

    fun title(categoryId: String): String = when (categoryId) {
        AdhkarCategoryId.MORNING -> "أذكار الصباح"
        AdhkarCategoryId.EVENING -> "أذكار المساء"
        AdhkarCategoryId.SLEEP -> "أذكار النوم"
        AdhkarCategoryId.WAKING -> "أذكار الاستيقاظ"
        AdhkarCategoryId.AFTER_PRAYER -> "أذكار بعد الصلاة"
        else -> "الأذكار"
    }

    fun body(categoryId: String): String = when (categoryId) {
        AdhkarCategoryId.MORNING -> "حان وقت أذكار الصباح، دقائق هادئة تبدأ بها يومك."
        AdhkarCategoryId.EVENING -> "حان وقت أذكار المساء، دقائق هادئة تختم بها نهارك."
        AdhkarCategoryId.SLEEP -> "حان وقت أذكار النوم، اختم يومك بذكر الله."
        else -> "حان وقت الأذكار، دقائق هادئة مع ذكر الله."
    }
}
