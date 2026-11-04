package app.yaqeen.feature.quran.tafsir

import app.yaqeen.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** كتاب تفسير متاح في التطبيق. */
data class TafsirEdition(
    val id: String,
    val name: String,
    val author: String,
    /** الحجم التقريبي للتنزيل بالميغابايت (يُعرض للمستخدم قبل التنزيل). */
    val approxMb: Int,
    val bundled: Boolean = false,
)

/**
 * التفاسير العربية المعروضة. المصدر: قائمة https://api.alquran.cloud/v1/edition?format=text&language=ar&type=tafsir
 * (راجعتُ محتوى كل كتاب بعد تنزيله). استُبعد "تنوير المقباس" لأن نص الخدمة تحت هذا الاسم لا يطابق الكتاب
 * (محتواه من تفسير آخر)، فلا يصحّ نسبته إلى مؤلّفه.
 */
object TafsirCatalog {
    const val DEFAULT = "ar.muyassar"
    const val SOURCE_NOTE = "تفاسير التنزيل من واجهة alquran.cloud"

    val all: List<TafsirEdition> = listOf(
        TafsirEdition("ar.muyassar", "التفسير الميسر", "نخبة من العلماء بإشراف مجمع الملك فهد لطباعة المصحف الشريف", 3, bundled = true),
        TafsirEdition("ar.jalalayn", "تفسير الجلالين", "جلال الدين المحلي وجلال الدين السيوطي", 3),
        TafsirEdition("ar.baghawi", "تفسير البغوي", "أبو محمد الحسين بن مسعود البغوي", 9),
        TafsirEdition("ar.waseet", "التفسير الوسيط", "محمد سيد طنطاوي", 19),
        TafsirEdition("ar.qurtubi", "تفسير القرطبي", "أبو عبد الله محمد بن أحمد القرطبي", 21),
    )

    fun byId(id: String): TafsirEdition? = all.firstOrNull { it.id == id }
}

/** حالة تنزيل تفسير جارٍ. fraction بين 0 و1؛ error إن فشل. */
data class TafsirDownload(val fraction: Float = 0f, val error: String? = null)

/**
 * مخزن التفاسير: المضمَّن من الأصول، والمنزَّلة في filesDir/tafsir/<edition>.json (+ .idx للقراءة المباشرة).
 * القراءة تعمل بلا إنترنت؛ الشبكة للتنزيل فقط.
 */
class TafsirStore private constructor(private val c: AppContainer) {
    companion object {
        @Volatile private var instance: TafsirStore? = null
        fun of(c: AppContainer): TafsirStore = instance ?: synchronized(this) { instance ?: TafsirStore(c).also { instance = it } }
        private const val API = "https://api.alquran.cloud/v1/quran/"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val dir: File get() = File(c.context.filesDir, "tafsir").also { it.mkdirs() }
    private fun jsonFile(id: String) = File(dir, "$id.json")
    private fun idxFile(id: String) = File(dir, "$id.idx")

    private val _downloads = MutableStateFlow<Map<String, TafsirDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, TafsirDownload>> = _downloads

    /** يزداد عند أي تنزيل/حذف ليُعاد حساب الحالة في الواجهات. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val jobs = HashMap<String, Job>()
    private val jobsLock = Any()
    private val bundledLock = Mutex()
    @Volatile private var bundledTexts: List<String>? = null

    // ------------------------------------------------------------ الحالة

    fun isDownloaded(id: String): Boolean {
        val e = TafsirCatalog.byId(id) ?: return false
        if (e.bundled) return true
        return jsonFile(id).isFile && idxFile(id).isFile
    }

    /** حجم ملفات التفسير المنزَّل بالبايت (0 للمضمَّن). */
    fun sizeBytes(id: String): Long = if (TafsirCatalog.byId(id)?.bundled == true) 0L else jsonFile(id).length() + idxFile(id).length()

    fun downloadedEditions(): List<TafsirEdition> = TafsirCatalog.all.filter { !it.bundled && isDownloaded(it.id) }

    // ------------------------------------------------------------ القراءة

    /** نص تفسير الآية [ayahId] (1..6236) من الكتاب [edition]، أو null إن لم يتوفر الكتاب على الجهاز. قد يكون فارغًا. */
    suspend fun text(edition: String, ayahId: Int): String? = withContext(Dispatchers.IO) {
        if (ayahId !in 1..TafsirJson.AYAH_COUNT) return@withContext null
        val e = TafsirCatalog.byId(edition) ?: return@withContext null
        if (e.bundled) {
            bundled()?.getOrNull(ayahId - 1)
        } else {
            readDownloaded(edition, ayahId)
        }
    }

    private fun readDownloaded(id: String, ayahId: Int): String? {
        val jf = jsonFile(id)
        val xf = idxFile(id)
        if (!jf.isFile || !xf.isFile) return null
        return try {
            val (start, len) = RandomAccessFile(xf, "r").use { TafsirJson.readIndexEntry(it, ayahId) } ?: return null
            val bytes = ByteArray(len)
            RandomAccessFile(jf, "r").use { it.seek(start.toLong()); it.readFully(bytes) }
            json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonPrimitive.content
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun bundled(): List<String>? {
        bundledTexts?.let { return it }
        return bundledLock.withLock {
            bundledTexts ?: runCatching {
                val raw = c.context.assets.open("tafsir/ar.muyassar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                json.parseToJsonElement(raw).jsonObject.getValue("texts").jsonArray.map { it.jsonPrimitive.content }
            }.getOrNull()?.also { bundledTexts = it }
        }
    }

    // ------------------------------------------------------------ التنزيل

    fun download(id: String) {
        val e = TafsirCatalog.byId(id) ?: return
        if (e.bundled) return
        synchronized(jobsLock) {
            if (jobs[id]?.isActive == true) return
            _downloads.update { it + (id to TafsirDownload(0f)) }
            jobs[id] = c.appScope.launch(Dispatchers.IO) { runDownload(e) }
        }
    }

    fun cancel(id: String) {
        synchronized(jobsLock) { jobs[id]?.cancel() }
    }

    private suspend fun runDownload(e: TafsirEdition) {
        val id = e.id
        val tmpJson = File(dir, "$id.json.tmp")
        val tmpIdx = File(dir, "$id.idx.tmp")
        var conn: HttpURLConnection? = null
        val job = currentCoroutineContext()[Job]
        try {
            conn = (URL(API + id).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (e.approxMb * 1024L * 1024L)
            val counting = CountingInputStream(conn.inputStream)
            val reader = InputStreamReader(counting, Charsets.UTF_8).buffered(64 * 1024)
            val writer = TafsirJson.FileWriter(tmpJson.outputStream(), id, e.name, e.author)
            var lastPublished = 0f
            reader.use {
                TafsirJson.extractAyahTexts(it) { text ->
                    writer.add(text)
                    job?.ensureActive()
                    if (writer.count % 50 == 0) {
                        val f = (counting.bytes.toFloat() / total).coerceIn(0f, 0.99f)
                        if (f - lastPublished >= 0.01f) {
                            lastPublished = f
                            _downloads.update { m -> m + (id to TafsirDownload(f)) }
                        }
                    }
                }
            }
            if (writer.count != TafsirJson.AYAH_COUNT) {
                writer.finish()
                throw java.io.IOException("عدد النصوص ${writer.count} بدل ${TafsirJson.AYAH_COUNT}")
            }
            writer.finish()
            writer.writeIndex(tmpIdx)
            // الفهرس أولًا ثم الملف، فلا يُعدّ الكتاب متاحًا قبل اكتمال الاثنين.
            idxFile(id).delete(); jsonFile(id).delete()
            if (!tmpIdx.renameTo(idxFile(id)) || !tmpJson.renameTo(jsonFile(id))) throw java.io.IOException("تعذّر حفظ الملف")
            _downloads.update { it - id }
            _version.update { it + 1 }
        } catch (ce: CancellationException) {
            _downloads.update { it - id }
            throw ce
        } catch (t: Throwable) {
            _downloads.update { it + (id to TafsirDownload(0f, "تعذّر التنزيل. تحقّق من اتصال الإنترنت ثم أعد المحاولة.")) }
        } finally {
            conn?.disconnect()
            tmpJson.delete(); tmpIdx.delete()
            synchronized(jobsLock) { jobs.remove(id) }
        }
    }

    /** يزيل رسالة الخطأ بعد أن يراها المستخدم. */
    fun clearError(id: String) {
        _downloads.update { m -> if (m[id]?.error != null) m - id else m }
    }

    fun delete(id: String) {
        if (TafsirCatalog.byId(id)?.bundled == true) return
        cancel(id)
        jsonFile(id).delete(); idxFile(id).delete()
        _version.update { it + 1 }
    }

    private class CountingInputStream(`in`: InputStream) : FilterInputStream(`in`) {
        @Volatile var bytes = 0L
        override fun read(): Int = super.read().also { if (it >= 0) bytes++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bytes += it }
    }
}

