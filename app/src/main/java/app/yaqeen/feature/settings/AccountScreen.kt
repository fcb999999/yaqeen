package app.yaqeen.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.core.ArabicText
import app.yaqeen.data.auth.AuthState
import app.yaqeen.data.sync.SyncStatus
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.YaqeenCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- تسجيل الدخول

internal class SignInController(
    val busy: Boolean,
    val error: String?,
    val start: () -> Unit,
    val clearError: () -> Unit,
)

private val ARABIC_CHAR = Regex("[\\u0600-\\u06FF]")

internal fun describeSignInError(t: Throwable): String = when (t) {
    is GetCredentialCancellationException -> "أُلغي تسجيل الدخول."
    is NoCredentialException -> "لا يوجد حساب Google على هذا الجهاز. أضف حسابًا من إعدادات النظام ثم أعد المحاولة."
    is GetCredentialException -> "تعذّر تسجيل الدخول عبر Google. تحقق من اتصالك بالإنترنت وأعد المحاولة."
    is com.google.firebase.FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت. أعد المحاولة عند توفره."
    else -> t.message?.takeIf { ARABIC_CHAR.containsMatchIn(it) } ?: "تعذّر تسجيل الدخول. حاول مرة أخرى."
}

/** يربط زر "تسجيل الدخول بحساب Google" بـ AuthRepository (يستخرج Activity من السياق). */
@Composable
internal fun rememberSignIn(c: AppContainer, onSuccess: () -> Unit = {}): SignInController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    return SignInController(
        busy = busy,
        error = error,
        start = start@{
            if (busy) return@start
            val activity = context.findActivity()
            if (activity == null) {
                error = "تعذّر فتح نافذة تسجيل الدخول."
                return@start
            }
            busy = true
            error = null
            scope.launch {
                val r = c.auth.signInWithGoogle(activity)
                busy = false
                r.onSuccess { onSuccess() }.onFailure { error = describeSignInError(it) }
            }
        },
        clearError = { error = null },
    )
}

@Composable
internal fun GoogleSignInButton(ctl: SignInController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = ctl.start, enabled = !ctl.busy, modifier = Modifier.fillMaxWidth()) {
            if (ctl.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("تسجيل الدخول بحساب Google")
        }
        if (ctl.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(ctl.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- شاشة الحساب

@Composable
internal fun AccountScreen(nav: NavHostController, c: AppContainer) {
    val auth by c.auth.state.collectAsState()
    SettingsPage("حساب Google", onBack = { nav.popBackStack() }) {
        when (val st = auth) {
            AuthState.Unavailable -> FirebaseUnavailableCard()
            AuthState.SignedOut -> SignedOutCard(c)
            is AuthState.SignedIn -> SignedInCard(c, st)
        }
        Spacer(Modifier.height(8.dp))
        SignOutPolicyCard()
    }
}

@Composable
internal fun FirebaseUnavailableCard() {
    YaqeenCard(Modifier.fillMaxWidth()) {
        EmptyState(
            title = "تسجيل الدخول غير متاح في هذا البناء",
            message = "هذا الإصدار غير مرتبط بخدمة Firebase، لذلك يعمل يقين كاملًا على جهازك بلا إنترنت وبلا حساب، ولا تتم أي مزامنة سحابية. كل بياناتك محفوظة محليًا على هذا الجهاز فقط.",
            icon = Icons.Filled.CloudOff,
        )
        Text("للمطوّر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(
                "لتفعيل تسجيل الدخول والمزامنة: أنشئ مشروع Firebase، فعّل الدخول بحساب Google، ثم نزّل ملف google-services.json وضعه في المجلد app/ وأعد بناء التطبيق. " +
                    "الخطوات الكاملة (بصمات SHA-1 وSHA-256 وقواعد Firestore) موجودة في الملف firebase/README.md داخل المستودع.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            Spacer(Modifier.height(8.dp))
            Pill("FIREBASE_CONFIGURED = false")
        }
    }
}

@Composable
private fun SignedOutCard(c: AppContainer) {
    val ctl = rememberSignIn(c)
    YaqeenCard(Modifier.fillMaxWidth()) {
        Text("أنت تستخدم يقين في الوضع المحلي", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "سجّل الدخول بحساب Google لتتزامن بياناتك (القراءة، الأذكار، الصلوات، المهام، والإعدادات) بين أجهزتك وتُحفظ في مساحتك الخاصة. " +
                "التطبيق يعمل كاملًا بدون حساب، ولن يرى أحد بياناتك.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GoogleSignInButton(ctl)
        Spacer(Modifier.height(8.dp))
        Text(
            "بياناتك الحالية على هذا الجهاز تُضمّ إلى أول حساب تسجّل به.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignedInCard(c: AppContainer, st: AuthState.SignedIn) {
    var confirm by remember { mutableStateOf(false) }
    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(st.photoUrl, st.name ?: st.email, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(st.name ?: "مستخدم يقين", style = MaterialTheme.typography.titleMedium)
                if (st.email != null) Text(st.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
    }
    Spacer(Modifier.height(8.dp))
    SyncGroup(c)
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

internal const val SIGN_OUT_SUMMARY =
    "ستبقى بياناتك على هذا الجهاز وتتوقف المزامنة. إن سجّلت الدخول لاحقًا بحساب مختلف فسيُستبدل ما على هذا الجهاز ببيانات ذلك الحساب. " +
        "تأكد من اكتمال المزامنة قبل الخروج."

@Composable
private fun SignOutPolicyCard() {
    YaqeenCard(Modifier.fillMaxWidth()) {
        Text("ماذا يحدث عند تسجيل الخروج؟", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        listOf(
            "تبقى بياناتك محفوظة على هذا الجهاز كما هي، ويستمر التطبيق بالعمل بلا إنترنت، وتتوقف المزامنة فقط.",
            "إذا سجّلت الدخول لاحقًا بالحساب نفسه فستُدمج بياناتك وتُزامَن من جديد.",
            "إذا سجّلت الدخول بحساب مختلف فسيُستبدل ما على هذا الجهاز ببيانات ذلك الحساب حتى لا تختلط بيانات حسابين. بيانات الحساب الأول تبقى محفوظة في سحابته إن كانت قد تمت مزامنتها.",
            "لذلك تأكد من اكتمال المزامنة (لا تغييرات معلّقة) قبل تسجيل الخروج.",
        ).forEach { line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- المزامنة

/** بطاقة حالة المزامنة وزر "مزامنة الآن" (تُستخدم في الإعدادات وفي شاشة الحساب). */
@Composable
internal fun SyncGroup(c: AppContainer) {
    val auth by c.auth.state.collectAsState()
    val sync by c.sync.state.collectAsState()
    val notify = rememberNotifier()
    val signedIn = auth is AuthState.SignedIn
    val now = System.currentTimeMillis()

    SettingsGroup("المزامنة") {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sync.status == SyncStatus.SYNCING) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(syncStatusText(auth, sync.status), style = MaterialTheme.typography.bodyLarge)
            }
            if (sync.status == SyncStatus.ERROR && !sync.message.isNullOrBlank() && ARABIC_CHAR.containsMatchIn(sync.message!!)) {
                Text(sync.message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (signedIn) {
            RowDivider()
            InfoRow("آخر مزامنة", ArabicUnits.ago(now, sync.lastSyncAt))
            RowDivider()
            InfoRow(
                "تغييرات بانتظار الإرسال",
                if (sync.pending == 0) "لا شيء" else ArabicText.indic(sync.pending),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    c.appScope.launch {
                        val ok = c.sync.syncNow()
                        withContext(Dispatchers.Main) {
                            notify(if (ok) "اكتملت المزامنة." else "تعذّرت المزامنة الآن. ستُرسل تغييراتك تلقائيًا عند توفر الاتصال.")
                        }
                    }
                },
                enabled = sync.status != SyncStatus.SYNCING,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("مزامنة الآن") }
            Spacer(Modifier.height(8.dp))
        }
        RowDivider()
        Hint(
            "يعمل يقين أولًا على جهازك (Local-First): كل تعديل يُحفظ فورًا محليًا حتى بلا إنترنت، ثم يُرسل تلقائيًا إلى مساحتك الخاصة عند توفر الاتصال " +
                "وتسجيل الدخول، وتُجلب تعديلات أجهزتك الأخرى. عند التعارض يغلب التعديل الأحدث.",
        )
    }
}

private fun syncStatusText(auth: AuthState, status: SyncStatus): String = when {
    auth is AuthState.Unavailable -> "وضع محلي: المزامنة غير مفعّلة في هذا البناء"
    auth is AuthState.SignedOut || status == SyncStatus.LOCAL_ONLY -> "وضع محلي: سجّل الدخول لمزامنة بياناتك"
    status == SyncStatus.SYNCING -> "جارٍ المزامنة…"
    status == SyncStatus.OFFLINE -> "لا يوجد اتصال، ستُرسل التغييرات عند عودته"
    status == SyncStatus.ERROR -> "تعذّرت المزامنة، ستُعاد المحاولة تلقائيًا"
    else -> "بياناتك متزامنة"
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
