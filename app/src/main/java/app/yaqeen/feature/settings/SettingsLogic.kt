package app.yaqeen.feature.settings

import app.yaqeen.core.ArabicText
import java.io.File

/** منطق خالص (بلا Android) تستخدمه شاشات الإعدادات؛ مغطّى باختبارات JVM. */

/** أوقات الإشعارات المخزّنة كنص "HH:mm" (24 ساعة). */
object TimeText {
    data class HM(val hour: Int, val minute: Int)

    /** يحلّل "HH:mm". أي قيمة غير صالحة تعيد [fallback]. */
    fun parse(text: String?, fallback: HM = HM(0, 0)): HM {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text?.trim().orEmpty()) ?: return fallback
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) HM(h, min) else fallback
    }

    fun format24(hour: Int, minute: Int): String = "%02d:%02d".format(java.util.Locale.US, hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /** عرض عربي بنظام 12 ساعة، مثل "٥:٣٠ ص" و"١١:٠٠ م". */
    fun display(text: String?, fallback: HM = HM(0, 0)): String {
        val (h, m) = parse(text, fallback)
        val suffix = if (h < 12) "ص" else "م"
        val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
        return ArabicText.indic("%d:%02d".format(java.util.Locale.US, h12, m)) + " " + suffix
    }
}

/** صيغ الجمع العربية للوحدات الزمنية والأعداد. */
object ArabicUnits {
    /** يختار صيغة الجمع: مفرد (1)، مثنى (2)، جمع (3-10)، مفرد منصوب (11+). */
    fun plural(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "${ArabicText.indic(n)} $few"
        else -> "${ArabicText.indic(n)} $many"
    }

    /** "منذ ..." بالنسبة للوقت الحالي. [thenMs] = 0 يعني "لم يحدث بعد". */
    fun ago(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0) return "لم تتم بعد"
        val sec = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 45 -> "الآن"
            sec < 3600 -> "منذ " + plural((sec / 60).toInt().coerceAtLeast(1), "دقيقة", "دقيقتين", "دقائق", "دقيقة")
            sec < 86_400 -> "منذ " + plural((sec / 3600).toInt(), "ساعة", "ساعتين", "ساعات", "ساعة")
            else -> "منذ " + plural((sec / 86_400).toInt(), "يوم", "يومين", "أيام", "يومًا")
        }
    }

    /** وصف الدقائق في الإعدادات: 0 = "معطّل". */
    fun minutes(n: Int): String = if (n <= 0) "معطّل" else plural(n, "دقيقة", "دقيقتان", "دقائق", "دقيقة")
}

/** تنسيق أحجام الملفات بالعربية (أرقام هندية): "١٢٫٣ ميغابايت". */
object ByteSize {
    fun format(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            b < 1024 -> ArabicText.indic(b.toString()) + " بايت"
            b < mb -> num(b / kb) + " كيلوبايت"
            b < gb -> num(b / mb) + " ميغابايت"
            else -> num(b / gb) + " غيغابايت"
        }
    }

    private fun num(v: Double): String {
        val s = if (v >= 100) "%.0f".format(java.util.Locale.US, v) else "%.1f".format(java.util.Locale.US, v)
        return ArabicText.indic(s.removeSuffix(".0")).replace('.', '٫')
    }
}

/** حساب أحجام المجلدات (آمن ضد الروابط الرمزية وأخطاء القراءة). */
object DiskUsage {
    fun size(file: File): Long {
        if (!file.exists()) return 0
        if (file.isFile) return file.length()
        val children = runCatching { file.listFiles() }.getOrNull() ?: return 0
        var total = 0L
        for (child in children) {
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue
            total += size(child)
        }
        return total
    }

    /** يحذف محتويات المجلد (لا المجلد نفسه). يعيد عدد البايتات التي تحرّرت فعليًا. */
    fun clearContents(dir: File): Long {
        var freed = 0L
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        for (child in children) {
            val s = size(child)
            if (child.deleteRecursively()) freed += s
        }
        return freed
    }
}
