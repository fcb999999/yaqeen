package app.yaqeen.feature.quran.plans

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen

/**
 * === عقد الأوراد القرآنية (يملكه منفذ "الأوراد والعلامات والصوت والتفسير") ===
 * القارئ (منفذ القراءة) يستدعي [ReadingTracker.onPageRead] عندما يمكث المستخدم في صفحة وقتًا كافيًا (>= 8 ثوانٍ).
 * هذا الهيكل أولي؛ يستبدل المنفذ التنفيذ مع الحفاظ على التواقيع.
 */
class ReadingTracker(private val c: AppContainer) {
    /**
     * تسجيل قراءة صفحة (1..604): يحدّث سجل القراءة اليومي (QuranDayLog) وسجلات الأوراد النشطة
     * وتقدّم الورد، دون تكرار احتساب الصفحة نفسها في اليوم نفسه.
     */
    suspend fun onPageRead(page: Int) {}
}

/** صفحة الأوراد. planId غير فارغ = فتح ورد محدد. */
@Composable
fun QuranPlansScreen(nav: NavHostController, c: AppContainer, planId: String?) {
    PlaceholderScreen("الأوراد")
}
