package app.yaqeen.feature.settings.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.feature.settings.Hint
import app.yaqeen.feature.settings.RowDivider
import app.yaqeen.feature.settings.SettingsGroup
import app.yaqeen.feature.settings.SettingsPage
import app.yaqeen.feature.settings.rememberNotifier
import app.yaqeen.ui.components.SwitchRow
import kotlinx.coroutines.launch

const val APP_LOCK_ROUTE = "settings/lock"

/** خطوات نافذة الرمز. */
private enum class PinStep { VerifyToChange, VerifyToDisable, New, Confirm }

@Composable
fun AppLockSettingsScreen(nav: NavHostController, c: AppContainer) {
    SettingsPage("قفل التطبيق", onBack = { nav.popBackStack() }) {
        AppLockSettingsContent(c)
    }
}

@Composable
private fun AppLockSettingsContent(c: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotifier()
    val cfg by AppLock.config.collectAsState()
    val config = cfg ?: LockConfig()
    val bioAvailable = remember(context) { BiometricAuth.available(context) }
    var step by remember { mutableStateOf<PinStep?>(null) }
    var firstPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { AppLock.init(c) }

    SettingsGroup(null) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                if (config.enabled) "القفل مفعّل" else "القفل غير مفعّل",
                style = MaterialTheme.typography.titleMedium,
                color = if (config.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "يطلب يقين رمزًا سريًا (أو بصمتك) عند فتحه، فلا يطّلع أحد على وردك وسجلّاتك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (!config.enabled) {
                Button(onClick = { firstPin = ""; step = PinStep.New }, modifier = Modifier.fillMaxWidth()) { Text("تفعيل القفل وإنشاء رمز") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { step = PinStep.VerifyToChange }, modifier = Modifier.weight(1f)) { Text("تغيير الرمز") }
                    OutlinedButton(onClick = { step = PinStep.VerifyToDisable }, modifier = Modifier.weight(1f)) { Text("إيقاف القفل") }
                }
            }
        }
    }

    if (config.enabled) {
        SettingsGroup("الفتح") {
            SwitchRow(
                title = "الفتح بالبصمة أو الوجه",
                subtitle = if (bioAvailable) "يُطلب الرمز إن تعذّرت البصمة" else "لا توجد بصمة مسجّلة في الجوال (أضفها من إعدادات الجوال)",
                checked = config.biometric && bioAvailable,
                onCheckedChange = { on ->
                    if (!bioAvailable) return@SwitchRow
                    if (!on) {
                        scope.launch { AppLock.setBiometric(c, false) }
                        return@SwitchRow
                    }
                    // تأكيد أن البصمة تعمل قبل اعتمادها
                    val activity = context.findFragmentActivity() ?: return@SwitchRow
                    BiometricAuth.prompt(
                        activity,
                        "تأكيد البصمة لقفل يقين",
                        onSuccess = { scope.launch { AppLock.setBiometric(c, true); notify("تم تفعيل الفتح بالبصمة") } },
                        onError = { err -> if (err != null) notify(err) },
                    )
                },
            )
            RowDivider()
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("يُقفل التطبيق بعد مغادرته", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in AppLock.DELAY_OPTIONS) {
                        FilterChip(
                            selected = config.delaySeconds == s,
                            onClick = { scope.launch { AppLock.setDelay(c, s) } },
                            label = { Text(delayLabel(s)) },
                        )
                    }
                }
            }
        }
    }
    Hint("الرمز محفوظ على هذا الجهاز فقط بصيغة مشفّرة ولا يُزامَن ولا يمكن استرجاعه. إن نسيته فامسح بيانات التطبيق من إعدادات الجوال.")

    when (val s = step) {
        null -> Unit
        PinStep.VerifyToChange, PinStep.VerifyToDisable -> PinDialog(
            title = "أدخل الرمز الحالي",
            onDismiss = { step = null },
            onSubmit = { pin, setError ->
                scope.launch {
                    when (val r = AppLock.checkPin(c, pin)) {
                        PinResult.Ok -> if (s == PinStep.VerifyToDisable) {
                            AppLock.disable(c)
                            step = null
                            notify("تم إيقاف القفل")
                        } else {
                            firstPin = ""
                            step = PinStep.New
                        }
                        is PinResult.Wrong -> setError("الرمز غير صحيح")
                        is PinResult.LockedOut -> setError("محاولات كثيرة، حاول بعد قليل")
                    }
                }
            },
        )
        PinStep.New -> PinDialog(
            title = "رمز جديد",
            hint = "من ${ArabicText.indic(PinHasher.MIN_LENGTH)} إلى ${ArabicText.indic(PinHasher.MAX_LENGTH)} أرقام",
            onDismiss = { step = null },
            onSubmit = { pin, setError ->
                if (!PinHasher.isValidPin(pin)) setError("أدخل من ${ArabicText.indic(PinHasher.MIN_LENGTH)} إلى ${ArabicText.indic(PinHasher.MAX_LENGTH)} أرقام")
                else { firstPin = pin; step = PinStep.Confirm }
            },
        )
        PinStep.Confirm -> PinDialog(
            title = "أعد إدخال الرمز",
            onDismiss = { step = null },
            onSubmit = { pin, setError ->
                if (pin != firstPin) setError("الرمزان غير متطابقين")
                else scope.launch {
                    val wasEnabled = config.enabled
                    AppLock.setPin(c, pin)
                    firstPin = ""
                    step = null
                    notify(if (wasEnabled) "تم تغيير الرمز" else "تم تفعيل القفل")
                }
            },
        )
    }
}

private fun delayLabel(seconds: Int) = when (seconds) {
    0 -> "فورًا"
    60 -> "بعد دقيقة"
    else -> "بعد ${ArabicText.indic(seconds / 60)} دقائق"
}

/** نافذة إدخال رمز (أرقام مخفية). [onSubmit] يتلقى الرمز ودالة لعرض خطأ. */
@Composable
private fun PinDialog(
    title: String,
    hint: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, (String) -> Unit) -> Unit,
) {
    var text by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    // يقبل الأرقام الهندية أيضًا ويحفظها لاتينية
                    text = v.mapNotNull { ch ->
                        when (ch) {
                            in '0'..'9' -> ch
                            in '٠'..'٩' -> '0' + (ch - '٠')
                            in '۰'..'۹' -> '0' + (ch - '۰')
                            else -> null
                        }
                    }.joinToString("").take(PinHasher.MAX_LENGTH)
                    error = null
                },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                supportingText = { (error ?: hint)?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotEmpty(), onClick = { onSubmit(text) { error = it } }) { Text("متابعة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
