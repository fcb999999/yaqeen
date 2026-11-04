package app.yaqeen.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.R
import app.yaqeen.data.auth.AuthState
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenColors
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.launch

private const val PAGES = 4

/** صفحات الترحيب عند أول تشغيل. تنتهي بضبط ONBOARDING_DONE والانتقال للرئيسية. */
@Composable
internal fun OnboardingScreen(nav: NavHostController, c: AppContainer) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()

    fun finish() {
        scope.launch {
            c.localPrefs.putBool(LocalPrefs.Keys.ONBOARDING_DONE, true)
            c.requestReschedule()
            // AppRoot يفتح الترحيب فوق الرئيسية، فنرجع إليها؛ وإلا ننتقل إليها مع إزالة الترحيب.
            if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < PAGES - 1) TextButton(onClick = { finish() }) { Text("تخطي") } else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturesPage()
                    2 -> PermissionsPage(c)
                    else -> AccountPage(c, onContinue = { finish() })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGES) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            if (pager.currentPage < PAGES - 1) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("التالي") }
            }
        }
    }
}

@Composable
private fun LogoPlate() {
    Box(Modifier.size(132.dp).clip(CircleShape).background(YaqeenColors.Ivory), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_logo_foreground), contentDescription = "شعار يقين", modifier = Modifier.size(120.dp))
    }
}

@Composable
private fun WelcomePage() {
    LogoPlate()
    Spacer(Modifier.height(24.dp))
    Text("يقين", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    Text("YAQEEN", style = MaterialTheme.typography.labelLarge, color = YaqeenTheme.extra.gold)
    Spacer(Modifier.height(16.dp))
    Text("مرحبًا بك", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "رفيقك اليومي للقرآن والأذكار والصلاة. هادئ، بسيط، ويعمل بلا إنترنت.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeaturesPage() {
    Text("ماذا يقدّم لك يقين؟", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    FeatureLine(Icons.Filled.MenuBook, "المصحف والأوراد", "قراءة المصحف كاملًا، علامات مرجعية، تفسير، وخطط ورد تتابع تقدّمك.")
    FeatureLine(Icons.Filled.WbSunny, "الأذكار", "أذكار الصباح والمساء والنوم وغيرها بعدّاد وتذكير في وقتها.")
    FeatureLine(Icons.Filled.Schedule, "الصلاة", "مواقيت دقيقة حسب موقعك، أذان، قبلة، وتسجيل صلواتك وسننك.")
    FeatureLine(Icons.Filled.CloudSync, "بياناتك لك", "كل شيء محفوظ على جهازك، ومزامنة اختيارية بين أجهزتك دون إعلانات أو تتبّع.")
}

@Composable
private fun PermissionsPage(c: AppContainer) {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ok = it
        if (it) c.requestReschedule()
    }
    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text("التنبيهات", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "نحتاج إذنك لإرسال تنبيهات الأذان وأذكار الصباح والمساء وتذكير الورد في أوقاتها. يمكنك التحكم بكل نوع منها أو إيقافه من الإعدادات.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    if (ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = YaqeenTheme.extra.success)
            Spacer(Modifier.width(8.dp))
            Text("تم السماح بالتنبيهات", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("السماح بالتنبيهات") }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "الموقع: لن نطلبه الآن. سيُطلب لاحقًا فقط عند فتح مواقيت الصلاة لحساب الأوقات واتجاه القبلة، ويبقى على جهازك.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AccountPage(c: AppContainer, onContinue: () -> Unit) {
    val auth by c.auth.state.collectAsState()
    val signIn = rememberSignIn(c)
    Icon(Icons.Filled.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text("حسابك (اختياري)", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    when (val st = auth) {
        is AuthState.SignedIn -> {
            Text(
                "أهلًا ${st.name ?: st.email ?: ""}، ستتزامن بياناتك بين أجهزتك.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("ابدأ") }
        }
        AuthState.SignedOut -> {
            Text(
                "سجّل الدخول بحساب Google لتتزامن بياناتك بين أجهزتك وتبقى محفوظة في مساحتك الخاصة. يمكنك أيضًا المتابعة دون حساب وتفعيله لاحقًا.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            GoogleSignInButton(signIn)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("المتابعة بدون حساب") }
        }
        AuthState.Unavailable -> {
            Text(
                "تسجيل الدخول غير متاح في هذا الإصدار، لكن يقين يعمل كاملًا على جهازك بلا حساب وبلا إنترنت.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("المتابعة بدون حساب") }
        }
    }
}
