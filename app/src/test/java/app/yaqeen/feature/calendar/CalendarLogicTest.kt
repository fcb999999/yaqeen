package app.yaqeen.feature.calendar

import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.CalendarEventEntity
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.TaskCompletionEntity
import app.yaqeen.feature.calendar.reminders.EventPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarLogicTest {
    @Test fun leadingBlanksWeekStartsSaturday() {
        assertEquals(0, CalendarMath.leadingBlanks(LocalDate.of(2026, 10, 3)))  // السبت
        assertEquals(1, CalendarMath.leadingBlanks(LocalDate.of(2026, 11, 1)))  // الأحد
        assertEquals(2, CalendarMath.leadingBlanks(LocalDate.of(2026, 6, 1)))   // الاثنين
        assertEquals(6, CalendarMath.leadingBlanks(LocalDate.of(2026, 5, 1)))   // الجمعة
        for (d in 0 until 14) {
            val date = LocalDate.of(2026, 3, 1).plusDays(d.toLong())
            val expected = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).indexOf(date.dayOfWeek)
            assertEquals(expected, CalendarMath.leadingBlanks(date))
        }
    }

    @Test fun gregorianMonthGrid() {
        val g = CalendarMath.buildMonth(CalendarMode.GREGORIAN, MonthKey(2026, 11), 0)
        assertEquals(30, g.days.size)
        assertEquals(LocalDate.of(2026, 11, 1), g.firstDay)
        assertEquals(LocalDate.of(2026, 11, 30), g.lastDay)
        assertEquals(1, g.leadingBlanks)
        assertEquals("نوفمبر ٢٠٢٦", g.title)
        // نوفمبر 2026 يمتد من جمادى الأولى إلى جمادى الآخرة 1448
        assertTrue(g.subtitle.contains("١٤٤٨"))
    }

    @Test fun hijriMonthGridMatchesCalendar() {
        val g = CalendarMath.buildMonth(CalendarMode.HIJRI, MonthKey(1447, 9), 0)
        assertEquals(HijriCalendar.lengthOfMonth(1447, 9), g.days.size)
        assertEquals(LocalDate.of(2026, 2, 18), g.firstDay)
        assertEquals("رمضان ١٤٤٧ هـ", g.title)
        assertTrue(g.subtitle.contains("فبراير"))
        assertEquals(9, HijriCalendar.fromGregorian(g.firstDay).month)
        assertEquals(1, HijriCalendar.fromGregorian(g.firstDay).day)
        // اليوم الأخير ما زال في رمضان واليوم التالي شوال
        assertEquals(9, HijriCalendar.fromGregorian(g.lastDay).month)
        assertEquals(10, HijriCalendar.fromGregorian(g.lastDay.plusDays(1)).month)
    }

    @Test fun adjustMovesHijriGrid() {
        val a0 = CalendarMath.buildMonth(CalendarMode.HIJRI, MonthKey(1447, 9), 0)
        val a1 = CalendarMath.buildMonth(CalendarMode.HIJRI, MonthKey(1447, 9), 1)
        assertEquals(a0.firstDay.minusDays(1), a1.firstDay)
        // في الوضع الميلادي يتغير وصف الهجري فقط
        val g0 = CalendarMath.buildMonth(CalendarMode.GREGORIAN, MonthKey(2026, 2), 0)
        val g2 = CalendarMath.buildMonth(CalendarMode.GREGORIAN, MonthKey(2026, 2), 2)
        assertEquals(g0.days, g2.days)
    }

    @Test fun monthOfAndShiftWrapYears() {
        assertEquals(MonthKey(2026, 11), CalendarMath.monthOf(LocalDate.of(2026, 11, 20), CalendarMode.GREGORIAN, 0))
        assertEquals(MonthKey(1447, 9), CalendarMath.monthOf(LocalDate.of(2026, 3, 1), CalendarMode.HIJRI, 0))
        assertEquals(MonthKey(2027, 1), CalendarMath.shift(MonthKey(2026, 12), CalendarMode.GREGORIAN, 1))
        assertEquals(MonthKey(2025, 12), CalendarMath.shift(MonthKey(2026, 1), CalendarMode.GREGORIAN, -1))
        assertEquals(MonthKey(1448, 1), CalendarMath.shift(MonthKey(1447, 12), CalendarMode.HIJRI, 1))
        assertEquals(MonthKey(1446, 12), CalendarMath.shift(MonthKey(1447, 1), CalendarMode.HIJRI, -1))
        assertEquals(MonthKey(2027, 11), CalendarMath.shift(MonthKey(2026, 11), CalendarMode.GREGORIAN, 12))
        // الحدود لا تتجاوز مدى التقويم المدعوم
        assertEquals(MonthKey(CalendarMath.MAX_HIJRI_YEAR, 12), CalendarMath.shift(MonthKey(CalendarMath.MAX_HIJRI_YEAR, 12), CalendarMode.HIJRI, 1))
        assertEquals(MonthKey(CalendarMath.MIN_GREGORIAN_YEAR, 1), CalendarMath.shift(MonthKey(CalendarMath.MIN_GREGORIAN_YEAR, 1), CalendarMode.GREGORIAN, -1))
    }

    @Test fun extremeMonthsStillBuild() {
        // نهايات المدى المسموح بها تُبنى دون استثناء
        CalendarMath.buildMonth(CalendarMode.HIJRI, MonthKey(CalendarMath.MIN_HIJRI_YEAR, 1), 2)
        CalendarMath.buildMonth(CalendarMode.HIJRI, MonthKey(CalendarMath.MAX_HIJRI_YEAR, 12), -2)
        CalendarMath.buildMonth(CalendarMode.GREGORIAN, MonthKey(CalendarMath.MIN_GREGORIAN_YEAR, 1), 2)
        CalendarMath.buildMonth(CalendarMode.GREGORIAN, MonthKey(CalendarMath.MAX_GREGORIAN_YEAR, 12), -2)
    }

    // ---------------- مؤشرات الإنجاز
    private val day = "2026-11-04"

    private fun indicators(
        prayers: List<PrayerLogEntity> = emptyList(),
        rawatib: List<RawatibLogEntity> = emptyList(),
        adhkar: List<AdhkarProgressEntity> = emptyList(),
        plan: List<QuranPlanLogEntity> = emptyList(),
        dayLogs: List<QuranDayLogEntity> = emptyList(),
        comps: List<TaskCompletionEntity> = emptyList(),
        sums: List<DailySummaryEntity> = emptyList(),
    ) = CalendarStats.indicators(prayers, rawatib, adhkar, plan, dayLogs, comps, sums)

    @Test fun indicatorsFromLogs() {
        val m = indicators(
            prayers = listOf(
                PrayerLogEntity("a", day, "FAJR", "MOSQUE"),
                PrayerLogEntity("b", day, "DHUHR", "HOME"),
                PrayerLogEntity("c", day, "ASR", "NOT_PRAYED"),
            ),
            adhkar = listOf(AdhkarProgressEntity("x", day, "morning", "m1", count = 1)),
            comps = listOf(TaskCompletionEntity("t_$day", "t", day)),
        )
        val i = m.getValue(day)
        assertEquals(2, i.prayers) // NOT_PRAYED لا يُحتسب تسجيلًا
        assertEquals(1, i.adhkarItems)
        assertEquals(1, i.tasks)
        assertFalse(i.wird)
        assertEquals(3, i.categories)
        assertNull(m["2026-11-05"])
    }

    @Test fun onlyNotPrayedOrDeletedMeansNoRecord() {
        val m = indicators(
            prayers = listOf(PrayerLogEntity("a", day, "FAJR", "NOT_PRAYED"), PrayerLogEntity("b", day, "ASR", "HOME", deleted = true)),
            rawatib = listOf(RawatibLogEntity("r", day, "FAJR_BEFORE", done = false)),
        )
        assertTrue(m.isEmpty())
    }

    @Test fun wirdFromPlanLogOrDayLog() {
        assertTrue(indicators(plan = listOf(QuranPlanLogEntity("p", "p", day, pagesRead = 1))).getValue(day).wird)
        assertTrue(indicators(dayLogs = listOf(QuranDayLogEntity(day, day, pagesRead = 2))).getValue(day).wird)
        assertTrue(indicators(plan = listOf(QuranPlanLogEntity("p", "p", "2026-11-03", targetMet = true))).containsKey("2026-11-03"))
    }

    @Test fun summaryFillsInWhenLogsAbsentAndMaxWhenBoth() {
        val sum = DailySummaryEntity(id = day, date = day, prayersMosque = 3, prayersHome = 1, rawatibDone = 2, tasksDone = 4, adhkarMorningDone = true)
        val m = indicators(sums = listOf(sum))
        val i = m.getValue(day)
        assertEquals(4, i.prayers)
        assertEquals(2, i.rawatib)
        assertEquals(4, i.tasks)
        assertEquals(1, i.adhkarItems)
        val merged = indicators(prayers = listOf(PrayerLogEntity("a", day, "FAJR", "MOSQUE")), sums = listOf(sum)).getValue(day)
        assertEquals(4, merged.prayers) // الأكبر
        // ملخص فارغ لا ينشئ يومًا مسجَّلًا
        assertTrue(indicators(sums = listOf(DailySummaryEntity(id = day, date = day))).isEmpty())
    }

    @Test fun monthStatsCountOnlyElapsedDaysAndStayNeutral() {
        val days = (1..30).map { LocalDate.of(2026, 11, it) }
        val ind = mapOf(
            "2026-11-01" to DayIndicator(prayers = 5, adhkarItems = 3),
            "2026-11-02" to DayIndicator(wird = true),
            "2026-11-20" to DayIndicator(prayers = 5), // مستقبل: لا يُحتسب
        )
        val s = CalendarStats.monthStats(days, ind, LocalDate.of(2026, 11, 10))
        assertEquals(10, s.daysCounted)
        assertEquals(2, s.daysWithRecord)
        assertEquals(5, s.prayersRecorded)
        assertEquals(1, s.adhkarDays)
        assertEquals(1, s.wirdDays)
        assertEquals("أيام فيها تسجيل: ٢ من ١٠", s.commitmentText())
        assertEquals("لا تسجيلات في هذا الشهر حتى الآن", CalendarStats.monthStats(days, emptyMap(), LocalDate.of(2026, 11, 10)).commitmentText())
        assertEquals("لم يبدأ هذا الشهر بعد", CalendarStats.monthStats(days, emptyMap(), LocalDate.of(2026, 10, 1)).commitmentText())
        // لا لوم ولا "فاتك"
        assertFalse(s.commitmentText().contains("فات"))
    }

    // ---------------- تذكيرات المناسبات
    private val zone = ZoneOffset.UTC
    private fun ms(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).toInstant(zone).toEpochMilli()

    @Test fun islamicRemindersOneDayBeforeAndOnDayAt0900() {
        val e = IslamicEvents.monthEvents(1447, 10, 0).first { it.key == IslamicEventKeys.EID_FITR }
        val r = EventPlanner.planIslamic(listOf(e), ms(e.date.minusDays(3), 0, 0), zone)
        assertEquals(2, r.size)
        assertEquals("event:islamic:EID_FITR:${e.date}:pre", r[0].key)
        assertEquals(ms(e.date.minusDays(1), 9, 0), r[0].triggerAtMillis)
        assertEquals("event:islamic:EID_FITR:${e.date}:day", r[1].key)
        assertEquals(ms(e.date, 9, 0), r[1].triggerAtMillis)
        assertEquals("events", r[0].channelId)
        assertEquals("calendar", r[0].route)
        assertTrue(r[0].title.startsWith("غدًا"))
        assertTrue(r[1].title.startsWith("اليوم"))
        // بعد مرور وقت التنبيه الأول يبقى تنبيه اليوم فقط
        assertEquals(1, EventPlanner.planIslamic(listOf(e), ms(e.date.minusDays(1), 10, 0), zone).size)
        assertTrue(EventPlanner.planIslamic(listOf(e), ms(e.date, 10, 0), zone).isEmpty())
    }

    @Test fun nonNotifiableEventsGetNoReminders() {
        val e = IslamicEvents.monthEvents(1447, 11, 0).first { it.key == IslamicEventKeys.MONTH_START }
        assertTrue(EventPlanner.planIslamic(listOf(e), 0, zone).isEmpty())
    }

    private fun personal(id: String, date: String, time: String?, reminder: Boolean, notes: String = "") =
        CalendarEventEntity(id = id, title = "موعد $id", notes = notes, date = date, timeOfDay = time, reminderEnabled = reminder)

    @Test fun personalReminders() {
        val now = ms(LocalDate.of(2026, 11, 4), 8, 0)
        val r = EventPlanner.planPersonal(
            listOf(
                personal("a", "2026-11-05", "14:30", true),
                personal("b", "2026-11-05", null, true, notes = "ملاحظة"),
                personal("c", "2026-11-05", "10:00", false),
                personal("d", "2026-11-03", "10:00", true),
                personal("e", "2026-11-05", "10:00", true).copy(deleted = true),
            ),
            now, zone,
        )
        assertEquals(listOf("event:personal:a", "event:personal:b"), r.map { it.key })
        assertEquals(ms(LocalDate.of(2026, 11, 5), 14, 30), r[0].triggerAtMillis)
        assertEquals(ms(LocalDate.of(2026, 11, 5), 9, 0), r[1].triggerAtMillis)
        assertEquals("ملاحظة", r[1].text)
        assertTrue(r.all { it.key.startsWith("event:") })
    }
}
