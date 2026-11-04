package app.yaqeen.feature.quran.plans

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer

/**
 * === عقد الأوراد القرآنية ===
 * القارئ يستدعي [ReadingTracker.onPageRead] عندما يمكث المستخدم في صفحة وقتًا كافيًا (>= 8 ثوانٍ).
 *
 * قواعد التسجيل (التفاصيل في [PlanMath] و[PlanRepository]):
 *  - الصفحة تُحتسب مرة واحدة في اليوم في سجل القراءة اليومي (مجموعة الصفحات في LocalPrefs: "quran.counted.<date>").
 *  - كل ورد نشط بدأ تاريخه يتقدّم إذا كانت الصفحة متصلة بتقدّمه أو متداخلة معه؛ القفز لصفحة أبعد لا يُحتسب.
 *  - يُنشأ/يُحدَّث سجل اليوم للورد (id = "{planId}_{date}") ويُحسب تحقق المقدار اليومي، ويكتمل الورد عند بلوغ نهايته.
 */
class ReadingTracker(private val c: AppContainer) {
    /**
     * تسجيل قراءة صفحة (1..604): يحدّث سجل القراءة اليومي (QuranDayLog) وسجلات الأوراد النشطة
     * وتقدّم الورد، دون تكرار احتساب الصفحة نفسها في اليوم نفسه.
     */
    suspend fun onPageRead(page: Int) {
        PlanRepository.of(c).onPageRead(page)
    }
}

/** صفحة الأوراد. planId غير فارغ = فتح ورد محدد. */
@Composable
fun QuranPlansScreen(nav: NavHostController, c: AppContainer, planId: String?) {
    PlansRoot(nav, c, planId)
}
