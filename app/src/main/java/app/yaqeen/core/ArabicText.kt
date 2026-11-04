package app.yaqeen.core

/** أدوات النص العربي: التطبيع للبحث، وتحويل الأرقام. لا تُستخدم أبدًا لتعديل النص القرآني المعروض. */
object ArabicText {
    private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u08D3-\\u08FF\\u0640]")

    /** يزيل التشكيل والتطويل ويوحّد أشكال الألف والياء والتاء المربوطة والهمزات لأغراض البحث فقط. */
    fun normalize(input: String): String = input
        .replace(DIACRITICS, "")
        .replace('ٱ', 'ا') // ألف وصل
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي') // ألف مقصورة -> ياء
        .replace('ة', 'ه') // تاء مربوطة -> هاء
        .replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(Regex("\\s+"), " ")
        .trim()

    private val ARABIC_INDIC = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /** يحوّل أرقامًا لاتينية إلى هندية (٠-٩) للعرض. */
    fun indic(n: Int): String = n.toString().map { if (it in '0'..'9') ARABIC_INDIC[it - '0'] else it }.joinToString("")
    fun indic(s: String): String = s.map { if (it in '0'..'9') ARABIC_INDIC[it - '0'] else it }.joinToString("")
}
