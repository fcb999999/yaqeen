package app.yaqeen.feature.tasks

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** لقطة سجلات العبادات ليوم واحد (مصدر الحقيقة لإنجاز المهام المرتبطة وللملخصات). */
data class WorshipDay(
    val date: LocalDate,
    val prayers: List<PrayerLogEntity> = emptyList(),
    val rawatib: List<RawatibLogEntity> = emptyList(),
    val adhkar: List<AdhkarProgressEntity> = emptyList(),
    val quranLogs: List<QuranPlanLogEntity> = emptyList(),
    val plans: List<QuranPlanEntity> = emptyList(),
    val quranDayLog: QuranDayLogEntity? = null,
)

/** يراقب سجلات العبادة ليوم معيّن مباشرة من Room (يتحدّث فور أي تسجيل في أي قسم). */
fun observeWorshipDay(c: AppContainer, date: LocalDate): Flow<WorshipDay> {
    val d = date.dayKey()
    val a = combine(
        c.db.prayerLogs().observeDay(d),
        c.db.rawatibLogs().observeDay(d),
        c.db.adhkarProgress().observeDay(d),
    ) { p, r, k -> Triple(p, r, k) }
    val b = combine(
        c.db.quranPlanLogs().observeRange(d, d),
        c.db.quranPlans().observeAll(),
        c.db.quranDayLogs().observeRange(d, d),
    ) { l, p, dl -> Triple(l, p, dl) }
    return combine(a, b) { x, y ->
        WorshipDay(date, x.first, x.second, x.third, y.first, y.second, y.third.firstOrNull())
    }
}
