package app.yaqeen.feature.quran.plans

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.core.newId
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.domain.model.QuranPlanStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * كل عمليات الأوراد التي تكتب في قاعدة البيانات (عبر c.writer دائمًا).
 * قفل واحد على مستوى التطبيق يمنع تسابق (قراءة صفحة) مع (أزرار شاشة الورد) على السجل نفسه.
 */
class PlanRepository private constructor(private val c: AppContainer) {

    companion object {
        private val lock = Mutex()
        @Volatile private var instance: PlanRepository? = null
        @Volatile private var cachedMath: PlanMath? = null

        fun of(c: AppContainer): PlanRepository =
            instance ?: synchronized(this) { instance ?: PlanRepository(c).also { instance = it } }

        /** مفتاح مجموعة الصفحات المحسوبة اليوم (تفضيل محلي للجهاز). */
        fun countedKey(date: String) = "quran.counted.$date"
    }

    suspend fun math(): PlanMath {
        val q: QuranData = c.quran()
        val m = cachedMath
        if (m != null && m.q === q) return m
        return PlanMath(q).also { cachedMath = it }
    }

    private fun today() = c.clock.today()

    // ------------------------------------------------------------ قراءة صفحة (من القارئ)

    suspend fun onPageRead(page: Int) = lock.withLock {
        val m = math()
        if (page < 1 || page > m.q.pageCount) return@withLock
        val today = today()
        val todayKey = today.dayKey()

        // 1) سجل القراءة اليومي: الصفحة تُحتسب مرة واحدة في اليوم (المجموعة محفوظة محليًا).
        val key = countedKey(todayKey)
        val counted = c.localPrefs.get(key)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
        if (counted.add(page)) {
            c.localPrefs.put(key, counted.sorted().joinToString(","))
            c.localPrefs.put(countedKey(today.minusDays(1).dayKey()), null)
            val ayahCount = m.q.ayahsOfPage(page).size
            val dao = c.db.quranDayLogs()
            val existing = dao.byId(todayKey)?.takeIf { !it.deleted }
            val row = existing?.copy(pagesRead = existing.pagesRead + 1, ayahsRead = existing.ayahsRead + ayahCount)
                ?: QuranDayLogEntity(id = todayKey, date = todayKey, pagesRead = 1, ayahsRead = ayahCount)
            c.writer.save(dao, row.copy(deleted = false))
        }

        // 2) الأوراد النشطة التي بدأت وتتقاطع مع الصفحة.
        var needReschedule = false
        for (plan in c.db.quranPlans().active()) {
            if (plan.startDate > todayKey) continue
            val newProgress = m.advanceForPage(plan, page) ?: continue
            needReschedule = applyProgress(m, plan, newProgress, pagesDelta = 1, today = todayKey) || needReschedule
        }
        if (needReschedule) c.requestReschedule()
    }

    /**
     * يطبّق تقدمًا جديدًا على ورد ويحدّث سجل اليوم والحالة. يرجع true إن تغيّر ما يؤثر على التذكيرات.
     * [pagesDelta] = null يعني احسب الصفحات من الآيات (تسجيل يدوي/ضبط موضع).
     */
    private suspend fun applyProgress(m: PlanMath, plan: QuranPlanEntity, newProgressRaw: Int, pagesDelta: Int?, today: String): Boolean {
        val newProgress = newProgressRaw.coerceIn(plan.startAyahId - 1, plan.endAyahId)
        val logDao = c.db.quranPlanLogs()
        val logId = "${plan.id}_$today"
        val old = logDao.byId(logId)?.takeIf { !it.deleted }
        val delta = newProgress - plan.progressAyahId
        val oldAyahs = old?.ayahsRead ?: 0
        val ayahsRead = (oldAyahs + delta).coerceAtLeast(0)
        val pagesRead = when {
            ayahsRead == 0 -> 0
            delta > 0 -> (old?.pagesRead ?: 0) + (pagesDelta ?: m.pagesBetween(plan.progressAyahId + 1, newProgress))
            else -> old?.pagesRead ?: 0
        }
        val newStatus = when {
            newProgress >= plan.endAyahId -> QuranPlanStatus.COMPLETED.name
            plan.status == QuranPlanStatus.COMPLETED.name -> QuranPlanStatus.ACTIVE.name
            else -> plan.status
        }
        val updated = plan.copy(progressAyahId = newProgress, status = newStatus)
        val met = m.isTargetMet(updated, newProgress, ayahsRead)
        if (updated.progressAyahId != plan.progressAyahId || updated.status != plan.status) {
            c.writer.save(c.db.quranPlans(), updated.copy(deleted = false))
        }
        val unchangedLog = old != null && old.ayahsRead == ayahsRead && old.pagesRead == pagesRead && old.targetMet == met
        if (!unchangedLog && (old != null || ayahsRead > 0 || met)) {
            val row = (old ?: QuranPlanLogEntity(id = logId, planId = plan.id, date = today))
                .copy(ayahsRead = ayahsRead, pagesRead = pagesRead, targetMet = met, deleted = false)
            c.writer.save(logDao, row)
        }
        return (old?.targetMet ?: false) != met || newStatus != plan.status
    }

    // ------------------------------------------------------------ أزرار شاشة الورد

    /** "تمّ ورد اليوم": يُسجَّل الورد حتى نهاية مقطع اليوم. */
    suspend fun markTodayDone(planId: String) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        val m = math()
        val today = today().dayKey()
        val log = c.db.quranPlanLogs().byId("${plan.id}_$today")?.takeIf { !it.deleted }
        val portion = m.todayPortion(plan, log) ?: return@withLock
        if (plan.progressAyahId < portion.toAyah) {
            applyProgress(m, plan, portion.toAyah, pagesDelta = null, today = today)
        } else if (log?.targetMet != true) {
            applyProgress(m, plan, plan.progressAyahId, pagesDelta = null, today = today)
        }
        c.requestReschedule()
    }

    /** يتراجع عن تسجيل اليوم: يعيد التقدم إلى بدايته صباح اليوم ويصفّر سجل اليوم. */
    suspend fun undoToday(planId: String) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        val m = math()
        val today = today().dayKey()
        val logDao = c.db.quranPlanLogs()
        val log = logDao.byId("${plan.id}_$today")?.takeIf { !it.deleted } ?: return@withLock
        val sod = m.startOfDayProgress(plan, log.ayahsRead)
        val status = if (plan.status == QuranPlanStatus.COMPLETED.name) QuranPlanStatus.ACTIVE.name else plan.status
        c.writer.save(c.db.quranPlans(), plan.copy(progressAyahId = sod, status = status, deleted = false))
        c.writer.save(logDao, log.copy(ayahsRead = 0, pagesRead = 0, targetMet = false, deleted = false))
        c.requestReschedule()
    }

    /** ضبط موضع التقدم يدويًا (للقراءة المتقدمة عمدًا أو التصحيح). */
    suspend fun setProgress(planId: String, ayahId: Int) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        val m = math()
        applyProgress(m, plan, ayahId, pagesDelta = null, today = today().dayKey())
        c.requestReschedule()
    }

    // ------------------------------------------------------------ إدارة الورد

    /** ينشئ ورداً جديدًا ويرجع معرّفه. */
    suspend fun create(draft: QuranPlanEntity): String = lock.withLock {
        val id = newId()
        c.writer.save(
            c.db.quranPlans(),
            draft.copy(
                id = id,
                progressAyahId = draft.startAyahId - 1,
                status = QuranPlanStatus.ACTIVE.name,
                createdAt = c.clock.nowMillis(),
                deleted = false,
            ),
        )
        c.requestReschedule()
        id
    }

    /** يحفظ تعديل ورد موجود؛ التقدم يُقصّ على المدى الجديد، والحالة تُعاد حسابها. */
    suspend fun update(edited: QuranPlanEntity) = lock.withLock {
        val old = c.db.quranPlans().byId(edited.id)?.takeIf { !it.deleted } ?: return@withLock
        val progress = old.progressAyahId.coerceIn(edited.startAyahId - 1, edited.endAyahId)
        val status = when {
            progress >= edited.endAyahId -> QuranPlanStatus.COMPLETED.name
            old.status == QuranPlanStatus.COMPLETED.name -> QuranPlanStatus.ACTIVE.name
            else -> old.status
        }
        c.writer.save(c.db.quranPlans(), edited.copy(progressAyahId = progress, status = status, createdAt = old.createdAt, deleted = false))
        c.requestReschedule()
    }

    suspend fun setStatus(planId: String, status: QuranPlanStatus) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        c.writer.save(c.db.quranPlans(), plan.copy(status = status.name, deleted = false))
        c.requestReschedule()
    }

    /** إتمام الورد يدويًا (إعلان من المستخدم): يُنقل التقدم إلى النهاية وتُعلَّم الحالة. */
    suspend fun complete(planId: String) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        val m = math()
        applyProgress(m, plan, plan.endAyahId, pagesDelta = null, today = today().dayKey())
        c.requestReschedule()
    }

    suspend fun setReminder(planId: String, enabled: Boolean, time: String?) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        c.writer.save(
            c.db.quranPlans(),
            plan.copy(reminderEnabled = enabled && time != null, reminderTime = time ?: plan.reminderTime, deleted = false),
        )
        c.requestReschedule()
    }

    /** حذف منطقي للورد وسجلاته (ليصل الحذف إلى بقية الأجهزة). */
    suspend fun delete(planId: String) = lock.withLock {
        val plan = c.db.quranPlans().byId(planId)?.takeIf { !it.deleted } ?: return@withLock
        val logDao = c.db.quranPlanLogs()
        val logs = logDao.range("0000-01-01", "9999-12-31").filter { it.planId == planId }
        logs.forEach { c.writer.delete(logDao, it) }
        c.writer.delete(c.db.quranPlans(), plan)
        c.requestReschedule()
    }
}
