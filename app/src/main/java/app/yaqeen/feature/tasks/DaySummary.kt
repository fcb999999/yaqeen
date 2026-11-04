package app.yaqeen.feature.tasks

import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.QuranPlanStatus
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.domain.model.Sunnah
import java.time.LocalDate

/** قسم أذكار من الكتالوج (للملخص): معرّفه وعنوانه ومعرّفات أذكاره. */
data class AdhkarCategoryInfo(val id: String, val title: String, val itemIds: List<String>)

/** قسم أذكار لم يكتمل في اليوم. */
data class AdhkarGap(val categoryId: String, val title: String, val done: Int, val total: Int)

/** حالة ورد نشط في اليوم. */
data class WirdLine(
    val planId: String,
    val planName: String,
    val unit: QuranPlanUnit,
    val dailyAmount: Int,
    /** المقروء بوحدة الورد (للجزء غير متاح فيُترك 0). */
    val read: Int,
    val met: Boolean,
) {
    /** نص المتبقي، أو null إذا بلغ المقدار اليومي. */
    val remainingText: String?
        get() = when {
            met -> null
            unit == QuranPlanUnit.PAGE -> (dailyAmount - read).let { if (it > 0) "بقي ${ArabicFormat.pages(it)}" else "لم يُسجَّل بلوغ المقدار اليومي" }
            unit == QuranPlanUnit.AYAH -> (dailyAmount - read).let { if (it > 0) "بقي ${ArabicFormat.ayahs(it)}" else "لم يُسجَّل بلوغ المقدار اليومي" }
            else -> "المقدار اليومي ${ArabicFormat.juz(dailyAmount)}"
        }
}

/**
 * ملخص اليوم. القاعدة: ما لم يُسجَّل يُسمّى "غير مسجّل" وليس "فات" (غياب السجل لا يعني أن العبادة لم تُؤدَّ).
 */
data class DaySummary(
    val date: LocalDate,
    val tasksDone: List<TodayTask>,
    val tasksPending: List<TodayTask>,
    val prayersRecorded: Int,
    val prayersUnrecorded: List<Prayer>,
    val rawatibRecorded: Int,
    val rawatibUnrecorded: List<Sunnah>,
    val adhkarIncomplete: List<AdhkarGap>,
    val wird: List<WirdLine>,
) {
    val tasksTotal: Int get() = tasksDone.size + tasksPending.size
    val wirdRemaining: List<WirdLine> get() = wird.filter { !it.met }

    /** هل لا يوجد شيء غير مكتمل أو غير مسجّل؟ */
    val allClear: Boolean
        get() = tasksPending.isEmpty() && prayersUnrecorded.isEmpty() && rawatibUnrecorded.isEmpty() &&
            adhkarIncomplete.isEmpty() && wirdRemaining.isEmpty()
}

object DayEndSummary {
    /** أقسام الأذكار اليومية التي يعرضها الملخص. */
    val DAILY_ADHKAR = listOf("morning", "evening", "sleep")

    fun build(
        date: LocalDate,
        tasks: List<TodayTask>,
        worship: WorshipDay,
        adhkar: List<AdhkarCategoryInfo>,
    ): DaySummary {
        val prayedOk = worship.prayers.asSequence()
            .filter { !it.deleted && (it.status == PrayerStatus.MOSQUE.name || it.status == PrayerStatus.HOME.name) }
            .map { it.prayer }.toSet()
        val prayersUnrecorded = Prayer.values().filter { it.name !in prayedOk }

        val sunnahOk = worship.rawatib.asSequence().filter { !it.deleted && it.done }.map { it.sunnahId }.toSet()
        val rawatibUnrecorded = Sunnah.values().filter { it.name !in sunnahOk }

        val completedItems = worship.adhkar.asSequence().filter { !it.deleted && it.completed }.map { it.itemId }.toSet()
        val gaps = adhkar.filter { it.id in DAILY_ADHKAR && it.itemIds.isNotEmpty() }.mapNotNull { cat ->
            val n = cat.itemIds.count { it in completedItems }
            if (n < cat.itemIds.size) AdhkarGap(cat.id, cat.title, n, cat.itemIds.size) else null
        }

        val logsByPlan: Map<String, QuranPlanLogEntity> = worship.quranLogs.filter { !it.deleted }.associateBy { it.planId }
        val wird = worship.plans
            .filter { !it.deleted && it.status == QuranPlanStatus.ACTIVE.name }
            .map { wirdLine(it, logsByPlan[it.id]) }

        return DaySummary(
            date = date,
            tasksDone = tasks.filter { it.done },
            tasksPending = tasks.filter { !it.done },
            prayersRecorded = Prayer.values().size - prayersUnrecorded.size,
            prayersUnrecorded = prayersUnrecorded,
            rawatibRecorded = Sunnah.values().size - rawatibUnrecorded.size,
            rawatibUnrecorded = rawatibUnrecorded,
            adhkarIncomplete = gaps,
            wird = wird,
        )
    }

    private fun wirdLine(plan: QuranPlanEntity, log: QuranPlanLogEntity?): WirdLine {
        val unit = runCatching { QuranPlanUnit.valueOf(plan.unit) }.getOrDefault(QuranPlanUnit.PAGE)
        val read = when (unit) {
            QuranPlanUnit.PAGE -> log?.pagesRead ?: 0
            QuranPlanUnit.AYAH -> log?.ayahsRead ?: 0
            QuranPlanUnit.JUZ -> 0
        }
        return WirdLine(plan.id, plan.name, unit, plan.dailyAmount, read, log?.targetMet == true)
    }

    /** نص مختصر للملخص (لمشاركته أو لإشعار يُبنى وقت الإطلاق). */
    fun notificationText(s: DaySummary): String {
        if (s.allClear) return "ما شاء الله، لا شيء متبقٍّ لهذا اليوم."
        val parts = mutableListOf<String>()
        if (s.tasksTotal > 0) parts += "المهام: ${ArabicText.indic(s.tasksDone.size)} من ${ArabicText.indic(s.tasksTotal)}"
        if (s.prayersUnrecorded.isNotEmpty()) parts += "صلوات غير مسجّلة: ${ArabicText.indic(s.prayersUnrecorded.size)}"
        if (s.rawatibUnrecorded.isNotEmpty()) parts += "سنن غير مسجّلة: ${ArabicText.indic(s.rawatibUnrecorded.size)}"
        if (s.adhkarIncomplete.isNotEmpty()) parts += "أذكار غير مكتملة: " + s.adhkarIncomplete.joinToString("، ") { it.title }
        if (s.wirdRemaining.isNotEmpty()) parts += "ورد متبقٍّ"
        return if (parts.isEmpty()) "ما شاء الله، لا شيء متبقٍّ لهذا اليوم." else parts.joinToString(" – ")
    }

    /** نص الإشعار العام (لا يتقادم لأن الملخص الحي يُحسب عند فتح الشاشة). */
    const val GENERIC_NOTIFICATION_TITLE = "ملخص يومك"
    const val GENERIC_NOTIFICATION_TEXT = "ملخص يومك جاهز — اطّلع على إنجازاتك وما لم يُسجَّل بعد"
}
