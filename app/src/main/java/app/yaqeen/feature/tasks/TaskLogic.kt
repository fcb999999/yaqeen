package app.yaqeen.feature.tasks

import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.TaskEntity
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.QuranPlanStatus
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.domain.model.TaskLink
import app.yaqeen.domain.model.TaskPriority
import app.yaqeen.domain.model.TaskRepeat
import java.time.LocalDate

// =====================================================================================
// قواعد التكرار والتواريخ (دوال خالصة)
// =====================================================================================

/** بت يوم الأسبوع: الاثنين = 1 shl 0 ... الأحد = 1 shl 6. */
fun dayBit(date: LocalDate): Int = 1 shl (date.dayOfWeek.value - 1)

const val ALL_DAYS_MASK = 127

/**
 * هل تسري المهمة في هذا اليوم؟
 *  - المحذوفة/المؤرشفة لا تسري، ولا تسري قبل تاريخ البداية ولا بعد تاريخ النهاية (إن وُجد).
 *  - NONE: يوم البداية فقط. DAILY: كل يوم في المدى (يتجاهل daysMask). WEEKLY: الأيام المحددة في daysMask،
 *    وإن كانت القناع صفرًا فيوم الأسبوع الموافق ليوم البداية.
 */
fun isTaskActiveOn(task: TaskEntity, date: LocalDate): Boolean {
    if (task.deleted || task.archived) return false
    val start = ArabicFormat.parseDate(task.startDate) ?: return false
    if (date.isBefore(start)) return false
    ArabicFormat.parseDate(task.endDate)?.let { if (date.isAfter(it)) return false }
    return when (task.repeat) {
        TaskRepeat.NONE.name -> date == start
        TaskRepeat.WEEKLY.name -> {
            val mask = if ((task.daysMask and ALL_DAYS_MASK) == 0) dayBit(start) else task.daysMask
            (mask and dayBit(date)) != 0
        }
        else -> true
    }
}

fun TaskEntity.linkEnum(): TaskLink = runCatching { TaskLink.valueOf(link) }.getOrDefault(TaskLink.NONE)
fun TaskEntity.repeatEnum(): TaskRepeat = runCatching { TaskRepeat.valueOf(repeat) }.getOrDefault(TaskRepeat.DAILY)
fun TaskEntity.priorityEnum(): TaskPriority = TaskPriority.values().getOrElse(priority) { TaskPriority.NORMAL }

/** معرّف قسم الأذكار المرتبط بالمهمة، أو null إن لم تكن مرتبطة بأذكار. */
fun adhkarCategoryFor(link: TaskLink): String? = when (link) {
    TaskLink.ADHKAR_MORNING -> AdhkarCategoryId.MORNING
    TaskLink.ADHKAR_EVENING -> AdhkarCategoryId.EVENING
    TaskLink.ADHKAR_SLEEP -> AdhkarCategoryId.SLEEP
    TaskLink.ADHKAR_AFTER_PRAYER -> AdhkarCategoryId.AFTER_PRAYER
    else -> null
}

// =====================================================================================
// اشتقاق الإنجاز من سجلات العبادة (لا علامة يدوية للمهام المرتبطة)
// =====================================================================================

/** نتيجة اشتقاق: هل أُنجزت، ونص تفصيلي للعرض (قد يكون null). */
data class Derived(val done: Boolean, val detail: String?)

object DerivedCompletion {
    private val PRAYER_NAMES = Prayer.values().map { it.name }.toSet()
    private val SUNNAH_NAMES = Sunnah.values().map { it.name }.toSet()

    /** الصلوات الخمس: كل صلاة بحالة MOSQUE أو HOME (لا يكفي NOT_PRAYED ولا غياب السجل). */
    fun prayers(logs: List<PrayerLogEntity>): Derived {
        val ok = logs.asSequence()
            .filter { !it.deleted && it.prayer in PRAYER_NAMES }
            .filter { it.status == PrayerStatus.MOSQUE.name || it.status == PrayerStatus.HOME.name }
            .map { it.prayer }.toSet()
        return Derived(ok.size == PRAYER_NAMES.size, "${ArabicText.indic(ok.size)} من ${ArabicText.indic(PRAYER_NAMES.size)} صلوات")
    }

    /** السنن الرواتب: السنن الافتراضية الخمس كلها done (تُتجاهل المخصصة). */
    fun rawatib(logs: List<RawatibLogEntity>): Derived {
        val ok = logs.asSequence()
            .filter { !it.deleted && it.done && it.sunnahId in SUNNAH_NAMES }
            .map { it.sunnahId }.toSet()
        return Derived(ok.size == SUNNAH_NAMES.size, "${ArabicText.indic(ok.size)} من ${ArabicText.indic(SUNNAH_NAMES.size)} سنن")
    }

    /** أذكار قسم: كل ذكر في القسم completed=true في ذلك اليوم. [itemIds] = أذكار القسم من الكتالوج (null/فارغ = غير معروف). */
    fun adhkar(itemIds: List<String>?, progress: List<AdhkarProgressEntity>): Derived {
        if (itemIds.isNullOrEmpty()) return Derived(false, null)
        val completed = progress.asSequence().filter { !it.deleted && it.completed }.map { it.itemId }.toSet()
        val n = itemIds.count { it in completed }
        return Derived(n == itemIds.size, "${ArabicText.indic(n)} من ${ArabicText.indic(itemIds.size)} أذكار")
    }

    /**
     * الورد القرآني: سجل الورد في ذلك اليوم targetMet. إن حُدّد [linkRefId] فهذا الورد بعينه،
     * وإلا فأي ورد (نشط أو سجّل بلوغ المقدار في ذلك اليوم).
     */
    fun quran(plans: List<QuranPlanEntity>, logs: List<QuranPlanLogEntity>, linkRefId: String): Derived {
        val livePlans = plans.filter { !it.deleted }
        val liveLogs = logs.filter { !it.deleted }
        if (linkRefId.isNotBlank()) {
            val plan = livePlans.firstOrNull { it.id == linkRefId } ?: return Derived(false, "الورد المرتبط غير موجود")
            val log = liveLogs.firstOrNull { it.planId == plan.id }
            return if (log?.targetMet == true) Derived(true, "تم بلوغ المقدار اليومي") else Derived(false, notMetText(plan, log))
        }
        val planIds = livePlans.map { it.id }.toSet()
        val met = liveLogs.any { it.planId in planIds && it.targetMet }
        if (met) return Derived(true, "تم بلوغ المقدار اليومي")
        val active = livePlans.filter { it.status == QuranPlanStatus.ACTIVE.name }
        return when {
            active.isEmpty() -> Derived(false, "لا يوجد ورد نشط")
            active.size == 1 -> Derived(false, notMetText(active[0], liveLogs.firstOrNull { it.planId == active[0].id }))
            else -> Derived(false, "لم يُبلغ المقدار اليومي بعد")
        }
    }

    private fun notMetText(plan: QuranPlanEntity, log: QuranPlanLogEntity?): String {
        val unit = runCatching { QuranPlanUnit.valueOf(plan.unit) }.getOrDefault(QuranPlanUnit.PAGE)
        val daily = when (unit) {
            QuranPlanUnit.PAGE -> ArabicFormat.pages(plan.dailyAmount)
            QuranPlanUnit.AYAH -> ArabicFormat.ayahs(plan.dailyAmount)
            QuranPlanUnit.JUZ -> ArabicFormat.juz(plan.dailyAmount)
        }
        val read = when (unit) {
            QuranPlanUnit.PAGE -> log?.pagesRead ?: 0
            QuranPlanUnit.AYAH -> log?.ayahsRead ?: 0
            QuranPlanUnit.JUZ -> 0
        }
        return buildString {
            append("المقدار اليومي: ").append(daily)
            if (read > 0) append(" · المقروء ").append(ArabicText.indic(read))
        }
    }
}

/** تقييم مهمة ليوم معيّن: يدوية (من إنجازات المهام) أو مشتقة (من سجل العبادة). دالة خالصة. */
fun evaluateTask(
    task: TaskEntity,
    manualDoneTaskIds: Set<String>,
    worship: WorshipDay,
    adhkarItemIds: (String) -> List<String>?,
): TodayTask {
    val link = task.linkEnum()
    val d: Derived? = when (link) {
        TaskLink.NONE -> null
        TaskLink.PRAYERS -> DerivedCompletion.prayers(worship.prayers)
        TaskLink.RAWATIB -> DerivedCompletion.rawatib(worship.rawatib)
        TaskLink.QURAN_PLAN -> DerivedCompletion.quran(worship.plans, worship.quranLogs, task.linkRefId)
        TaskLink.ADHKAR_MORNING, TaskLink.ADHKAR_EVENING, TaskLink.ADHKAR_SLEEP, TaskLink.ADHKAR_AFTER_PRAYER -> {
            val cat = adhkarCategoryFor(link)!!
            DerivedCompletion.adhkar(adhkarItemIds(cat), worship.adhkar.filter { it.categoryId == cat })
        }
    }
    return if (d == null) TodayTask(task, done = task.id in manualDoneTaskIds, derived = false, detail = null)
    else TodayTask(task, done = d.done, derived = true, detail = d.detail)
}

/** الترتيب: ذات الوقت أولًا بالتسلسل الزمني، ثم بلا وقت حسب الأولوية (الأعلى أولًا) ثم ترتيب المستخدم. */
fun sortTodayTasks(list: List<TodayTask>): List<TodayTask> =
    list.sortedWith(
        compareBy<TodayTask> { it.task.timeOfDay?.let { t -> ArabicFormat.parseTime(t) }?.toSecondOfDay() ?: Int.MAX_VALUE }
            .thenByDescending { it.task.priority }
            .thenBy { it.task.sortOrder }
            .thenBy { it.task.createdAt }
            .thenBy { it.task.id },
    )

// =====================================================================================
// المهام الجاهزة
// =====================================================================================

enum class TaskPreset(val titleAr: String, val descriptionAr: String, val link: TaskLink) {
    QURAN_WIRD("قراءة الورد", "تُحتسب تلقائيًا عند بلوغ مقدار ورد اليوم", TaskLink.QURAN_PLAN),
    ADHKAR_MORNING("أذكار الصباح", "تُحتسب تلقائيًا عند إتمام كل أذكار الصباح", TaskLink.ADHKAR_MORNING),
    ADHKAR_EVENING("أذكار المساء", "تُحتسب تلقائيًا عند إتمام كل أذكار المساء", TaskLink.ADHKAR_EVENING),
    ADHKAR_SLEEP("أذكار النوم", "تُحتسب تلقائيًا عند إتمام كل أذكار النوم", TaskLink.ADHKAR_SLEEP),
    PRAYERS("الصلوات الخمس", "تُحتسب تلقائيًا عند تسجيل الصلوات الخمس", TaskLink.PRAYERS),
    RAWATIB("السنن الرواتب", "تُحتسب تلقائيًا عند تسجيل السنن الرواتب الخمس", TaskLink.RAWATIB),
    BOOK("قراءة كتاب", "مهمة يدوية: تضع علامة الإنجاز بنفسك", TaskLink.NONE),
    PERSONAL("مهمة شخصية", "أنشئ مهمة بعنوانك ووقتك الذي تختاره", TaskLink.NONE);

    /** معرّف حتمي: لا تتكرر المهمة الجاهزة عند المزامنة بين الأجهزة (إلا الشخصية فلها معرّف عشوائي). */
    val taskId: String get() = "preset_$name"

    /** تُضاف بنقرة واحدة؟ (الشخصية تفتح المحرر). */
    val oneTap: Boolean get() = this != PERSONAL

    fun toEntity(today: LocalDate, sortOrder: Int, now: Long): TaskEntity = TaskEntity(
        id = taskId,
        title = titleAr,
        link = link.name,
        repeat = TaskRepeat.DAILY.name,
        daysMask = ALL_DAYS_MASK,
        startDate = today.toString(),
        preset = name,
        sortOrder = sortOrder,
        createdAt = now,
    )

    companion object {
        fun fromKey(key: String): TaskPreset? = values().firstOrNull { it.name == key }
    }
}

// =====================================================================================
// مسودة المحرر والتحقق منها
// =====================================================================================

data class TaskDraft(
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val timeOfDay: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val repeat: TaskRepeat = TaskRepeat.DAILY,
    val daysMask: Int = ALL_DAYS_MASK,
    val link: TaskLink = TaskLink.NONE,
    val linkRefId: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val reminderEnabled: Boolean = false,
    val preset: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

fun TaskEntity.toDraft(): TaskDraft = TaskDraft(
    id = id,
    title = title,
    notes = notes,
    timeOfDay = timeOfDay?.takeIf { ArabicFormat.parseTime(it) != null },
    priority = priorityEnum(),
    repeat = repeatEnum(),
    daysMask = if ((daysMask and ALL_DAYS_MASK) == 0) ALL_DAYS_MASK else daysMask and ALL_DAYS_MASK,
    link = linkEnum(),
    linkRefId = linkRefId,
    startDate = ArabicFormat.parseDate(startDate) ?: LocalDate.now(),
    endDate = ArabicFormat.parseDate(endDate),
    reminderEnabled = reminderEnabled,
    preset = preset,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

/** رسالة خطأ بالعربية إن كانت المسودة غير صالحة، وإلا null. */
fun validateDraft(d: TaskDraft): String? = when {
    d.title.isBlank() -> "اكتب عنوان المهمة"
    d.endDate != null && d.endDate.isBefore(d.startDate) -> "تاريخ النهاية قبل تاريخ البداية"
    d.repeat == TaskRepeat.WEEKLY && (d.daysMask and ALL_DAYS_MASK) == 0 -> "اختر يومًا واحدًا على الأقل"
    d.reminderEnabled && ArabicFormat.parseTime(d.timeOfDay) == null -> "حدّد وقت المهمة لتفعيل التذكير"
    else -> null
}

/** يحوّل المسودة إلى كيان بعد تطبيع الحقول المتعارضة (مثل مرجع الورد لغير الورد). */
fun TaskDraft.toEntity(newId: String, now: Long): TaskEntity {
    val time = ArabicFormat.parseTime(timeOfDay)?.let { ArabicFormat.formatHHmm(it.hour, it.minute) }
    return TaskEntity(
        id = id ?: newId,
        title = title.trim(),
        notes = notes.trim(),
        timeOfDay = time,
        priority = priority.ordinal,
        repeat = repeat.name,
        daysMask = if (repeat == TaskRepeat.WEEKLY) daysMask and ALL_DAYS_MASK else ALL_DAYS_MASK,
        link = link.name,
        linkRefId = if (link == TaskLink.QURAN_PLAN) linkRefId else "",
        startDate = startDate.toString(),
        endDate = if (repeat == TaskRepeat.NONE) null else endDate?.toString(),
        reminderEnabled = reminderEnabled && time != null,
        preset = preset,
        sortOrder = sortOrder,
        createdAt = if (createdAt == 0L) now else createdAt,
    )
}

// =====================================================================================
// الرسائل التحفيزية الهادئة (بلا نقاط ولا سلاسل ولا ألعاب)
// =====================================================================================

object TaskMessages {
    private val EMPTY = listOf(
        "لا مهام لهذا اليوم. أضف ما يعينك على الثبات بهدوء.",
        "يومٌ خفيف بلا مهام، وإن شئت فأضف ما ينفعك.",
    )
    private val NONE_DONE = listOf(
        "ابدأ بخطوة صغيرة؛ فقليلٌ دائم خير من كثيرٍ منقطع.",
        "على بركة الله، ابدأ بأيسر مهمة.",
        "كل بداية صغيرة تُقرّبك، فتوكّل على الله وابدأ.",
    )
    private val PARTIAL = listOf(
        "بارك الله في وقتك، وما تيسّر لك اليوم فهو خير.",
        "خطوة بعد خطوة، وما بقي قليل بإذن الله.",
        "أنجزتَ جزءًا طيبًا، فأتمّ ما بقي على مهل.",
    )
    private val ALL_DONE = listOf(
        "تمّت مهام اليوم، تقبّل الله منك.",
        "الحمد لله على التوفيق، تقبّل الله طاعتك.",
        "أتممتَ ما أردتَ اليوم، فاحمد الله واسأله القبول.",
    )

    /** [seed] غالبًا epochDay لثبات الرسالة خلال اليوم الواحد مع تنوّعها بين الأيام. */
    fun motivation(progress: DayProgress, seed: Long): String {
        val pool = when {
            progress.total == 0 -> EMPTY
            progress.done == 0 -> NONE_DONE
            progress.done >= progress.total -> ALL_DONE
            else -> PARTIAL
        }
        return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
    }
}
