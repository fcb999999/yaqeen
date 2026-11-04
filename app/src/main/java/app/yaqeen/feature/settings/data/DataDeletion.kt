package app.yaqeen.feature.settings.data

import app.yaqeen.AppContainer
import app.yaqeen.data.auth.AuthState
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.notifications.UpdateNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** نتيجة "حذف كل بياناتي". */
sealed interface DeletionOutcome {
    /** حُذفت بيانات الجهاز؛ [cloudDeleted] = وُسمت نسخة السحابة كمحذوفة ووصل ذلك للخادم (false إن لم يكن هناك حساب). */
    data class Done(val records: Int, val cloudDeleted: Boolean, val hadAccount: Boolean) : DeletionOutcome

    /**
     * سجّل المستخدم الدخول لكن تعذّر الوصول للسحابة الآن: وُسمت كل السجلات كمحذوفة محليًا (لا تظهر في التطبيق)
     * وستُرسل شواهد الحذف تلقائيًا عند توفر الاتصال. لم تُمسح الجداول بعد حتى لا تضيع شواهد الحذف.
     */
    data class CloudPending(val records: Int) : DeletionOutcome

    data class Failed(val message: String) : DeletionOutcome
}

/**
 * يحذف كل بيانات المستخدم بالترتيب الآمن:
 *  1) (إن كان مسجّلًا) شاهد حذف لكل سجل عبر مسار الكتابة العادي ثم مزامنة فورية حتى تتبعه بقية الأجهزة.
 *  2) إن وصل الحذف للسحابة (أو لا يوجد حساب): مسح كل جداول Room (db.clearAllTables) وتفضيلات الجهاز المحلية.
 */
class DataDeletion(private val c: AppContainer) {

    suspend fun deleteEverything(): DeletionOutcome {
        return try {
            val manager = UserDataManager(c.db, c.writer)
            val signedIn = c.auth.state.value is AuthState.SignedIn
            var cloudDeleted = false
            val records: Int

            if (signedIn) {
                records = manager.tombstoneAll()
                c.sync.syncNow()
                cloudDeleted = c.sync.state.value.pending == 0
                if (!cloudDeleted) return DeletionOutcome.CloudPending(records)
            } else {
                records = manager.totalRecords()
            }

            withContext(Dispatchers.IO) { c.db.clearAllTables() }
            clearDevicePrefs()
            c.requestReschedule()
            DeletionOutcome.Done(records, cloudDeleted, signedIn)
        } catch (e: Exception) {
            DeletionOutcome.Failed(e.message?.takeIf { it.isNotBlank() } ?: "حدث خطأ غير متوقع أثناء الحذف.")
        }
    }

    /** تفضيلات الجهاز غير المتزامنة (الموقع وآخر فحص للتحديث...). نُبقي علم انتهاء الترحيب والحساب المرتبط. */
    private suspend fun clearDevicePrefs() {
        val keys = listOf(
            LocalPrefs.Keys.LAST_LAT,
            LocalPrefs.Keys.LAST_LON,
            LocalPrefs.Keys.LAST_CITY,
            LocalPrefs.Keys.LOCATION_MODE,
            LocalPrefs.Keys.LAST_UPDATE_CHECK,
            UpdateNotifications.PREF_LAST_NOTIFIED,
        )
        keys.forEach { c.localPrefs.put(it, null) }
    }
}
