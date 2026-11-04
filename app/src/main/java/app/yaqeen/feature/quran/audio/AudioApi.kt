package app.yaqeen.feature.quran.audio

import android.content.ComponentName
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.quran.plans.SurahNames
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * === عقد تشغيل التلاوة ===
 * عميل رقيق لخدمة [QuranPlaybackService] (ExoPlayer داخل MediaSessionService) عبر MediaController.
 * كل الدوال آمنة الاستدعاء من أي خيط. يُفضَّل الملف المحلي المحمَّل (filesDir/audio/<قارئ>/<آية>.mp3) وإلا يُبثّ من الشبكة.
 * القارئ من Settings.QURAN_RECITER، والتكرار من [AudioSettings]. تُحدَّث [state] من أحداث المشغّل.
 */
class QuranAudioController(private val c: AppContainer) {
    private val _state = MutableStateFlow(QuranAudioState())
    val state: StateFlow<QuranAudioState> = _state

    internal val container: AppContainer get() = c

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mc: MediaController? = null
    private var pending: ListenableFuture<MediaController>? = null
    private val waiting = ArrayList<(MediaController) -> Unit>()

    private var repeat = AudioRepeat.NONE
    private var repeatTimes = 3
    private val counter = AyahRepeatCounter(3)
    private var reciter: QuranReciter = Reciters.byId(null)
    private var reciterKnown = false
    private var currentIds: List<Int> = emptyList()
    private var startJob: Job? = null

    init {
        // متابعة إعدادات القارئ والتكرار (تتغير من خيارات التشغيل أو من جهاز آخر).
        main.launch {
            c.settings.observe(Settings.QURAN_RECITER).collect { id ->
                val r = Reciters.byId(id)
                val changed = reciterKnown && r.id != reciter.id
                reciter = r
                reciterKnown = true
                if (changed && _state.value.isActive && currentIds.isNotEmpty()) restartCurrent()
            }
        }
        main.launch {
            c.settings.observe(AudioSettings.REPEAT_KIND).collect { k ->
                repeat = runCatching { AudioRepeat.valueOf(k) }.getOrDefault(AudioRepeat.NONE)
                applyRepeatIfConnected()
            }
        }
        main.launch {
            c.settings.observe(AudioSettings.REPEAT_TIMES).collect { t ->
                repeatTimes = t.coerceIn(2, AudioSettings.MAX_REPEAT)
                applyRepeatIfConnected()
            }
        }
    }

    // ------------------------------------------------------------ الواجهة العامة

    /** تشغيل آية واحدة. */
    fun playAyah(ayahId: Int) {
        if (ayahId !in 1..6236) return
        begin(listOf(ayahId), 0)
    }

    /** تشغيل سورة ابتداءً من آية معيّنة حتى نهايتها (والتكرار "للسورة" يعود لأولها). */
    fun playSurah(surah: Int, fromAyah: Int = 1) {
        if (surah !in 1..114) return
        main.launch {
            val q = runCatching { c.quran() }.getOrNull() ?: return@launch
            val (ids, start) = AudioPlaylists.surah(q, surah, fromAyah)
            begin(ids, start)
        }
    }

    /** تشغيل مجموعة آيات متتالية [fromAyahId, toAyahId]. */
    fun playRange(fromAyahId: Int, toAyahId: Int) {
        val ids = AudioPlaylists.range(6236, fromAyahId, toAyahId)
        begin(ids, 0)
    }

    fun togglePause() {
        if (!_state.value.isActive) return
        main.launch {
            withController { p ->
                if (p.playbackState == Player.STATE_IDLE && p.mediaItemCount > 0) {
                    _state.update { it.copy(error = null) }
                    p.prepare(); p.play()
                } else if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) {
                    p.pause()
                } else {
                    p.play()
                }
            }
        }
    }

    fun next() { main.launch { withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } } }
    fun previous() { main.launch { withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0) } } }

    fun stop() {
        main.launch { release() }
    }

    /** يغيّر القارئ (يُحفظ في الإعدادات ويُعاد تشغيل الآية الجارية بصوته إن كان هناك تشغيل). */
    fun setReciter(id: String) {
        val r = Reciters.byId(id)
        main.launch { c.settings.set(Settings.QURAN_RECITER, r.id) }
    }

    fun setRepeat(kind: AudioRepeat, times: Int = repeatTimes) {
        main.launch {
            c.settings.set(AudioSettings.REPEAT_KIND, kind.name)
            c.settings.set(AudioSettings.REPEAT_TIMES, times.coerceIn(2, AudioSettings.MAX_REPEAT))
        }
    }

    // ------------------------------------------------------------ التنفيذ

    private fun begin(ids: List<Int>, startIndex: Int) {
        if (ids.isEmpty()) return
        startJob?.cancel()
        startJob = main.launch {
            val q = runCatching { c.quran() }.getOrNull() ?: return@launch
            // القارئ يُقرأ مباشرة من الإعدادات لضمان القيمة الأحدث.
            val r = Reciters.byId(c.settings.get(Settings.QURAN_RECITER))
            reciter = r
            reciterKnown = true
            val items = withContext(Dispatchers.IO) { ids.map { buildItem(q, it, r) } }
            currentIds = ids
            val idx = startIndex.coerceIn(0, ids.lastIndex)
            _state.update {
                it.copy(
                    currentAyahId = ids[idx], reciterId = r.id, reciterName = r.nameAr, isBuffering = true,
                    isPlaying = true, error = null, repeat = repeat, repeatTimes = repeatTimes, currentRepeat = 1,
                )
            }
            withController { p ->
                p.setMediaItems(items, idx, 0L)
                applyRepeat(p)
                p.prepare()
                p.play()
            }
        }
    }

    /** يعيد تشغيل القائمة الحالية من الآية الجارية بصوت القارئ الجديد. */
    private fun restartCurrent() {
        val ayah = _state.value.currentAyahId ?: return
        val ids = currentIds
        val idx = ids.indexOf(ayah).coerceAtLeast(0)
        begin(ids, idx)
    }

    private fun buildItem(q: QuranData, ayahId: Int, r: QuranReciter): MediaItem {
        val a = q.ayah(ayahId)
        val local = AudioFiles.file(c.context.filesDir, r.id, ayahId)
        val uri = if (local.isFile && local.length() > 0) Uri.fromFile(local) else Uri.parse(Reciters.url(r, ayahId))
        return MediaItem.Builder()
            .setMediaId(ayahId.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${SurahNames.plain(q.surah(a.surah).nameAr)} • الآية ${ArabicText.indic(a.number)}")
                    .setArtist(r.nameAr)
                    .setAlbumTitle("القرآن الكريم")
                    .build(),
            )
            .build()
    }

    private fun applyRepeatIfConnected() {
        mc?.let { applyRepeat(it) } ?: _state.update { it.copy(repeat = repeat, repeatTimes = repeatTimes) }
    }

    private fun applyRepeat(p: MediaController) {
        counter.times = repeatTimes
        p.repeatMode = when (repeat) {
            AudioRepeat.NONE -> Player.REPEAT_MODE_OFF
            AudioRepeat.SURAH -> Player.REPEAT_MODE_ALL
            AudioRepeat.AYAH -> if (counter.onNewItem()) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
        publish(p)
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = mc ?: return
            if (repeat != AudioRepeat.AYAH) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (!counter.onLooped()) p.repeatMode = Player.REPEAT_MODE_OFF
            } else {
                p.repeatMode = if (counter.onNewItem()) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) main.launch { release() }
        }

        override fun onPlayerError(error: PlaybackException) {
            val network = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
                else -> false
            }
            _state.update {
                it.copy(
                    isPlaying = false, isBuffering = false,
                    error = if (network) "تعذّر تحميل التلاوة. تحقّق من اتصال الإنترنت أو نزّل السورة للاستماع دون اتصال."
                    else "تعذّر تشغيل التلاوة.",
                )
            }
        }
    }

    private fun publish(p: Player) {
        if (p.mediaItemCount == 0) return
        val ayah = p.currentMediaItem?.mediaId?.toIntOrNull()
        val st = p.playbackState
        _state.update {
            it.copy(
                currentAyahId = ayah ?: it.currentAyahId,
                isPlaying = p.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING) && it.error == null,
                isBuffering = p.playWhenReady && st == Player.STATE_BUFFERING,
                hasNext = p.hasNextMediaItem() || repeat == AudioRepeat.SURAH,
                hasPrevious = p.hasPreviousMediaItem(),
                repeat = repeat, repeatTimes = repeatTimes,
                currentRepeat = if (repeat == AudioRepeat.AYAH) counter.played else 1,
                error = if (st == Player.STATE_READY) null else it.error,
            )
        }
    }

    /** ينفّذ [block] حين يكون MediaController متصلًا (يتصل أولًا إن لزم). */
    private fun withController(block: (MediaController) -> Unit) {
        mc?.let { block(it); return }
        waiting += block
        if (pending != null) return
        val token = SessionToken(c.context, ComponentName(c.context, QuranPlaybackService::class.java))
        val future = MediaController.Builder(c.context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    main.launch { if (mc === controller) { mc = null; pending = null; resetState() } }
                }
            })
            .buildAsync()
        pending = future
        future.addListener({
            main.launch {
                try {
                    val controller = future.get()
                    mc = controller
                    controller.addListener(listener)
                    val queued = waiting.toList()
                    waiting.clear()
                    queued.forEach { it(controller) }
                } catch (t: Throwable) {
                    pending = null
                    waiting.clear()
                    _state.update { it.copy(isPlaying = false, isBuffering = false, error = "تعذّر تشغيل التلاوة.") }
                }
            }
        }, ContextCompat.getMainExecutor(c.context))
    }

    private fun resetState() {
        currentIds = emptyList()
        _state.value = QuranAudioState(repeat = repeat, repeatTimes = repeatTimes, reciterId = reciter.id, reciterName = reciter.nameAr)
    }

    private fun release() {
        startJob?.cancel()
        waiting.clear()
        val controller = mc
        val fut = pending
        mc = null
        pending = null
        if (controller != null) {
            controller.removeListener(listener)
            controller.stop()
            controller.clearMediaItems()
            controller.release()
        } else if (fut != null) {
            MediaController.releaseFuture(fut)
        }
        // بعد فك الاتصال نُنهي الخدمة (إن كانت قد بدأت) فيزول إشعار الوسائط ولا تبقى عملية خدمة عبثًا.
        runCatching { c.context.stopService(android.content.Intent(c.context, QuranPlaybackService::class.java)) }
        resetState()
    }

    /** يمنح الواجهة بيانات المصحف لتسمية الآية الجارية. */
    internal suspend fun quran(): QuranData = c.quran()

    /** حجم ملفات الصوت المحلية لتنبيه المستخدم (للاستعمال الداخلي). */
    internal fun audioRoot(): File = c.context.filesDir
}

/** شريط تحكم مصغّر يظهر أثناء التشغيل (لا يعرض شيئًا إن لم يكن هناك تشغيل). */
@Composable
fun QuranMiniPlayer(controller: QuranAudioController, modifier: Modifier = Modifier) {
    val st by controller.state.collectAsState()
    val quran by produceState<QuranData?>(null, controller) { value = runCatching { controller.quran() }.getOrNull() }
    var showOptions by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = st.isActive && quran != null) {
        val q = quran
        val id = st.currentAyahId
        if (q != null && id != null) {
            val a = q.ayah(id)
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                "${SurahNames.plain(q.surah(a.surah).nameAr)} • الآية ${ArabicText.indic(a.number)}",
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val sub = buildString {
                                append(st.reciterName)
                                if (st.repeat == AudioRepeat.AYAH) append(" • مرة ${ArabicText.indic(st.currentRepeat)} من ${ArabicText.indic(st.repeatTimes)}")
                                if (st.repeat == AudioRepeat.SURAH) append(" • تكرار السورة")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = controller::previous, enabled = st.hasPrevious) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "الآية السابقة") }
                        if (st.isBuffering) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        } else {
                            IconButton(onClick = controller::togglePause) {
                                Icon(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (st.isPlaying) "إيقاف مؤقت" else "تشغيل")
                            }
                        }
                        IconButton(onClick = controller::next, enabled = st.hasNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "الآية التالية") }
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Filled.Tune, "خيارات التلاوة") }
                        IconButton(onClick = controller::stop) { Icon(Icons.Filled.Close, "إنهاء التلاوة") }
                    }
                    if (st.error != null) {
                        Text(st.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
    if (showOptions) AudioOptionsSheet(controller, onDismiss = { showOptions = false })
}

/** إدارة التلاوات والتفاسير المحمّلة (المساحة، الحذف، التنزيل). */
@Composable
fun QuranDownloadsScreen(nav: NavHostController, c: AppContainer) {
    DownloadsScreenContent(nav, c)
}
