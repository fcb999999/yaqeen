package app.yaqeen.feature.quran.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.ReadingProgressEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.feature.quran.QuranRoutes
import app.yaqeen.feature.quran.reader.PlanMath
import app.yaqeen.feature.quran.reader.QuranLoad
import app.yaqeen.feature.quran.reader.QuranText
import app.yaqeen.feature.quran.reader.rememberQuranLoad
import app.yaqeen.ui.components.HeroButton
import app.yaqeen.ui.components.HeroCard
import app.yaqeen.ui.components.LocalHero
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.YaqeenTile
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.Hue
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import kotlinx.coroutines.flow.map

/** صفحة تبويب القرآن: متابعة القراءة، وصول سريع، وملخص الأوراد النشطة. */
@Composable
fun QuranHomeScreen(nav: NavHostController, c: AppContainer) {
    var retry by remember { mutableIntStateOf(0) }
    val load by rememberQuranLoad(c, retry)
    // Opt: نفرّق بين "لم تُقرأ القاعدة بعد" (null) و"لا يوجد سجل قراءة" (Opt(null))
    val latest by remember { c.db.readingProgress().observeLatest().map { Opt(it) } }.collectAsState(initial = null)
    val plans by remember { c.db.quranPlans().observeActive() }.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("القرآن الكريم")
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContinueCard(nav, c, load, loaded = latest != null, latest = latest?.value, onRetry = { retry++ })

            SectionTitle("وصول سريع")
            val actions = listOf(
                Action("الفهرس", Icons.Filled.FormatListNumbered, QuranRoutes.INDEX, Hue.VIOLET),
                Action("البحث", Icons.Filled.Search, QuranRoutes.SEARCH, Hue.TEAL),
                Action("العلامات", Icons.Filled.Bookmarks, QuranRoutes.BOOKMARKS, Hue.CORAL),
                Action("الأوراد", Icons.Filled.Flag, Routes.quranPlans(), Hue.AMBER),
                Action("التنزيلات", Icons.Filled.Download, QuranRoutes.DOWNLOADS, Hue.GREEN),
                Action("إعدادات القراءة", Icons.Filled.Tune, QuranRoutes.SETTINGS, Hue.BLUE),
            )
            actions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { a -> ActionTile(a, Modifier.weight(1f)) { nav.navigate(a.route) } }
                }
            }

            SectionTitle("الأوراد النشطة") {
                TextButton(onClick = { nav.navigate(Routes.quranPlans()) }) { Text("عرض الكل") }
            }
            if (plans.isEmpty()) {
                YaqeenCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.quranPlans()) }) {
                    Text("لا يوجد ورد نشط", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أنشئ وردًا لتنظيم قراءتك وتتبّع تقدّمك يومًا بيوم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                plans.take(3).forEach { PlanCard(nav, it) }
                if (plans.size > 3) {
                    Text(
                        "و${ArabicText.indic(plans.size - 3)} أوراد أخرى",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class Opt(val value: ReadingProgressEntity?)

private data class Action(val label: String, val icon: ImageVector, val route: String, val hue: Hue)

@Composable
private fun ActionTile(action: Action, modifier: Modifier, onClick: () -> Unit) {
    YaqeenTile(action.label, action.icon, onClick, modifier, hue = action.hue)
}

@Composable
private fun ContinueCard(nav: NavHostController, c: AppContainer, load: QuranLoad, loaded: Boolean, latest: ReadingProgressEntity?, onRetry: () -> Unit) {
    val quran: QuranData? = (load as? QuranLoad.Ready)?.quran
    val valid = latest != null && quran != null && latest.surah in 1..quran.surahs.size
    HeroCard(
        Area.QURAN,
        Modifier.fillMaxWidth(),
        onClick = if (valid) ({ nav.navigate(Routes.quranReader()) }) else null,
    ) {
        val on = LocalHero.current.content
        when {
            load is QuranLoad.Failed -> {
                Text("تعذّر تحميل المصحف", style = MaterialTheme.typography.titleMedium, color = on)
                Spacer(Modifier.height(8.dp))
                HeroButton("إعادة المحاولة", onRetry)
            }
            quran == null || !loaded -> {
                Text("جارٍ التحميل…", style = MaterialTheme.typography.bodyMedium, color = on)
            }
            valid -> {
                val p = latest!!
                val surah = quran.surah(p.surah)
                val frac = (p.page.toFloat() / quran.pageCount).coerceIn(0f, 1f)
                Text("تابع القراءة", style = MaterialTheme.typography.labelLarge, color = on)
                Text(
                    "سورة ${QuranText.plainSurahName(surah.nameAr)} – الآية ${ArabicText.indic(p.ayah)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = on,
                )
                Text(
                    "صفحة ${ArabicText.indic(p.page)} – الجزء ${ArabicText.indic(p.juz)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                )
                if (p.id != c.device.deviceId) {
                    Text(
                        "آخر قراءة على جهاز ${p.deviceName.trim().ifEmpty { "آخر" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = on,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(frac, onHero = true)
                Text(
                    "${ArabicText.indic((frac * 100).toInt())}٪ من المصحف",
                    style = MaterialTheme.typography.bodySmall,
                    color = on,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            else -> {
                Text("ابدأ رحلتك مع القرآن", style = MaterialTheme.typography.titleMedium, color = on)
                Text(
                    "لم تبدأ القراءة بعد. سيُحفظ موضعك تلقائيًا أثناء القراءة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                )
                Spacer(Modifier.height(8.dp))
                HeroButton("ابدأ من الفاتحة", { nav.navigate(Routes.quranReader(surah = 1)) })
            }
        }
    }
}

@Composable
private fun PlanCard(nav: NavHostController, plan: QuranPlanEntity) {
    val frac = PlanMath.fraction(plan.startAyahId, plan.endAyahId, plan.progressAyahId)
    val done = PlanMath.done(plan.startAyahId, plan.endAyahId, plan.progressAyahId)
    val total = PlanMath.total(plan.startAyahId, plan.endAyahId)
    YaqeenCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.quranPlans(plan.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${ArabicText.indic((frac * 100).toInt())}٪", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(frac)
        Text(
            "${ArabicText.indic(done)} من ${ArabicText.indic(total)} آية",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
