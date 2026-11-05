package app.yaqeen.feature.settings.updates

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.core.ArabicText
import app.yaqeen.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * يُظهر التحديث الجديد للمستخدم داخل التطبيق دون أن يبحث عنه: عند فتح التطبيق (وعند العودة إليه)
 * يُفحص GitHub مرة كل [CHECK_INTERVAL_MS] على الأكثر، وإن وُجد إصدار أحدث تظهر نافذة بما الجديد
 * وزر «تحديث الآن». «لاحقًا» يؤجّل الإصدار نفسه [SNOOZE_MS] ثم يُذكَّر به من جديد.
 */
object UpdatePrompt {
    const val CHECK_INTERVAL_MS = 3L * 60 * 60 * 1000
    const val SNOOZE_MS = 24L * 60 * 60 * 1000

    private const val K_LAST_CHECK = "update.prompt.last_check"
    private const val K_SNOOZE_TAG = "update.prompt.snooze_tag"
    private const val K_SNOOZE_UNTIL = "update.prompt.snooze_until"

    /** هل حان وقت فحص جديد؟ (خالصة، للاختبار) */
    fun isDue(nowMs: Long, lastCheckMs: Long): Boolean = lastCheckMs <= 0 || nowMs - lastCheckMs >= CHECK_INTERVAL_MS || nowMs < lastCheckMs

    /** هل الإصدار مؤجَّل بـ«لاحقًا»؟ (خالصة) */
    fun isSnoozed(tag: String, snoozedTag: String?, snoozeUntilMs: Long, nowMs: Long): Boolean =
        snoozedTag != null && SemVer.parse(snoozedTag) == SemVer.parse(tag) && nowMs < snoozeUntilMs

    /** يفحص إن حان الوقت ويعيد الإصدار الأحدث الواجب عرضه، أو null. لا يرمي أخطاء. */
    suspend fun maybeCheck(c: AppContainer, context: Context): ReleaseInfo? {
        val repo = BuildConfig.GITHUB_REPO
        if (!UpdateChecker.isValidRepo(repo) || UpdateInstaller.isInstalledFromPlayStore(context)) return null
        val now = System.currentTimeMillis()
        val last = c.localPrefs.get(K_LAST_CHECK)?.toLongOrNull() ?: 0L
        if (!isDue(now, last)) return null
        val result = try {
            withContext(Dispatchers.IO) { UpdateChecker(UrlHttp(), repo, BuildConfig.VERSION_NAME).check() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        // دون اتصال أو فشل مؤقت: نعيد المحاولة في المرة القادمة لا بعد ثلاث ساعات
        if (result is UpdateCheckResult.Offline || result is UpdateCheckResult.Failed) return null
        c.localPrefs.put(K_LAST_CHECK, now.toString())
        val release = (result as? UpdateCheckResult.Available)?.release ?: return null
        val snoozedTag = c.localPrefs.get(K_SNOOZE_TAG)
        val until = c.localPrefs.get(K_SNOOZE_UNTIL)?.toLongOrNull() ?: 0L
        return if (isSnoozed(release.tag, snoozedTag, until, now)) null else release
    }

    suspend fun snooze(c: AppContainer, tag: String) {
        c.localPrefs.put(K_SNOOZE_TAG, tag)
        c.localPrefs.put(K_SNOOZE_UNTIL, (System.currentTimeMillis() + SNOOZE_MS).toString())
        // نسمح بفحص جديد بعد انتهاء التأجيل مباشرة
        c.localPrefs.put(K_LAST_CHECK, null)
    }
}

/**
 * مضيف نافذة «تحديث جديد متاح» في جذر التطبيق. [suppressed]: لا تُعرض فوق شاشة القفل أو صفحة الترحيب
 * (يبقى الإصدار محفوظًا ويظهر حين يزول المانع).
 */
@Composable
fun UpdatePromptHost(nav: NavHostController, c: AppContainer, suppressed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (release == null) UpdatePrompt.maybeCheck(c, context)?.let { release = it }
        }
    }

    val r = release ?: return
    if (suppressed) return
    val version = SemVer.parse(r.tag)?.toString() ?: r.tag.removePrefix("v")
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("تحديث جديد متاح") },
        text = {
            Column {
                Text(
                    "الإصدار ${ArabicText.indic(version)} من يقين جاهز (لديك ${ArabicText.indic(BuildConfig.VERSION_NAME.removeSuffix("-debug"))}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (r.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("ما الجديد", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        Changelog(r.body)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                release = null
                runCatching { nav.navigate(Routes.SETTINGS_UPDATES) { launchSingleTop = true } }
            }) { Text("تحديث الآن") }
        },
        dismissButton = {
            TextButton(onClick = {
                release = null
                scope.launch { UpdatePrompt.snooze(c, r.tag) }
            }) { Text("لاحقًا") }
        },
    )
}
