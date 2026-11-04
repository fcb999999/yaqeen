package app.yaqeen.feature.settings.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** نتيجة تنزيل ناجح: الملف النهائي ومجموعه SHA-256 محسوبًا أثناء التنزيل. */
data class DownloadedFile(val file: File, val sha256: String, val bytes: Long)

/**
 * ينزّل ملفًا إلى ملف مؤقت (.part) ثم ينقله إلى اسمه النهائي بعد اكتمال الحجم.
 * https فقط (بما في ذلك التحويلات)، مهلات صريحة، يتحقق من المساحة الحرة، يدعم الإلغاء بإلغاء الكوروتين.
 */
class ApkDownloader(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000,
) {
    suspend fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        onProgress: (done: Long, total: Long) -> Unit,
    ): DownloadedFile = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        try {
            if (expectedSize > 0) {
                val free = dest.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < expectedSize + MIN_FREE_MARGIN) throw IOException("لا توجد مساحة تخزين كافية لتنزيل التحديث.")
            }
            val conn = openFollowingHttpsRedirects(url)
            try {
                val code = conn.responseCode
                if (code != 200) throw IOException("تعذّر التنزيل (رمز الخادم $code).")
                val declared = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
                if (declared > MAX_APK_BYTES) throw IOException("حجم الملف أكبر من المسموح.")
                val md = MessageDigest.getInstance("SHA-256")
                var done = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            done += n
                            if (done > MAX_APK_BYTES) throw IOException("حجم الملف أكبر من المسموح.")
                            val now = System.nanoTime()
                            if (now - lastReport > 100_000_000L) { // ~10 مرات في الثانية كحد أقصى
                                lastReport = now
                                onProgress(done, declared)
                            }
                        }
                    }
                }
                if (declared > 0 && done != declared) throw IOException("انقطع التنزيل قبل اكتماله.")
                onProgress(done, declared)
                dest.delete()
                if (!part.renameTo(dest)) throw IOException("تعذّر حفظ الملف المنزَّل.")
                DownloadedFile(dest, Hashing.toHex(md.digest()), done)
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    /** يفتح الاتصال ويتبع التحويلات يدويًا (حتى 5) مع رفض أي رابط غير https. */
    private fun openFollowingHttpsRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!current.startsWith("https://")) throw IOException("رابط التنزيل غير آمن.")
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", UpdateChecker.USER_AGENT)
            conn.setRequestProperty("Accept", "application/octet-stream")
            val code = conn.responseCode
            if (code in 301..303 || code == 307 || code == 308) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IOException("تحويل غير صالح من الخادم.")
                current = URL(URL(current), loc).toString()
            } else {
                return conn
            }
        }
        throw IOException("تحويلات كثيرة جدًا.")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_APK_BYTES = 300L * 1024 * 1024
        const val MIN_FREE_MARGIN = 20L * 1024 * 1024
    }
}
