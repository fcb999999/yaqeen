package app.yaqeen.feature.tasks

import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.TaskEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.domain.model.TaskLink
import app.yaqeen.domain.model.TaskPriority
import app.yaqeen.domain.model.TaskRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskLogicTest {
    private val D = LocalDate.of(2026, 11, 4) // الأربعاء

    private fun task(
        id: String = "t1",
        repeat: TaskRepeat = TaskRepeat.DAILY,
        mask: Int = 127,
        start: String = "2026-11-01",
        end: String? = null,
        link: TaskLink = TaskLink.NONE,
        ref: String = "",
        time: String? = null,
        priority: Int = 1,
        sort: Int = 0,
    ) = TaskEntity(
        id = id, title = "مهمة $id", startDate = start, endDate = end, repeat = repeat.name, daysMask = mask,
        link = link.name, linkRefId = ref, timeOfDay = time, priority = priority, sortOrder = sort,
    )

    // ---------------- التكرار وأيام الأسبوع والنطاق
    @Test fun dailyIsActiveEveryDayInRange() {
        val t = task(end = "2026-11-10")
        assertFalse(isTaskActiveOn(t, LocalDate.of(2026, 10, 31)))
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 1)))
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 10)))
        assertFalse(isTaskActiveOn(t, LocalDate.of(2026, 11, 11)))
    }

    @Test fun noneIsOnlyOnStartDate() {
        val t = task(repeat = TaskRepeat.NONE, start = "2026-11-04")
        assertTrue(isTaskActiveOn(t, D))
        assertFalse(isTaskActiveOn(t, D.plusDays(1)))
        assertFalse(isTaskActiveOn(t, D.minusDays(1)))
    }

    @Test fun weeklyUsesMaskMondayIsBit0() {
        // الاثنين(bit0) والخميس(bit3)
        val t = task(repeat = TaskRepeat.WEEKLY, mask = (1 shl 0) or (1 shl 3))
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 2)))  // الاثنين
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 5)))  // الخميس
        assertFalse(isTaskActiveOn(t, D))                          // الأربعاء
        assertFalse(isTaskActiveOn(t, LocalDate.of(2026, 11, 8))) // الأحد
    }

    @Test fun sundayIsBit6() {
        val t = task(repeat = TaskRepeat.WEEKLY, mask = 1 shl 6)
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 8)))
        assertFalse(isTaskActiveOn(t, LocalDate.of(2026, 11, 2)))
    }

    @Test fun weeklyWithEmptyMaskFallsBackToStartWeekday() {
        val t = task(repeat = TaskRepeat.WEEKLY, mask = 0, start = "2026-11-04")
        assertTrue(isTaskActiveOn(t, LocalDate.of(2026, 11, 11)))
        assertFalse(isTaskActiveOn(t, LocalDate.of(2026, 11, 12)))
    }

    @Test fun dailyIgnoresMask() {
        assertTrue(isTaskActiveOn(task(repeat = TaskRepeat.DAILY, mask = 1), D))
    }

    @Test fun deletedOrArchivedNeverActive() {
        assertFalse(isTaskActiveOn(task().copy(deleted = true), D))
        assertFalse(isTaskActiveOn(task().copy(archived = true), D))
    }

    @Test fun badDatesAreInactive() {
        assertFalse(isTaskActiveOn(task(start = "oops"), D))
    }

    // ---------------- الاشتقاق من سجلات العبادة
    private fun prayer(p: Prayer, s: PrayerStatus, deleted: Boolean = false) =
        PrayerLogEntity(id = "x_${p.name}", date = "2026-11-04", prayer = p.name, status = s.name, deleted = deleted)

    @Test fun prayersDoneOnlyWhenAllFiveMosqueOrHome() {
        val all = Prayer.values().map { prayer(it, PrayerStatus.MOSQUE) }
        assertTrue(DerivedCompletion.prayers(all).done)
        val mixed = all.take(2) + all.drop(2).map { it.copy(status = PrayerStatus.HOME.name) }
        assertTrue(DerivedCompletion.prayers(mixed).done)
    }

    @Test fun prayersNotDoneWithNotPrayedMissingOrDeleted() {
        val four = Prayer.values().take(4).map { prayer(it, PrayerStatus.MOSQUE) }
        val r = DerivedCompletion.prayers(four)
        assertFalse(r.done)
        assertEquals("٤ من ٥ صلوات", r.detail)
        assertFalse(DerivedCompletion.prayers(four + prayer(Prayer.ISHA, PrayerStatus.NOT_PRAYED)).done)
        assertFalse(DerivedCompletion.prayers(four + prayer(Prayer.ISHA, PrayerStatus.HOME, deleted = true)).done)
        assertEquals("٣ من ٥ صلوات", DerivedCompletion.prayers(four.take(3)).detail)
    }

    @Test fun rawatibNeedsAllFiveDefaultsAndIgnoresCustom() {
        fun log(id: String, done: Boolean = true) = RawatibLogEntity(id = "d_$id", date = "2026-11-04", sunnahId = id, done = done)
        val five = Sunnah.values().map { log(it.name) }
        assertTrue(DerivedCompletion.rawatib(five).done)
        assertFalse(DerivedCompletion.rawatib(five.drop(1) + log("custom:abc")).done)
        assertFalse(DerivedCompletion.rawatib(five.drop(1) + log(Sunnah.FAJR_BEFORE.name, done = false)).done)
        assertEquals("٢ من ٥ سنن", DerivedCompletion.rawatib(five.take(2)).detail)
    }

    private fun adh(item: String, completed: Boolean, cat: String = "morning") =
        AdhkarProgressEntity(id = "d_$item", date = "2026-11-04", categoryId = cat, itemId = item, completed = completed)

    @Test fun adhkarDoneWhenEveryCategoryItemCompleted() {
        val ids = listOf("a", "b", "c")
        assertTrue(DerivedCompletion.adhkar(ids, ids.map { adh(it, true) }).done)
        val r = DerivedCompletion.adhkar(ids, listOf(adh("a", true), adh("b", false)))
        assertFalse(r.done)
        assertEquals("١ من ٣ أذكار", r.detail)
        assertFalse(DerivedCompletion.adhkar(emptyList(), emptyList()).done)
        assertNull(DerivedCompletion.adhkar(null, emptyList()).detail)
    }

    private fun plan(id: String, status: String = "ACTIVE", daily: Int = 5, unit: String = "PAGE") = QuranPlanEntity(
        id = id, name = "ورد $id", startDate = "2026-11-01", unit = unit, startAyahId = 1, endAyahId = 6236,
        dailyAmount = daily, progressAyahId = 0, status = status,
    )

    private fun qlog(planId: String, met: Boolean, pages: Int = 0) =
        QuranPlanLogEntity(id = "${planId}_2026-11-04", planId = planId, date = "2026-11-04", pagesRead = pages, targetMet = met)

    @Test fun quranSpecificPlan() {
        val plans = listOf(plan("p1"), plan("p2"))
        assertTrue(DerivedCompletion.quran(plans, listOf(qlog("p1", true)), "p1").done)
        assertFalse(DerivedCompletion.quran(plans, listOf(qlog("p1", true)), "p2").done)
        val r = DerivedCompletion.quran(plans, listOf(qlog("p2", false, pages = 2)), "p2")
        assertFalse(r.done)
        assertTrue(r.detail!!.contains("٥ صفحات"))
        assertTrue(r.detail!!.contains("٢"))
        assertEquals("الورد المرتبط غير موجود", DerivedCompletion.quran(plans, emptyList(), "zzz").detail)
    }

    @Test fun quranAnyPlanWhenRefBlank() {
        val plans = listOf(plan("p1"), plan("p2"))
        assertTrue(DerivedCompletion.quran(plans, listOf(qlog("p2", true)), "").done)
        assertFalse(DerivedCompletion.quran(plans, listOf(qlog("p2", false)), "").done)
        assertEquals("لا يوجد ورد نشط", DerivedCompletion.quran(listOf(plan("p1", status = "PAUSED")), emptyList(), "").detail)
        // سجل لخطة محذوفة لا يُحتسب
        assertFalse(DerivedCompletion.quran(listOf(plan("p1").copy(deleted = true)), listOf(qlog("p1", true)), "").done)
    }

    @Test fun evaluateManualAndDerived() {
        val manual = task("m")
        val worship = WorshipDay(D, prayers = Prayer.values().map { prayer(it, PrayerStatus.HOME) })
        val pr = task("p", link = TaskLink.PRAYERS)
        val none = evaluateTask(manual, emptySet(), worship) { null }
        assertFalse(none.done)
        assertFalse(none.derived)
        assertTrue(evaluateTask(manual, setOf("m"), worship) { null }.done)
        val d = evaluateTask(pr, emptySet(), worship) { null }
        assertTrue(d.derived)
        assertTrue(d.done)
        // علامة يدوية لا تجعل المهمة المرتبطة منجزة
        val notDone = evaluateTask(pr, setOf("p"), WorshipDay(D)) { null }
        assertTrue(notDone.derived)
        assertFalse(notDone.done)
    }

    @Test fun evaluateAdhkarUsesCategoryOfLink() {
        val worship = WorshipDay(D, adhkar = listOf(adh("e1", true, "evening"), adh("m1", true, "morning")))
        val lookup: (String) -> List<String>? = { cat -> if (cat == "evening") listOf("e1") else listOf("m1", "m2") }
        assertTrue(evaluateTask(task("a", link = TaskLink.ADHKAR_EVENING), emptySet(), worship, lookup).done)
        assertFalse(evaluateTask(task("b", link = TaskLink.ADHKAR_MORNING), emptySet(), worship, lookup).done)
    }

    @Test fun sortingTimedFirstThenPriorityThenOrder() {
        fun tt(t: TaskEntity) = TodayTask(t, false, false)
        val list = listOf(
            tt(task("low", priority = 0, sort = 0)),
            tt(task("high", priority = 2, sort = 5)),
            tt(task("late", time = "21:00")),
            tt(task("early", time = "06:30")),
        )
        assertEquals(listOf("early", "late", "high", "low"), sortTodayTasks(list).map { it.task.id })
    }

    // ---------------- المسودة والتحقق
    private fun draft() = TaskDraft(title = "ورد", startDate = D)

    @Test fun validation() {
        assertNull(validateDraft(draft()))
        assertNotNull(validateDraft(draft().copy(title = "  ")))
        assertNotNull(validateDraft(draft().copy(endDate = D.minusDays(1))))
        assertNotNull(validateDraft(draft().copy(repeat = TaskRepeat.WEEKLY, daysMask = 0)))
        assertNotNull(validateDraft(draft().copy(reminderEnabled = true, timeOfDay = null)))
        assertNull(validateDraft(draft().copy(reminderEnabled = true, timeOfDay = "07:00")))
    }

    @Test fun toEntityNormalizes() {
        val e = draft().copy(
            title = " قراءة ", link = TaskLink.PRAYERS, linkRefId = "p1", repeat = TaskRepeat.DAILY, daysMask = 5,
            timeOfDay = "7:05", priority = TaskPriority.HIGH, reminderEnabled = true, endDate = D.plusDays(3),
        ).toEntity("new-id", 123)
        assertEquals("new-id", e.id)
        assertEquals("قراءة", e.title)
        assertEquals("", e.linkRefId)
        assertEquals(127, e.daysMask)
        assertEquals("07:05", e.timeOfDay)
        assertEquals(2, e.priority)
        assertTrue(e.reminderEnabled)
        assertEquals(123L, e.createdAt)
        assertNull(draft().copy(repeat = TaskRepeat.NONE, endDate = D.plusDays(2)).toEntity("x", 1).endDate)
        assertFalse(draft().copy(reminderEnabled = true).toEntity("x", 1).reminderEnabled)
        assertEquals("p9", draft().copy(link = TaskLink.QURAN_PLAN, linkRefId = "p9").toEntity("x", 1).linkRefId)
    }

    @Test fun roundTripEntityDraft() {
        val e = task("r", repeat = TaskRepeat.WEEKLY, mask = 9, time = "05:00", link = TaskLink.QURAN_PLAN, ref = "p1")
        val back = e.toDraft().toEntity("ignored", 0)
        assertEquals(e.id, back.id)
        assertEquals(e.daysMask, back.daysMask)
        assertEquals(e.linkRefId, back.linkRefId)
        assertEquals(e.timeOfDay, back.timeOfDay)
    }

    // ---------------- المهام الجاهزة
    @Test fun presetsMatchSpec() {
        assertEquals(
            listOf("قراءة الورد", "أذكار الصباح", "أذكار المساء", "أذكار النوم", "الصلوات الخمس", "السنن الرواتب", "قراءة كتاب", "مهمة شخصية"),
            TaskPreset.values().map { it.titleAr },
        )
        assertEquals(TaskLink.QURAN_PLAN, TaskPreset.QURAN_WIRD.link)
        assertEquals(TaskLink.NONE, TaskPreset.BOOK.link)
        val e = TaskPreset.PRAYERS.toEntity(D, 3, 99)
        assertEquals("preset_PRAYERS", e.id)
        assertEquals("PRAYERS", e.preset)
        assertEquals(TaskLink.PRAYERS.name, e.link)
        assertEquals("2026-11-04", e.startDate)
        assertNotNull(TaskPreset.fromKey("RAWATIB"))
        assertNull(TaskPreset.fromKey("none"))
    }

    // ---------------- الرسائل
    @Test fun motivationVariesAndIsNeverEmpty() {
        val all = listOf(DayProgress(0, 0), DayProgress(3, 0), DayProgress(3, 1), DayProgress(3, 3))
        for (p in all) {
            val seen = (0L..10L).map { TaskMessages.motivation(p, it) }.toSet()
            assertTrue(seen.all { it.isNotBlank() })
            assertTrue(seen.size >= 2)
        }
        assertTrue(TaskMessages.motivation(DayProgress(3, 1), -7).isNotBlank())
    }

    @Test fun progressMath() {
        val p = DayProgress(4, 1)
        assertEquals(3, p.remaining)
        assertEquals(0.25f, p.ratio, 0.0001f)
        assertEquals(0f, DayProgress(0, 0).ratio, 0f)
    }
}
