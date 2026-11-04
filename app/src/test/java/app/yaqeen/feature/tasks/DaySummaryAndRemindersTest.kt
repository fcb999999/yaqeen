package app.yaqeen.feature.tasks

import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.TaskEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.feature.tasks.reminders.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DaySummaryAndRemindersTest {
    private val D = LocalDate.of(2026, 11, 4)
    private val zone = ZoneOffset.UTC

    private fun t(id: String, done: Boolean, reminder: Boolean = false, time: String? = null, notes: String = "") =
        TodayTask(TaskEntity(id = id, title = "مهمة $id", startDate = "2026-11-01", reminderEnabled = reminder, timeOfDay = time, notes = notes), done, false)

    private val cats = listOf(
        AdhkarCategoryInfo("morning", "أذكار الصباح", listOf("m1", "m2")),
        AdhkarCategoryInfo("evening", "أذكار المساء", listOf("e1", "e2")),
        AdhkarCategoryInfo("sleep", "أذكار النوم", listOf("s1")),
        AdhkarCategoryInfo("after_prayer", "أذكار بعد الصلاة", listOf("a1")),
    )

    @Test fun emptyDayEverythingIsUnrecordedNotMissed() {
        val s = DayEndSummary.build(D, emptyList(), WorshipDay(D), cats)
        assertEquals(5, s.prayersUnrecorded.size)
        assertEquals(5, s.rawatibUnrecorded.size)
        assertEquals(listOf("morning", "evening", "sleep"), s.adhkarIncomplete.map { it.categoryId }) // بعد الصلاة لا تدخل
        assertFalse(s.allClear)
        assertEquals(0, s.prayersRecorded)
        // صياغة "غير مسجّل" وليس "فات"
        val text = DayEndSummary.notificationText(s)
        assertTrue(text.contains("غير مسجّلة"))
        assertFalse(text.contains("فات"))
    }

    @Test fun summaryCountsTasksPrayersAndAdhkar() {
        val worship = WorshipDay(
            D,
            prayers = listOf(
                PrayerLogEntity("a", "2026-11-04", Prayer.FAJR.name, "MOSQUE"),
                PrayerLogEntity("b", "2026-11-04", Prayer.DHUHR.name, "HOME"),
                PrayerLogEntity("c", "2026-11-04", Prayer.ASR.name, "NOT_PRAYED"),
            ),
            rawatib = listOf(RawatibLogEntity("r", "2026-11-04", Sunnah.FAJR_BEFORE.name, true)),
            adhkar = listOf(
                AdhkarProgressEntity("1", "2026-11-04", "morning", "m1", completed = true),
                AdhkarProgressEntity("2", "2026-11-04", "morning", "m2", completed = true),
                AdhkarProgressEntity("3", "2026-11-04", "evening", "e1", completed = true),
            ),
        )
        val s = DayEndSummary.build(D, listOf(t("a", true), t("b", false), t("c", false)), worship, cats)
        assertEquals(1, s.tasksDone.size)
        assertEquals(2, s.tasksPending.size)
        assertEquals(2, s.prayersRecorded)
        assertEquals(listOf(Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA), s.prayersUnrecorded)
        assertEquals(1, s.rawatibRecorded)
        assertEquals(4, s.rawatibUnrecorded.size)
        // الصباح مكتمل، المساء ١ من ٢، النوم ٠ من ١
        assertEquals(listOf("evening", "sleep"), s.adhkarIncomplete.map { it.categoryId })
        assertEquals(1, s.adhkarIncomplete[0].done)
        assertEquals(2, s.adhkarIncomplete[0].total)
        val text = DayEndSummary.notificationText(s)
        assertTrue(text.contains("المهام: ١ من ٣"))
        assertTrue(text.contains("صلوات غير مسجّلة: ٣"))
    }

    private fun plan(id: String, unit: QuranPlanUnit, daily: Int, status: String = "ACTIVE") = QuranPlanEntity(
        id = id, name = "ورد $id", startDate = "2026-11-01", unit = unit.name, startAyahId = 1, endAyahId = 6236,
        dailyAmount = daily, progressAyahId = 0, status = status,
    )

    @Test fun wirdRemainingByUnit() {
        val worship = WorshipDay(
            D,
            plans = listOf(
                plan("p", QuranPlanUnit.PAGE, 5),
                plan("a", QuranPlanUnit.AYAH, 20),
                plan("j", QuranPlanUnit.JUZ, 1),
                plan("done", QuranPlanUnit.PAGE, 2),
                plan("paused", QuranPlanUnit.PAGE, 2, status = "PAUSED"),
            ),
            quranLogs = listOf(
                QuranPlanLogEntity("p_d", "p", "2026-11-04", pagesRead = 2),
                QuranPlanLogEntity("a_d", "a", "2026-11-04", ayahsRead = 20, targetMet = false),
                QuranPlanLogEntity("done_d", "done", "2026-11-04", pagesRead = 2, targetMet = true),
            ),
        )
        val s = DayEndSummary.build(D, emptyList(), worship, cats)
        assertEquals(4, s.wird.size) // الموقوف لا يظهر
        val byId = s.wird.associateBy { it.planId }
        assertEquals("بقي ٣ صفحات", byId.getValue("p").remainingText)
        assertEquals("لم يُسجَّل بلوغ المقدار اليومي", byId.getValue("a").remainingText)
        assertEquals("المقدار اليومي جزء واحد", byId.getValue("j").remainingText)
        assertNull(byId.getValue("done").remainingText)
        assertEquals(3, s.wirdRemaining.size)
    }

    @Test fun allClearDay() {
        val worship = WorshipDay(
            D,
            prayers = Prayer.values().map { PrayerLogEntity(it.name, "2026-11-04", it.name, "MOSQUE") },
            rawatib = Sunnah.values().map { RawatibLogEntity(it.name, "2026-11-04", it.name, true) },
            adhkar = listOf("m1", "m2", "e1", "e2", "s1").map { AdhkarProgressEntity(it, "2026-11-04", "x", it, completed = true) },
        )
        val s = DayEndSummary.build(D, listOf(t("a", true)), worship, cats)
        assertTrue(s.allClear)
        assertTrue(DayEndSummary.notificationText(s).contains("لا شيء متبق"))
    }

    // ---------------- التذكيرات
    private fun ms(date: LocalDate, hh: Int, mm: Int) = date.atTime(hh, mm).toInstant(zone).toEpochMilli()

    @Test fun taskRemindersOnlyForEnabledTimedPendingFuture() {
        val now = ms(D, 8, 0)
        val days = listOf(
            D to listOf(
                t("future", false, reminder = true, time = "18:00"),
                t("past", false, reminder = true, time = "07:00"),
                t("done", true, reminder = true, time = "19:00"),
                t("off", false, reminder = false, time = "19:00"),
                t("notime", false, reminder = true, time = null),
            ),
            D.plusDays(1) to listOf(t("future", false, reminder = true, time = "18:00", notes = "ملاحظة")),
        )
        val r = ReminderPlanner.planTasks(days, now, zone)
        assertEquals(listOf("task:future:2026-11-04", "task:future:2026-11-05"), r.map { it.key })
        assertEquals(ms(D, 18, 0), r[0].triggerAtMillis)
        assertEquals("ملاحظة", r[1].text)
        assertTrue(r.all { it.key.startsWith("task:") && it.channelId == "tasks" && it.route == "tasks" })
    }

    @Test fun dayEndPlansTwoDaysWithGenericText() {
        val now = ms(D, 12, 0)
        val r = ReminderPlanner.planDayEnd(D, "23:00", now, zone)
        assertEquals(listOf("dayend:2026-11-04", "dayend:2026-11-05"), r.map { it.key })
        assertEquals(ms(D, 23, 0), r[0].triggerAtMillis)
        assertEquals("day_end", r[0].channelId)
        assertEquals("tasks/day_summary?date=2026-11-04", r[0].route)
        assertEquals(DayEndSummary.GENERIC_NOTIFICATION_TEXT, r[0].text)
        assertTrue(r[0].text.contains("ما لم يُسجَّل"))
    }

    @Test fun dayEndSkipsPastAndHandlesBadTime() {
        val now = ms(D, 23, 30)
        val r = ReminderPlanner.planDayEnd(D, "23:00", now, zone)
        assertEquals(listOf("dayend:2026-11-05"), r.map { it.key })
        // وقت غير صالح -> الافتراضي 23:00
        val bad = ReminderPlanner.planDayEnd(D, "xx", ms(D, 12, 0), zone)
        assertEquals(ms(D, 23, 0), bad[0].triggerAtMillis)
    }

    @Test fun dayEndAfterMidnightBelongsToPreviousDay() {
        // 00:30 يُطلق في اليوم التالي لكنه يخص اليوم المنتهي
        val r = ReminderPlanner.planDayEnd(D, "00:30", ms(D, 12, 0), zone)
        assertEquals("dayend:2026-11-04", r[0].key)
        assertEquals(ms(D.plusDays(1), 0, 30), r[0].triggerAtMillis)
        assertEquals("tasks/day_summary?date=2026-11-04", r[0].route)
        // ملخص أمس الذي يحين وقته بعد منتصف الليل اليوم
        val early = ReminderPlanner.planDayEnd(D, "00:30", ms(D, 0, 10), zone)
        assertEquals("dayend:2026-11-03", early[0].key)
    }
}
