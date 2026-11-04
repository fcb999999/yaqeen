package app.yaqeen.feature.settings.data

import app.yaqeen.data.local.SyncDao
import app.yaqeen.data.local.SyncRow
import app.yaqeen.data.local.YaqeenDatabase
import app.yaqeen.data.sync.SyncJson
import app.yaqeen.data.sync.SyncWriter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** جدول بيانات مستخدم واحد بعد محو النوع: تصدير/استيراد/حذف منطقي. أسماء الجداول = أسماء مجموعات Firestore. */
internal abstract class UserTable(val name: String) {
    abstract suspend fun exportRows(db: YaqeenDatabase, json: Json): JsonArray
    abstract suspend fun importRows(db: YaqeenDatabase, writer: SyncWriter, json: Json, rows: JsonArray): Int
    abstract suspend fun tombstoneAll(db: YaqeenDatabase, writer: SyncWriter): Int
    abstract suspend fun liveCount(db: YaqeenDatabase): Int
}

internal class TableOf<T : SyncRow<T>>(
    name: String,
    private val serializer: KSerializer<T>,
    private val dao: (YaqeenDatabase) -> SyncDao<T>,
    /** كل الصفوف الحيّة (غير المحذوفة). */
    private val readLive: suspend (YaqeenDatabase) -> List<T>,
) : UserTable(name) {
    override suspend fun exportRows(db: YaqeenDatabase, json: Json): JsonArray =
        JsonArray(readLive(db).map { json.encodeToJsonElement(serializer, it) })

    override suspend fun importRows(db: YaqeenDatabase, writer: SyncWriter, json: Json, rows: JsonArray): Int {
        val decoded = rows.mapNotNull { el -> runCatching { json.decodeFromJsonElement(serializer, el) }.getOrNull() }
            .filter { it.id.isNotBlank() }
        if (decoded.isEmpty()) return 0
        // دفعات صغيرة حتى لا تتجاوز حدود SQLite وتبقى العملية سريعة.
        decoded.chunked(400).forEach { writer.saveAll(dao(db), it) }
        return decoded.size
    }

    override suspend fun tombstoneAll(db: YaqeenDatabase, writer: SyncWriter): Int {
        val rows = readLive(db)
        val d = dao(db)
        rows.forEach { writer.delete(d, it) }
        return rows.size
    }

    override suspend fun liveCount(db: YaqeenDatabase): Int = readLive(db).size
}

/** مجموعة كل جداول بيانات المستخدم (تطابق جداول المزامنة في AppContainer). */
internal object UserTables {
    private const val MIN_DATE = "0000-00-00"
    private const val MAX_DATE = "9999-99-99"

    val all: List<UserTable> = listOf(
        TableOf("reading_progress", app.yaqeen.data.local.ReadingProgressEntity.serializer(), { it.readingProgress() }) { it.readingProgress().observeAll().first() },
        TableOf("quran_bookmark_categories", app.yaqeen.data.local.BookmarkCategoryEntity.serializer(), { it.bookmarkCategories() }) { it.bookmarkCategories().observeAll().first() },
        TableOf("quran_bookmarks", app.yaqeen.data.local.BookmarkEntity.serializer(), { it.bookmarks() }) { it.bookmarks().observeAll().first() },
        TableOf("quran_plans", app.yaqeen.data.local.QuranPlanEntity.serializer(), { it.quranPlans() }) { it.quranPlans().observeAll().first() },
        TableOf("quran_plan_logs", app.yaqeen.data.local.QuranPlanLogEntity.serializer(), { it.quranPlanLogs() }) { it.quranPlanLogs().range(MIN_DATE, MAX_DATE) },
        TableOf("quran_day_logs", app.yaqeen.data.local.QuranDayLogEntity.serializer(), { it.quranDayLogs() }) { it.quranDayLogs().range(MIN_DATE, MAX_DATE) },
        TableOf("adhkar_progress", app.yaqeen.data.local.AdhkarProgressEntity.serializer(), { it.adhkarProgress() }) { it.adhkarProgress().range(MIN_DATE, MAX_DATE) },
        TableOf("adhkar_favorites", app.yaqeen.data.local.AdhkarMarkEntity.serializer(), { it.adhkarMarks() }) { it.adhkarMarks().observeAll().first() },
        TableOf("prayers_tracking", app.yaqeen.data.local.PrayerLogEntity.serializer(), { it.prayerLogs() }) { it.prayerLogs().range(MIN_DATE, MAX_DATE) },
        TableOf("rawatib_tracking", app.yaqeen.data.local.RawatibLogEntity.serializer(), { it.rawatibLogs() }) { it.rawatibLogs().range(MIN_DATE, MAX_DATE) },
        TableOf("custom_sunnah", app.yaqeen.data.local.CustomSunnahEntity.serializer(), { it.customSunnah() }) { it.customSunnah().observeAll().first() },
        TableOf("daily_tasks", app.yaqeen.data.local.TaskEntity.serializer(), { it.tasks() }) { it.tasks().observeAllIncludingArchived().first() },
        TableOf("task_completions", app.yaqeen.data.local.TaskCompletionEntity.serializer(), { it.taskCompletions() }) { it.taskCompletions().range(MIN_DATE, MAX_DATE) },
        TableOf("calendar_events", app.yaqeen.data.local.CalendarEventEntity.serializer(), { it.calendarEvents() }) { it.calendarEvents().observeAll().first() },
        TableOf("settings", app.yaqeen.data.local.SettingEntity.serializer(), { it.settings() }) { it.settings().observeAll().first() },
        TableOf("achievement_statistics", app.yaqeen.data.local.DailySummaryEntity.serializer(), { it.dailySummaries() }) { it.dailySummaries().range(MIN_DATE, MAX_DATE) },
    )
}

/** بنية ملف التصدير (قابلة للاختبار دون قاعدة بيانات). */
object BackupFormat {
    const val FORMAT = "yaqeen-backup"
    const val VERSION = 1

    val json: Json get() = SyncJson

    /** يبني ملف النسخة الاحتياطية من جداول جاهزة (اسم الجدول → صفوفه). */
    fun build(
        tables: Map<String, JsonArray>,
        appVersion: String,
        exportedAtEpochMs: Long,
        account: String? = null,
    ): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        put("app", "YAQEEN")
        put("appVersion", appVersion)
        put("exportedAt", exportedAtEpochMs)
        if (account != null) put("account", account)
        put("tables", JsonObject(tables))
    }

    /** نتيجة قراءة ملف: إما الجداول أو سبب الرفض. */
    sealed interface Parsed {
        data class Ok(val tables: Map<String, JsonArray>, val rowCount: Int) : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    /** يتحقق من ملف مستورد: الصيغة والإصدار وبنية الجداول. */
    fun parse(text: String): Parsed {
        val root: JsonElement = runCatching { Json.parseToJsonElement(text) }.getOrNull()
            ?: return Parsed.Invalid("الملف ليس بصيغة JSON صالحة.")
        val obj = root as? JsonObject ?: return Parsed.Invalid("بنية الملف غير مدعومة.")
        if ((obj["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return Parsed.Invalid("هذا الملف ليس نسخة احتياطية من يقين.")
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: return Parsed.Invalid("إصدار النسخة الاحتياطية غير معروف.")
        if (version > VERSION) return Parsed.Invalid("النسخة الاحتياطية أحدث من هذا الإصدار من التطبيق. حدّث التطبيق أولًا.")
        val tablesObj = obj["tables"] as? JsonObject ?: return Parsed.Invalid("لا توجد بيانات داخل الملف.")
        val out = LinkedHashMap<String, JsonArray>()
        var count = 0
        for ((k, v) in tablesObj) {
            val arr = v as? JsonArray ?: return Parsed.Invalid("بنية الجدول «$k» غير صالحة.")
            out[k] = arr
            count += arr.size
        }
        return Parsed.Ok(out, count)
    }
}

/** عمليات بيانات المستخدم على قاعدة Room: تصدير، استيراد، حذف منطقي لكل الصفوف. */
class UserDataManager(private val db: YaqeenDatabase, private val writer: SyncWriter) {

    /** عدد السجلات الحيّة لكل جدول (للعرض قبل التصدير/الحذف). */
    suspend fun counts(): Map<String, Int> = UserTables.all.associate { it.name to it.liveCount(db) }

    suspend fun totalRecords(): Int = counts().values.sum()

    /** ملف النسخة الاحتياطية كنص JSON منسّق قراءةً. */
    suspend fun exportJson(appVersion: String, nowMs: Long, account: String?): String {
        val tables = LinkedHashMap<String, JsonArray>()
        for (t in UserTables.all) tables[t.name] = t.exportRows(db, SyncJson)
        val doc = BackupFormat.build(tables, appVersion, nowMs, account)
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), doc)
    }

    /** يدمج ملفًا مستوردًا: السجلات المتطابقة تُستبدل بما في الملف. يعيد عدد السجلات المستوردة. */
    suspend fun import(parsed: BackupFormat.Parsed.Ok): Int {
        var n = 0
        for (t in UserTables.all) {
            val rows = parsed.tables[t.name] ?: continue
            n += t.importRows(db, writer, SyncJson, rows)
        }
        return n
    }

    /**
     * يضع شاهد حذف (deleted=true) على كل الصفوف الحيّة عبر مسار الكتابة العادي (فتصل لبقية الأجهزة عند المزامنة).
     * يعيد عدد الصفوف التي وُسمت.
     */
    suspend fun tombstoneAll(): Int = UserTables.all.sumOf { it.tombstoneAll(db, writer) }
}
