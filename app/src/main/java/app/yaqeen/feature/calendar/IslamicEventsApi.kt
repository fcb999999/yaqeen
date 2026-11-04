package app.yaqeen.feature.calendar

import app.yaqeen.core.HijriCalendar
import app.yaqeen.core.HijriDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * === عقد المناسبات الإسلامية (يملكه منفذ "المهام والتقويم") ===
 * الرئيسية وتنبيهات المناسبات تعتمد على هذه الواجهة.
 * التواريخ تُحسب من تقويم أم القرى مع [adjustDays] (تعديل المستخدم لاختلاف رؤية الهلال).
 *
 * الحقول الإضافية (لها قيم افتراضية فلا تكسر من يستهلك العقد):
 *  - [endDate]: آخر يوم للمناسبة ذات المدى (أيام التشريق، الأيام البيض، العشر الأواخر، الأشهر الحرم)، وإلا null.
 *  - [note]: ملاحظة قصيرة للعرض.
 *  - [notifiable]: هل تُرسَل تنبيهات اقتراب لهذه المناسبة (بدايات الأشهر والأشهر الحرم لا تُنبَّه).
 */
data class IslamicEvent(
    /** مفتاح ثابت مثل RAMADAN_START, EID_FITR, EID_ADHA, ARAFAH, ASHURA, TARWIYAH, WHITE_DAYS, SACRED_MONTH, HIJRI_NEW_YEAR... */
    val key: String,
    val nameAr: String,
    /** أول يوم للمناسبة (ميلادي، بعد تطبيق التعديل). */
    val date: LocalDate,
    val hijri: HijriDate,
    /** الأيام المتبقية من [from] (0 = اليوم). */
    val daysUntil: Int,
    val endDate: LocalDate? = null,
    val note: String = "",
    val notifiable: Boolean = true,
) {
    val isRange: Boolean get() = endDate != null && endDate != date

    /** هل تقع هذه المناسبة في هذا اليوم (تشمل أيام المدى)؟ */
    fun covers(d: LocalDate): Boolean = !d.isBefore(date) && !d.isAfter(endDate ?: date)
}

/** مفاتيح المناسبات الثابتة. */
object IslamicEventKeys {
    const val HIJRI_NEW_YEAR = "HIJRI_NEW_YEAR"
    const val TASUA = "TASUA"
    const val ASHURA = "ASHURA"
    const val RAMADAN_START = "RAMADAN_START"
    const val LAST_TEN_RAMADAN = "LAST_TEN_RAMADAN"
    const val LAYLAT_AL_QADR = "LAYLAT_AL_QADR"
    const val EID_FITR = "EID_FITR"
    const val TARWIYAH = "TARWIYAH"
    const val ARAFAH = "ARAFAH"
    const val EID_ADHA = "EID_ADHA"
    const val TASHREEQ = "TASHREEQ"
    const val WHITE_DAYS = "WHITE_DAYS"
    const val SACRED_MONTH = "SACRED_MONTH"
    const val MONTH_START = "MONTH_START"

    /** ترتيب العرض عند تشابه اليوم (الأهم أولًا). */
    val ORDER = listOf(
        EID_FITR, EID_ADHA, ARAFAH, TARWIYAH, RAMADAN_START, ASHURA, TASUA, HIJRI_NEW_YEAR, LAYLAT_AL_QADR,
        LAST_TEN_RAMADAN, TASHREEQ, WHITE_DAYS, SACRED_MONTH, MONTH_START,
    )
}

/**
 * المناسبات الإسلامية من تقويم أم القرى (JDK HijrahChronology). كلها دوال خالصة.
 *
 * ملاحظات علمية: تقويم أم القرى حسابي وقد يختلف عن رؤية الهلال في بلد المستخدم بيوم؛ لذلك يوجد التعديل اليدوي (-٢..+٢).
 * لا تُضاف المولد ولا الإسراء والمعراج لعدم ثبوت تاريخ معتمد بإجماع.
 */
object IslamicEvents {
    private const val MAX_MONTHS_SCAN = 40

    /** أقرب المناسبات القادمة ابتداءً من [from] (تشمل اليوم)، مرتبة بالتاريخ. تُحسب بحسب يوم البداية للمناسبات ذات المدى. */
    fun upcoming(from: LocalDate, adjustDays: Int = 0, limit: Int = 5): List<IslamicEvent> {
        if (limit <= 0) return emptyList()
        val out = mutableListOf<IslamicEvent>()
        val h = runCatching { HijriCalendar.fromGregorian(from, adjustDays) }.getOrNull() ?: return emptyList()
        var y = h.year
        var m = h.month
        var scanned = 0
        while (out.size < limit && scanned++ < MAX_MONTHS_SCAN) {
            val evs = runCatching { monthEvents(y, m, adjustDays) }.getOrNull() ?: break
            out += evs.filter { !it.date.isBefore(from) }
            if (m == 12) { y++; m = 1 } else m++
        }
        return out.sortedWith(ORDERING).take(limit).map { it.copy(daysUntil = ChronoUnit.DAYS.between(from, it.date).toInt()) }
    }

    /** مناسبات يوم معيّن (تشمل المناسبات ذات المدى التي يقع اليوم ضمنها). daysUntil = 0. */
    fun on(date: LocalDate, adjustDays: Int = 0): List<IslamicEvent> {
        val h = runCatching { HijriCalendar.fromGregorian(date, adjustDays) }.getOrNull() ?: return emptyList()
        val evs = runCatching { monthEvents(h.year, h.month, adjustDays) }.getOrNull() ?: return emptyList()
        return evs.filter { it.covers(date) }.sortedWith(ORDERING).map { it.copy(daysUntil = 0) }
    }

    /** المناسبات التي يبدأ يومها الأول بين [from] و[to] (شاملتين)، مرتبة. daysUntil نسبةً إلى [from]. */
    fun startingBetween(from: LocalDate, to: LocalDate, adjustDays: Int = 0): List<IslamicEvent> {
        if (to.isBefore(from)) return emptyList()
        val h1 = runCatching { HijriCalendar.fromGregorian(from, adjustDays) }.getOrNull() ?: return emptyList()
        val h2 = runCatching { HijriCalendar.fromGregorian(to, adjustDays) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<IslamicEvent>()
        var y = h1.year
        var m = h1.month
        var scanned = 0
        while ((y < h2.year || (y == h2.year && m <= h2.month)) && scanned++ < MAX_MONTHS_SCAN) {
            val evs = runCatching { monthEvents(y, m, adjustDays) }.getOrNull() ?: break
            out += evs.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            if (m == 12) { y++; m = 1 } else m++
        }
        return out.sortedWith(ORDERING).map { it.copy(daysUntil = ChronoUnit.DAYS.between(from, it.date).toInt()) }
    }

    private val ORDERING = compareBy<IslamicEvent> { it.date }.thenBy {
        IslamicEventKeys.ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i }
    }

    private val SACRED_MONTHS = setOf(1, 7, 11, 12)

    /** اسم الشهر مضافًا إليه "شهر" (ذو القعدة/ذو الحجة تُجرّان: شهر ذي القعدة). */
    fun monthNameAfterShahr(month: Int): String = when (month) {
        11 -> "ذي القعدة"
        12 -> "ذي الحجة"
        else -> HijriCalendar.MONTH_NAMES[month - 1]
    }

    /** كل مناسبات شهر هجري معيّن (التواريخ ميلادية بعد التعديل، daysUntil مؤقتة = 0). */
    internal fun monthEvents(year: Int, month: Int, adj: Int): List<IslamicEvent> {
        val len = HijriCalendar.lengthOfMonth(year, month)
        val out = mutableListOf<IslamicEvent>()

        fun add(key: String, name: String, startDay: Int, endDay: Int? = null, note: String = "", notifiable: Boolean = true) {
            val s = HijriCalendar.toGregorian(HijriDate(year, month, startDay), adj)
            val e = endDay?.let { HijriCalendar.toGregorian(HijriDate(year, month, minOf(it, len)), adj) }
            out += IslamicEvent(key, name, s, HijriDate(year, month, startDay), 0, e, note, notifiable)
        }

        when (month) {
            1 -> {
                add(IslamicEventKeys.HIJRI_NEW_YEAR, "رأس السنة الهجرية", 1)
                add(IslamicEventKeys.TASUA, "تاسوعاء", 9, note = "يُستحب صيامه مع عاشوراء")
                add(IslamicEventKeys.ASHURA, "يوم عاشوراء", 10, note = "يُستحب صيامه، ويُستحب أن يُصام معه يوم قبله أو بعده")
            }
            9 -> {
                add(IslamicEventKeys.RAMADAN_START, "بداية شهر رمضان", 1, note = "يثبت دخول الشهر برؤية الهلال، وقد يختلف عن التقويم بيوم")
                add(IslamicEventKeys.LAST_TEN_RAMADAN, "العشر الأواخر من رمضان", 21, len)
                add(
                    IslamicEventKeys.LAYLAT_AL_QADR, "ليلة القدر", 27,
                    note = "تُلتمس في الوتر من العشر الأواخر (٢١، ٢٣، ٢٥، ٢٧، ٢٩)، وتُرجى ليلة ٢٧ عند كثير من أهل العلم، وتبدأ ليلتها بعد غروب شمس اليوم السابق",
                )
            }
            10 -> add(IslamicEventKeys.EID_FITR, "عيد الفطر", 1, note = "لا يُصام هذا اليوم")
            12 -> {
                add(IslamicEventKeys.TARWIYAH, "يوم التروية", 8)
                add(IslamicEventKeys.ARAFAH, "يوم عرفة", 9, note = "يُستحب صيامه لغير الحاج")
                add(IslamicEventKeys.EID_ADHA, "عيد الأضحى", 10, note = "لا يُصام هذا اليوم")
                add(IslamicEventKeys.TASHREEQ, "أيام التشريق", 11, 13, note = "أيام أكل وشرب وذكر لله، ولا تُصام")
            }
        }

        add(
            IslamicEventKeys.WHITE_DAYS, "الأيام البيض", 13, 15,
            note = if (month == 12) "يُستحب صيامها من كل شهر هجري، ويستثنى يوم ١٣ ذي الحجة لأنه من أيام التشريق" else "يُستحب صيامها من كل شهر هجري",
        )
        if (month in SACRED_MONTHS) {
            add(
                IslamicEventKeys.SACRED_MONTH, "شهر ${monthNameAfterShahr(month)} من الأشهر الحرم", 1, len,
                note = "الأشهر الحرم: ذو القعدة وذو الحجة ومحرم ورجب", notifiable = false,
            )
        }
        if (month != 1 && month != 9 && month != 10) {
            add(IslamicEventKeys.MONTH_START, "بداية شهر ${monthNameAfterShahr(month)}", 1, notifiable = false)
        }
        return out
    }
}
