package app.yaqeen.feature.settings.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** البصمة/الوجه عبر BiometricPrompt (القوية أو الضعيفة، كما يدعم الجهاز). */
object BiometricAuth {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onError: (String?) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onError(if (cancelled) null else errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("استخدام الرمز")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/** يغطي التطبيق كله ما دام مقفلًا (أو ما دام إعداد القفل لم يُقرأ بعد عند البدء). */
@Composable
fun AppLockOverlay(c: AppContainer) {
    val phase by AppLock.phase.collectAsState()
    if (phase == LockPhase.Unlocked) return
    Surface(
        Modifier
            .fillMaxSize()
            // يمنع وصول اللمس إلى ما تحت الشاشة
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (phase == LockPhase.Locked) LockScreen(c)
    }
}

@Composable
private fun LockScreen(c: AppContainer) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val cfg by AppLock.config.collectAsState()
    val pinLength = cfg?.pinLength?.takeIf { it in PinHasher.MIN_LENGTH..PinHasher.MAX_LENGTH } ?: PinHasher.MAX_LENGTH
    val bioAvailable = remember(context) { BiometricAuth.available(context) }
    val useBio = cfg?.biometric == true && bioAvailable && activity != null

    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var checking by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

    // زر الرجوع يخرج من التطبيق ولا يتجاوز القفل
    BackHandler { (context as? Activity ?: activity)?.moveTaskToBack(true) }

    fun askBiometric() {
        val a = activity ?: return
        BiometricAuth.prompt(a, "فتح يقين", onSuccess = { AppLock.unlock() }, onError = { err -> if (err != null) message = err })
    }

    LaunchedEffect(Unit) {
        lockedUntil = AppLock.lockoutUntil(c)
        if (useBio) askBiometric()
    }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val lockedOut = lockedUntil > now

    fun submit(value: String) {
        if (checking) return
        checking = true
        scope.launch {
            when (val r = AppLock.checkPin(c, value)) {
                PinResult.Ok -> AppLock.unlock()
                is PinResult.Wrong -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    message = "الرمز غير صحيح" + if (r.attemptsLeft in 1..2) " – بقيت ${ArabicText.indic(r.attemptsLeft)} محاولة" else ""
                }
                is PinResult.LockedOut -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lockedUntil = r.untilMs
                    message = null
                }
            }
            pin = ""
            checking = false
        }
    }

    fun press(d: Char) {
        if (lockedOut || checking || pin.length >= PinHasher.MAX_LENGTH) return
        message = null
        pin += d
        // طول الرمز محفوظ: يُتحقق تلقائيًا عند اكتماله
        if (pin.length == pinLength) submit(pin)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text("يقين", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            if (lockedOut) "محاولات كثيرة، انتظر ${ArabicText.indic(((lockedUntil - now + 999) / 1000).toInt())} ثانية" else "أدخل رمز القفل",
            style = MaterialTheme.typography.bodyLarge,
            color = if (lockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PinDots(filled = pin.length, total = pinLength)
        Spacer(Modifier.height(12.dp))
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        // لوحة الأرقام بترتيب لوحات الهاتف المعتاد (١ يسارًا) مهما كان اتجاه الواجهة
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            PinPad(
                enabled = !lockedOut && !checking,
                onDigit = ::press,
                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onBiometric = if (useBio) ({ askBiometric() }) else null,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { forgot = true }) { Text("نسيت الرمز؟") }
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("نسيت رمز القفل") },
            text = {
                Text(
                    "الرمز محفوظ على هذا الجهاز فقط ولا يمكن استرجاعه. لإزالته: إعدادات الجوال ← التطبيقات ← يقين ← التخزين ← مسح البيانات. " +
                        "يُحذف بذلك ما في الجهاز من بيانات يقين، وإن كنت مسجّلًا بحساب Google تعود بياناتك المتزامنة بعد تسجيل الدخول.",
                )
            },
            confirmButton = { TextButton(onClick = { forgot = false }) { Text("حسنًا") } },
        )
    }
}

@Composable
internal fun PinDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)?) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { for (d in r) DigitKey(d, enabled, onDigit) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY), contentAlignment = Alignment.Center) {
                if (onBiometric != null) {
                    IconButton(onClick = onBiometric, modifier = Modifier.size(KEY)) {
                        Icon(Icons.Filled.Fingerprint, "الفتح بالبصمة", Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            DigitKey('0', enabled, onDigit)
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(KEY)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "حذف")
            }
        }
    }
}

private val KEY = 72.dp

@Composable
private fun DigitKey(d: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    Surface(
        onClick = { onDigit(d) },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(KEY),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(ArabicText.indic(d - '0'), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
