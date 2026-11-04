package app.yaqeen.feature.quran.plans

import app.yaqeen.core.ArabicText
import java.time.LocalDate

/** أسماء السور للعرض: بلا تشكيل (الاسم في بيانات المصحف مشكَّل). */
object SurahNames {
    private val MARKS = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640]")
    fun plain(nameAr: String): String = nameAr.replace("آ", "آ").replace(MARKS, "").replace('ٱ', 'ا').trim()
}

/** تنسيق التواريخ والأوقات بالعربية (دوال خالصة قابلة للاختبار). */
object PlanFormat {
    private val MONTHS = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    /** "الأربعاء ٤ نوفمبر ٢٠٢٦" */
    fun dateLong(d: LocalDate): String =
        "${DAYS[d.dayOfWeek.value - 1]} ${ArabicText.indic(d.dayOfMonth)} ${MONTHS[d.monthValue - 1]} ${ArabicText.indic(d.year)}"

    /** "٤ نوفمبر ٢٠٢٦" */
    fun dateShort(d: LocalDate): String = "${ArabicText.indic(d.dayOfMonth)} ${MONTHS[d.monthValue - 1]} ${ArabicText.indic(d.year)}"

    /** "٠٥:٣٠" ثم ص/م: "٥:٣٠ ص" */
    fun time12(hhmm: String): String {
        val (h, m) = PlanMath.parseTime(hhmm) ?: return ArabicText.indic(hhmm)
        val suffix = if (h < 12) "ص" else "م"
        val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
        return "${ArabicText.indic(h12)}:${ArabicText.indic(String.format(java.util.Locale.US, "%02d", m))} $suffix"
    }

    /** يحوّل الأرقام الهندية/الفارسية المكتوبة إلى لاتينية لتحليل حقول الأرقام. */
    fun toLatinDigits(s: String): String = buildString {
        for (ch in s) append(
            when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch
            },
        )
    }
}
