package app.yaqeen.ui.navigation

/**
 * مسارات التنقل. عقد التكامل بين الميزات:
 *  - كل ميزة تسجّل رسمًا فرعيًا (navigation) باسم مسارها الأعلى، ووجهتها الابتدائية "<route>/home".
 *  - الميزات الأخرى لا تعرف تفاصيل صفحات غيرها إلا عبر الدوال المساعدة هنا.
 */
object Routes {
    const val HOME = "home"
    const val QURAN = "quran"
    const val ADHKAR = "adhkar"
    const val PRAYER = "prayer"
    const val MORE = "more"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"

    // وجهات الابتداء لكل تبويب
    const val QURAN_HOME = "quran/home"
    const val ADHKAR_HOME = "adhkar/home"
    const val PRAYER_HOME = "prayer/home"
    const val TASKS_HOME = "tasks/home"
    const val CALENDAR_HOME = "calendar/home"
    const val JOURNEY_HOME = "journey/home"
    const val SETTINGS_HOME = "settings/home"

    /** الشاشات التي يظهر فيها الشريط السفلي. */
    val TAB_ROOTS = setOf(HOME, QURAN_HOME, ADHKAR_HOME, PRAYER_HOME, MORE)

    // ---- القرآن ----
    /** نمط المسار الذي يجب أن تسجّله ميزة القرآن لشاشة القراءة. القيمة -1 تعني "غير محدد" (استئناف آخر موضع). */
    const val QURAN_READER_PATTERN = "quran/reader?surah={surah}&ayah={ayah}&page={page}"
    fun quranReader(surah: Int = -1, ayah: Int = -1, page: Int = -1) = "quran/reader?surah=$surah&ayah=$ayah&page=$page"

    /** صفحة الأوراد وخطة ورد محددة (planId اختياري). */
    const val QURAN_PLANS_PATTERN = "quran/plans?planId={planId}"
    fun quranPlans(planId: String = "") = "quran/plans?planId=$planId"

    // ---- الأذكار ----
    /** categoryId من AdhkarCategoryId، start = رقم الذكر الذي يبدأ منه (-1 = آخر موضع محفوظ). */
    const val ADHKAR_CATEGORY_PATTERN = "adhkar/category/{categoryId}?start={start}"
    fun adhkarCategory(categoryId: String, start: Int = -1) = "adhkar/category/$categoryId?start=$start"

    // ---- الصلاة ----
    const val PRAYER_TIMES = "prayer/times"
    const val PRAYER_QIBLA = "prayer/qibla"
    const val PRAYER_STATS = "prayer/stats"

    // ---- الإعدادات ----
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_UPDATES = "settings/updates"
}
