package app.yaqeen.feature.settings.updates

/** جزء من سطر: نص عادي أو عريض. */
data class MdSpan(val text: String, val bold: Boolean = false)

/** كتلة في ملاحظات الإصدار بعد التحليل. */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullet(val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
}

/**
 * محلّل ماركداون مخفّف لعرض ملاحظات الإصدار: عناوين (#)، نقاط (- * + أو 1.)، فقرات، **عريض**.
 * الروابط [نص](رابط) تُختزل إلى نصها، وعلامات `الكود` وما شابه تُزال. لا يُنفَّذ أي HTML؛ كله نص خام.
 */
object MarkdownLight {
    private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val BULLET = Regex("""^\s*(?:[-*+]|\d{1,3}[.)])\s+(.*)$""")
    private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val HTML_TAG = Regex("""</?[A-Za-z][^>]*>""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    fun parse(markdown: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) blocks += MdBlock.Paragraph(spans(para.toString().trim()))
            para.clear()
        }
        for (raw in markdown.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flush(); continue }
            if (RULE.matches(line)) { flush(); continue }
            val h = HEADING.matchEntire(line)
            if (h != null) {
                flush()
                blocks += MdBlock.Heading(h.groupValues[1].length, spans(h.groupValues[2]))
                continue
            }
            val b = BULLET.matchEntire(line)
            if (b != null) {
                flush()
                blocks += MdBlock.Bullet(spans(b.groupValues[1]))
                continue
            }
            if (para.isNotEmpty()) para.append(' ')
            para.append(line.trim())
        }
        flush()
        return blocks
    }

    /** يفكّك **عريض** ويزيل الروابط والعلامات الأخرى. */
    fun spans(input: String): List<MdSpan> {
        val clean = input
            .replace(LINK) { it.groupValues[1] }
            .replace(HTML_TAG, "")
            .replace("`", "")
            .replace(Regex("""(?<!\*)\*(?!\*)([^*]+)(?<!\*)\*(?!\*)"""), "$1") // مائل → عادي
            .replace(Regex("""(?<!_)__(?!_)"""), "**")
        val out = ArrayList<MdSpan>()
        var bold = false
        val parts = clean.split("**")
        for ((i, p) in parts.withIndex()) {
            if (i > 0) bold = !bold
            if (p.isNotEmpty()) out += MdSpan(p, bold)
        }
        return if (out.isEmpty()) listOf(MdSpan("")) else out
    }
}
