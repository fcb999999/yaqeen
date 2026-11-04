package app.yaqeen.feature.prayer.tracking

import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.domain.model.Prayer
import java.time.LocalDate
import java.time.ZonedDateTime

/*
 * منطق خالص للعرض: الصلاة الحالية/القادمة، تنسيق الوقت المتبقي، ونصوص الإحصاءات الهادئة المشجّعة.
 * (بلا Android ليسهل اختباره.)
 */

/** موضع الوقت الحالي بين صلوات اليوم. */
data class PrayerTimeline(
    /** آخر صلاة دخل وقتها اليوم (null قبل الفجر). */
    val current: Prayer?,
    /** الصلاة القادمة، وبعد العشاء فجر الغد إن توفرت مواقيته. */
    val next: Prayer?,
    val nextTime: ZonedDateTime?,
    /** الدقائق المتبقية للقادمة (تُقرَّب للأعلى). */
    val minutesToNext: Long?,
)

fun computeTimeline(
    times: Map<Prayer, ZonedDateTime>,
    tomorrowFajr: ZonedDateTime?,
    now: ZonedDateTime,
): PrayerTimeline {
    val ordered = Prayer.entries.mapNotNull { p -> times[p]?.let { p to it } }
    val current = ordered.lastOrNull { !it.second.isAfter(now) }?.first
    val upcoming = ordered.firstOrNull { it.second.isAfter(now) }
    val (next, nextTime) = when {
        upcoming != null -> upcoming.first to upcoming.second
        tomorrowFajr != null && tomorrowFajr.isAfter(now) -> Prayer.FAJR to tomorrowFajr
        else -> null to null
    }
    val minutes = nextTime?.let { (java.time.Duration.between(now, it).seconds + 59) / 60 }
    return PrayerTimeline(current, next, nextTime, minutes)
}

object PrayerFormat {
    private fun plural(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "${ArabicText.indic(n)} $few"
        else -> "${ArabicText.indic(n)} $many"
    }

    private fun hoursText(h: Int) = plural(h, "ساعة", "ساعتان", "ساعات", "ساعة")
    private fun minutesText(m: Int) = plural(m, "دقيقة", "دقيقتان", "دقائق", "دقيقة")

    /** مثل: "ساعتان و١٠ دقائق" أو "٥ دقائق" أو "أقل من دقيقة". */
    fun remaining(totalMinutes: Long): String {
        if (totalMinutes < 1) return "أقل من دقيقة"
        val h = (totalMinutes / 60).toInt()
        val m = (totalMinutes % 60).toInt()
        return when {
            h == 0 -> minutesText(m)
            m == 0 -> hoursText(h)
            else -> "${hoursText(h)} و${minutesText(m)}"
        }
    }

    /** وقت بنظام ١٢ ساعة بأرقام هندية، مثل "٤:٣٢ ص". يستعمل منطقة الوقت نفسها للقيمة. */
    fun clock12(time: ZonedDateTime): String {
        val h24 = time.hour
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val suffix = if (h24 < 12) "ص" else "م"
        return "${ArabicText.indic(h12)}:${ArabicText.indic("%02d".format(time.minute))} $suffix"
    }

    /** "اليوم" / "أمس" / "الأحد ٣ نوفمبر" (مع السنة إن اختلفت عن سنة [today]). */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "اليوم"
        today.minusDays(1) -> "أمس"
        else -> shortDate(date, today)
    }

    fun shortDate(date: LocalDate, today: LocalDate): String {
        val base = "${HijriCalendar.dayName(date)} ${ArabicText.indic(date.dayOfMonth)} ${HijriCalendar.GREGORIAN_MONTHS[date.monthValue - 1]}"
        return if (date.year == today.year) base else "$base ${ArabicText.indic(date.year)}"
    }

    fun percent(p: Int): String = "${ArabicText.indic(p)}٪"

    /** مثل: "سجّلتَ ٣٢ من ٣٥ صلاة". */
    fun coverage(recorded: Int, possible: Int): String =
        "سجّلتَ ${ArabicText.indic(recorded)} من ${ArabicText.indic(possible)} صلاة"

    fun rakaat(n: Int): String = when (n) {
        0 -> "لا ركعات"
        1 -> "ركعة واحدة"
        2 -> "ركعتان"
        in 3..10 -> "${ArabicText.indic(n)} ركعات"
        else -> "${ArabicText.indic(n)} ركعة"
    }

    fun days(n: Int): String = when (n) {
        0 -> "لا أيام"
        1 -> "يوم واحد"
        2 -> "يومان"
        in 3..10 -> "${ArabicText.indic(n)} أيام"
        else -> "${ArabicText.indic(n)} يومًا"
    }

    /**
     * عبارة هادئة تعليقًا على الفترة. لا ترتيب ولا نقاط ولا لوم: عبارة واحدة، ولا تُوصف النتيجة بالتقصير أبدًا،
     * وغير المسجّل لا يُعامل كأنه لم يُؤدَّ.
     */
    fun encouragement(stats: PrayerRangeStats): String {
        val jamaa = stats.jamaaPercent
        return when {
            !stats.hasRecords ->
                "لم تُسجَّل صلوات في هذه الفترة. التسجيل اختياري، وما لم تسجّله لا يُحتسب تقصيرًا."
            stats.coverage < 0.5f ->
                "كلما سجّلتَ أكثر صارت الصورة أوضح لك. وتذكّر أن ما لم تسجّله لا يُعدّ تقصيرًا."
            jamaa != null && jamaa >= 80 ->
                "ما شاء الله، حافظتَ على الجماعة في معظم صلواتك المسجّلة. بارك الله فيك."
            jamaa != null && jamaa >= 50 ->
                "أحسنتَ، واصل بإذن الله؛ ولك في كل صلاة جماعة فرصة جديدة."
            jamaa != null ->
                "الحمد لله على محافظتك على الصلاة. ويمكنك الارتقاء خطوةً خطوة، ولو بصلاة جماعة إضافية في الأسبوع."
            else ->
                "كل يوم صفحة جديدة وباب العودة مفتوح. ابدأ بالصلاة القادمة بإذن الله."
        }
    }
}
