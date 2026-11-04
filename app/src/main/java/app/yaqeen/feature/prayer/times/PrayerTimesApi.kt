package app.yaqeen.feature.prayer.times

import app.yaqeen.AppContainer
import app.yaqeen.domain.model.Prayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * === عقد مواقيت الصلاة (يملكه منفذ "المواقيت والقبلة والأذان") ===
 * بقية الميزات (الرئيسية، المهام، التقويم، أذكار بعد الصلاة...) تعتمد على هذه الأنواع فقط.
 * هذا الملف هيكل أولي: يستبدل المنفذ التنفيذ (احتفظ بأسماء الأنواع والتواقيع كما هي).
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
    /** الموقع الحالي (GPS بعد الإذن أو مدينة يدوية)، أو null إن لم يُحدَّد بعد. */
    fun observeLocation(): Flow<ResolvedLocation?> = flowOf(null)

    /** مواقيت يوم معيّن حسب الموقع وطريقة الحساب المختارة، أو null إن لم يوجد موقع. */
    suspend fun timesFor(date: LocalDate): PrayerTimesDay? = null

    /** الصلاة القادمة (بعد العشاء تعيد فجر الغد). null إن لم يوجد موقع. */
    suspend fun nextPrayer(now: ZonedDateTime = ZonedDateTime.now()): NextPrayer? = null
}
