package app.yaqeen.feature.settings.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** ملف مرفق بالإصدار (من واجهة GitHub). [sha256] يأتي من حقل digest إن وُجد. */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

/**
 * إصدار منشور على GitHub Releases بعد التحليل.
 *  - [apk]: ملف APK الأول المناسب (ينتهي بـ .apk).
 *  - [checksumAssetUrl]: رابط ملف المجموع (.apk.sha256 / .sha256) إن نُشر.
 *  - [sha256]: المجموع إن كان متاحًا دون طلب إضافي (حقل digest أو نص الإصدار).
 */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    val body: String,
    val publishedAt: String?,
    val htmlUrl: String?,
    val apk: ReleaseAsset?,
    val checksumAssetUrl: String?,
    val sha256: String?,
) {
    /** تاريخ النشر (YYYY-MM-DD) إن كان صالحًا. */
    val publishedDate: String? get() = publishedAt?.take(10)?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
}

object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val HEX64 = Regex("""(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])""")
    private val BODY_LABELLED = Regex("""(?i)sha-?256\W{0,16}([0-9a-f]{64})(?![0-9a-f])""")
    private val BODY_APK_LINE = Regex("""(?i)(?<![0-9a-f])([0-9a-f]{64})(?![0-9a-f])[\s*`:|-]+[\w./-]*\.apk""")

    /** يحلّل ردّ `releases/latest`. يعيد null إن لم يكن JSON صالحًا أو بلا tag_name. */
    fun parse(text: String): ReleaseInfo? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val tag = root.str("tag_name")?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toAsset() }

        val apk = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            // نفضّل الملف الموقّع الذي يحمل الاسم الافتراضي، ثم أي APK آخر.
            .sortedByDescending { it.name.equals("app-release.apk", ignoreCase = true) }
            .firstOrNull()
        val checksumAsset = assets.firstOrNull { apk != null && it.name.equals(apk.name + ".sha256", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".apk.sha256", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".sha256", ignoreCase = true) }

        val body = root.str("body").orEmpty()
        val sha = apk?.sha256 ?: sha256FromBody(body)
        return ReleaseInfo(
            tag = tag,
            title = root.str("name")?.takeIf { it.isNotBlank() } ?: tag,
            body = body,
            publishedAt = root.str("published_at"),
            htmlUrl = root.str("html_url"),
            apk = apk,
            checksumAssetUrl = checksumAsset?.url,
            sha256 = sha,
        )
    }

    private fun JsonObject.toAsset(): ReleaseAsset? {
        val name = str("name") ?: return null
        val url = str("browser_download_url") ?: return null
        val digest = str("digest")?.let(::sha256FromDigest)
        return ReleaseAsset(name, url, (this["size"] as? JsonPrimitive)?.longOrNull ?: -1L, digest)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** حقل digest في واجهة GitHub بالشكل "sha256:<hex>". */
    fun sha256FromDigest(digest: String): String? {
        val d = digest.trim()
        if (!d.startsWith("sha256:", ignoreCase = true)) return null
        return d.substring(7).takeIf { HEX64.matches(it) }?.lowercase()
    }

    /** يستخرج مجموع SHA-256 من نص ملف .sha256 (صيغة sha256sum: "<hex>  <name>" أو hex وحده). */
    fun parseSha256Text(text: String, fileName: String? = null): String? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (fileName != null) {
            lines.firstOrNull { it.contains(fileName) }?.let { line -> HEX64.find(line)?.let { return it.value.lowercase() } }
        }
        for (line in lines) HEX64.find(line)?.let { return it.value.lowercase() }
        return null
    }

    /** يبحث في نص الإصدار عن "SHA-256: <hex>" أو سطر "<hex>  app.apk". */
    fun sha256FromBody(body: String): String? =
        BODY_LABELLED.find(body)?.groupValues?.get(1)?.lowercase()
            ?: BODY_APK_LINE.find(body)?.groupValues?.get(1)?.lowercase()
}
