package app.yaqeen.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.core.ArabicText
import app.yaqeen.data.auth.AuthState
import app.yaqeen.data.settings.Settings
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.HeroCard
import app.yaqeen.ui.components.LocalHero
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.Hue
import app.yaqeen.ui.theme.ThemeStyle
import app.yaqeen.ui.theme.YaqeenTheme
import app.yaqeen.ui.components.NavRow
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTextStyles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun ChevronNavRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hue: Hue? = null,
    onClick: () -> Unit,
) = NavRow(
    title = title,
    onClick = onClick,
    subtitle = subtitle,
    leading = icon,
    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) },
    hue = hue,
)

@Composable
internal fun SettingsHomeScreen(nav: NavHostController, c: AppContainer) {
    val auth by c.auth.state.collectAsState()
    SettingsPage("الإعدادات", onBack = { nav.popBackStack() }) {
        val notify = rememberNotifier()
        fun open(route: String) {
            if (!nav.tryNavigate(route)) notify("هذه الشاشة غير متاحة في هذا الإصدار بعد.")
        }

        ProfileCard(auth) { open(Routes.SETTINGS_ACCOUNT) }
        Spacer(Modifier.height(8.dp))

        SettingsGroup("حساب Google") {
            ChevronNavRow(
                title = when (auth) {
                    is AuthState.SignedIn -> "إدارة الحساب"
                    AuthState.SignedOut -> "تسجيل الدخول بحساب Google"
                    AuthState.Unavailable -> "حالة الحساب"
                },
                subtitle = when (auth) {
                    is AuthState.SignedIn -> "المزامنة بين أجهزتك وتسجيل الخروج"
                    AuthState.SignedOut -> "لمزامنة بياناتك بين أجهزتك"
                    AuthState.Unavailable -> "غير متاح في هذا البناء (الوضع المحلي)"
                },
                icon = Icons.Filled.AccountCircle,
            ) { open(Routes.SETTINGS_ACCOUNT) }
        }

        AppearanceGroup(c)
        QuranGroup(c)
        AdhkarGroup(c)

        SettingsGroup("الإشعارات والصلاة") {
            ChevronNavRow("الإشعارات", "التحكم في كل نوع من التذكيرات وأوقاتها", Icons.Filled.Notifications, Hue.AMBER) { open(Routes.SETTINGS_NOTIFICATIONS) }
            RowDivider()
            ChevronNavRow("مواقيت الصلاة", "طريقة الحساب والمذهب والموقع", Icons.Filled.Schedule, Hue.TEAL) { open(Routes.PRAYER_TIMES) }
            RowDivider()
            ChevronNavRow("المؤذن", "اختيار صوت الأذان لكل صلاة", Icons.Filled.VolumeUp, Hue.GREEN) { open(SettingsRoutes.ADHAN_SETTINGS) }
        }

        SyncGroup(c)

        SettingsGroup("التخزين") {
            ChevronNavRow("إدارة الملفات المحمّلة", "الصوتيات والتفاسير المحمّلة للاستخدام دون إنترنت", Icons.Filled.Download, Hue.BLUE) { open(SettingsRoutes.QURAN_DOWNLOADS) }
            RowDivider()
            ChevronNavRow("مساحة التخزين", "حجم قاعدة البيانات والملفات والذاكرة المؤقتة", Icons.Filled.Storage, Hue.PINK) { open(SettingsRoutes.STORAGE) }
        }

        SettingsGroup("الخصوصية والبيانات") {
            ChevronNavRow("قفل التطبيق", "رمز سري مع البصمة أو الوجه", Icons.Filled.Lock, Hue.TEAL) {
                open(app.yaqeen.feature.settings.lock.APP_LOCK_ROUTE)
            }
            RowDivider()
            ChevronNavRow("الخصوصية", "ما نجمعه وما لا نجمعه وحقوقك", Icons.Filled.PrivacyTip, Hue.GREEN) { open(SettingsRoutes.PRIVACY) }
            RowDivider()
            ChevronNavRow("إدارة البيانات", "تصدير بياناتك أو استيرادها أو حذفها", Icons.Filled.Backup, Hue.CORAL) { open(SettingsRoutes.DATA) }
            Hint("بياناتك تبقى على جهازك، وإن سجّلت الدخول تُحفظ أيضًا في مساحتك الخاصة التي لا يصل إليها أحد غيرك.")
        }

        SettingsGroup("التطبيق") {
            ChevronNavRow(
                "رقم الإصدار",
                "${ArabicText.indic(BuildConfig.VERSION_NAME)} (${ArabicText.indic(BuildConfig.VERSION_CODE)})",
                Icons.Filled.Tag,
                Hue.AMBER,
            ) { open(Routes.SETTINGS_UPDATES) }
            RowDivider()
            ChevronNavRow("التحقق من التحديثات", "تحديث التطبيق إلى أحدث إصدار", Icons.Filled.SystemUpdate, Hue.TEAL) { open(Routes.SETTINGS_UPDATES) }
            RowDivider()
            ChevronNavRow("عن التطبيق", "الشكر والمصادر والتراخيص", Icons.Filled.Info, Hue.BLUE) { open(SettingsRoutes.ABOUT) }
        }

        if (auth is AuthState.SignedIn) SignOutRow(c)
    }
}

@Composable
private fun ProfileCard(auth: AuthState, onClick: () -> Unit) {
    val hayat = YaqeenTheme.isHayat
    val body: @Composable ColumnScope.() -> Unit = {
        // classic: الألوان الافتراضية للبطاقة كما كانت؛ حياة: ألوان محتوى بطاقة البطل.
        val on = if (hayat) LocalHero.current.content else androidx.compose.ui.graphics.Color.Unspecified
        val sub = if (hayat) on.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
        val chevron = if (hayat) on else MaterialTheme.colorScheme.outline
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (auth) {
                is AuthState.SignedIn -> {
                    ProfileAvatar(auth.photoUrl, auth.name ?: auth.email, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(auth.name ?: "مستخدم يقين", style = MaterialTheme.typography.titleMedium, color = on)
                        if (auth.email != null) Text(auth.email, style = MaterialTheme.typography.bodyMedium, color = sub)
                    }
                }
                else -> {
                    ProfileAvatar(null, "ي", 56.dp)
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("وضع محلي", style = MaterialTheme.typography.titleMedium, color = on)
                        Text(
                            if (auth == AuthState.Unavailable) "بياناتك محفوظة على هذا الجهاز فقط" else "سجّل الدخول لمزامنة بياناتك بين أجهزتك",
                            style = MaterialTheme.typography.bodyMedium,
                            color = sub,
                        )
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = chevron)
        }
    }
    if (hayat) HeroCard(Area.SETTINGS, Modifier.fillMaxWidth(), onClick = onClick, content = body)
    else YaqeenCard(Modifier.fillMaxWidth(), onClick = onClick, content = body)
}

@Composable
private fun AppearanceGroup(c: AppContainer) {
    val theme by c.rememberSetting(Settings.THEME_MODE)
    val style by c.rememberSetting(Settings.THEME_STYLE)
    val scale by c.rememberSetting(Settings.UI_FONT_SCALE)
    SettingsGroup("المظهر") {
        ThemeStylePicker(
            selected = ThemeStyle.normalize(style),
            onSelect = { c.putSetting(Settings.THEME_STYLE, it) },
        )
        RowDivider()
        ChoiceChips(
            title = "السمة",
            options = listOf("system" to "حسب النظام", "light" to "فاتح", "dark" to "داكن"),
            selected = theme,
            onSelect = { c.putSetting(Settings.THEME_MODE, it) },
        )
        RowDivider()
        SliderRow(
            title = "حجم خط الواجهة",
            value = scale,
            range = UI_FONT_SCALE_MIN..UI_FONT_SCALE_MAX,
            steps = 10,
            valueLabel = { ArabicText.indic((it * 100).roundToInt()) + "٪" },
            onCommit = { c.putSetting(Settings.UI_FONT_SCALE, (it * 20).roundToInt() / 20f) },
        )
        Text(
            "هكذا يظهر النص في التطبيق بعد التغيير.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun QuranGroup(c: AppContainer) {
    val size by c.rememberSetting(Settings.QURAN_FONT_SIZE)
    val spacing by c.rememberSetting(Settings.QURAN_LINE_SPACING)
    val background by c.rememberSetting(Settings.QURAN_BACKGROUND)
    val keepOn by c.rememberSetting(Settings.QURAN_KEEP_SCREEN_ON)
    val speed by c.rememberSetting(Settings.QURAN_SCROLL_SPEED)
    // معاينة من النص القرآني المحلي نفسه (لا نص مولّد).
    val preview by produceState<String?>(null) { value = runCatching { c.quran().ayah(112, 1).text }.getOrNull() }

    SettingsGroup("إعدادات المصحف") {
        if (preview != null) {
            Text(
                app.yaqeen.feature.quran.reader.QuranText.forHafsFont(preview!!),
                style = YaqeenTextStyles.quran(size, spacing),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            RowDivider()
        }
        SliderRow("حجم خط المصحف", size, 20f..48f, 27, { ArabicText.indic(it.roundToInt()) }) { c.putSetting(Settings.QURAN_FONT_SIZE, it.roundToInt().toFloat()) }
        RowDivider()
        SliderRow("تباعد الأسطر", spacing, 1.4f..2.6f, 11, { ArabicText.indic("%.1f".format(java.util.Locale.US, it)).replace('.', '٫') }) {
            c.putSetting(Settings.QURAN_LINE_SPACING, (it * 10).roundToInt() / 10f)
        }
        RowDivider()
        ChoiceChips(
            title = "خلفية المصحف",
            options = listOf("ivory" to "عاجي", "sepia" to "ورقي", "gray" to "رمادي", "night" to "ليلي"),
            selected = background,
            onSelect = { c.putSetting(Settings.QURAN_BACKGROUND, it) },
        )
        RowDivider()
        SliderRow("سرعة التمرير التلقائي", speed.toFloat(), 1f..10f, 8, { ArabicText.indic(it.roundToInt()) }) { c.putSetting(Settings.QURAN_SCROLL_SPEED, it.roundToInt()) }
        RowDivider()
        SwitchRow(
            title = "إبقاء الشاشة مضاءة أثناء القراءة",
            checked = keepOn,
            onCheckedChange = { c.putSetting(Settings.QURAN_KEEP_SCREEN_ON, it) },
        )
    }
}

@Composable
private fun AdhkarGroup(c: AppContainer) {
    val auto by c.rememberSetting(Settings.ADHKAR_AUTO_ADVANCE)
    val haptics by c.rememberSetting(Settings.ADHKAR_HAPTICS)
    val fadl by c.rememberSetting(Settings.ADHKAR_SHOW_FADL)
    val textSize by c.rememberSetting(Settings.ADHKAR_TEXT_SIZE)
    val axis by c.rememberSetting(Settings.ADHKAR_SWIPE_AXIS)
    SettingsGroup("إعدادات الأذكار") {
        SwitchRow("الانتقال التلقائي للذكر التالي", auto, { c.putSetting(Settings.ADHKAR_AUTO_ADVANCE, it) }, "ينتقل تلقائيًا عند اكتمال عدّ الذكر")
        RowDivider()
        SwitchRow("الاهتزاز عند العدّ", haptics, { c.putSetting(Settings.ADHKAR_HAPTICS, it) })
        RowDivider()
        SwitchRow("إظهار فضل الذكر", fadl, { c.putSetting(Settings.ADHKAR_SHOW_FADL, it) })
        RowDivider()
        SliderRow("حجم خط الأذكار", textSize, 16f..40f, 23, { ArabicText.indic(it.roundToInt()) }) { c.putSetting(Settings.ADHKAR_TEXT_SIZE, it.roundToInt().toFloat()) }
        RowDivider()
        ChoiceChips(
            title = "اتجاه التنقل بين الأذكار",
            options = listOf("horizontal" to "أفقي (يمين ويسار)", "vertical" to "عمودي (أعلى وأسفل)"),
            selected = axis,
            onSelect = { c.putSetting(Settings.ADHKAR_SWIPE_AXIS, it) },
        )
    }
}

@Composable
private fun SignOutRow(c: AppContainer) {
    var confirm by remember { mutableStateOf(false) }
    SettingsGroup(null) {
        NavRow(
            title = "تسجيل الخروج",
            onClick = { confirm = true },
            subtitle = "تبقى بياناتك على هذا الجهاز",
            leading = Icons.AutoMirrored.Filled.Logout,
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = "تسجيل الخروج؟",
            message = SIGN_OUT_SUMMARY,
            confirmText = "تسجيل الخروج",
            onConfirm = { c.appScope.launch { c.auth.signOut() } },
            onDismiss = { confirm = false },
        )
    }
}
