package app.yaqeen.feature.quran.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.data.local.ReadingProgressEntity
import app.yaqeen.data.quran.QuranData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Failed(val error: Throwable) : ReaderState

    /**
     * @param startPage الصفحة التي يُفتح عليها القارئ.
     * @param focusAyahId آية تُبرَز وتُمرَّر إليها (عند الفتح على آية محددة فقط).
     * @param resumeNote نص يُعرض (snackbar) عند الاستئناف من جهاز آخر.
     */
    data class Ready(
        val quran: QuranData,
        val startPage: Int,
        val focusAyahId: Int?,
        val resumeNote: String?,
    ) : ReaderState
}

class QuranReaderViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state

    /** null حتى تُقرأ الإعدادات من قاعدة البيانات (لا نرسم القارئ بإعدادات افتراضية خاطئة أولًا). */
    val prefs: StateFlow<ReaderPrefs?> = c.settings.observeReaderPrefs()
        .map<ReaderPrefs, ReaderPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var target: Triple<Int, Int, Int>? = null

    /** يحدّد صفحة البداية: هدف صريح من التنقل، وإلا آخر موضع قراءة بين كل الأجهزة، وإلا الصفحة الأولى. */
    fun start(surah: Int, ayah: Int, page: Int) {
        if (target != null) return
        target = Triple(surah, ayah, page)
        resolve()
    }

    fun retry() = resolve()

    private fun resolve() {
        val (surah, ayah, page) = target ?: return
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            try {
                val quran = c.quran()
                _state.value = when {
                    surah in 1..quran.surahs.size -> {
                        val s = quran.surah(surah)
                        val n = if (ayah >= 1) ayah.coerceAtMost(s.ayahCount) else 1
                        val a = quran.ayah(surah, n)
                        ReaderState.Ready(quran, a.page, focusAyahId = if (ayah >= 1) a.id else null, resumeNote = null)
                    }
                    page >= 1 -> ReaderState.Ready(quran, page.coerceIn(1, quran.pageCount), null, null)
                    else -> resume(quran)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ReaderState.Failed(e)
            }
        }
    }

    private suspend fun resume(quran: QuranData): ReaderState.Ready {
        val own = c.device.deviceId
        val points = c.db.readingProgress().observeAll().first().map {
            ProgressPoint(it.id, it.deviceName, it.surah, it.ayah, it.page, it.juz, it.lastReadAt)
        }
        val choice = Resume.select(points, own, quran.pageCount, quran.surahs.size)
            ?: return ReaderState.Ready(quran, 1, null, null)
        val p = choice.point
        // الموضع المحفوظ قد يكون آية في وسط الصفحة: نفتح على صفحتها
        val page = quran.ayah(p.surah, p.ayah.coerceIn(1, quran.surah(p.surah).ayahCount)).page
        return ReaderState.Ready(
            quran,
            startPage = page,
            focusAyahId = null,
            resumeNote = if (choice.fromOtherDevice) Resume.snackbarText(p.deviceName) else null,
        )
    }

    /**
     * يحفظ موضع القراءة الحالي لهذا الجهاز (سجل واحد id = deviceId فلا تعارض بين الأجهزة).
     * يُستدعى فقط بعد بقاء المستخدم في الصفحة مدة كافية، وليس عند مجرد الفتح.
     * الكتابة على نطاق التطبيق حتى لا تُلغى إن غادر المستخدم الشاشة أثناءها.
     */
    fun saveProgress(quran: QuranData, ayahId: Int) {
        val a = quran.ayah(ayahId.coerceIn(1, quran.totalAyahs))
        c.appScope.launch {
            c.writer.save(
                c.db.readingProgress(),
                ReadingProgressEntity(
                    id = c.device.deviceId,
                    surah = a.surah,
                    ayah = a.number,
                    page = a.page,
                    juz = a.juz,
                    lastReadAt = c.clock.nowMillis(),
                    deviceName = c.device.deviceName,
                ),
            )
        }
    }

    /** احتساب قراءة الصفحة في الأوراد والإحصاء (>= 8 ثوانٍ). */
    fun pageRead(page: Int) {
        c.appScope.launch {
            try {
                c.readingTracker.onPageRead(page)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // فشل التتبع لا يجب أن يؤثر على القراءة
            }
        }
    }
}
