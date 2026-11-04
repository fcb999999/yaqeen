package app.yaqeen.feature.settings.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** أدوات التجزئة (SHA-256) الخالصة. */
object Hashing {
    private const val HEX = "0123456789abcdef"

    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256Hex(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return toHex(md.digest())
    }

    fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    /** مقارنة مجموعين سداسيين بلا حساسية لحالة الأحرف وبزمن ثابت. */
    fun hexEquals(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        if (x.length != y.length) return false
        var diff = 0
        for (i in x.indices) diff = diff or (x[i].code xor y[i].code)
        return diff == 0
    }
}

/** نتيجة التحقق من الملف المنزَّل قبل التثبيت. */
sealed interface VerifyResult {
    /** [checksumVerified] = false يعني أنه لم يُنشر مجموع للمقارنة وتم الاكتفاء بالتحقق من التوقيع والحزمة. */
    data class Ok(val checksumVerified: Boolean) : VerifyResult
    data class Failed(val reason: String) : VerifyResult
}

/** قواعد خالصة للتحقق (قابلة للاختبار دون Android). */
object VerifyRules {
    /** مجموعتا شهادات التوقيع (SHA-256 سداسية) يجب أن تتطابقا تمامًا ولا تكون فارغة. */
    fun signersMatch(installed: Set<String>, candidate: Set<String>): Boolean {
        if (installed.isEmpty() || candidate.isEmpty()) return false
        return installed.map { it.lowercase() }.toSet() == candidate.map { it.lowercase() }.toSet()
    }

    /** يقرر نتيجة المجموع: null = لا مجموع منشور، true/false = تطابق أو لا. */
    fun checksumMatches(expected: String?, actual: String): Boolean? =
        expected?.let { Hashing.hexEquals(it, actual) }
}

/**
 * يتحقق من APK المنزَّل:
 *  1) المجموع SHA-256 يطابق المنشور (إن وُجد).
 *  2) الملف حزمة Android صالحة باسم حزمة هذا التطبيق نفسه ورقم إصدار أعلى من المثبّت.
 *  3) شهادة توقيعه تطابق شهادة التطبيق المثبّت (فلا يُستبدل التطبيق إلا بنسخة موقّعة من الجهة نفسها).
 */
object ApkVerifier {
    fun verify(context: Context, apk: File, expectedSha256: String?, actualSha256: String): VerifyResult {
        val checksum = VerifyRules.checksumMatches(expectedSha256, actualSha256)
        if (checksum == false) return VerifyResult.Failed("مجموع التحقق (SHA-256) لا يطابق المنشور؛ الملف تالف أو تم العبث به.")

        val pm = context.packageManager
        val info = archiveInfo(pm, apk) ?: return VerifyResult.Failed("الملف المنزَّل ليس حزمة تطبيق صالحة.")
        if (info.packageName != context.packageName) {
            return VerifyResult.Failed("اسم حزمة الملف (${info.packageName}) لا يطابق هذا التطبيق (${context.packageName}).")
        }
        val installed = runCatching { pm.getPackageInfoCompat(context.packageName) }.getOrNull()
            ?: return VerifyResult.Failed("تعذّر قراءة معلومات التطبيق المثبّت.")
        if (versionCodeOf(info) <= versionCodeOf(installed)) {
            return VerifyResult.Failed("رقم إصدار الملف المنزَّل ليس أحدث من المثبّت.")
        }
        val candidateSigners = signerDigests(info)
        val installedSigners = signerDigests(installed)
        if (!VerifyRules.signersMatch(installedSigners, candidateSigners)) {
            return VerifyResult.Failed("توقيع الملف لا يطابق توقيع التطبيق المثبّت؛ لن يُثبَّت حفاظًا على أمان بياناتك.")
        }
        return VerifyResult.Ok(checksumVerified = checksum == true)
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        runCatching { pm.getPackageArchiveInfo(apk.absolutePath, signingFlags()) }.getOrNull()

    private fun PackageManager.getPackageInfoCompat(pkg: String): PackageInfo = getPackageInfo(pkg, signingFlags())

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return sigs.orEmpty().map { Hashing.sha256Hex(it.toByteArray()) }.toSet()
    }
}
