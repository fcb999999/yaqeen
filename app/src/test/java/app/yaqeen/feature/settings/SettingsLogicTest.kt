package app.yaqeen.feature.settings

import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.feature.settings.data.BackupFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import app.yaqeen.data.sync.SyncJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsLogicTest {
    @Test fun timeParseAndFormat() {
        assertEquals(TimeText.HM(5, 30), TimeText.parse("05:30"))
        assertEquals(TimeText.HM(23, 0), TimeText.parse("23:00"))
        assertEquals(TimeText.HM(9, 5), TimeText.parse("9:05"))
        assertEquals(TimeText.HM(1, 1), TimeText.parse("25:00", TimeText.HM(1, 1)))
        assertEquals(TimeText.HM(1, 1), TimeText.parse("abc", TimeText.HM(1, 1)))
        assertEquals(TimeText.HM(1, 1), TimeText.parse(null, TimeText.HM(1, 1)))
        assertEquals("05:30", TimeText.format24(5, 30))
        assertEquals("23:59", TimeText.format24(99, 99))
    }

    @Test fun timeDisplayArabic12h() {
        assertEquals("٥:٣٠ ص", TimeText.display("05:30"))
        assertEquals("٥:٠٠ م", TimeText.display("17:00"))
        assertEquals("١٢:٠٠ ص", TimeText.display("00:00"))
        assertEquals("١٢:٣٠ م", TimeText.display("12:30"))
        assertEquals("١١:٠٠ م", TimeText.display("23:00"))
    }

    @Test fun arabicPlurals() {
        assertEquals("دقيقة", ArabicUnits.plural(1, "دقيقة", "دقيقتان", "دقائق", "دقيقة"))
        assertEquals("دقيقتان", ArabicUnits.plural(2, "دقيقة", "دقيقتان", "دقائق", "دقيقة"))
        assertEquals("٥ دقائق", ArabicUnits.plural(5, "دقيقة", "دقيقتان", "دقائق", "دقيقة"))
        assertEquals("١١ دقيقة", ArabicUnits.plural(11, "دقيقة", "دقيقتان", "دقائق", "دقيقة"))
        assertEquals("معطّل", ArabicUnits.minutes(0))
        assertEquals("١٠ دقائق", ArabicUnits.minutes(10))
    }

    @Test fun relativeTime() {
        val now = 10_000_000_000L
        assertEquals("لم تتم بعد", ArabicUnits.ago(now, 0))
        assertEquals("الآن", ArabicUnits.ago(now, now - 10_000))
        assertEquals("منذ دقيقة", ArabicUnits.ago(now, now - 60_000))
        assertEquals("منذ ٥ دقائق", ArabicUnits.ago(now, now - 5 * 60_000))
        assertEquals("منذ ساعتين", ArabicUnits.ago(now, now - 2 * 3_600_000L))
        assertEquals("منذ ٣ أيام", ArabicUnits.ago(now, now - 3 * 86_400_000L))
        assertEquals("الآن", ArabicUnits.ago(now, now + 5_000)) // ساعة مستقبلية لا تعطي قيمة سالبة
    }

    @Test fun byteSizes() {
        assertEquals("٠ بايت", ByteSize.format(0))
        assertEquals("٥١٢ بايت", ByteSize.format(512))
        assertEquals("١ كيلوبايت", ByteSize.format(1024))
        assertEquals("١٫٥ ميغابايت", ByteSize.format((1.5 * 1024 * 1024).toLong()))
        assertEquals("٢ غيغابايت", ByteSize.format(2L * 1024 * 1024 * 1024))
        assertEquals("٠ بايت", ByteSize.format(-5))
    }

    @Test fun diskUsageAndClear() {
        val root = Files.createTempDirectory("yaqeen-disk").toFile()
        try {
            File(root, "a").apply { mkdirs() }
            File(root, "a/one.bin").writeBytes(ByteArray(100))
            File(root, "two.bin").writeBytes(ByteArray(50))
            assertEquals(150L, DiskUsage.size(root))
            assertEquals(0L, DiskUsage.size(File(root, "missing")))
            assertEquals(150L, DiskUsage.clearContents(root))
            assertEquals(0L, DiskUsage.size(root))
            assertTrue(root.exists())
        } finally { root.deleteRecursively() }
    }

    // ---- ملف النسخة الاحتياطية

    private fun sampleTables(): Map<String, JsonArray> {
        val row = PrayerLogEntity(id = "2026-11-04_FAJR", date = "2026-11-04", prayer = "FAJR", status = "MOSQUE", loggedAt = 5, updatedAt = 9, opId = "op", deviceId = "dev", dirty = true)
        return mapOf("prayers_tracking" to JsonArray(listOf(SyncJson.encodeToJsonElement(PrayerLogEntity.serializer(), row))), "custom_sunnah" to JsonArray(emptyList()))
    }

    @Test fun backupRoundTrip() {
        val doc = BackupFormat.build(sampleTables(), "0.1.0", 123L, "a@b.c")
        assertEquals("yaqeen-backup", (doc["format"] as kotlinx.serialization.json.JsonPrimitive).content)
        val parsed = BackupFormat.parse(Json.encodeToString(JsonObject.serializer(), doc))
        assertTrue(parsed is BackupFormat.Parsed.Ok)
        parsed as BackupFormat.Parsed.Ok
        assertEquals(1, parsed.rowCount)
        val back = SyncJson.decodeFromJsonElement(PrayerLogEntity.serializer(), parsed.tables.getValue("prayers_tracking")[0])
        assertEquals("2026-11-04_FAJR", back.id)
        assertEquals("MOSQUE", back.status)
        assertEquals(false, back.dirty) // حقل dirty لا يُصدَّر
    }

    @Test fun backupRejectsBadInput() {
        fun reason(text: String) = (BackupFormat.parse(text) as BackupFormat.Parsed.Invalid).reason
        assertTrue(reason("garbage").isNotBlank())
        assertTrue(reason("[]").isNotBlank())
        assertTrue(reason("""{"format":"other","version":1,"tables":{}}""").contains("ليس نسخة"))
        assertTrue(reason("""{"format":"yaqeen-backup","version":99,"tables":{}}""").contains("أحدث"))
        assertTrue(reason("""{"format":"yaqeen-backup","tables":{}}""").isNotBlank())
        assertTrue(reason("""{"format":"yaqeen-backup","version":1}""").isNotBlank())
        assertTrue(reason("""{"format":"yaqeen-backup","version":1,"tables":{"x":5}}""").contains("x"))
    }
}
