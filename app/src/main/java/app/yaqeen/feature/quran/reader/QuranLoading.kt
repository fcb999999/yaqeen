package app.yaqeen.feature.quran.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.yaqeen.AppContainer
import app.yaqeen.data.quran.QuranData
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import kotlinx.coroutines.CancellationException

/** حالة تحميل بيانات المصحف من الأصول. */
sealed interface QuranLoad {
    data object Loading : QuranLoad
    data class Ready(val quran: QuranData) : QuranLoad
    data class Failed(val error: Throwable) : QuranLoad
}

/** يحمّل المصحف مرة (مخزَّن في الذاكرة بعد أول تحميل) خارج الخيط الرئيسي. غيّر retryKey لإعادة المحاولة. */
@Composable
fun rememberQuranLoad(c: AppContainer, retryKey: Int = 0): State<QuranLoad> =
    produceState<QuranLoad>(QuranLoad.Loading, c, retryKey) {
        value = QuranLoad.Loading
        value = try {
            QuranLoad.Ready(c.quran())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            QuranLoad.Failed(e)
        }
    }

/** حالة الخطأ/التحميل الموحّدة لشاشات القرآن. */
@Composable
fun QuranLoadGate(load: QuranLoad, onRetry: () -> Unit, content: @Composable (QuranData) -> Unit) {
    when (load) {
        QuranLoad.Loading -> LoadingBox()
        is QuranLoad.Failed -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = "تعذّر تحميل المصحف",
                message = "حدثت مشكلة أثناء قراءة بيانات المصحف من التطبيق. جرّب مرة أخرى.",
                icon = Icons.Filled.ErrorOutline,
                action = { Button(onClick = onRetry) { Text("إعادة المحاولة") } },
            )
        }
        is QuranLoad.Ready -> content(load.quran)
    }
}
