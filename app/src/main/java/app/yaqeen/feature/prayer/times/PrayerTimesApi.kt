package app.yaqeen.feature.prayer.times

import app.yaqeen.AppContainer
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * === عقد مواقيت الصلاة (يملكه منفذ "المواقيت والقبلة والأذان") ===
 * بقية الميزات (الرئيسية، المهام، التقويم، أذكار بعد الصلاة...) تعتمد على هذه الأنواع فقط.
 */
data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    /** اسم المدينة للعرض. */
    val name: String,
    /** true إذا اختارها المستخدم يدويًا، false إذا من GPS. */
    val isManual: Boolean,
    /** معرّف المنطقة الزمنية (مثل Asia/Riyadh). */
    val zoneId: String,
)

data class NextPrayer(val prayer: Prayer, val time: ZonedDateTime)

data class PrayerTimesDay(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun timeOf(p: Prayer): ZonedDateTime = when (p) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    /** أقرب صلاة من الخمس بعد [now] في هذا اليوم، أو null إن انتهت الصلوات (الشروق ليس صلاة). */
    fun next(now: ZonedDateTime): NextPrayer? =
        Prayer.entries.map { NextPrayer(it, timeOf(it)) }.firstOrNull { it.time.isAfter(now) }
}

class PrayerTimesService(private val c: AppContainer) {
    /** الموقع الحالي (GPS بعد الإذن أو مدينة يدوية)، أو null إن لم يُحدَّد بعد. يتحدث عند تغيّر التفضيلات. */
    fun observeLocation(): Flow<ResolvedLocation?> {
        val p = c.localPrefs
        return combine(
            p.observe(LocalPrefs.Keys.LAST_LAT),
            p.observe(LocalPrefs.Keys.LAST_LON),
            p.observe(LocalPrefs.Keys.LAST_CITY),
            p.observe(LocalPrefs.Keys.LOCATION_MODE),
            p.observe(PrayerKeys.LOC_ZONE),
        ) { lat, lon, city, mode, zone -> buildLocation(lat, lon, city, mode, zone) }.distinctUntilChanged()
    }

    /** الموقع الحالي دفعة واحدة (للجدولة والحسابات). */
    suspend fun currentLocation(): ResolvedLocation? = observeLocation().first()

    /** إعدادات الحساب (الطريقة والمذهب والتعديلات) كتدفق يتحدث عند تغيّرها. */
    fun observeCalcSettings(): Flow<CalcSettings> = combine(
        c.settings.observe(Settings.PRAYER_METHOD),
        c.settings.observe(Settings.PRAYER_MADHAB),
        c.settings.observe(Settings.PRAYER_HIGH_LAT_RULE),
        c.settings.observe(PrayerKeys.ADJUSTMENTS),
    ) { method, madhab, highLat, adj -> CalcSettings(method, madhab, highLat, MinuteAdjustments.decode(adj)) }.distinctUntilChanged()

    suspend fun calcSettings(): CalcSettings = observeCalcSettings().first()

    /** مواقيت يوم معيّن حسب الموقع وطريقة الحساب المختارة، أو null إن لم يوجد موقع. */
    suspend fun timesFor(date: LocalDate): PrayerTimesDay? {
        val loc = currentLocation() ?: return null
        return timesFor(date, loc, calcSettings())
    }

    /** الصلاة القادمة (بعد العشاء تعيد فجر الغد). null إن لم يوجد موقع. */
    suspend fun nextPrayer(now: ZonedDateTime = ZonedDateTime.now()): NextPrayer? {
        val loc = currentLocation() ?: return null
        val settings = calcSettings()
        val zoned = now.withZoneSameInstant(ZoneId.of(loc.zoneId))
        val today = timesFor(zoned.toLocalDate(), loc, settings) ?: return null
        val tomorrow = timesFor(zoned.toLocalDate().plusDays(1), loc, settings) ?: return null
        return PrayerCalculator.nextAfter(zoned, today, tomorrow)
    }

    /** نسخة تأخذ الموقع والإعدادات جاهزة (لتفادي قراءتها مرارًا في الجدولة والواجهة). */
    fun timesFor(date: LocalDate, loc: ResolvedLocation, settings: CalcSettings): PrayerTimesDay? =
        PrayerCalculator.compute(date, loc.latitude, loc.longitude, ZoneId.of(loc.zoneId), settings)

    // ---- حفظ الموقع (يُخزَّن على الجهاز فقط ولا يُزامَن) ----

    /** يحفظ موقع GPS. الاسم أقرب مدينة من القائمة المدمجة إن كانت ضمن ٣٠ كم وإلا "موقعي الحالي". */
    suspend fun saveGpsLocation(lat: Double, lon: Double) {
        val near = CityRepository.nearest(CityRepository.load(c.context), lat, lon)
        val p = c.localPrefs
        p.put(LocalPrefs.Keys.LAST_LAT, lat.toString())
        p.put(LocalPrefs.Keys.LAST_LON, lon.toString())
        p.put(LocalPrefs.Keys.LAST_CITY, near?.name ?: CURRENT_LOCATION_NAME)
        p.put(PrayerKeys.LOC_ZONE, ZoneId.systemDefault().id)
        p.put(LocalPrefs.Keys.LOCATION_MODE, PrayerKeys.MODE_GPS)
        c.requestReschedule()
    }

    suspend fun saveManualCity(city: City) {
        val p = c.localPrefs
        p.put(LocalPrefs.Keys.LAST_LAT, city.lat.toString())
        p.put(LocalPrefs.Keys.LAST_LON, city.lon.toString())
        p.put(LocalPrefs.Keys.LAST_CITY, city.name)
        p.put(PrayerKeys.LOC_ZONE, city.zone)
        p.put(LocalPrefs.Keys.LOCATION_MODE, PrayerKeys.MODE_MANUAL)
        c.requestReschedule()
    }

    suspend fun clearLocation() {
        val p = c.localPrefs
        listOf(LocalPrefs.Keys.LAST_LAT, LocalPrefs.Keys.LAST_LON, LocalPrefs.Keys.LAST_CITY, LocalPrefs.Keys.LOCATION_MODE, PrayerKeys.LOC_ZONE)
            .forEach { p.put(it, null) }
        c.requestReschedule()
    }

    companion object {
        const val CURRENT_LOCATION_NAME = "موقعي الحالي"

        /** يبني الموقع من القيم الخام المخزّنة؛ null إن كانت ناقصة أو غير صالحة. (دالة خالصة للاختبار) */
        internal fun buildLocation(lat: String?, lon: String?, city: String?, mode: String?, zone: String?): ResolvedLocation? {
            val la = lat?.toDoubleOrNull() ?: return null
            val lo = lon?.toDoubleOrNull() ?: return null
            if (la !in -90.0..90.0 || lo !in -180.0..180.0) return null
            val isManual = mode != PrayerKeys.MODE_GPS
            val zoneId = if (!isManual) {
                // في وضع GPS تتبع المنطقة الزمنية الحالية للجهاز (قد يسافر المستخدم).
                ZoneId.systemDefault().id
            } else {
                zone?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: ZoneId.systemDefault().id
            }
            return ResolvedLocation(la, lo, city?.takeIf { it.isNotBlank() } ?: CURRENT_LOCATION_NAME, isManual, zoneId)
        }
    }
}
