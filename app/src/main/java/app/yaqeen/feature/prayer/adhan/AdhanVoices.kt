package app.yaqeen.feature.prayer.adhan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** مرجع صوت الأذان كما يُخزَّن في Settings.adhanVoice: "default" | "file:<اسم>" | "raw:<اسم>". */
sealed interface VoiceRef {
    data object Default : VoiceRef
    data class File(val name: String) : VoiceRef
    data class Raw(val name: String) : VoiceRef
}

class AdhanImportException(message: String) : Exception(message)

data class VoiceFile(val name: String, val sizeBytes: Long) {
    val value: String get() = AdhanVoices.fileValue(name)
    val title: String get() = AdhanVoices.titleOfFile(name)
}

/**
 * مكتبة أصوات الأذان المستوردة (ملفات يملك المستخدم حقوقها) داخل filesDir/adhan.
 * لا يتضمن التطبيق أي تسجيل أذان: الافتراضي نغمة إشعارات النظام (انظر docs/licenses/ADHAN_AUDIO.md).
 */
object AdhanVoices {
    const val DEFAULT = "default"
    const val MAX_BYTES = 25L * 1024 * 1024
    const val MAX_DURATION_MS = 10L * 60 * 1000
    private const val MIN_DURATION_MS = 1_000L
    private const val DIR = "adhan"

    // ---- دوال خالصة (قابلة للاختبار) ----

    fun parse(value: String?): VoiceRef = when {
        value.isNullOrBlank() || value == DEFAULT -> VoiceRef.Default
        value.startsWith("file:") && value.length > 5 -> VoiceRef.File(value.removePrefix("file:"))
        value.startsWith("raw:") && value.length > 4 -> VoiceRef.Raw(value.removePrefix("raw:"))
        else -> VoiceRef.Default
    }

    fun fileValue(name: String) = "file:$name"

    /** اسم ملف آمن: بلا فواصل مسار أو محارف محجوزة ولا يبدأ بنقطة. */
    fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim().trimStart('.')
        val cut = if (base.length > 80) base.take(80) else base
        return cut.ifBlank { "adhan" }
    }

    /** عنوان للعرض من اسم الملف (بلا الامتداد). */
    fun titleOfFile(name: String): String = name.substringBeforeLast('.', name).ifBlank { name }

    fun displayName(value: String?): String = when (val r = parse(value)) {
        VoiceRef.Default -> "الافتراضي (نغمة إشعارات الجهاز)"
        is VoiceRef.File -> titleOfFile(r.name)
        is VoiceRef.Raw -> r.name
    }

    private val BLOCKED_HOSTS = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "soundcloud.com", "spotify.com",
        "music.apple.com", "deezer.com", "anghami.com", "tiktok.com", "facebook.com", "instagram.com",
    )

    /** يتحقق من الرابط قبل التنزيل؛ يعيد رسالة الخطأ أو null إن كان مقبولًا. */
    fun validateUrl(url: String): String? {
        val u = url.trim()
        val uri = try { URI(u) } catch (e: Exception) { return "الرابط غير صالح." }
        val host = uri.host?.lowercase() ?: return "الرابط غير صالح."
        if (uri.scheme?.lowercase() != "https") return "يجب أن يبدأ الرابط بـ https:// (الروابط غير الآمنة غير مدعومة)."
        if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) {
            return "روابط مواقع الفيديو والبث (مثل يوتيوب) غير مدعومة. أدخل رابطًا مباشرًا لملف صوتي تملك حق استخدامه، أو نزّل الملف واستورده من جهازك."
        }
        return null
    }

    fun extensionForContentType(type: String?): String? = when (type?.substringBefore(';')?.trim()?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/aac", "audio/aacp" -> "m4a"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        else -> null
    }

    // ---- الملفات ----

    fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    /** يحلّ اسم الملف داخل مجلد الأذان فقط (يمنع الخروج منه بـ ../). */
    fun resolveFile(context: Context, name: String): File? {
        val d = dir(context)
        val f = File(d, name)
        return try {
            if (f.isFile && f.canonicalPath.startsWith(d.canonicalPath + File.separator)) f else null
        } catch (e: IOException) {
            null
        }
    }

    fun resolveRawId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)

    /** هل الصوت المحدد قابل للتشغيل فعلًا الآن؟ (الافتراضي يعني نغمة النظام وليس ملفًا). */
    fun isCustomPlayable(context: Context, value: String?): Boolean = when (val r = parse(value)) {
        VoiceRef.Default -> false
        is VoiceRef.File -> resolveFile(context, r.name) != null
        is VoiceRef.Raw -> resolveRawId(context, r.name) != 0
    }

    fun list(context: Context): List<VoiceFile> =
        dir(context).listFiles { f -> f.isFile && !f.name.endsWith(".part") }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { VoiceFile(it.name, it.length()) }

    fun delete(context: Context, name: String): Boolean = resolveFile(context, name)?.delete() == true

    private fun uniqueFile(context: Context, desired: String): File {
        val d = dir(context)
        var f = File(d, desired)
        if (!f.exists()) return f
        val base = desired.substringBeforeLast('.', desired)
        val ext = desired.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (true) {
            f = File(d, "$base-$i$ext")
            if (!f.exists()) return f
            i++
        }
    }

    // ---- الاستيراد ----

    /** ينسخ ملفًا اختاره المستخدم عبر Storage Access Framework إلى التخزين الداخلي مرة واحدة. */
    suspend fun importFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        var dest: File? = null
        try {
            val display = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "adhan"
            val d = uniqueFile(context, sanitizeFileName(display))
            dest = d
            val input = context.contentResolver.openInputStream(uri) ?: throw AdhanImportException("تعذّر فتح الملف المختار.")
            input.use { copyLimited(it, d) }
            validateAudio(d)
            Result.success(d.name)
        } catch (e: Exception) {
            dest?.delete()
            Result.failure(if (e is AdhanImportException) e else AdhanImportException("تعذّر استيراد الملف. تأكد أنه ملف صوتي سليم."))
        }
    }

    /** ينزّل ملفًا صوتيًا مباشرًا (https) مرة واحدة إلى التخزين الداخلي. لا يدعم صفحات الفيديو إطلاقًا. */
    suspend fun importFromUrl(context: Context, url: String): Result<String> = withContext(Dispatchers.IO) {
        validateUrl(url)?.let { return@withContext Result.failure(AdhanImportException(it)) }
        var dest: File? = null
        try {
            var current = URL(url.trim())
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = current.openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "audio/*")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw AdhanImportException("تعذّر تنزيل الملف (تحويل غير صالح).")
                    val next = URL(current, loc)
                    if (next.protocol != "https" || hops++ >= 5) throw AdhanImportException("تعذّر تنزيل الملف (تحويل غير آمن).")
                    validateUrl(next.toString())?.let { throw AdhanImportException(it) }
                    conn.disconnect()
                    current = next
                    continue
                }
                if (code != 200) throw AdhanImportException("تعذّر تنزيل الملف (رمز الخادم $code).")
                break
            }
            val type = conn.contentType?.lowercase()
            if (type != null && (type.startsWith("text/") || type.contains("html") || type.contains("json"))) {
                throw AdhanImportException("الرابط لا يشير إلى ملف صوتي مباشر.")
            }
            if (conn.contentLengthLong > MAX_BYTES) throw AdhanImportException("الملف كبير جدًا (الحد الأقصى ٢٥ ميغابايت).")
            var name = sanitizeFileName(current.path.substringAfterLast('/').ifBlank { "adhan" })
            if (!name.contains('.')) name += "." + (extensionForContentType(type) ?: "mp3")
            val d = uniqueFile(context, name)
            dest = d
            conn.inputStream.use { copyLimited(it, d) }
            conn.disconnect()
            validateAudio(d)
            Result.success(d.name)
        } catch (e: Exception) {
            dest?.delete()
            Result.failure(if (e is AdhanImportException) e else AdhanImportException("تعذّر تنزيل الملف. تحقق من الرابط والاتصال بالإنترنت."))
        }
    }

    private fun copyLimited(input: InputStream, dest: File) {
        var total = 0L
        dest.outputStream().use { out ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw AdhanImportException("الملف كبير جدًا (الحد الأقصى ٢٥ ميغابايت).")
                out.write(buf, 0, n)
            }
        }
        if (total == 0L) throw AdhanImportException("الملف فارغ.")
    }

    /** يتأكد أن الملف صوتي صالح وأن مدته بين ثانية و١٠ دقائق. */
    private fun validateAudio(file: File) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (hasAudio != "yes" || dur == null) throw AdhanImportException("الملف ليس ملفًا صوتيًا مدعومًا.")
            if (dur < MIN_DURATION_MS) throw AdhanImportException("الملف الصوتي قصير جدًا.")
            if (dur > MAX_DURATION_MS) throw AdhanImportException("مدة الملف تتجاوز ١٠ دقائق.")
        } catch (e: AdhanImportException) {
            throw e
        } catch (e: Exception) {
            throw AdhanImportException("الملف ليس ملفًا صوتيًا مدعومًا.")
        } finally {
            runCatching { r.release() }
        }
    }
}
