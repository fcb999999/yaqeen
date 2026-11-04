package app.yaqeen.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.yaqeen.core.HybridClock
import app.yaqeen.core.newId
import app.yaqeen.data.local.SyncDao
import app.yaqeen.data.local.SyncMeta
import app.yaqeen.data.local.SyncRow
import java.util.concurrent.atomic.AtomicReference

/** هوية هذا الجهاز: معرّف ثابت يُولَّد مرة واحدة (ولا يُنسخ احتياطيًا، انظر backup_rules.xml). */
class DeviceInfo(context: Context) {
    val deviceId: String
    val deviceName: String = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim()

    init {
        val prefs = context.getSharedPreferences("yaqeen_device", Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId", null) ?: newId().also { prefs.edit().putString("deviceId", it).apply() }
    }
}

/** يختم كل تعديل: updatedAt من الساعة الهجينة، ومعرّف الجهاز، ومعرّف عملية فريد. */
class Stamper(private val deviceId: String, private val clock: HybridClock) {
    fun next(): SyncMeta = SyncMeta(clock.next(), deviceId, newId())
    fun deviceId(): String = deviceId
}

/**
 * نقطة الكتابة الوحيدة لبيانات المستخدم: تختم، تحفظ محليًا (Room)، ثم تطلب مزامنة مؤجلة.
 * استخدمها دائمًا بدل dao.upsert مباشرة حتى لا يفلت تعديل من المزامنة.
 */
class SyncWriter(private val stamper: Stamper) {
    private val trigger = AtomicReference<() -> Unit>({})

    fun setPushTrigger(fn: () -> Unit) = trigger.set(fn)

    suspend fun <T : SyncRow<T>> save(dao: SyncDao<T>, row: T): T {
        val stamped = row.withMeta(stamper.next(), dirty = true)
        dao.upsertAll(listOf(stamped))
        trigger.get().invoke()
        return stamped
    }

    suspend fun <T : SyncRow<T>> saveAll(dao: SyncDao<T>, rows: List<T>): List<T> {
        if (rows.isEmpty()) return rows
        val stamped = rows.map { it.withMeta(stamper.next(), dirty = true) }
        dao.upsertAll(stamped)
        trigger.get().invoke()
        return stamped
    }

    /** حذف منطقي (tombstone) يصل إلى بقية الأجهزة. */
    suspend fun <T : SyncRow<T>> delete(dao: SyncDao<T>, row: T) {
        dao.upsertAll(listOf(row.withMeta(stamper.next(), deleted = true, dirty = true)))
        trigger.get().invoke()
    }

    fun deviceId(): String = stamper.deviceId()
}

/** حالة الاتصال بالإنترنت. */
class Connectivity(private val context: Context) {
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
