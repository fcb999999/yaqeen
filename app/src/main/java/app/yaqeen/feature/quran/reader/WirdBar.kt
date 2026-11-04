package app.yaqeen.feature.quran.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.domain.model.QuranPlanStatus
import app.yaqeen.feature.quran.plans.PlanMath
import app.yaqeen.feature.quran.plans.unitEnum

/**
 * شريط "ورد اليوم" أعلى القارئ: كم بقي من مقدار اليوم، وكم بقي من الورد كله.
 * الأرقام من حسابات [PlanMath] نفسها التي تعتمد عليها شاشة الأوراد، فلا يختلف الرقمان.
 */
data class WirdBar(
    val planId: String,
    val planName: String,
    /** مثل: "باقي ٣ صفحات من ورد اليوم" أو "أتممتَ ورد اليوم". */
    val headline: String,
    /** مثل: "الورد كله: باقي ٤٢ صفحة". */
    val detail: String?,
    /** تقدّم مقطع اليوم 0..1. */
    val ratio: Float,
    val met: Boolean,
)

object WirdBarLogic {
    /**
     * يختار الورد الأنسب للعرض: أول ورد نشط بدأ تاريخه ولم يتحقق مقداره اليوم، وإلا أول ورد نشط.
     * يعيد null إن لم يوجد ورد نشط بدأ.
     */
    fun build(math: PlanMath, plans: List<QuranPlanEntity>, logs: List<QuranPlanLogEntity>, date: String): WirdBar? {
        val live = plans.filter { !it.deleted && it.status == QuranPlanStatus.ACTIVE.name && it.startDate <= date }
        if (live.isEmpty()) return null
        val logOf = logs.filter { !it.deleted && it.date == date }.groupBy { it.planId }.mapValues { (_, r) -> r.maxByOrNull { it.updatedAt }!! }
        val rows = live.map { plan -> evaluate(math, plan, logOf[plan.id]) }
        return rows.firstOrNull { !it.met } ?: rows.first()
    }

    private fun evaluate(math: PlanMath, plan: QuranPlanEntity, log: QuranPlanLogEntity?): WirdBar {
        val unit = plan.unitEnum()
        val portion = math.todayPortion(plan, log)
        val allDone = plan.progressAyahId >= plan.endAyahId
        val met = allDone || portion == null || log?.targetMet == true || plan.progressAyahId >= portion.toAyah
        val detail = if (allDone) null else "الورد كله: باقي ${PlanMath.unitText(unit, math.remainingUnits(plan))}"
        if (met) {
            return WirdBar(plan.id, plan.name, if (allDone) "تمّ الورد بحمد الله" else "أتممتَ ورد اليوم", detail, 1f, true)
        }
        val remainingToday = math.unitCount(unit, plan.progressAyahId + 1, portion!!.toAyah).coerceAtLeast(1)
        val startOfDay = math.startOfDayProgress(plan, log?.ayahsRead ?: 0)
        val ratio = math.fraction(startOfDay + 1, portion.toAyah, plan.progressAyahId)
        return WirdBar(plan.id, plan.name, "باقي ${PlanMath.unitText(unit, remainingToday)} من ورد اليوم", detail, ratio, false)
    }
}

@Composable
fun WirdStrip(bar: WirdBar, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (bar.met) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(tint.copy(alpha = 0.08f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (bar.met) Icons.Filled.Check else Icons.Filled.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(bar.headline, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = tint, maxLines = 1)
                if (bar.detail != null) {
                    Text(
                        "${bar.planName} – ${bar.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                "${ArabicText.indic((bar.ratio * 100).toInt())}٪",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(tint.copy(alpha = 0.14f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bar.ratio.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(tint),
            )
        }
    }
}
