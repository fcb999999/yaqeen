package app.yaqeen.feature.prayer.times

import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.settings.StringKey
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.SettingScope
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** مفاتيح إعدادات المواقيت الإضافية (غير المعرّفة في Settings). */
object PrayerKeys {
    /** تعديل الدقائق للصلوات الخمس بالترتيب "الفجر,الظهر,العصر,المغرب,العشاء" (مثل "0,0,0,0,0"). مُزامَن مع الحساب. */
    val ADJUSTMENTS = StringKey(SettingScope.USER, "prayer_adjustments", "0,0,0,0,0")

    /** معرّف المنطقة الزمنية للموقع المحفوظ (يُخزَّن في LocalPrefs: خاص بهذا الجهاز). */
    const val LOC_ZONE = "loc.zone"

    const val MODE_GPS = "gps"
    const val MODE_MANUAL = "manual"
}

/** تعديل بالدقائق لكل صلاة من الخمس (للتوفيق مع تقويم المسجد المحلي). */
data class MinuteAdjustments(
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    operator fun get(p: Prayer): Int = when (p) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    fun with(p: Prayer, minutes: Int): MinuteAdjustments {
        val v = minutes.coerceIn(RANGE)
        return when (p) {
            Prayer.FAJR -> copy(fajr = v)
            Prayer.DHUHR -> copy(dhuhr = v)
            Prayer.ASR -> copy(asr = v)
            Prayer.MAGHRIB -> copy(maghrib = v)
            Prayer.ISHA -> copy(isha = v)
        }
    }

    val isZero: Boolean get() = fajr == 0 && dhuhr == 0 && asr == 0 && maghrib == 0 && isha == 0

    fun encode(): String = "$fajr,$dhuhr,$asr,$maghrib,$isha"

    companion object {
        val RANGE = -30..30

        fun decode(raw: String?): MinuteAdjustments {
            val parts = raw?.split(',')?.map { it.trim().toIntOrNull() ?: 0 } ?: return MinuteAdjustments()
            fun at(i: Int) = (parts.getOrNull(i) ?: 0).coerceIn(RANGE)
            return MinuteAdjustments(at(0), at(1), at(2), at(3), at(4))
        }
    }
}

/** كل ما يؤثر على حساب المواقيت سوى الموقع والتاريخ. القيم نصية كما تُخزَّن في الإعدادات. */
data class CalcSettings(
    val method: String = "UMM_AL_QURA",
    val madhab: String = "SHAFI",
    val highLatRule: String = "AUTO",
    val adjustments: MinuteAdjustments = MinuteAdjustments(),
)

data class MethodInfo(val id: String, val nameAr: String, val descAr: String)

object CalcMethods {
    const val TURKEY = "TURKEY"
    const val HIGH_LAT_AUTO = "AUTO"

    /** كل طرق الحساب المتاحة. الوصف يلخّص زاويتي الفجر والعشاء. */
    val ALL: List<MethodInfo> = listOf(
        MethodInfo("UMM_AL_QURA", "أم القرى (مكة المكرمة)", "الفجر ١٨٫٥° والعشاء بعد المغرب بـ ٩٠ دقيقة (١٢٠ دقيقة في رمضان)"),
        MethodInfo("MUSLIM_WORLD_LEAGUE", "رابطة العالم الإسلامي", "الفجر ١٨° والعشاء ١٧°"),
        MethodInfo("EGYPTIAN", "الهيئة المصرية العامة للمساحة", "الفجر ١٩٫٥° والعشاء ١٧٫٥°"),
        MethodInfo("KARACHI", "جامعة العلوم الإسلامية بكراتشي", "الفجر ١٨° والعشاء ١٨°"),
        MethodInfo("NORTH_AMERICA", "الجمعية الإسلامية لأمريكا الشمالية", "الفجر ١٥° والعشاء ١٥°"),
        MethodInfo("DUBAI", "دبي", "الفجر ١٨٫٢° والعشاء ١٨٫٢°"),
        MethodInfo("QATAR", "قطر", "الفجر ١٨° والعشاء بعد المغرب بـ ٩٠ دقيقة"),
        MethodInfo("KUWAIT", "الكويت", "الفجر ١٨° والعشاء ١٧٫٥°"),
        MethodInfo("SINGAPORE", "سنغافورة", "الفجر ٢٠° والعشاء ١٨°"),
        MethodInfo(TURKEY, "رئاسة الشؤون الدينية التركية", "الفجر ١٨° والعشاء ١٧° مع تعديلات الرئاسة"),
        MethodInfo("MOON_SIGHTING_COMMITTEE", "لجنة رؤية الهلال", "الفجر ١٨° والعشاء ١٨°"),
    )

    val MADHABS: List<Pair<String, String>> = listOf(
        "SHAFI" to "الجمهور (الشافعي والمالكي والحنبلي): ظل الشيء مثله",
        "HANAFI" to "الحنفي: ظل الشيء مثليه",
    )

    val HIGH_LAT_RULES: List<Pair<String, String>> = listOf(
        HIGH_LAT_AUTO to "تلقائي (موصى به)",
        "MIDDLE_OF_THE_NIGHT" to "منتصف الليل",
        "SEVENTH_OF_THE_NIGHT" to "سُبع الليل",
        "TWILIGHT_ANGLE" to "زاوية الشفق",
    )

    fun nameOf(id: String): String = ALL.firstOrNull { it.id == id }?.nameAr ?: ALL.first().nameAr
    fun madhabName(id: String): String = if (id == "HANAFI") "الحنفي" else "الجمهور"
    fun highLatName(id: String): String = HIGH_LAT_RULES.firstOrNull { it.first == id }?.second ?: HIGH_LAT_RULES.first().second

    /**
     * طريقة يشيع اعتمادها في البلد (للاقتراح فقط، ولا تُطبَّق تلقائيًا). null إن لم نكن واثقين.
     */
    fun suggestedForCountry(countryAr: String): String? = when (countryAr) {
        "المملكة العربية السعودية" -> "UMM_AL_QURA"
        "الكويت" -> "KUWAIT"
        "قطر" -> "QATAR"
        "الإمارات العربية المتحدة" -> "DUBAI"
        "مصر", "السودان", "ليبيا" -> "EGYPTIAN"
        "باكستان", "الهند", "بنغلاديش", "أفغانستان" -> "KARACHI"
        "الولايات المتحدة", "كندا" -> "NORTH_AMERICA"
        "سنغافورة", "ماليزيا", "إندونيسيا", "بروناي" -> "SINGAPORE"
        "تركيا" -> TURKEY
        else -> null
    }

    /** يحوّل الإعدادات النصية إلى معاملات مكتبة adhan. أي قيمة مجهولة ترجع للافتراضي بدل الانهيار. */
    fun parameters(s: CalcSettings, latitude: Double): CalculationParameters {
        val p: CalculationParameters = if (s.method == TURKEY) {
            CalculationParameters(18.0, 17.0).withMethodAdjustments(PrayerAdjustments(0, -7, 5, 4, 7, 0))
        } else {
            val m = runCatching { CalculationMethod.valueOf(s.method) }.getOrDefault(CalculationMethod.UMM_AL_QURA)
            m.parameters
        }
        p.madhab = if (s.madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        p.highLatitudeRule = when (s.highLatRule) {
            "MIDDLE_OF_THE_NIGHT" -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
            "SEVENTH_OF_THE_NIGHT" -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
            "TWILIGHT_ANGLE" -> HighLatitudeRule.TWILIGHT_ANGLE
            else -> if (abs(latitude) > 48.0) HighLatitudeRule.SEVENTH_OF_THE_NIGHT else HighLatitudeRule.MIDDLE_OF_THE_NIGHT
        }
        val a = s.adjustments
        p.adjustments = PrayerAdjustments(a.fajr, 0, a.dhuhr, a.asr, a.maghrib, a.isha)
        return p
    }
}

/**
 * غلاف الحساب الخالص (بلا اعتماد على Android) فوق مكتبة adhan.
 * التاريخ [date] هو التاريخ المحلي في [zone]، والنتائج تُحوَّل إلى المنطقة الزمنية نفسها.
 */
object PrayerCalculator {
    /** مدة زيادة العشاء في رمضان لطريقة أم القرى (٩٠ → ١٢٠ دقيقة بعد المغرب). */
    private const val UMM_AL_QURA_RAMADAN_EXTRA_MIN = 30L

    fun compute(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, settings: CalcSettings): PrayerTimesDay? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val pt = try {
            PrayerTimes(
                Coordinates(latitude, longitude),
                DateComponents(date.year, date.monthValue, date.dayOfMonth),
                CalcMethods.parameters(settings, latitude),
            )
        } catch (e: Exception) {
            return null
        }
        // المكتبة تقرّب إلى الدقيقة لكنها تُبقي ملّي ثواني عشوائية من وقت التشغيل؛ نقتطع لدقيقة كاملة ليكون الناتج حتميًا.
        fun conv(d: java.util.Date?): ZonedDateTime? = d?.toInstant()?.atZone(zone)?.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val fajr = conv(pt.fajr) ?: return null
        val sunrise = conv(pt.sunrise) ?: return null
        val dhuhr = conv(pt.dhuhr) ?: return null
        val asr = conv(pt.asr) ?: return null
        val maghrib = conv(pt.maghrib) ?: return null
        var isha = conv(pt.isha) ?: return null
        // مكتبة adhan (1.2.1) لا تزيد عشاء رمضان لأم القرى، فنطبّقها هنا (التقويم الهجري الرسمي بلا تعديل المستخدم).
        if (settings.method == "UMM_AL_QURA" && HijriCalendar.fromGregorian(date, 0).month == 9) {
            isha = isha.plusMinutes(UMM_AL_QURA_RAMADAN_EXTRA_MIN)
        }
        return PrayerTimesDay(date, fajr, sunrise, dhuhr, asr, maghrib, isha)
    }

    /** الصلاة القادمة بعد [now]: من اليوم أو فجر الغد إن انقضت الصلوات. */
    fun nextAfter(now: ZonedDateTime, today: PrayerTimesDay, tomorrow: PrayerTimesDay): NextPrayer =
        today.next(now) ?: NextPrayer(Prayer.FAJR, tomorrow.fajr)

    /** الصلاة الحالية (آخر صلاة دخل وقتها) أو null قبل الفجر. */
    fun currentAt(now: ZonedDateTime, today: PrayerTimesDay): Prayer? =
        Prayer.entries.lastOrNull { !today.timeOf(it).isAfter(now) }
}
