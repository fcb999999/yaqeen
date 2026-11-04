package app.yaqeen.data.settings

import app.yaqeen.data.local.SettingDao
import app.yaqeen.data.local.SettingEntity
import app.yaqeen.data.sync.SyncWriter
import app.yaqeen.domain.model.SettingScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** مفتاح إعداد مُنمَّط: يعرف نطاقه (user/notification) وقيمته الافتراضية وطريقة الترميز. */
abstract class SettingKey<T>(val scope: String, val key: String, val default: T) {
    val id: String get() = "$scope:$key"
    abstract fun encode(value: T): String
    abstract fun decode(raw: String): T?
}

class BoolKey(scope: String, key: String, default: Boolean) : SettingKey<Boolean>(scope, key, default) {
    override fun encode(value: Boolean) = value.toString()
    override fun decode(raw: String) = raw.toBooleanStrictOrNull()
}

class IntKey(scope: String, key: String, default: Int) : SettingKey<Int>(scope, key, default) {
    override fun encode(value: Int) = value.toString()
    override fun decode(raw: String) = raw.toIntOrNull()
}

class FloatKey(scope: String, key: String, default: Float) : SettingKey<Float>(scope, key, default) {
    override fun encode(value: Float) = value.toString()
    override fun decode(raw: String) = raw.toFloatOrNull()
}

class StringKey(scope: String, key: String, default: String) : SettingKey<String>(scope, key, default) {
    override fun encode(value: String) = value
    override fun decode(raw: String) = raw
}

/** الإعدادات المعروفة. يمكن لأي ميزة تعريف مفاتيح إضافية بنفس الأنواع داخل ملفاتها. */
object Settings {
    private const val U = SettingScope.USER
    private const val N = SettingScope.NOTIFICATION

    // ---- المظهر ----
    /** system | light | dark */
    val THEME_MODE = StringKey(U, "theme_mode", "system")
    /** classic | hayat (القيم المجهولة تُعامل كـ classic عبر ThemeStyle.normalize) */
    val THEME_STYLE = StringKey(U, "theme_style", "classic")
    val UI_FONT_SCALE = FloatKey(U, "ui_font_scale", 1.0f)

    // ---- المصحف ----
    val QURAN_FONT_SIZE = FloatKey(U, "quran_font_size", 28f)
    val QURAN_LINE_SPACING = FloatKey(U, "quran_line_spacing", 1.9f)
    /** ivory | sepia | gray | night */
    val QURAN_BACKGROUND = StringKey(U, "quran_background", "ivory")
    val QURAN_KEEP_SCREEN_ON = BoolKey(U, "quran_keep_screen_on", true)
    val QURAN_FULLSCREEN = BoolKey(U, "quran_fullscreen", false)
    /** سرعة التمرير التلقائي 1..10 */
    val QURAN_SCROLL_SPEED = IntKey(U, "quran_scroll_speed", 3)
    /** معرّف القارئ في خدمة الصوت (مثل ar.alafasy) */
    val QURAN_RECITER = StringKey(U, "quran_reciter", "ar.alafasy")
    val QURAN_TAFSIR_EDITION = StringKey(U, "quran_tafsir_edition", "ar.muyassar")

    // ---- الأذكار ----
    val ADHKAR_AUTO_ADVANCE = BoolKey(U, "adhkar_auto_advance", true)
    val ADHKAR_HAPTICS = BoolKey(U, "adhkar_haptics", true)
    val ADHKAR_SHOW_FADL = BoolKey(U, "adhkar_show_fadl", true)
    val ADHKAR_TEXT_SIZE = FloatKey(U, "adhkar_text_size", 24f)
    /** horizontal | vertical */
    val ADHKAR_SWIPE_AXIS = StringKey(U, "adhkar_swipe_axis", "horizontal")

    // ---- مواقيت الصلاة ----
    /** اسم قيمة com.batoulapps.adhan.CalculationMethod */
    val PRAYER_METHOD = StringKey(U, "prayer_method", "UMM_AL_QURA")
    /** SHAFI | HANAFI */
    val PRAYER_MADHAB = StringKey(U, "prayer_madhab", "SHAFI")
    val PRAYER_HIGH_LAT_RULE = StringKey(U, "prayer_high_lat_rule", "AUTO")
    /** تعديل التاريخ الهجري يدويًا بالأيام (-2..+2) لاختلاف رؤية الهلال */
    val HIJRI_ADJUST_DAYS = IntKey(U, "hijri_adjust_days", 0)

    // ---- الرئيسية ----
    /** عناصر الصفحة الرئيسية المرئية بالترتيب، مفصولة بفاصلة. فارغ = الافتراضي */
    val HOME_SECTIONS = StringKey(U, "home_sections", "")

    // ---- الإشعارات (مجموعة notification_settings) ----
    val NOTIF_WIRD = BoolKey(N, "wird_enabled", true)
    val NOTIF_MORNING = BoolKey(N, "morning_enabled", true)
    val NOTIF_MORNING_TIME = StringKey(N, "morning_time", "05:30")
    val NOTIF_EVENING = BoolKey(N, "evening_enabled", true)
    val NOTIF_EVENING_TIME = StringKey(N, "evening_time", "17:00")
    val NOTIF_SLEEP = BoolKey(N, "sleep_enabled", true)
    val NOTIF_SLEEP_TIME = StringKey(N, "sleep_time", "22:00")
    val NOTIF_TASKS = BoolKey(N, "tasks_enabled", true)
    val NOTIF_DAY_END = BoolKey(N, "day_end_enabled", true)
    val NOTIF_DAY_END_TIME = StringKey(N, "day_end_time", "23:00")
    val NOTIF_EVENTS = BoolKey(N, "events_enabled", true)
    val NOTIF_UPDATES = BoolKey(N, "updates_enabled", true)

    /** إشعار دخول وقت الصلاة (الأذان). */
    fun notifAdhan(prayer: String) = BoolKey(N, "adhan_${prayer.lowercase()}", true)
    /** التنبيه قبل الصلاة بالدقائق (0 = معطّل). */
    fun notifPreMinutes(prayer: String) = IntKey(N, "pre_${prayer.lowercase()}_min", 10)
    /** تنبيه الإقامة بعد الأذان بالدقائق (0 = معطّل). */
    fun notifIqamaMinutes(prayer: String) = IntKey(N, "iqama_${prayer.lowercase()}_min", 0)
    /** معرّف المؤذن/الصوت للصلاة (قيمة نصية يفسّرها قسم الأذان). */
    fun adhanVoice(prayer: String) = StringKey(U, "adhan_voice_${prayer.lowercase()}", "default")
}

/** الوصول إلى الإعدادات المتزامنة (Room + مزامنة). */
class SettingsRepository(
    private val dao: SettingDao,
    private val writer: SyncWriter,
) {
    fun <T> observe(key: SettingKey<T>): Flow<T> =
        dao.observe(key.id).map { row -> row?.value?.let { key.decode(it) } ?: key.default }.distinctUntilChanged()

    suspend fun <T> get(key: SettingKey<T>): T = dao.byId(key.id)?.takeIf { !it.deleted }?.value?.let { key.decode(it) } ?: key.default

    suspend fun <T> set(key: SettingKey<T>, value: T) {
        val existing = dao.byId(key.id)
        val row = existing?.copy(value = key.encode(value))
            ?: SettingEntity(id = key.id, scope = key.scope, key = key.key, value = key.encode(value))
        // إن لم تتغير القيمة فعليًا لا نُنشئ عملية مزامنة جديدة.
        if (existing != null && !existing.deleted && existing.value == row.value) return
        writer.save(dao, row.copy(deleted = false))
    }

    /** إرجاع الإعداد إلى قيمته الافتراضية (حذف منطقي متزامن). */
    suspend fun <T> reset(key: SettingKey<T>) {
        dao.byId(key.id)?.let { writer.delete(dao, it) }
    }

    /** كل الإعدادات الحالية كخريطة id → قيمة نصية. */
    fun observeRaw(): Flow<Map<String, String>> = dao.observeAll().map { rows -> rows.associate { it.id to it.value } }

    suspend fun snapshot(): Map<String, String> = observeRaw().first()
}
