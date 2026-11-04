package app.yaqeen.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yaqeen.data.sync.CheckpointStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.localStore by preferencesDataStore(name = "yaqeen_local")

/**
 * تفضيلات خاصة بهذا الجهاز فقط ولا تُزامَن (الموقع، الحساب الذي تمت مزامنته، نقاط المتابعة، مسارات الأصوات المحلية...).
 * الإعدادات التي ينبغي أن تتبع المستخدم بين أجهزته تذهب إلى SettingsRepository.
 */
class LocalPrefs(context: Context) : CheckpointStore {
    private val store = context.applicationContext.localStore

    fun observe(key: String): Flow<String?> = store.data.map { it[stringPreferencesKey(key)] }
    suspend fun get(key: String): String? = store.data.first()[stringPreferencesKey(key)]
    suspend fun put(key: String, value: String?) {
        store.edit { p: androidx.datastore.preferences.core.MutablePreferences ->
            if (value == null) p.remove(stringPreferencesKey(key)) else p[stringPreferencesKey(key)] = value
        }
    }

    suspend fun getBool(key: String, default: Boolean = false): Boolean = get(key)?.toBooleanStrictOrNull() ?: default
    suspend fun putBool(key: String, value: Boolean) = put(key, value.toString())
    fun observeBool(key: String, default: Boolean = false): Flow<Boolean> = observe(key).map { it?.toBooleanStrictOrNull() ?: default }

    // ---- نقاط المتابعة للمزامنة (لكل مستخدم ومجموعة) ----
    override suspend fun get(uid: String, collection: String): Long =
        store.data.first()[longPreferencesKey("ckpt.$uid.$collection")] ?: 0L

    override suspend fun set(uid: String, collection: String, value: Long) {
        store.edit { it[longPreferencesKey("ckpt.$uid.$collection")] = value }
    }

    suspend fun clearCheckpoints() {
        store.edit { p ->
            p.asMap().keys.filter { it.name.startsWith("ckpt.") }.forEach { p.remove(it as Preferences.Key<Any>) }
        }
    }

    object Keys {
        /** آخر uid تمت مزامنة قاعدة البيانات المحلية معه (لاكتشاف تبديل الحساب). */
        const val SYNCED_UID = "synced_uid"
        const val ONBOARDING_DONE = "onboarding_done"
        const val LAST_LAT = "loc.lat"
        const val LAST_LON = "loc.lon"
        const val LAST_CITY = "loc.city"
        /** "gps" أو "manual" */
        const val LOCATION_MODE = "loc.mode"
        const val LAST_UPDATE_CHECK = "update.last_check"
    }
}
