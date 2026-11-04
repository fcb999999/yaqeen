package app.yaqeen.feature.home

import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.feature.journey.AdhkarItemIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeSectionsTest {
    private val default = HomeSections.DEFAULT_ORDER

    @Test fun emptyMeansDefaultAndNoneMeansNothing() {
        assertEquals(default, HomeSections.visible(""))
        assertEquals(default, HomeSections.visible("   "))
        assertEquals(emptyList<String>(), HomeSections.visible(HomeSections.NONE))
    }

    @Test fun parsesOrderDropsUnknownAndDuplicates() {
        assertEquals(listOf("tasks", "adhkar"), HomeSections.visible("tasks, adhkar,tasks,bogus"))
        assertEquals("كل المفاتيح مجهولة = الافتراضي", default, HomeSections.visible("x,y"))
    }

    @Test fun layoutPutsHiddenAfterVisibleInDefaultOrder() {
        val l = HomeSections.layout("tasks,adhkar")
        assertEquals(default.size, l.size)
        assertEquals(listOf("tasks", "adhkar"), l.filter { it.visible }.map { it.key })
        assertEquals(l.take(2).map { it.key }, listOf("tasks", "adhkar"))
        assertEquals(default.filter { it != "tasks" && it != "adhkar" }, l.drop(2).map { it.key })
        assertTrue(l.drop(2).none { it.visible })
    }

    @Test fun toggleHideThenShowReturnsToDefault() {
        val hidden = HomeSections.toggle("", HomeSections.EVENT)
        assertFalse(HomeSections.EVENT in HomeSections.visible(hidden))
        assertEquals(default.size - 1, HomeSections.visible(hidden).size)
        val back = HomeSections.toggle(hidden, HomeSections.EVENT)
        // الإظهار يعيده في مكانه الافتراضي (آخر) فيعود التخزين فارغًا
        assertEquals("", back)
    }

    @Test fun hidingEverythingStoresNoneNotEmpty() {
        var csv = ""
        default.forEach { csv = HomeSections.toggle(csv, it) }
        assertEquals(HomeSections.NONE, csv)
        assertTrue(HomeSections.visible(csv).isEmpty())
        // إظهار قسم واحد بعدها
        assertEquals(listOf(HomeSections.TASKS), HomeSections.visible(HomeSections.toggle(csv, HomeSections.TASKS)))
    }

    @Test fun moveUpAndDownWithinVisibleOnly() {
        val up = HomeSections.move("", HomeSections.PRAYERS, -1)
        assertEquals(listOf("overall", "prayers", "next_prayer"), HomeSections.visible(up).take(3))
        val down = HomeSections.move(up, HomeSections.PRAYERS, 1)
        assertEquals("", down)
        // أول عنصر لا يتحرك للأعلى، وآخر مرئي لا يتحرك للأسفل
        assertEquals("", HomeSections.move("", HomeSections.OVERALL, -1))
        assertEquals("", HomeSections.move("", HomeSections.EVENT, 1))
        // لا يمكن نقل قسم مخفي
        val hiddenCsv = HomeSections.toggle("", HomeSections.TASKS)
        assertEquals(hiddenCsv, HomeSections.move(hiddenCsv, HomeSections.TASKS, -1))
        // ولا يتحرك آخر مرئي للأسفل فوق قسم مخفي (EVENT مخفي، TASKS آخر مرئي)
        val noEvent = HomeSections.toggle("", HomeSections.EVENT)
        assertEquals(noEvent, HomeSections.move(noEvent, HomeSections.TASKS, 1))
    }

    @Test fun encodeRoundTrip() {
        val csv = "event,overall,tasks"
        assertEquals(csv, HomeSections.encode(HomeSections.layout(csv)))
    }

    @Test fun everySectionHasArabicTitle() {
        default.forEach { assertTrue(HomeSections.title(it) != it) }
    }
}

class HomeTextTest {
    @Test fun greetingByHour() {
        assertEquals("طاب ليلك", HomeText.greeting(2))
        assertEquals("صباح الخير", HomeText.greeting(4))
        assertEquals("صباح الخير", HomeText.greeting(11))
        assertEquals("طاب يومك", HomeText.greeting(13))
        assertEquals("مساء الخير", HomeText.greeting(17))
        assertEquals("مساء الخير", HomeText.greeting(23))
        assertEquals("طاب ليلك", HomeText.greeting(0))
    }

    @Test fun clock12() {
        val z = ZoneId.of("Asia/Riyadh")
        assertEquals("١٢:٠٥ ص", HomeText.clock12(ZonedDateTime.of(2026, 11, 4, 0, 5, 0, 0, z)))
        assertEquals("١٢:٣٠ م", HomeText.clock12(ZonedDateTime.of(2026, 11, 4, 12, 30, 0, 0, z)))
        assertEquals("٠٣:٤٥ م", HomeText.clock12(ZonedDateTime.of(2026, 11, 4, 15, 45, 0, 0, z)))
    }

    @Test fun countdown() {
        assertEquals("٠٢:١٤:٣٠", HomeText.countdown(Duration.ofSeconds(2 * 3600 + 14 * 60 + 30)))
        assertEquals("٠٠:٠٠:٠٠", HomeText.countdown(Duration.ZERO))
        assertEquals("٠٠:٠٠:٠٠", HomeText.countdown(Duration.ofSeconds(-5)))
        assertEquals("٢٥:٠٠:٠٠", HomeText.countdown(Duration.ofHours(25)))
    }

    @Test fun spokenRemaining() {
        assertEquals("بعد ساعتين و١٠ دقائق", HomeText.spokenRemaining(Duration.ofMinutes(130)))
        assertEquals("بعد ساعة واحدة", HomeText.spokenRemaining(Duration.ofMinutes(60)))
        assertEquals("بعد ٥ دقائق", HomeText.spokenRemaining(Duration.ofMinutes(5)))
        assertEquals("بعد أقل من دقيقة", HomeText.spokenRemaining(Duration.ofSeconds(20)))
    }

    @Test fun daysUntil() {
        assertEquals("اليوم", HomeText.daysUntil(0))
        assertEquals("غدًا", HomeText.daysUntil(1))
        assertEquals("بعد يومين", HomeText.daysUntil(2))
        assertEquals("بعد ٣ أيام", HomeText.daysUntil(3))
        assertEquals("بعد ١٢ يومًا", HomeText.daysUntil(12))
    }

    @Test fun motivationIsCalmForAnyRatioAndSeed() {
        val bad = listOf("مقصّر", "فاشل", "كسول", "لم تفعل")
        for (r in listOf(0f, 0.2f, 0.7f, 1f)) for (seed in -2L..4L) {
            val m = HomeText.motivation(r, seed)
            assertTrue(m.isNotBlank())
            bad.forEach { assertFalse(m.contains(it)) }
        }
        assertTrue(HomeText.motivation(1f, 0).contains("تقبّل الله") || HomeText.motivation(1f, 0).contains("بحمد الله"))
    }
}

class PrayersTodayTest {
    private fun row(p: String, st: String, deleted: Boolean = false, upd: Long = 1) =
        PrayerLogEntity("2026-11-04_$p", "2026-11-04", p, st, deleted = deleted, updatedAt = upd)

    @Test fun chipsAlwaysFiveInOrderWithMissingAsNull() {
        val chips = PrayersToday.chips(listOf(row("ASR", "MOSQUE"), row("FAJR", "NOT_PRAYED")))
        assertEquals(Prayer.entries, chips.map { it.prayer })
        assertEquals(PrayerStatus.NOT_PRAYED, chips[0].status)
        assertNull(chips[1].status)
        assertEquals(PrayerStatus.MOSQUE, chips[2].status)
        assertEquals(1, PrayersToday.prayed(chips)) // لم أصلِّ بعد وغير المسجّل ليسا مؤداة
    }

    @Test fun deletedRowsMeanUnrecordedAndUnknownStatusIsNull() {
        val chips = PrayersToday.chips(listOf(row("FAJR", "MOSQUE", deleted = true), row("DHUHR", "???")))
        assertNull(chips[0].status)
        assertNull(chips[1].status)
        assertEquals(0, PrayersToday.prayed(chips))
    }

    @Test fun latestRowWinsAndIdIsDeterministic() {
        val chips = PrayersToday.chips(listOf(row("ISHA", "HOME", upd = 1), row("ISHA", "MOSQUE", upd = 5)))
        assertEquals(PrayerStatus.MOSQUE, chips[4].status)
        assertEquals("2026-11-04_ISHA", PrayersToday.id("2026-11-04", Prayer.ISHA))
        assertEquals(2, PrayersToday.prayed(PrayersToday.chips(listOf(row("ISHA", "HOME"), row("FAJR", "MOSQUE")))))
    }
}

class RawatibSummaryTest {
    private fun r(id: String, rakaat: Int, done: Boolean = true, deleted: Boolean = false) =
        RawatibLogEntity("d_$id", "2026-11-04", id, done, rakaat, deleted = deleted)

    @Test fun emptyIsZeroOfFive() {
        val s = RawatibSummary.of(emptyList())
        assertEquals(0, s.doneDefaults); assertEquals(5, s.totalDefaults); assertEquals(0, s.rakaat)
    }

    @Test fun countsDefaultsRakaatAndCustomSeparately() {
        val s = RawatibSummary.of(listOf(
            r("FAJR_BEFORE", 2), r("DHUHR_BEFORE", 0), // 0 => يؤخذ من تعريف السنة (4)
            r("ISHA_AFTER", 2, done = false), r("MAGHRIB_AFTER", 2, deleted = true),
            r("custom:abc", 3),
        ))
        assertEquals(2, s.doneDefaults)
        assertEquals(1, s.customDone)
        assertEquals(2 + 4 + 3, s.rakaat)
    }
}

class AdhkarTodayTest {
    private val index = AdhkarItemIndex(mapOf("morning" to setOf("m1", "m2"), "evening" to setOf("e1"), "sleep" to setOf("s1")))
    private fun p(item: String, cat: String, done: Boolean, count: Int = 1, target: Int = 1) =
        AdhkarProgressEntity("d_$item", "2026-11-04", cat, item, count, target, done)

    @Test fun rowsReportDoneOverTotalFromCatalog() {
        val rows = AdhkarToday.rows(index, listOf(p("m1", "morning", true), p("m2", "morning", false, 0), p("e1", "evening", true)))
        assertEquals(listOf("morning", "evening", "sleep"), rows.map { it.categoryId })
        assertEquals(1, rows[0].done); assertEquals(2, rows[0].total)
        assertTrue(rows[0].started); assertFalse(rows[0].complete)
        assertTrue(rows[1].complete)
        assertFalse(rows[2].started)
        assertEquals(0.5f, rows[0].ratio, 1e-6f)
    }

    @Test fun staleItemsNotInCatalogAndDeletedAreIgnored() {
        val rows = AdhkarToday.rows(index, listOf(
            p("old", "morning", true),
            AdhkarProgressEntity("x", "2026-11-04", "morning", "m1", 1, 1, true, deleted = true),
        ))
        assertEquals(0, rows[0].done)
    }

    @Test fun emptyCatalogMeansNotCompleteAndZeroRatio() {
        val rows = AdhkarToday.rows(AdhkarItemIndex.EMPTY, listOf(p("m1", "morning", true)))
        assertFalse(rows[0].complete)
        assertEquals(0f, rows[0].ratio, 0f)
    }
}

class WirdTodayTest {
    private val date = "2026-11-04"
    private fun plan(unit: String, daily: Int, start: Int = 1, end: Int = 100, progress: Int = 0, id: String = "p") = QuranPlanEntity(
        id = id, name = "ورد $id", startDate = "2026-01-01", unit = unit, startAyahId = start, endAyahId = end,
        dailyAmount = daily, progressAyahId = progress,
    )
    private val pageOf = { ayah: Int -> (ayah - 1) / 10 + 1 }

    @Test fun pageUnitProgressLabelAndRatio() {
        val items = WirdToday.items(
            listOf(plan("PAGE", 5)),
            listOf(QuranPlanLogEntity("p_$date", "p", date, pagesRead = 2)),
            date, 604, pageOf,
        )
        assertEquals("٢ من ٥ صفحات", items[0].todayLabel)
        assertEquals(0.4f, items[0].ratio, 1e-6f)
        assertFalse(items[0].targetMet)
    }

    @Test fun targetMetForcesFullRatio() {
        val items = WirdToday.items(listOf(plan("AYAH", 10)), listOf(QuranPlanLogEntity("p_$date", "p", date, ayahsRead = 3, targetMet = true)), date, 604, pageOf)
        assertEquals(1f, items[0].ratio, 0f)
        assertTrue(items[0].targetMet)
    }

    @Test fun noLogTodayIsZeroNotFailure() {
        val items = WirdToday.items(listOf(plan("PAGE", 2)), listOf(QuranPlanLogEntity("p_x", "p", "2026-11-03", pagesRead = 9, targetMet = true)), date, 604, pageOf)
        assertEquals(0f, items[0].ratio, 0f)
        assertEquals("٠ من صفحتين", items[0].todayLabel)
    }

    @Test fun juzUnitUsesPagesPerJuz() {
        val items = WirdToday.items(listOf(plan("JUZ", 1)), listOf(QuranPlanLogEntity("p_$date", "p", date, pagesRead = 10)), date, 600, pageOf)
        assertEquals(0.5f, items[0].ratio, 1e-6f) // 600/30 = 20 صفحة لكل جزء
        assertEquals("٠٫٥ من جزء واحد", items[0].todayLabel)
    }

    @Test fun deletedPlansAndLogsIgnoredAndRemainingPages() {
        val items = WirdToday.items(
            listOf(plan("PAGE", 1, progress = 55), plan("PAGE", 1, id = "gone").copy(deleted = true)),
            listOf(QuranPlanLogEntity("p_$date", "p", date, pagesRead = 1, targetMet = true, deleted = true)),
            date, 604, pageOf,
        )
        assertEquals(1, items.size)
        assertFalse(items[0].targetMet)
        assertEquals(5, items[0].remainingPages) // الآية 56 (ص6) حتى 100 (ص10)
        assertNull(WirdToday.remainingPages(plan("PAGE", 1), null))
        assertEquals(0, WirdToday.remainingPages(plan("PAGE", 1, progress = 100), pageOf))
        assertEquals(10, WirdToday.remainingPages(plan("PAGE", 1, progress = 0), pageOf))
    }
}

class OverallCompletionTest {
    private fun ad(done: Int, total: Int, cat: String = "morning") = AdhkarStatusRow(cat, cat, done, total)
    private fun wird(met: Boolean) = WirdTodayItem(
        QuranPlanEntity("p", "n", "2026-01-01", unit = "PAGE", startAyahId = 1, endAyahId = 10, dailyAmount = 1, progressAyahId = 0),
        "", if (met) 1f else 0f, met, null,
    )

    @Test fun baselineIsPrayersAndRawatibOnlyWhenNothingElseExists() {
        val o = OverallCompletion.compute(0, 0, emptyList(), emptyList(), 0, 0)
        assertEquals(10, o.total); assertEquals(0, o.done); assertEquals(0f, o.ratio, 0f)
    }

    @Test fun includesAdhkarWirdAndManualTasks() {
        val o = OverallCompletion.compute(
            prayed = 5, rawatibDone = 2,
            adhkarRows = listOf(ad(3, 3, "morning"), ad(1, 2, "evening"), ad(0, 0, "sleep")), // غير المعروف لا يُحسب
            wird = listOf(wird(true), wird(false)),
            manualTasksTotal = 3, manualTasksDone = 2,
        )
        assertEquals(10 + 2 + 2 + 3, o.total)
        assertEquals(5 + 2 + 1 + 1 + 2, o.done)
    }

    @Test fun clampsOverflowAndFullDayIsOne() {
        val o = OverallCompletion.compute(9, 9, listOf(ad(2, 2)), listOf(wird(true)), 2, 5)
        assertEquals(o.total, o.done)
        assertEquals(1f, o.ratio, 0f)
    }
}
