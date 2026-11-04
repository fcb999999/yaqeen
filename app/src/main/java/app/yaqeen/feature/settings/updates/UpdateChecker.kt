package app.yaqeen.feature.settings.updates

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** ردّ HTTP مبسّط. أسماء الترويسات بأحرف صغيرة. */
data class HttpResult(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

/** عميل GET بسيط (قابل للاستبدال في الاختبارات). كل الاستدعاءات متزامنة: شغّلها على Dispatchers.IO. */
interface SimpleHttp {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult
}

/** تنفيذ حقيقي عبر HttpURLConnection: مهلات صريحة، حد أقصى لحجم الردّ، https فقط. */
class UrlHttp(private val connectTimeoutMs: Int = 15_000, private val readTimeoutMs: Int = 20_000) : SimpleHttp {
    override fun get(url: String, headers: Map<String, String>): HttpResult {
        require(url.startsWith("https://")) { "https only" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val n = s.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BODY) throw IOException("response too large")
                    buf.write(chunk, 0, n)
                }
                buf.toString(Charsets.UTF_8.name())
            }.orEmpty()
            val hs = HashMap<String, String>()
            for ((k, v) in conn.headerFields) if (k != null && v.isNotEmpty()) hs[k.lowercase()] = v.last()
            return HttpResult(code, body, hs)
        } finally {
            conn.disconnect()
        }
    }

    private companion object { const val MAX_BODY = 2 * 1024 * 1024 }
}

/** نتيجة التحقق من وجود إصدار أحدث. */
sealed interface UpdateCheckResult {
    /** لم يُضبط مستودع GitHub في هذا البناء. */
    data object Disabled : UpdateCheckResult
    data class UpToDate(val latestTag: String) : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult

    /** المستودع بلا إصدارات منشورة بعد (أو غير موجود/خاص). */
    data object NoRelease : UpdateCheckResult
    data class RateLimited(val resetAtEpochSeconds: Long?) : UpdateCheckResult
    data object Offline : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * يستعلم GitHub Releases (مستودع عام، بلا توكن) ويقارن آخر إصدار بإصدار التطبيق المثبّت.
 * [repo] بصيغة owner/name (فارغ = معطّل). [installedVersion] هو BuildConfig.VERSION_NAME (تُهمل لاحقة -debug).
 */
class UpdateChecker(
    private val http: SimpleHttp,
    private val repo: String,
    private val installedVersion: String,
) {
    fun check(): UpdateCheckResult {
        if (!isValidRepo(repo)) return UpdateCheckResult.Disabled
        val res = try {
            http.get(latestUrl(repo), HEADERS)
        } catch (e: IOException) {
            return UpdateCheckResult.Offline
        } catch (e: Exception) {
            return UpdateCheckResult.Failed(e.message ?: "خطأ غير متوقع")
        }
        when (res.code) {
            200 -> Unit
            404 -> return UpdateCheckResult.NoRelease
            403, 429 -> {
                val remaining = res.headers["x-ratelimit-remaining"]?.toIntOrNull()
                if (res.code == 429 || remaining == 0 || res.headers.containsKey("retry-after")) {
                    val reset = res.headers["x-ratelimit-reset"]?.toLongOrNull()
                        ?: res.headers["retry-after"]?.toLongOrNull()?.let { System.currentTimeMillis() / 1000 + it }
                    return UpdateCheckResult.RateLimited(reset)
                }
                return UpdateCheckResult.Failed("رفض الخادم الطلب (403)")
            }
            else -> return UpdateCheckResult.Failed("ردّ غير متوقع من الخادم (${res.code})")
        }
        val release = ReleaseParser.parse(res.body) ?: return UpdateCheckResult.Failed("تعذّر قراءة بيانات الإصدار")
        if (SemVer.parse(release.tag) == null) return UpdateCheckResult.Failed("رقم الإصدار المنشور غير صالح: ${release.tag}")
        return if (SemVer.isNewer(release.tag, installedVersion)) UpdateCheckResult.Available(release)
        else UpdateCheckResult.UpToDate(release.tag)
    }

    /**
     * مجموع SHA-256 المنشور للإصدار: من حقل digest أو نص الإصدار، وإلا من ملف .sha256 المرفق.
     * يعيد null إن لم يُنشر أي مجموع (يبقى التحقق من التوقيع إلزاميًا عندها).
     */
    fun resolveSha256(release: ReleaseInfo): String? {
        release.sha256?.let { return it }
        val url = release.checksumAssetUrl ?: return null
        if (!url.startsWith("https://")) return null
        return try {
            val r = http.get(url, mapOf("User-Agent" to USER_AGENT))
            if (r.code == 200) ReleaseParser.parseSha256Text(r.body, release.apk?.name) else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val USER_AGENT = "YAQEEN-Android-UpdateChecker"
        private val HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to USER_AGENT,
        )
        private val REPO = Regex("""^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$""")

        fun isValidRepo(repo: String): Boolean = REPO.matches(repo.trim()) && !repo.contains("..")
        fun latestUrl(repo: String) = "https://api.github.com/repos/${repo.trim()}/releases/latest"
        fun releasesPageUrl(repo: String) = "https://github.com/${repo.trim()}/releases/latest"
    }
}
