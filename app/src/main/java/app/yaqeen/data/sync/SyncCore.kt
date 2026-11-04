package app.yaqeen.data.sync

import app.yaqeen.data.local.SyncDao
import app.yaqeen.data.local.SyncRow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** وثيقة جاهزة للإرسال إلى السحابة. */
data class PendingDoc(val id: String, val opId: String, val updatedAt: Long, val data: Map<String, Any?>)

/** وثيقة مستلمة من السحابة. */
data class RemoteDoc(val id: String, val data: Map<String, Any?>)

/** جدول قابل للمزامنة بعد محو النوع (type erasure) ليستخدمه SyncManager. */
interface AnySyncTable {
    /** اسم المجموعة في Firestore (تحت users/{uid}/). */
    val collection: String

    suspend fun pendingDocs(): List<PendingDoc>
    suspend fun markClean(id: String, opId: String)

    /** يطبّق وثائق مستلمة بقاعدة "الأحدث يغلب"؛ يعيد عدد ما غيّر القاعدة المحلية + أكبر updatedAt رآه. */
    suspend fun applyRemote(docs: List<RemoteDoc>): ApplyResult

    suspend fun purge(before: Long)
    suspend fun dirtyCount(): Int
}

data class ApplyResult(val applied: Int, val maxUpdatedAt: Long)

/**
 * قاعدة حل التعارض (Last-Writer-Wins حتمية):
 * يفوز الأكبر updatedAt، وعند التساوي يفوز الأكبر deviceId (ترتيب نصي) ليصل كل الأجهزة لنفس النتيجة.
 */
fun remoteWins(remoteUpdatedAt: Long, remoteDevice: String, localUpdatedAt: Long, localDevice: String): Boolean =
    when {
        remoteUpdatedAt != localUpdatedAt -> remoteUpdatedAt > localUpdatedAt
        else -> remoteDevice > localDevice
    }

class RoomSyncTable<T : SyncRow<T>>(
    override val collection: String,
    private val serializer: KSerializer<T>,
    private val dao: SyncDao<T>,
    private val json: Json = SyncJson,
) : AnySyncTable {

    override suspend fun pendingDocs(): List<PendingDoc> = dao.dirty().map { row ->
        val obj = json.encodeToJsonElement(serializer, row).jsonObjectOrThrow()
        PendingDoc(row.id, row.opId, row.updatedAt, obj.toPlainMap())
    }

    override suspend fun markClean(id: String, opId: String) = dao.markClean(id, opId)

    override suspend fun applyRemote(docs: List<RemoteDoc>): ApplyResult {
        var maxTs = 0L
        val toWrite = ArrayList<T>()
        for (doc in docs) {
            val remote = runCatching { json.decodeFromJsonElement(serializer, doc.data.toJsonObject()) }.getOrNull() ?: continue
            if (remote.id != doc.id) continue // وثيقة تالفة: المعرّف داخل الحقول يجب أن يطابق مفتاح الوثيقة
            if (remote.updatedAt > maxTs) maxTs = remote.updatedAt
            val local = dao.byId(remote.id)
            val take = when {
                local == null -> true
                local.opId == remote.opId && local.updatedAt == remote.updatedAt -> false // العملية نفسها: لا تكرار
                local.dirty -> remoteWins(remote.updatedAt, remote.deviceId, local.updatedAt, local.deviceId)
                else -> remote.updatedAt >= local.updatedAt
            }
            if (take) toWrite += remote.asClean()
        }
        if (toWrite.isNotEmpty()) dao.upsertAll(toWrite)
        return ApplyResult(toWrite.size, maxTs)
    }

    override suspend fun purge(before: Long) = dao.purgeDeleted(before)
    override suspend fun dirtyCount(): Int = dao.dirty().size
}

private fun JsonElement.jsonObjectOrThrow(): JsonObject = this as? JsonObject ?: error("expected object")

val SyncJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = true
}

fun JsonObject.toPlainMap(): Map<String, Any?> = entries.associate { (k, v) -> k to v.toPlain() }

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
    is JsonArray -> map { it.toPlain() }
    is JsonObject -> toPlainMap()
}

fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(entries.associate { (k, v) -> k to v.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> if (this % 1.0 == 0.0 && kotlin.math.abs(this) < 9.0e15) JsonPrimitive(this.toLong()) else JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
