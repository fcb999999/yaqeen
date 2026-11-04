package app.yaqeen.domain.model

/** الصلوات الخمس بترتيب اليوم. المفتاح (name) هو ما يُخزَّن في قاعدة البيانات. */
enum class Prayer(val ar: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء");
}

/**
 * حالة تسجيل الصلاة. غياب السجل = "غير مسجلة" وهو لا يعني أن الصلاة لم تُؤدَّ
 * (قاعدة أساسية في الإحصائيات: لا تُحتسب العبادة غير المسجلة على أنها لم تحدث).
 */
enum class PrayerStatus(val ar: String) {
    MOSQUE("صليت في المسجد جماعة"),
    HOME("صليت في البيت"),
    NOT_PRAYED("لم أصلِّ بعد");
}

/** السنن الرواتب الافتراضية. السنن المخصصة تستخدم معرّفًا يبدأ بـ "custom:". */
enum class Sunnah(val ar: String, val rakaat: Int, val after: Prayer?) {
    FAJR_BEFORE("ركعتا الفجر", 2, Prayer.FAJR),
    DHUHR_BEFORE("أربع ركعات قبل الظهر", 4, Prayer.DHUHR),
    DHUHR_AFTER("ركعتان بعد الظهر", 2, Prayer.DHUHR),
    MAGHRIB_AFTER("ركعتان بعد المغرب", 2, Prayer.MAGHRIB),
    ISHA_AFTER("ركعتان بعد العشاء", 2, Prayer.ISHA);

    companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}

enum class TaskPriority(val ar: String) { LOW("منخفضة"), NORMAL("عادية"), HIGH("عالية") }

enum class TaskRepeat(val ar: String) { NONE("مرة واحدة"), DAILY("يوميًا"), WEEKLY("أسبوعيًا") }

/**
 * ربط المهمة بقسم داخل التطبيق. إذا كانت المهمة مرتبطة (≠ NONE) فإن إنجازها يُشتق
 * من سجل العبادة نفسه ولا يمكن وضع علامة إنجاز يدوية عليها.
 */
enum class TaskLink(val ar: String) {
    NONE("بدون ربط"),
    QURAN_PLAN("الورد القرآني"),
    ADHKAR_MORNING("أذكار الصباح"),
    ADHKAR_EVENING("أذكار المساء"),
    ADHKAR_SLEEP("أذكار النوم"),
    ADHKAR_AFTER_PRAYER("أذكار بعد الصلاة"),
    PRAYERS("الصلوات الخمس"),
    RAWATIB("السنن الرواتب"),
}

enum class QuranPlanUnit(val ar: String) { PAGE("صفحة"), JUZ("جزء"), AYAH("آية") }

enum class QuranPlanStatus { ACTIVE, PAUSED, COMPLETED }

enum class AdhkarMarkType { FAVORITE, BOOKMARK }

/** معرّفات أقسام الأذكار كما هي في ملف adhkar.json. */
object AdhkarCategoryId {
    const val MORNING = "morning"
    const val EVENING = "evening"
    const val SLEEP = "sleep"
    const val WAKING = "waking"
    const val AFTER_PRAYER = "after_prayer"
    const val MISC = "misc"
}

/** نطاقات الإعدادات المتزامنة. */
object SettingScope {
    const val USER = "user"                    // مجموعة user_settings
    const val NOTIFICATION = "notification"    // مجموعة notification_settings
}
