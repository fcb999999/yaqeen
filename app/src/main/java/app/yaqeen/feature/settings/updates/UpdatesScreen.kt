package app.yaqeen.feature.settings.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.core.ArabicText
import app.yaqeen.feature.settings.ArabicUnits
import app.yaqeen.feature.settings.ByteSize
import app.yaqeen.feature.settings.Hint
import app.yaqeen.feature.settings.SettingsPage
import app.yaqeen.feature.settings.rememberNotifier
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes

@Composable
fun UpdatesScreen(nav: NavHostController, c: AppContainer) {
    val vm = containerViewModel { UpdatesViewModel(it) }
    val check by vm.check.collectAsState()
    val install by vm.install.collectAsState()
    val context = LocalContext.current

    // العودة من شاشة "تثبيت التطبيقات غير المعروفة": إن مُنح الإذن نكمل التثبيت.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (install is InstallUi.NeedsPermission && UpdateInstaller.canInstallPackages(context)) vm.retryInstall()
    }

    SettingsPage("التحقق من التحديثات", onBack = { nav.popBackStack() }) {
        val notify = rememberNotifierSafe()
        VersionCard()
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

        if (vm.fromPlayStore) {
            YaqeenCard(Modifier.fillMaxWidth()) {
                Text("تُدار التحديثات من متجر Google Play", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "ثُبّت يقين من المتجر، لذلك يتولى المتجر تحديثه. يمكنك فتح صفحة التطبيق فيه للتحقق من وجود تحديث.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (!UpdateInstaller.openStorePage(context)) notify("تعذّر فتح المتجر.") }, modifier = Modifier.fillMaxWidth()) {
                    Text("افتح صفحة التطبيق في المتجر")
                }
            }
            return@SettingsPage
        }

        when (val st = check) {
            CheckUi.Checking -> YaqeenCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("جارٍ التحقق من وجود إصدار أحدث…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is CheckUi.Done -> ResultSection(vm, st, install, context)
        }

        Spacer(Modifier.height(12.dp))
        if (check is CheckUi.Done && vm.configured) {
            OutlinedButton(
                onClick = { vm.check() },
                enabled = install !is InstallUi.Downloading && install !is InstallUi.Verifying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("التحقق من وجود إصدار أحدث") }
            val at = (check as CheckUi.Done).checkedAtMs
            Hint("آخر تحقق: ${ArabicUnits.ago(System.currentTimeMillis(), at)}. بياناتك تبقى محفوظة عند التحديث لأن الحزمة نفسها تُحدَّث.")
        }
    }
}

@Composable
private fun rememberNotifierSafe(): (String) -> Unit = rememberNotifier()

@Composable
private fun VersionCard() {
    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("الإصدار الحالي", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${ArabicText.indic(BuildConfig.VERSION_NAME)} (${ArabicText.indic(BuildConfig.VERSION_CODE)})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ResultSection(vm: UpdatesViewModel, st: CheckUi.Done, install: InstallUi, context: android.content.Context) {
    when (val r = st.result) {
        UpdateCheckResult.Disabled -> YaqeenCard(Modifier.fillMaxWidth()) {
            EmptyState(
                title = "التحديث من داخل التطبيق غير مُفعَّل",
                message = "لم يُضبط مستودع GitHub الذي تُنشر فيه الإصدارات في هذا البناء.",
                icon = Icons.Filled.CloudOff,
            )
            Text("للمطوّر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "اضبط yaqeen.githubRepo=owner/name في ملف gradle.properties (أو مرّر -Pyaqeen.githubRepo=owner/name عند البناء) ثم انشر إصدارًا على GitHub Releases. " +
                    "راجع .github/README.md.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UpdateCheckResult.UpToDate -> YaqeenCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = app.yaqeen.ui.theme.YaqeenTheme.extra.success)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("أنت على أحدث إصدار", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "آخر إصدار منشور: ${ArabicText.indic(r.latestTag.removePrefix("v"))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        UpdateCheckResult.NoRelease -> MessageCard("لا توجد إصدارات منشورة بعد", "لم يُنشر أي إصدار في المستودع حتى الآن (أو أن المستودع غير عام).")
        UpdateCheckResult.Offline -> MessageCard("لا يوجد اتصال بالإنترنت", "تعذّر الوصول إلى الخادم. تحقق من اتصالك وأعد المحاولة. التطبيق يعمل بصورة طبيعية بلا إنترنت.", offline = true)
        is UpdateCheckResult.RateLimited -> {
            val wait = r.resetAtEpochSeconds?.let { (it - System.currentTimeMillis() / 1000).coerceAtLeast(60) / 60 }
            MessageCard(
                "تجاوزت حدّ الطلبات المسموح",
                if (wait != null) "يحدّ GitHub عدد الطلبات المجهولة. حاول بعد ${ArabicUnits.plural(wait.toInt(), "دقيقة", "دقيقتين", "دقائق", "دقيقة")}." else "يحدّ GitHub عدد الطلبات المجهولة. حاول لاحقًا.",
            )
        }
        is UpdateCheckResult.Failed -> MessageCard("تعذّر التحقق من التحديثات", r.message)
        is UpdateCheckResult.Available -> AvailableCard(vm, r.release, install, context)
    }
}

@Composable
private fun MessageCard(title: String, message: String, offline: Boolean = false) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        EmptyState(title = title, message = message, icon = if (offline) Icons.Filled.CloudOff else Icons.Filled.ErrorOutline)
    }
}

@Composable
private fun AvailableCard(vm: UpdatesViewModel, release: ReleaseInfo, install: InstallUi, context: android.content.Context) {
    val notify = rememberNotifierSafe()
    val version = SemVer.parse(release.tag)?.toString() ?: release.tag
    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("إصدار جديد متاح", style = MaterialTheme.typography.titleMedium)
                Text(
                    "الإصدار ${ArabicText.indic(version)}" + (release.publishedDate?.let { " – ${ArabicText.indic(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill("جديد")
        }
        Spacer(Modifier.height(12.dp))

        when (install) {
            InstallUi.Idle -> {
                if (release.apk != null) {
                    Button(onClick = { vm.startUpdate(release) }, modifier = Modifier.fillMaxWidth()) {
                        Text("تحديث الآن" + (release.apk.size.takeIf { it > 0 }?.let { " (${ByteSize.format(it)})" } ?: ""))
                    }
                } else {
                    Text("لا يوجد ملف APK مرفق بهذا الإصدار، يمكنك فتح صفحة الإصدار.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { if (!UpdateInstaller.openUrl(context, release.htmlUrl ?: UpdateChecker.releasesPageUrl(vm.repo))) notify("تعذّر فتح الرابط.") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("فتح صفحة الإصدار") }
                }
            }
            is InstallUi.Downloading -> {
                val p = if (install.total > 0) install.done.toFloat() / install.total else 0f
                ProgressBar(p)
                Spacer(Modifier.height(6.dp))
                Text(
                    "جارٍ التنزيل… ${ArabicText.indic((p * 100).toInt())}٪ (${ByteSize.format(install.done)}" +
                        (if (install.total > 0) " من ${ByteSize.format(install.total)})" else ")"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { vm.cancelDownload() }) { Text("إلغاء") }
            }
            InstallUi.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("جارٍ التحقق من سلامة الملف وتوقيعه…", style = MaterialTheme.typography.bodyMedium)
            }
            is InstallUi.NeedsPermission -> {
                Text("تم تنزيل التحديث والتحقق منه (${verifiedText(install.checksumVerified)}).", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "لتثبيته يحتاج أندرويد إلى إذنك بتثبيت التطبيقات من يقين. اسمح بذلك في الشاشة التالية ثم ارجع وسيُكمل التثبيت تلقائيًا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (!UpdateInstaller.openUnknownSourcesSettings(context)) notify("تعذّر فتح إعدادات النظام.") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("السماح بتثبيت التحديثات") }
            }
            is InstallUi.Launched -> {
                Text("فُتح مثبّت النظام. أكمل التثبيت وسيُعاد تشغيل التطبيق ببياناتك كما هي.", style = MaterialTheme.typography.bodyMedium)
                Text("التحقق: ${verifiedText(install.checksumVerified)}، وتوقيع الملف يطابق توقيع التطبيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.retryInstall() }, modifier = Modifier.fillMaxWidth()) { Text("فتح المثبّت مجددًا") }
            }
            is InstallUi.Failed -> {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(install.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.dismissError() }, modifier = Modifier.fillMaxWidth()) { Text("حسنًا") }
            }
        }

        if (release.body.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("ما الجديد", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Changelog(release.body)
        }
    }
}

private fun verifiedText(checksum: Boolean) = if (checksum) "المجموع SHA-256 مطابق" else "لم يُنشر مجموع، اكتُفي بالتوقيع"

/** عرض ملاحظات الإصدار (ماركداون مخفّف). */
@Composable
internal fun Changelog(markdown: String) {
    val blocks = androidx.compose.runtime.remember(markdown) { MarkdownLight.parse(markdown) }
    Column {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    spansToString(b.spans),
                    style = if (b.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                is MdBlock.Bullet -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(spansToString(b.spans), style = MaterialTheme.typography.bodyMedium)
                }
                is MdBlock.Paragraph -> Text(spansToString(b.spans), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun spansToString(spans: List<MdSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        if (s.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) } else append(s.text)
    }
}
