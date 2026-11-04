package app.yaqeen.feature.journey

import app.yaqeen.core.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** يبثّ تاريخ اليوم ويحدّثه عند منتصف الليل (حدّ السجلات هو اليوم الميلادي المحلي). */
object DayTicker {
    fun today(clock: Clock): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.today())
            delay(millisUntilNextDay(clock.nowMillis(), clock.zone()).coerceIn(1_000L, 60_000L))
        }
    }.distinctUntilChanged()

    /** الملّي ثانية المتبقية حتى بداية اليوم التالي (+ هامش ثانية). */
    fun millisUntilNextDay(nowMillis: Long, zone: ZoneId): Long {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        return java.time.Duration.between(now, next).toMillis() + 1_000L
    }
}
