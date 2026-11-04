package app.yaqeen.feature.settings.updates

/**
 * رقم إصدار دلالي (Semantic Version) مبسّط: أرقام حتى أربعة مستويات + لاحقة ما قبل الإصدار اختيارية.
 * أمثلة صالحة: "1.2.3" و"v1.2" و"2.0.0-beta.1" و"1.0.0+build5".
 * قاعدة الترتيب: الإصدار بلا لاحقة أعلى من الإصدار نفسه مع لاحقة (1.0.0 > 1.0.0-beta).
 */
data class SemVer(val numbers: List<Int>, val preRelease: List<String>) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        val n = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until n) {
            val a = numbers.getOrElse(i) { 0 }
            val b = other.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val m = minOf(preRelease.size, other.preRelease.size)
        for (i in 0 until m) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1 // المعرّف الرقمي أدنى من النصي
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String =
        numbers.joinToString(".") + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d{1,6}(?:\.\d{1,6}){0,3})(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")
        private val DEBUG_SUFFIX = Regex("-debug$", RegexOption.IGNORE_CASE)

        /** يحلّل نص الإصدار (يسمح ببادئة v). يعيد null إن لم يكن صالحًا. */
        fun parse(raw: String): SemVer? {
            val m = PATTERN.matchEntire(raw.trim()) ?: return null
            val nums = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val pre = m.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')?.filter { it.isNotEmpty() }.orEmpty()
            return SemVer(nums, pre)
        }

        /** إصدار التطبيق المثبّت: تُهمل لاحقة "-debug" التي يضيفها بناء التطوير. */
        fun parseInstalled(raw: String): SemVer? = parse(raw.trim().replace(DEBUG_SUFFIX, ""))

        /** هل [remote] (مثل tag_name) أحدث من الإصدار المثبّت؟ أي نص غير صالح يعني "لا". */
        fun isNewer(remote: String, installed: String): Boolean {
            val r = parse(remote) ?: return false
            val i = parseInstalled(installed) ?: return false
            return r > i
        }
    }
}
