package app.yaqeen.feature.quran.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.feature.quran.reader.QuranLoadGate
import app.yaqeen.feature.quran.reader.ReaderSettingsContent
import app.yaqeen.feature.quran.reader.observeReaderPrefs
import app.yaqeen.feature.quran.reader.rememberQuranLoad
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.YaqeenTopBar

/** إعدادات القراءة كشاشة كاملة (نفس عناصر التحكم في نافذة القارئ). */
@Composable
fun QuranSettingsScreen(nav: NavHostController, c: AppContainer) {
    var retry by remember { mutableIntStateOf(0) }
    val load by rememberQuranLoad(c, retry)
    val prefs by remember { c.settings.observeReaderPrefs() }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("إعدادات القراءة", onBack = { nav.popBackStack() })
        QuranLoadGate(load, onRetry = { retry++ }) { quran ->
            val p = prefs
            if (p == null) {
                LoadingBox()
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                ) {
                    ReaderSettingsContent(c, p, quran.bismillah)
                }
            }
        }
    }
}
