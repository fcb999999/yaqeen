package app.yaqeen.feature.adhkar

import app.yaqeen.data.adhkar.AdhkarCatalogLoader
import app.yaqeen.data.adhkar.AdhkarCategory
import app.yaqeen.data.adhkar.AdhkarItem
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.feature.adhkar.reminders.AdhkarReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AdhkarLogicTest {
    private fun row(date: String, item: String, completed: Boolean, count: Int = 1, target: Int = 1, deleted: Boolean = false, cat: String = "morning", updatedAt: Long = 0) =
        AdhkarProgressEntity(id = "${date}_$item", date = date, categoryId = cat, itemId = item, count = count, target = target, completed = completed, deleted = deleted, updatedAt = updatedAt)

    private val day = "2026-11-04"

    // ---------------- AdhkarStats
    @Test fun categoryCompletedOnlyWhenAllItemsCompleted() {
        val ids = listOf("a", "b", "c")
        assertFalse(AdhkarStats.isCategoryCompleted(ids, emptyList()))
        assertFalse(AdhkarStats.isCategoryCompleted(ids, listOf(row(day, "a", true), row(day, "b", true))))
        assertFalse(AdhkarStats.isCategoryCompleted(ids, listOf(row(day, "a", true), row(day, "b", true), row(day, "c", false, count = 0))))
        assertTrue(AdhkarStats.isCategoryCompleted(ids, ids.map { row(day, it, true) }))
    }

    @Test fun emptyCategoryIsNeverCompleted() {
        assertFalse(AdhkarStats.isCategoryCompleted(emptyList(), listOf(row(day, "a", true))))
    }

    @Test fun deletedRowsAreIgnored() {
        assertFalse(AdhkarStats.isCategoryCompleted(listOf("a"), listOf(row(day, "a", true, deleted = true))))
        assertEquals(0, AdhkarStats.completedItems(listOf(row(day, "a", true, deleted = true))))
    }

    @Test fun completedItemsCountsDistinctAndPerCategory() {
        val rows = listOf(row(day, "a", true), row(day, "b", false), row(day, "x", true, cat = "evening"))
        assertEquals(2, AdhkarStats.completedItems(rows))
        assertEquals(1, AdhkarStats.completedItems(listOf("a", "b"), rows))
        val p = AdhkarStats.categoryProgress(listOf("a", "b"), rows)
        assertEquals(1, p.done); assertEquals(2, p.total); assertFalse(p.isComplete); assertEquals(0.5f, p.ratio, 0.001f)
    }

    @Test fun completedCategoryIdsAndDays() {
        val cats = listOf(
            AdhkarCategory("m", "م", listOf(AdhkarItem("m1", "t"), AdhkarItem("m2", "t"))),
            AdhkarCategory("e", "ع", listOf(AdhkarItem("e1", "t"))),
        )
        val rows = listOf(row(day, "m1", true), row(day, "m2", true), row(day, "e1", false))
        assertEquals(setOf("m"), AdhkarStats.completedCategoryIds(cats, rows))
        val range = rows + listOf(row("2026-11-05", "m1", true), row("2026-11-05", "m2", false))
        assertEquals(setOf(day), AdhkarStats.completedDays(listOf("m1", "m2"), range))
    }

    // ---------------- counter
    @Test fun counterTapsDownToZeroAndStops() {
        var count = 0
        repeat(5) { count = AdhkarCounter.afterTap(count, 3) }
        assertEquals(3, count)
        assertEquals(0, AdhkarCounter.remaining(3, 3))
        assertTrue(AdhkarCounter.isCompleted(3, 3))
        assertEquals(2, AdhkarCounter.remaining(1, 3))
    }

    @Test fun targetParsingAcceptsArabicDigitsAndRejectsInvalid() {
        assertEquals(33, AdhkarCounter.parseTarget("33"))
        assertEquals(33, AdhkarCounter.parseTarget("٣٣"))
        assertEquals(10, AdhkarCounter.parseTarget(" ۱۰ "))
        assertNull(AdhkarCounter.parseTarget(""))
        assertNull(AdhkarCounter.parseTarget("0"))
        assertNull(AdhkarCounter.parseTarget("abc"))
        assertNull(AdhkarCounter.parseTarget("-5"))
        assertNull(AdhkarCounter.parseTarget("1001"))
        assertNull(AdhkarCounter.parseTarget("99999999999999"))
        assertEquals(1000, AdhkarCounter.parseTarget("1000"))
    }

    @Test fun effectiveTargetUsesCustomOrCatalogAndClamps() {
        assertEquals(33, AdhkarCounter.effectiveTarget(33, null))
        assertEquals(10, AdhkarCounter.effectiveTarget(33, 10))
        assertEquals(1, AdhkarCounter.effectiveTarget(0, null))
        assertEquals(1000, AdhkarCounter.effectiveTarget(1, 5000))
    }

    // ---------------- settings keys / positions
    @Test fun keysUseSpecifiedNames() {
        assertEquals("adhkar_target_morning_01", AdhkarKeys.target("morning_01", 1).key)
        assertEquals(3, AdhkarKeys.target("morning_01", 3).default)
        assertEquals("adhkar_pos_sleep", AdhkarKeys.position("sleep").key)
        assertEquals("user:adhkar_pos_sleep", AdhkarKeys.position("sleep").id)
    }

    @Test fun targetsAndPositionsParsedFromRawSettings() {
        val raw = mapOf(
            "user:adhkar_target_morning_01" to "7",
            "user:adhkar_target_bad" to "x",
            "user:adhkar_pos_morning" to "2026-11-04|morning_05|123",
            "user:adhkar_pos_bad" to "garbage",
            "user:other" to "1",
        )
        assertEquals(mapOf("morning_01" to 7), AdhkarKeys.targetsFrom(raw))
        assertEquals(mapOf("morning" to AdhkarPosition("2026-11-04", "morning_05", 123)), AdhkarKeys.positionsFrom(raw))
    }

    @Test fun positionEncodeDecodeRoundTrip() {
        val p = AdhkarPosition(day, "morning_07", 42L)
        assertEquals(p, AdhkarPosition.decode(p.encode()))
        assertEquals(AdhkarPosition(day, "x", 0), AdhkarPosition.decode("$day|x"))
        assertNull(AdhkarPosition.decode(""))
        assertNull(AdhkarPosition.decode(null))
        assertNull(AdhkarPosition.decode("|x|1"))
    }

    @Test fun resumeStartPage() {
        val ids = listOf("a", "b", "c")
        val saved = AdhkarPosition(day, "b", 1)
        assertEquals(2, AdhkarResume.startPage(2, ids, saved, day, false))          // صريح
        assertEquals(1, AdhkarResume.startPage(-1, ids, saved, day, false))         // موضع محفوظ اليوم
        assertEquals(0, AdhkarResume.startPage(-1, ids, saved, "2026-11-05", false)) // يتصفّر في اليوم التالي
        assertEquals(3, AdhkarResume.startPage(-1, ids, saved, day, true))          // مكتمل: صفحة الإتمام
        assertEquals(0, AdhkarResume.startPage(-1, ids, AdhkarPosition(day, "zzz", 1), day, false))
        assertEquals(0, AdhkarResume.startPage(99, ids, null, day, false))
    }

    // ---------------- overlay merge
    @Test fun overlayWinsUntilDatabaseCatchesUp() {
        val db = row(day, "a", false, count = 1, updatedAt = 100)
        val pending = ProgressOverlay(row(day, "a", false, count = 2), seq = 1, savedUpdatedAt = null)
        assertEquals(2, AdhkarMerge.effective(db, pending, day)!!.count)
        val saved = pending.copy(savedUpdatedAt = 200)
        assertEquals(2, AdhkarMerge.effective(db, saved, day)!!.count)                      // DB أقدم
        assertEquals(3, AdhkarMerge.effective(db.copy(count = 3, updatedAt = 200), saved, day)!!.count) // لحقت أو تجاوزت
        assertEquals(1, AdhkarMerge.effective(db, null, day)!!.count)
        assertEquals(1, AdhkarMerge.effective(db, pending, "2026-11-05")!!.count)           // تاريخ مختلف
        assertNull(AdhkarMerge.effective(null, null, day))
    }

    @Test fun buildItemUisAppliesTargetsOverlaysAndMarks() {
        val cat = AdhkarCategory("m", "م", listOf(AdhkarItem("a", "t", count = 3), AdhkarItem("b", "t", count = 1)))
        val ui = buildItemUis(
            cat, day,
            dbRows = mapOf("a" to row(day, "a", false, count = 2, target = 3)),
            overlays = mapOf("b" to ProgressOverlay(row(day, "b", true, count = 1), 1)),
            targets = mapOf("a" to 5),
            marks = AdhkarMarks(favorites = setOf("b")),
        )
        assertEquals(5, ui[0].progress.target); assertEquals(3, ui[0].progress.remaining); assertTrue(ui[0].customTarget)
        assertTrue(ui[1].progress.completed); assertTrue(ui[1].favorite); assertFalse(ui[1].bookmark)
    }

    // ---------------- home helpers
    @Test fun continuePicksLatestUnfinishedSavedPositionElseSuggestion() {
        val cats = listOf(
            AdhkarCategory("morning", "الصباح", listOf(AdhkarItem("m1", "t"), AdhkarItem("m2", "t"))),
            AdhkarCategory("evening", "المساء", listOf(AdhkarItem("e1", "t"), AdhkarItem("e2", "t"))),
            AdhkarCategory("sleep", "النوم", listOf(AdhkarItem("s1", "t"))),
        )
        val pos = mapOf(
            "morning" to AdhkarPosition(day, "m2", 10),
            "evening" to AdhkarPosition(day, "e2", 20),
        )
        assertEquals("evening", pickContinue(cats, pos, emptySet(), day, 9)!!.categoryId)
        assertEquals("morning", pickContinue(cats, pos, setOf("evening"), day, 9)!!.categoryId)
        assertEquals(1, pickContinue(cats, pos, setOf("evening"), day, 9)!!.itemIndex)
        // لا موضع اليوم: اقتراح بحسب الساعة
        val old = mapOf("morning" to AdhkarPosition("2026-11-03", "m2", 10))
        val s = pickContinue(cats, old, emptySet(), day, 9)!!
        assertEquals("morning", s.categoryId); assertFalse(s.isResume)
        assertEquals("evening", pickContinue(cats, old, emptySet(), day, 16)!!.categoryId)
        assertEquals("sleep", pickContinue(cats, old, emptySet(), day, 23)!!.categoryId)
        assertNull(pickContinue(cats, old, setOf("sleep"), day, 23))
    }

    @Test fun groupsOnlyWhenAtLeastTwoNamedGroups() {
        assertTrue(AdhkarGroups.starts(listOf(AdhkarItem("a", "t"), AdhkarItem("b", "t"))).isEmpty())
        assertTrue(AdhkarGroups.starts(listOf(AdhkarItem("a", "t", group = "x"), AdhkarItem("b", "t", group = "x"))).isEmpty())
        assertEquals(
            listOf("x" to 0, "y" to 2),
            AdhkarGroups.starts(listOf(AdhkarItem("a", "t", group = "x"), AdhkarItem("b", "t", group = "x"), AdhkarItem("c", "t", group = "y"))),
        )
    }

    @Test fun labelsAndShareText() {
        val id: (Int) -> String = { it.toString() }
        assertEquals("ذكر واحد", AdhkarLabels.adhkarCount(1, id))
        assertEquals("ذكران", AdhkarLabels.adhkarCount(2, id))
        assertEquals("4 أذكار", AdhkarLabels.adhkarCount(4, id))
        assertEquals("26 ذكرًا", AdhkarLabels.adhkarCount(26, id))
        assertEquals("مرة واحدة", AdhkarLabels.times(1, id))
        assertEquals("3 مرات", AdhkarLabels.times(3, id))
        assertEquals("33 مرة", AdhkarLabels.times(33, id))
        assertEquals("نص\n\nالمصدر: مصدر", AdhkarShareText.of(AdhkarItem("a", " نص ", source = " مصدر ")))
        assertEquals("نص", AdhkarShareText.of(AdhkarItem("a", "نص")))
    }

    // ---------------- reminders
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun millis(date: String, hhmm: String) = LocalDate.parse(date).atTime(java.time.LocalTime.parse(hhmm)).atZone(zone).toInstant().toEpochMilli()
    private val slots = listOf(
        AdhkarReminderPlanner.Slot("morning", true, "05:30", "05:30"),
        AdhkarReminderPlanner.Slot("evening", true, "17:00", "17:00"),
        AdhkarReminderPlanner.Slot("sleep", false, "22:00", "22:00"),
    )

    @Test fun plannerSchedulesThreeDaysSkippingPastDisabledAndCompleted() {
        val now = millis(day, "10:00")
        val planned = AdhkarReminderPlanner.plan(now, zone, LocalDate.parse(day), slots, emptyMap())
        // اليوم: الصباح فات، المساء باقٍ؛ اليومان التاليان: صباح ومساء
        assertEquals(
            listOf("adhkar:evening:2026-11-04", "adhkar:morning:2026-11-05", "adhkar:evening:2026-11-05", "adhkar:morning:2026-11-06", "adhkar:evening:2026-11-06"),
            planned.map { it.key },
        )
        assertEquals(millis(day, "17:00"), planned.first().triggerAtMillis)
        assertTrue(planned.none { it.categoryId == "sleep" })
        assertEquals(planned.size, planned.map { it.key }.toSet().size)

        val skipped = AdhkarReminderPlanner.plan(now, zone, LocalDate.parse(day), slots, mapOf(day to setOf("evening")))
        assertFalse(skipped.any { it.key == "adhkar:evening:2026-11-04" })
        assertEquals(4, skipped.size)
    }

    @Test fun plannerHandlesInvalidTimeAndAllDisabled() {
        val now = millis(day, "00:00")
        val bad = listOf(AdhkarReminderPlanner.Slot("morning", true, "xx", "05:30"))
        assertEquals(millis(day, "05:30"), AdhkarReminderPlanner.plan(now, zone, LocalDate.parse(day), bad, emptyMap()).first().triggerAtMillis)
        assertTrue(AdhkarReminderPlanner.plan(now, zone, LocalDate.parse(day), slots.map { it.copy(enabled = false) }, emptyMap()).isEmpty())
        assertEquals(java.time.LocalTime.of(5, 30), AdhkarReminderPlanner.parseTime("٠٥:٣٠", "09:00"))
    }

    @Test fun reminderTextsAreSetForEachScheduledCategory() {
        for (id in listOf("morning", "evening", "sleep")) {
            assertTrue(AdhkarReminderPlanner.title(id).startsWith("أذكار"))
            assertTrue(AdhkarReminderPlanner.body(id).isNotBlank())
        }
    }

    // ---------------- الكتالوج الحقيقي
    @Test fun realCatalogEveryCategoryHasItemsAndStatsWork() {
        val text = AdhkarLogicTest::class.java.classLoader!!.getResourceAsStream(AdhkarCatalogLoader.ASSET_PATH)!!.readBytes().toString(Charsets.UTF_8)
        val catalog = AdhkarCatalogLoader.parse(text)
        val morning = catalog.category("morning")!!
        val rows = morning.items.map { row(day, it.id, true, it.count, it.count) }
        assertTrue(AdhkarStats.isCategoryCompleted(morning.items.map { it.id }, rows))
        assertEquals(setOf("morning"), AdhkarStats.completedCategoryIds(catalog.categories, rows))
        assertTrue(AdhkarGroups.starts(catalog.category("misc")!!.items).size >= 2)
    }
}
