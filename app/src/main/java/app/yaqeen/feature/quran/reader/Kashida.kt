package app.yaqeen.feature.quran.reader

/**
 * الكشيدة (التطويل «ـ») لمدّ السطر كالمصحف المطبوع بدل توسيع الفراغ بين الكلمات وحده.
 * عرض فقط: الكشيدة لا تغيّر حرفًا ولا حركة، وتُدرج بين حرفين موصولين بعد علامات الحرف الأول.
 */
object Kashida {
    const val TATWEEL = 'ـ'

    /** حروف تتصل بما بعدها (ثنائية الاتصال). */
    private const val DUAL = "بتثجحخسشصضطظعغفقكلمنهيئىـ"

    /** حروف تتصل بما قبلها فقط. */
    private const val RIGHT_ONLY = "اأإآٱدذرزوؤة"

    private const val ALEFS = "اأإآٱ"

    /** علامة تُركَّب على الحرف السابق (حركة، علامة ضبط، ألف خنجرية، واو/ياء صغيرة). */
    fun isMark(c: Char): Boolean {
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt() || c == 'ۥ' || c == 'ۦ'
    }

    /**
     * موضع إدراج الكشيدة في الكلمة (الفهرس الذي تُدرج قبله)، أو -1 إن لم يصلح.
     * المفضَّل آخر وصلة صالحة في الكلمة (ٱلنَّـاسِ، كَفَـرُواْ)؛ ولا مدّ في لفظ الجلالة ولا في «لا».
     */
    fun position(word: String): Int {
        val bases = word.indices.filter { !isMark(word[it]) }
        if (bases.size < 2) return -1
        val skeleton = buildString { for (i in bases) append(word[i]) }
        if ("لله" in skeleton) return -1
        var best = -1
        for (k in 0 until bases.size - 1) {
            val a = word[bases[k]]
            val b = word[bases[k + 1]]
            if (a !in DUAL) continue
            if (b !in DUAL && b !in RIGHT_ONLY) continue
            if (a == 'ل' && b in ALEFS) continue
            best = bases[k + 1]
        }
        return best
    }

    /** الكلمة بعد إدراج [count] كشيدة في موضعها (أو كما هي). */
    fun stretch(word: String, count: Int): String {
        if (count <= 0) return word
        val at = position(word)
        if (at < 0) return word
        return word.substring(0, at) + TATWEEL.toString().repeat(count) + word.substring(at)
    }
}
