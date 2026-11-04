package app.yaqeen.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.BuildConfig
import app.yaqeen.R
import app.yaqeen.core.ArabicText
import app.yaqeen.feature.settings.updates.UpdateChecker
import app.yaqeen.feature.settings.updates.UpdateInstaller
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.theme.YaqeenColors

private data class PolicySection(val title: String, val lines: List<String>, val bullets: Boolean = true)

private val PRIVACY_SECTIONS = listOf(
    PolicySection(
        "الفكرة باختصار",
        listOf(
            "يقين يعمل على جهازك أولًا وبلا إنترنت. بياناتك تبقى على جهازك، وإن اخترت تسجيل الدخول تُحفظ نسخة منها في مساحة خاصة بك فقط لتتزامن بين أجهزتك.",
        ),
        bullets = false,
    ),
    PolicySection(
        "ما الذي نجمعه",
        listOf(
            "بيانات تسجيل الدخول بحساب Google (اختياري): المعرّف الفريد للحساب والبريد الإلكتروني والاسم. تُستخدم لتعريف مساحتك الخاصة وعرض اسمك في الإعدادات. صورة الحساب تُعرض من رابطها ولا نحفظ نسخة منها.",
            "سجلات عبادتك التي تُدخلها بنفسك: موضع القراءة والأوراد والعلامات المرجعية، تقدّم الأذكار والمفضلة، تسجيل الصلوات والسنن، المهام، أحداث التقويم، وإعدادات التطبيق. تُحفظ على جهازك وتُزامَن إلى مساحتك الخاصة في Firebase Firestore عند تسجيل الدخول فقط.",
            "الموقع الجغرافي (إن سمحت به): يُستخدم لحساب مواقيت الصلاة واتجاه القبلة، ويُحفظ على جهازك فقط ولا يُزامَن مع حسابك.",
            "ملفات التلاوة والتفسير: تُنزَّل من خوادم عامة عند طلبك وتُخزَّن على جهازك، ولا يُرسل فيها شيء عنك.",
        ),
    ),
    PolicySection(
        "ما الذي لا نجمعه",
        listOf(
            "لا إعلانات إطلاقًا.",
            "لا أدوات تحليل أو تتبّع لسلوكك داخل التطبيق.",
            "لا نشارك بياناتك مع أي طرف ثالث ولا نبيعها.",
            "لا تظهر بياناتك لأي مستخدم آخر أبدًا؛ قواعد الأمان في الخادم تسمح لصاحب الحساب وحده بقراءة بياناته وكتابتها.",
        ),
    ),
    PolicySection(
        "الخدمات التي يستخدمها التطبيق",
        listOf(
            "Firebase Authentication وFirestore وCloud Messaging من Google: لتسجيل الدخول والمزامنة وإشعارات الإصدارات الجديدة.",
            "GitHub Releases: للتحقق من وجود إصدار أحدث وتنزيله. يرى الخادم عنوان الاتصال (IP) كأي طلب عادي ولا تُرسل إليه أي بيانات شخصية.",
        ),
    ),
    PolicySection(
        "حقوقك",
        listOf(
            "الاطلاع والتصدير: من الإعدادات > إدارة البيانات > «تصدير بياناتي» تحصل على ملف JSON بكل بياناتك.",
            "الحذف: من إدارة البيانات > «حذف كل بياناتي»؛ تُعلَّم نسختك السحابية كمحذوفة ثم تُمسح بيانات الجهاز. تبقى سجلات الحذف في الخادم بحالة «محذوفة» لتصل لبقية أجهزتك ولا يعرضها أي جهاز.",
            "الانسحاب: يمكنك تسجيل الخروج في أي وقت فتبقى بياناتك على جهازك وتتوقف المزامنة، أو استخدام التطبيق كاملًا دون حساب.",
            "الأذونات: يمكنك سحب إذن الإشعارات أو الموقع من إعدادات النظام في أي وقت.",
        ),
    ),
    PolicySection(
        "التغييرات على هذه السياسة",
        listOf("إذا تغيّرت هذه السياسة سيُحدَّث هذا النص داخل التطبيق مع الإصدار الذي يتضمن التغيير."),
        bullets = false,
    ),
)

@Composable
internal fun PrivacyScreen(nav: NavHostController) {
    SettingsPage("الخصوصية", onBack = { nav.popBackStack() }) {
        YaqeenCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "بياناتك أمانة: تبقى على جهازك، ولا تُشارك ولا تُباع، ولا يراها أحد غيرك.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(12.dp))
        PRIVACY_SECTIONS.forEach { s ->
            YaqeenCard(Modifier.fillMaxWidth()) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                s.lines.forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        if (s.bullets) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private data class Credit(val title: String, val detail: String, val license: String)

private val CREDITS = listOf(
    Credit("نص القرآن الكريم", "النص العثماني من مشروع تنزيل (Tanzil)، مع المطابقة والتحقق من سلامته. لا يُعدَّل النص أبدًا داخل التطبيق.", "ترخيص مشروع تنزيل (tanzil.net)"),
    Credit("التفسير الميسَّر", "مجمع الملك فهد لطباعة المصحف الشريف بالمدينة المنورة.", "حقوق المجمع، يُعرض بذكر المصدر"),
    Credit("نصوص الأذكار", "مجموعات بيانات مفتوحة المصدر مبنية على كتاب «حصن المسلم».", "MIT"),
    Credit("الخطوط", "Amiri وAmiri Quran (للمصحف والأذكار) وIBM Plex Sans Arabic (للواجهة).", "SIL Open Font License 1.1"),
    Credit("مواقيت الصلاة", "مكتبة Adhan لحساب أوقات الصلاة (Batoul Apps).", "MIT"),
    Credit("مكتبات أندرويد", "Jetpack Compose وRoom وWorkManager وMedia3 وFirebase وغيرها.", "Apache License 2.0"),
)

@Composable
internal fun AboutScreen(nav: NavHostController) {
    SettingsPage("عن التطبيق", onBack = { nav.popBackStack() }) {
        val context = LocalContext.current
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(YaqeenColors.Ivory), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(painterResource(R.drawable.ic_logo_foreground), contentDescription = "شعار يقين", modifier = Modifier.size(88.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("يقين", style = MaterialTheme.typography.headlineMedium)
            Text("YAQEEN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "الإصدار ${ArabicText.indic(BuildConfig.VERSION_NAME)} (${ArabicText.indic(BuildConfig.VERSION_CODE)})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "رفيقك اليومي للقرآن والأذكار والصلاة: يعمل بلا إنترنت، ويحفظ تقدّمك، ويحترم خصوصيتك.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        SettingsGroup("الشكر والمصادر") {
            CREDITS.forEachIndexed { i, cr ->
                if (i > 0) RowDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(cr.title, style = MaterialTheme.typography.titleSmall)
                    Text(cr.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("الترخيص: ${cr.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Hint("مصادر التلاوات والتفاسير الإضافية المحمّلة تُذكر في شاشاتها. نسأل الله أن يتقبّل هذا العمل خالصًا لوجهه الكريم.")
        }
        if (UpdateChecker.isValidRepo(BuildConfig.GITHUB_REPO)) {
            OutlinedButton(
                onClick = { UpdateInstaller.openUrl(context, "https://github.com/${BuildConfig.GITHUB_REPO.trim()}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("صفحة المشروع على GitHub") }
        }
    }
}
