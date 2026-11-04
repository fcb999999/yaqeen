package app.yaqeen.feature.prayer.times

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.yaqeen.ui.components.YaqeenTopBar
import kotlinx.coroutines.delay

/** هيكل موحّد لشاشات الصلاة: شريط علوي بعنوان وزر رجوع + منطقة محتوى + شريط رسائل. */
@Composable
fun PrayerScreenFrame(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        // الحشوات (شريط الحالة والتنقل) يطبّقها الـScaffold الخارجي في AppRoot.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = { YaqeenTopBar(title, onBack = onBack, actions = actions) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        content = content,
    )
}

/**
 * ساعة حية: تُحدَّث كل [intervalMs] (مُحاذاة لحدود الثانية) وتتوقف تلقائيًا عند إيقاف الشاشة (لا استهلاك بطارية في الخلفية).
 */
@Composable
fun rememberNowMillis(intervalMs: Long = 1000L): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, intervalMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis()
                state.longValue = t
                delay(intervalMs - (t % intervalMs))
            }
        }
    }
    return state
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** يفتح صفحة معلومات التطبيق في إعدادات النظام (لإعادة منح الأذونات المرفوضة نهائيًا). */
fun Context.openAppSettings() {
    val i = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(i) }
}

fun Context.openLocationSettings() {
    runCatching { startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
