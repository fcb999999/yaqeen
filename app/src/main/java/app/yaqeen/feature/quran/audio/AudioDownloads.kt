package app.yaqeen.feature.quran.audio

import app.yaqeen.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** تقدّم تنزيل سورة لقارئ. [error] عند الفشل (ما نُزّل يبقى ويُستكمل لاحقًا). */
data class SurahDownload(val done: Int, val total: Int, val error: String? = null) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** نتيجة مسح ملفات قارئ: عدد الآيات المحمّلة وحجمها لكل سورة (فهرس 1..114). */
class ReciterScan(val counts: IntArray, val bytes: LongArray) {
    val totalBytes: Long get() = bytes.sum()
    val totalFiles: Int get() = counts.sum()
}

/**
 * مدير تنزيل التلاوات للاستماع دون إنترنت: سورة كاملة لقارئ، ملف لكل آية في filesDir/audio/<قارئ>/<آية>.mp3.
 * التنزيل يُكمل ما نقص (يتخطى الموجود، ويستأنف الملف الجزئي بطلب Range)، بثلاثة اتصالات متوازية،
 * ويعمل في نطاق التطبيق فلا يتوقف بإغلاق الشاشة (لكنه يتوقف إن قُتلت العملية، ثم يُستأنف يدويًا).
 */
class AudioDownloadManager private constructor(private val c: AppContainer) {
    companion object {
        @Volatile private var instance: AudioDownloadManager? = null
        fun of(c: AppContainer): AudioDownloadManager = instance ?: synchronized(this) { instance ?: AudioDownloadManager(c).also { instance = it } }
        private const val PARALLEL = 3
        private fun key(reciterId: String, surah: Int) = "$reciterId:$surah"
    }

    private val root: File get() = c.context.filesDir
    private val _active = MutableStateFlow<Map<String, SurahDownload>>(emptyMap())
    /** التنزيلات الجارية أو الفاشلة حديثًا، بمفتاح "قارئ:سورة". */
    val active: StateFlow<Map<String, SurahDownload>> = _active

    private val _version = MutableStateFlow(0)
    /** يزداد عند اكتمال/إلغاء/حذف ما يستدعي إعادة مسح الملفات. */
    val version: StateFlow<Int> = _version

    private val jobs = HashMap<String, Job>()
    private val lock = Any()

    fun progress(reciterId: String, surah: Int): SurahDownload? = _active.value[key(reciterId, surah)]

    fun start(reciterId: String, surah: Int) {
        val r = Reciters.all.firstOrNull { it.id == reciterId } ?: return
        val k = key(reciterId, surah)
        synchronized(lock) {
            if (jobs[k]?.isActive == true) return
            _active.update { it + (k to SurahDownload(0, 1)) }
            jobs[k] = c.appScope.launch(Dispatchers.IO) { run(r, surah, k) }
        }
    }

    fun cancel(reciterId: String, surah: Int) {
        synchronized(lock) { jobs[key(reciterId, surah)]?.cancel() }
    }

    fun clearError(reciterId: String, surah: Int) {
        _active.update { m -> val k = key(reciterId, surah); if (m[k]?.error != null) m - k else m }
    }

    private suspend fun run(r: QuranReciter, surah: Int, k: String) {
        try {
            val q = c.quran()
            val s = q.surah(surah)
            val ids = (s.firstAyahId until s.firstAyahId + s.ayahCount).toList()
            AudioFiles.reciterDir(root, r.id).mkdirs()
            val missing = ids.filter { !isComplete(AudioFiles.file(root, r.id, it)) }
            val done = AtomicInteger(ids.size - missing.size)
            _active.update { it + (k to SurahDownload(done.get(), ids.size)) }
            val sem = Semaphore(PARALLEL)
            coroutineScope {
                missing.map { id ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            downloadFile(r, id)
                            val d = done.incrementAndGet()
                            _active.update { it + (k to SurahDownload(d, ids.size)) }
                        }
                    }
                }.awaitAll()
            }
            _active.update { it - k }
        } catch (ce: CancellationException) {
            _active.update { it - k }
            throw ce
        } catch (t: Throwable) {
            val prev = _active.value[k]
            _active.update { it + (k to SurahDownload(prev?.done ?: 0, prev?.total ?: 1, "تعذّر إكمال التنزيل. تحقّق من اتصال الإنترنت والمساحة ثم أعد المحاولة؛ سيُكمل ما بقي.")) }
        } finally {
            synchronized(lock) { jobs.remove(k) }
            _version.update { it + 1 }
        }
    }

    private fun isComplete(f: File) = f.isFile && f.length() > 0

    private suspend fun downloadFile(r: QuranReciter, ayahId: Int) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                fetch(r, ayahId)
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                delay(400L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("فشل التنزيل")
    }

    /** ينزّل ملفًا واحدًا إلى .part ثم يعيد تسميته؛ يستأنف الجزئي إن دعم الخادم Range. */
    private suspend fun fetch(r: QuranReciter, ayahId: Int) {
        val job = currentCoroutineContext()[Job]
        val dest = AudioFiles.file(root, r.id, ayahId)
        val part = AudioFiles.partFile(root, r.id, ayahId)
        var existing = if (part.isFile) part.length() else 0L
        var attempt = 0
        while (true) {
            val conn = (URL(Reciters.url(r, ayahId)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            try {
                val code = conn.responseCode
                if (code == 416 && attempt == 0) { part.delete(); existing = 0; attempt++; continue }
                if (code != 200 && code != 206) throw IOException("HTTP $code")
                val append = code == 206 && existing > 0
                val expected = conn.contentLengthLong.let { if (it > 0) (if (append) it + existing else it) else -1L }
                RandomAccessFile(part, "rw").use { out ->
                    if (append) out.seek(existing) else out.setLength(0)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            job?.ensureActive()
                        }
                    }
                }
                if (expected > 0 && part.length() != expected) throw IOException("ملف ناقص")
                if (part.length() == 0L) throw IOException("ملف فارغ")
                dest.delete()
                if (!part.renameTo(dest)) throw IOException("تعذّر حفظ الملف")
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    // ------------------------------------------------------------ المسح والحذف

    suspend fun scan(reciterId: String): ReciterScan = withContext(Dispatchers.IO) {
        val q = c.quran()
        val dir = AudioFiles.reciterDir(root, reciterId)
        val counts = IntArray(115)
        val bytes = LongArray(115)
        dir.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".mp3") || f.length() <= 0) return@forEach
            val id = f.name.removeSuffix(".mp3").toIntOrNull() ?: return@forEach
            if (id !in 1..q.totalAyahs) return@forEach
            val s = q.ayah(id).surah
            counts[s]++
            bytes[s] += f.length()
        }
        ReciterScan(counts, bytes)
    }

    /** حجم ملفات كل قارئ لديه تنزيلات (يشمل الجزئي .part). */
    suspend fun bytesByReciter(): Map<String, Long> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Long>()
        for (r in Reciters.all) {
            val dir = AudioFiles.reciterDir(root, r.id)
            val size = dir.listFiles()?.sumOf { it.length() } ?: 0L
            if (size > 0) out[r.id] = size
        }
        out
    }

    fun deleteSurah(reciterId: String, surah: Int) {
        cancel(reciterId, surah)
        c.appScope.launch(Dispatchers.IO) {
            val q = c.quran()
            val s = q.surah(surah)
            for (id in s.firstAyahId until s.firstAyahId + s.ayahCount) {
                AudioFiles.file(root, reciterId, id).delete()
                AudioFiles.partFile(root, reciterId, id).delete()
            }
            _version.update { it + 1 }
        }
    }

    fun deleteReciter(reciterId: String) {
        synchronized(lock) { jobs.filterKeys { it.startsWith("$reciterId:") }.values.forEach { it.cancel() } }
        c.appScope.launch(Dispatchers.IO) {
            AudioFiles.reciterDir(root, reciterId).deleteRecursively()
            _version.update { it + 1 }
        }
    }
}
