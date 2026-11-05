package app.yaqeen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.yaqeen.feature.settings.lock.AppLock
import app.yaqeen.ui.AppRoot
import kotlinx.coroutines.launch

/** FragmentActivity (لا ComponentActivity) لأن نافذة البصمة BiometricPrompt تتطلبها. */
class MainActivity : FragmentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLock.init(appContainer)
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        // مع القفل: لا تظهر صورة المحتوى في قائمة التطبيقات الأخيرة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                AppLock.config.collect { cfg -> setRecentsScreenshotEnabled(cfg?.enabled != true) }
            }
        }
        setContent {
            AppRoot(
                container = appContainer,
                pendingRoute = pendingRoute,
                onRouteConsumed = { pendingRoute = null },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        // تغيير الاتجاه ليس مغادرة للتطبيق
        if (!isChangingConfigurations) AppLock.onActivityStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    companion object {
        /** مسار تنقل داخلي يُفتح عند النقر على إشعار. */
        const val EXTRA_ROUTE = "route"
    }
}
