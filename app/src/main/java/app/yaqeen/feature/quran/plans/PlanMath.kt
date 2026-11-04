package app.yaqeen.feature.quran.plans

import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.domain.model.QuranPlanUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** مقطع من الورد اليومي: من آية إلى آية (معرّفات عامة 1..6236، شاملة الطرفين). */
data class Portion(val fromAyah: Int, val toAyah: Int)

/** حالة يوم في سجل الالتزام. لا يوجد "تقصير": غير المسجَّل محايد. */
enum class DayStatus { MET, PARTIAL, UNRECORDED }

/**
 * حسابات الأوراد (دوال خالصة بلا Android) فوق [QuranData].
 *
 * قواعد مضبوطة هنا:
 *  - المدى دائمًا بمعرّفات الآيات العامة [startAyahId, endAyahId]؛ progressAyahId = آخر آية أُنجزت (startAyahId-1 عند البداية).
 *  - وحدة الورد (صفحة/جزء/آية) تحدّد "حدود" المقطع اليومي: المقطع يبدأ من أول آية غير مقروءة وينتهي بنهاية
 *    الوحدة رقم (المقدار) محسوبةً من وحدة تلك الآية، ولا يتجاوز نهاية الورد. فإن بدأ الورد في منتصف صفحة،
 *    يكون مقطع أول يوم بقية تلك الصفحة ثم بقية الصفحات المطلوبة.
 *  - بداية اليوم = progress - آيات اليوم المنجزة (من سجل اليوم)، فيبقى مقطع اليوم ثابتًا طوال اليوم ولو أُنجز.
 *  - تحقق المقدار اليومي: progress >= نهاية مقطع اليوم (أو اكتمال الورد).
 *  - نسبة الإنجاز تُحسب بحجم النص (عدد الحروف) لا بعدد الآيات، فتعكس حجم القراءة فعلًا.
 */
class PlanMath(val q: QuranData) {
    private val pageCount = q.pageCount
    private val pageFirst = IntArray(pageCount + 2)
    private val pageLast = IntArray(pageCount + 2)
    private val juzFirst = IntArray(32)
    private val juzLast = IntArray(32)

    /** مجموع تراكمي لأطوال نصوص الآيات: weightPrefix[i] = مجموع أطوال الآيات 1..i. */
    private val weightPrefix = LongArray(q.totalAyahs + 1)

    init {
        q.ayahs.forEach { a ->
            if (pageFirst[a.page] == 0) pageFirst[a.page] = a.id
            pageLast[a.page] = a.id
            if (juzFirst[a.juz] == 0) juzFirst[a.juz] = a.id
            juzLast[a.juz] = a.id
            weightPrefix[a.id] = weightPrefix[a.id - 1] + a.text.length.coerceAtLeast(1)
        }
    }

    val totalAyahs: Int get() = q.totalAyahs

    // ------------------------------------------------------------ تحويل الوحدات

    fun unitOf(unit: QuranPlanUnit, ayahId: Int): Int = when (unit) {
        QuranPlanUnit.PAGE -> q.ayah(ayahId).page
        QuranPlanUnit.JUZ -> q.ayah(ayahId).juz
        QuranPlanUnit.AYAH -> ayahId
    }

    /** آخر آية في الوحدة ذات الرقم [u]. */
    fun unitLast(unit: QuranPlanUnit, u: Int): Int = when (unit) {
        QuranPlanUnit.PAGE -> pageLast[u.coerceIn(1, pageCount)]
        QuranPlanUnit.JUZ -> juzLast[u.coerceIn(1, 30)]
        QuranPlanUnit.AYAH -> u.coerceIn(1, q.totalAyahs)
    }

    fun firstAyahOfPage(page: Int): Int = pageFirst[page.coerceIn(1, pageCount)]
    fun lastAyahOfPage(page: Int): Int = pageLast[page.coerceIn(1, pageCount)]
    fun firstAyahOfJuz(juz: Int): Int = juzFirst[juz.coerceIn(1, 30)]
    fun lastAyahOfJuz(juz: Int): Int = juzLast[juz.coerceIn(1, 30)]
    fun firstAyahOfSurah(surah: Int): Int = q.surah(surah).firstAyahId
    fun lastAyahOfSurah(surah: Int): Int = q.surah(surah).let { it.firstAyahId + it.ayahCount - 1 }

    /** عدد الوحدات التي يمسّها المدى [from, to] (الجزئية تُعدّ كاملة). */
    fun unitCount(unit: QuranPlanUnit, fromAyah: Int, toAyah: Int): Int {
        if (toAyah < fromAyah) return 0
        return unitOf(unit, toAyah) - unitOf(unit, fromAyah) + 1
    }

    fun totalUnits(plan: QuranPlanEntity): Int = unitCount(plan.unitEnum(), plan.startAyahId, plan.endAyahId)

    /** الوحدات المتبقية (تشمل الوحدة الجارية إن لم تُكمل). */
    fun remainingUnits(plan: QuranPlanEntity): Int =
        if (plan.progressAyahId >= plan.endAyahId) 0 else unitCount(plan.unitEnum(), plan.progressAyahId + 1, plan.endAyahId)

    fun remainingAyahs(plan: QuranPlanEntity): Int = (plan.endAyahId - plan.progressAyahId).coerceAtLeast(0)

    // ------------------------------------------------------------ المقطع اليومي

    /** آخر آية في مقطع يبدأ من [fromAyah] بمقدار [amount] من الوحدة، دون تجاوز [planEnd]. */
    fun portionEnd(unit: QuranPlanUnit, fromAyah: Int, amount: Int, planEnd: Int): Int {
        val a = amount.coerceAtLeast(1)
        val lastUnit = unitOf(unit, fromAyah) + a - 1
        val maxUnit = unitOf(unit, planEnd)
        val u = minOf(lastUnit, maxUnit)
        return minOf(planEnd, unitLast(unit, u)).coerceAtLeast(fromAyah.coerceAtMost(planEnd))
    }

    /** بداية اليوم: آخر آية منجزة قبل أن يبدأ المستخدم ورد هذا اليوم. */
    fun startOfDayProgress(plan: QuranPlanEntity, ayahsReadToday: Int): Int =
        (plan.progressAyahId - ayahsReadToday.coerceAtLeast(0)).coerceIn(plan.startAyahId - 1, plan.endAyahId)

    /** مقطع اليوم، أو null إن اكتمل الورد قبل هذا اليوم. */
    fun todayPortion(plan: QuranPlanEntity, startOfDayProgress: Int): Portion? {
        if (startOfDayProgress >= plan.endAyahId) return null
        val from = maxOf(startOfDayProgress + 1, plan.startAyahId)
        return Portion(from, portionEnd(plan.unitEnum(), from, plan.dailyAmount, plan.endAyahId))
    }

    fun todayPortion(plan: QuranPlanEntity, todayLog: QuranPlanLogEntity?): Portion? =
        todayPortion(plan, startOfDayProgress(plan, todayLog?.ayahsRead ?: 0))

    /** هل تحقق المقدار اليومي بعد أن صار التقدم [progress]؟ */
    fun isTargetMet(plan: QuranPlanEntity, progress: Int, ayahsReadToday: Int): Boolean {
        if (progress >= plan.endAyahId) return true
        val portion = todayPortion(plan, (progress - ayahsReadToday.coerceAtLeast(0)).coerceIn(plan.startAyahId - 1, plan.endAyahId))
            ?: return true
        return progress >= portion.toAyah
    }

    /** المقاطع المتوقعة للأيام القادمة انطلاقًا من اليوم (لجدولة التذكيرات). العنصر الأول لليوم نفسه. */
    fun upcomingPortions(plan: QuranPlanEntity, todayLog: QuranPlanLogEntity?, days: Int): List<Portion?> {
        val out = ArrayList<Portion?>(days)
        var cursor = startOfDayProgress(plan, todayLog?.ayahsRead ?: 0)
        repeat(days) { i ->
            val p = todayPortion(plan, cursor)
            out += p
            if (p == null) return@repeat
            // نفترض إتمام مقطع اليوم عند حساب اليوم التالي، وإن كان التقدم الفعلي أبعد فهو المعتمد.
            cursor = if (i == 0) maxOf(p.toAyah, plan.progressAyahId) else p.toAyah
        }
        return out
    }

    // ------------------------------------------------------------ التقدم عند قراءة صفحة

    /**
     * التقدم الجديد بعد قراءة الصفحة [page]، أو null إن لم يتغير.
     * القاعدة: تتقدم الورد فقط إذا كانت الصفحة متصلة بالتقدم الحالي أو متداخلة معه (لا فجوة غير مقروءة بينهما).
     * القفز لصفحة أبعد (وفيها فجوة) لا يُحتسب وردًا لئلا تُحسب آيات لم تُقرأ؛ ولمن يريد القفز عمدًا
     * خيار "ضبط موضع التقدم" في شاشة الورد. إعادة قراءة صفحة سابقة لا تغيّر شيئًا.
     */
    fun advanceForPage(plan: QuranPlanEntity, page: Int): Int? {
        if (page < 1 || page > pageCount) return null
        val first = pageFirst[page]
        val last = pageLast[page]
        if (last < plan.startAyahId || first > plan.endAyahId) return null
        val pf = maxOf(first, plan.startAyahId)
        val pl = minOf(last, plan.endAyahId)
        if (pl <= plan.progressAyahId) return null
        if (pf > plan.progressAyahId + 1) return null
        return pl
    }

    /** عدد الصفحات المختلفة التي تغطيها الآيات [from, to]. */
    fun pagesBetween(fromAyah: Int, toAyah: Int): Int {
        if (toAyah < fromAyah) return 0
        return q.ayah(toAyah).page - q.ayah(fromAyah).page + 1
    }

    // ------------------------------------------------------------ النسبة

    /** نسبة الإنجاز 0..1 بحسب حجم النص. */
    fun fraction(startAyah: Int, endAyah: Int, progressAyah: Int): Float {
        if (endAyah < startAyah) return 0f
        if (progressAyah >= endAyah) return 1f
        if (progressAyah < startAyah) return 0f
        val total = weightPrefix[endAyah] - weightPrefix[startAyah - 1]
        val done = weightPrefix[progressAyah] - weightPrefix[startAyah - 1]
        return if (total <= 0) 0f else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    fun fraction(plan: QuranPlanEntity): Float = fraction(plan.startAyahId, plan.endAyahId, plan.progressAyahId)

    // ------------------------------------------------------------ نصوص العرض

    /** "من البقرة ٢٥ إلى ٤٣" أو "من البقرة ٢٨٣ إلى آل عمران ٢٠". */
    fun rangeLabel(fromAyah: Int, toAyah: Int): String {
        val a = q.ayah(fromAyah)
        val b = q.ayah(toAyah)
        val sa = SurahNames.plain(q.surah(a.surah).nameAr)
        if (fromAyah == toAyah) return "$sa ${ArabicText.indic(a.number)}"
        return if (a.surah == b.surah) {
            "من $sa ${ArabicText.indic(a.number)} إلى ${ArabicText.indic(b.number)}"
        } else {
            "من $sa ${ArabicText.indic(a.number)} إلى ${SurahNames.plain(q.surah(b.surah).nameAr)} ${ArabicText.indic(b.number)}"
        }
    }

    fun rangeLabel(p: Portion): String = rangeLabel(p.fromAyah, p.toAyah)

    /** "ص ٣ – ٤" */
    fun pagesLabel(fromAyah: Int, toAyah: Int): String {
        val pa = q.ayah(fromAyah).page
        val pb = q.ayah(toAyah).page
        return if (pa == pb) "ص ${ArabicText.indic(pa)}" else "ص ${ArabicText.indic(pa)} – ${ArabicText.indic(pb)}"
    }

    /** موضع آية: "البقرة ٢٥٥". */
    fun ayahLabel(ayahId: Int): String = q.ayah(ayahId).let { "${SurahNames.plain(q.surah(it.surah).nameAr)} ${ArabicText.indic(it.number)}" }

    companion object {
        // ------------------------------------------------------------ تواريخ

        /** عدد الأيام شاملًا الطرفين (0 إن كان [end] قبل [start]). */
        fun daysInclusive(start: LocalDate, end: LocalDate): Int =
            if (end.isBefore(start)) 0 else (ChronoUnit.DAYS.between(start, end) + 1).toInt()

        /** المقدار اليومي المقترح: إجمالي الوحدات ÷ عدد الأيام (تقريب لأعلى، لا يقل عن 1). */
        fun suggestedDaily(totalUnits: Int, days: Int): Int {
            if (totalUnits <= 0 || days <= 0) return 1
            return ((totalUnits + days - 1) / days).coerceAtLeast(1)
        }

        /** الأيام المتبقية شاملةً اليوم، أو null إن لم يكن هناك تاريخ نهاية. لا تقل عن 0. */
        fun daysRemaining(today: LocalDate, endDate: LocalDate?): Int? =
            endDate?.let { if (it.isBefore(today)) 0 else (ChronoUnit.DAYS.between(today, it) + 1).toInt() }

        /** المقدار اللازم يوميًا لإنهاء الباقي في المدة المتبقية (null إن لم يكن هناك موعد أو انتهت المدة). */
        fun requiredDaily(remainingUnits: Int, daysRemaining: Int?): Int? =
            if (daysRemaining == null || daysRemaining <= 0 || remainingUnits <= 0) null else suggestedDaily(remainingUnits, daysRemaining)

        fun dayStatus(log: QuranPlanLogEntity?): DayStatus = when {
            log == null || log.deleted -> DayStatus.UNRECORDED
            log.targetMet -> DayStatus.MET
            log.ayahsRead > 0 || log.pagesRead > 0 -> DayStatus.PARTIAL
            else -> DayStatus.UNRECORDED
        }

        /**
         * أيام سجل الالتزام من الأحدث إلى الأقدم: من تاريخ البداية حتى اليوم (أو تاريخ النهاية إن انتهت المدة)،
         * مع أي يوم له سجل بعد ذلك. الأيام المستقبلية لا تظهر.
         */
        fun historyDates(start: LocalDate, endDate: LocalDate?, today: LocalDate, logDates: Collection<LocalDate>): List<LocalDate> {
            val last = if (endDate != null && endDate.isBefore(today)) endDate else today
            val set = HashSet<LocalDate>()
            var d = start
            while (!d.isAfter(last)) { set += d; d = d.plusDays(1) }
            logDates.filter { !it.isBefore(start) && !it.isAfter(today) }.forEach { set += it }
            return set.sortedDescending()
        }

        /** الوحدة مع العدد بصياغة عربية سليمة: ١ صفحة، ٢ صفحتان، ٣ صفحات، ١١ صفحة. */
        fun unitText(unit: QuranPlanUnit, n: Int): String {
            val (one, two, few, many) = when (unit) {
                QuranPlanUnit.PAGE -> listOf("صفحة", "صفحتان", "صفحات", "صفحة")
                QuranPlanUnit.JUZ -> listOf("جزء", "جزءان", "أجزاء", "جزءًا")
                QuranPlanUnit.AYAH -> listOf("آية", "آيتان", "آيات", "آية")
            }
            return when {
                n == 1 -> one
                n == 2 -> two
                n in 3..10 -> "${ArabicText.indic(n)} $few"
                else -> "${ArabicText.indic(n)} $many"
            }
        }

        fun planUnit(name: String): QuranPlanUnit = runCatching { QuranPlanUnit.valueOf(name) }.getOrDefault(QuranPlanUnit.PAGE)

        /** تحليل "HH:mm". */
        fun parseTime(s: String?): Pair<Int, Int>? {
            val parts = s?.split(":") ?: return null
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h to m else null
        }

        fun formatTime(h: Int, m: Int): String = String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }
}

fun QuranPlanEntity.unitEnum(): QuranPlanUnit = PlanMath.planUnit(unit)
