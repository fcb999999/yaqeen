package app.yaqeen.feature.quran.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * === عقد تشغيل التلاوة (يملكه منفذ "الأوراد والعلامات والصوت والتفسير") ===
 * القارئ يستدعي هذه الواجهة ويعرض [QuranMiniPlayer]. هذا الهيكل أولي؛ يستبدل المنفذ التنفيذ مع الحفاظ على التواقيع.
 */
data class QuranAudioState(
    /** الآية الجارية (المعرّف العام 1..6236) أو null إن لم يكن هناك تشغيل. */
    val currentAyahId: Int? = null,
    val isPlaying: Boolean = false,
    val reciterName: String = "",
)

class QuranAudioController(private val c: AppContainer) {
    private val _state = MutableStateFlow(QuranAudioState())
    val state: StateFlow<QuranAudioState> = _state

    /** تشغيل آية واحدة. */
    fun playAyah(ayahId: Int) {}

    /** تشغيل سورة ابتداءً من آية معيّنة حتى نهايتها. */
    fun playSurah(surah: Int, fromAyah: Int = 1) {}

    /** تشغيل مجموعة آيات متتالية [fromAyahId, toAyahId]. */
    fun playRange(fromAyahId: Int, toAyahId: Int) {}

    fun togglePause() {}
    fun next() {}
    fun previous() {}
    fun stop() {}
}

/** شريط تحكم مصغّر يظهر أثناء التشغيل (لا يعرض شيئًا إن لم يكن هناك تشغيل). */
@Composable
fun QuranMiniPlayer(controller: QuranAudioController, modifier: Modifier = Modifier) {}

/** إدارة التلاوات والتفاسير المحمّلة (المساحة، الحذف، التنزيل). */
@Composable
fun QuranDownloadsScreen(nav: NavHostController, c: AppContainer) {
    PlaceholderScreen("التنزيلات")
}
