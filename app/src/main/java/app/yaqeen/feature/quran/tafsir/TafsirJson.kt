package app.yaqeen.feature.quran.tafsir

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.io.Reader

/**
 * معالجة ملفات التفسير (دوال خالصة بلا Android قابلة للاختبار):
 *  - قراءة استجابة alquran.cloud بالتدفق (دون تحميلها كلها في الذاكرة؛ بعضها يتجاوز ٢٥ م.ب).
 *  - كتابة ملف التفسير المحلي بالصيغة نفسها لملف التفسير المضمَّن: {edition,name,author,texts:[...]}
 *    مع ملف فهرس ثنائي (.idx) يحوي موضع كل نص في الملف ليُقرأ تفسير آية واحدة دون تحميل الملف كله.
 */
object TafsirJson {
    const val AYAH_COUNT = 6236
    private const val IDX_MAGIC = 0x54414653 // "TAFS"

    // ------------------------------------------------------------ قراءة بالتدفق

    /**
     * يستخرج نصوص data.surahs[].ayahs[].text بالترتيب ويمرّرها إلى [sink] واحدًا واحدًا.
     * يرمي IllegalArgumentException إن لم يكن الدخل JSON صالحًا.
     */
    fun extractAyahTexts(reader: Reader, sink: (String) -> Unit) {
        val s = Scanner(reader)
        s.skipWs()
        s.parseValue(ArrayList(), sink)
    }

    private class Scanner(private val r: Reader) {
        private val buf = CharArray(16 * 1024)
        private var len = 0
        private var pos = 0

        fun peek(): Int {
            if (pos >= len) {
                len = r.read(buf)
                pos = 0
                if (len <= 0) { len = 0; return -1 }
            }
            return buf[pos].code
        }

        fun next(): Int { val c = peek(); if (c >= 0) pos++; return c }

        fun skipWs() { while (true) { val c = peek(); if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) pos++ else return } }

        fun expect(ch: Char) {
            val c = next()
            require(c == ch.code) { "JSON غير صالح: المتوقع '$ch'" }
        }

        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                require(c >= 0) { "JSON غير صالح: نص غير منتهٍ" }
                when (c) {
                    '"'.code -> return sb.toString()
                    '\\'.code -> {
                        when (val e = next()) {
                            '"'.code -> sb.append('"')
                            '\\'.code -> sb.append('\\')
                            '/'.code -> sb.append('/')
                            'b'.code -> sb.append('\b')
                            'f'.code -> sb.append('\u000C')
                            'n'.code -> sb.append('\n')
                            'r'.code -> sb.append('\r')
                            't'.code -> sb.append('\t')
                            'u'.code -> {
                                var v = 0
                                repeat(4) {
                                    val h = Character.digit(next(), 16)
                                    require(h >= 0) { "JSON غير صالح: تهريب u" }
                                    v = v * 16 + h
                                }
                                sb.append(v.toChar())
                            }
                            else -> throw IllegalArgumentException("JSON غير صالح: تهريب $e")
                        }
                    }
                    else -> sb.append(c.toChar())
                }
            }
        }

        fun parseValue(path: ArrayList<String>, sink: (String) -> Unit) {
            skipWs()
            when (val c = peek()) {
                '{'.code -> {
                    next(); skipWs()
                    if (peek() == '}'.code) { next(); return }
                    while (true) {
                        skipWs()
                        val key = parseString()
                        skipWs(); expect(':')
                        path.add(key)
                        parseValue(path, sink)
                        path.removeAt(path.lastIndex)
                        skipWs()
                        val d = next()
                        if (d == ','.code) continue
                        require(d == '}'.code) { "JSON غير صالح: المتوقع } أو ," }
                        return
                    }
                }
                '['.code -> {
                    next(); skipWs()
                    if (peek() == ']'.code) { next(); return }
                    while (true) {
                        path.add("#")
                        parseValue(path, sink)
                        path.removeAt(path.lastIndex)
                        skipWs()
                        val d = next()
                        if (d == ','.code) continue
                        require(d == ']'.code) { "JSON غير صالح: المتوقع ] أو ," }
                        return
                    }
                }
                '"'.code -> {
                    val str = parseString()
                    if (path.size == 6 && path[0] == "data" && path[1] == "surahs" && path[3] == "ayahs" && path[5] == "text") sink(str)
                }
                -1 -> throw IllegalArgumentException("JSON غير صالح: نهاية مبكرة")
                else -> {
                    // رقم أو true/false/null
                    require(c != '}'.code && c != ']'.code && c != ','.code) { "JSON غير صالح" }
                    while (true) {
                        val x = peek()
                        if (x < 0 || x == ','.code || x == '}'.code || x == ']'.code || x == ' '.code || x == '\n'.code || x == '\r'.code || x == '\t'.code) return
                        pos++
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ كتابة الملف المحلي

    /** يحوّل نصًا إلى سلسلة JSON بعلامات اقتباس (بلا تهريب للأحرف العربية). */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch < ' ' -> sb.append(String.format(java.util.Locale.US, "\\u%04x", ch.code))
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** يكتب ملف {edition,name,author,texts} ويسجل (بداية، طول) كل نص بالبايتات لفهرسته. */
    class FileWriter(out: OutputStream, edition: String, name: String, author: String) {
        private val out = java.io.BufferedOutputStream(out, 64 * 1024)
        private var written = 0L
        private val starts = ArrayList<Int>()
        private val lens = ArrayList<Int>()

        init {
            raw("{\"edition\":${quote(edition)},\"name\":${quote(name)},\"author\":${quote(author)},\"texts\":[")
        }

        private fun raw(s: String) { val b = s.toByteArray(Charsets.UTF_8); out.write(b); written += b.size }

        val count: Int get() = starts.size

        fun add(text: String) {
            if (starts.isNotEmpty()) raw(",")
            val b = quote(text).toByteArray(Charsets.UTF_8)
            starts += written.toInt()
            lens += b.size
            out.write(b)
            written += b.size
        }

        fun finish() { raw("]}"); out.flush(); out.close() }

        fun writeIndex(file: File) {
            DataOutputStream(java.io.BufferedOutputStream(file.outputStream())).use { d ->
                d.writeInt(IDX_MAGIC)
                d.writeInt(starts.size)
                for (i in starts.indices) { d.writeInt(starts[i]); d.writeInt(lens[i]) }
            }
        }
    }

    /** يقرأ (بداية، طول) نص آية من ملف الفهرس، أو null إن لم يكن صالحًا. */
    fun readIndexEntry(idx: java.io.RandomAccessFile, ayahId: Int): Pair<Int, Int>? {
        if (idx.length() < 8) return null
        idx.seek(0)
        if (idx.readInt() != IDX_MAGIC) return null
        val n = idx.readInt()
        if (ayahId < 1 || ayahId > n || idx.length() != 8L + n * 8L) return null
        idx.seek(8L + (ayahId - 1) * 8L)
        return idx.readInt() to idx.readInt()
    }

    fun indexCount(idx: File): Int = runCatching {
        DataInputStream(idx.inputStream().buffered()).use { d -> if (d.readInt() == IDX_MAGIC) d.readInt() else -1 }
    }.getOrDefault(-1)

    // ------------------------------------------------------------ عرض النص

    /**
     * يقسّم نص التفسير إلى فقرات للعرض: على أسطر النص، وتُقطَّع الفقرة الطويلة عند أقرب مسافة
     * بعد [maxChunk] حرفًا حتى لا يُرسَم نص ضخم (يبلغ بعضها ٣٧ ألف حرف) في عنصر واجهة واحد. لا يغيّر الحروف.
     */
    fun paragraphs(text: String, maxChunk: Int = 1200): List<String> {
        val out = ArrayList<String>()
        for (line in text.split('\n')) {
            var rest = line.trim()
            if (rest.isEmpty()) continue
            while (rest.length > maxChunk) {
                var cut = rest.indexOf(' ', maxChunk)
                if (cut < 0 || cut > maxChunk + 300) cut = maxChunk
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out += rest
        }
        return out
    }
}
