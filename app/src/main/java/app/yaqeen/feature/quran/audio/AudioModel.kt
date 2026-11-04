package app.yaqeen.feature.quran.audio

import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.IntKey
import app.yaqeen.data.settings.StringKey
import app.yaqeen.domain.model.SettingScope
import java.io.File
import java.util.Locale

/**
 * قارئ متاح للاستماع. [bitrate] هو مجلد الجودة في شبكة CDN الذي تأكدتُ فعليًا من وجود ملفات هذا القارئ فيه
 * (بعض القرّاء لا يتوفرون إلا بجودة محددة).
 */
data class QuranReciter(val id: String, val nameAr: String, val bitrate: Int)

object Reciters {
    /** قائمة منتقاة؛ لكل عنصر تحقّقتُ بطلب HEAD من أول آية وآخر آية في كل سور المصحف (١١٤ سورة) ورجع 200. */
    val all: List<QuranReciter> = listOf(
        QuranReciter("ar.alafasy", "مشاري راشد العفاسي", 128),
        QuranReciter("ar.abdulbasitmurattal", "عبد الباسط عبد الصمد (مرتّل)", 192),
        QuranReciter("ar.husary", "محمود خليل الحصري", 128),
        QuranReciter("ar.husarymujawwad", "محمود خليل الحصري (مجوّد)", 64),
        QuranReciter("ar.minshawi", "محمد صدّيق المنشاوي", 128),
        QuranReciter("ar.minshawimujawwad", "محمد صدّيق المنشاوي (مجوّد)", 64),
        QuranReciter("ar.abdurrahmaansudais", "عبد الرحمن السديس", 192),
        QuranReciter("ar.saoodshuraym", "سعود الشريم", 64),
        QuranReciter("ar.mahermuaiqly", "ماهر المعيقلي", 128),
        QuranReciter("ar.hudhaify", "علي بن عبد الرحمن الحذيفي", 128),
        QuranReciter("ar.shaatree", "أبو بكر الشاطري", 128),
        QuranReciter("ar.muhammadayyoub", "محمد أيوب", 128),
        QuranReciter("ar.ahmedajamy", "أحمد بن علي العجمي", 128),
        QuranReciter("ar.hanirifai", "هاني الرفاعي", 192),
        QuranReciter("ar.muhammadjibreel", "محمد جبريل", 128),
    )
    const val DEFAULT_ID = "ar.alafasy"

    fun byId(id: String?): QuranReciter = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }

    /** رابط تلاوة آية (المعرّف العام 1..6236) من شبكة islamic.network. */
    fun url(r: QuranReciter, ayahId: Int): String = "https://cdn.islamic.network/quran/audio/${r.bitrate}/${r.id}/$ayahId.mp3"
}

/** أنماط التكرار. */
enum class AudioRepeat { NONE, AYAH, SURAH }

/** إعدادات الاستماع المتزامنة مع حساب المستخدم. */
object AudioSettings {
    private const val U = SettingScope.USER
    val REPEAT_KIND = StringKey(U, "quran_audio_repeat", AudioRepeat.NONE.name)
    val REPEAT_TIMES = IntKey(U, "quran_audio_repeat_times", 3)
    const val MAX_REPEAT = 20
    val REPEAT_CHOICES = listOf(2, 3, 5, 7, 10)
}

data class QuranAudioState(
    /** الآية الجارية (المعرّف العام 1..6236) أو null إن لم يكن هناك تشغيل. */
    val currentAyahId: Int? = null,
    val isPlaying: Boolean = false,
    val reciterName: String = "",
    // ---- إضافات (لها قيم افتراضية فلا تكسر الاستدعاءات القائمة) ----
    val reciterId: String = "",
    val isBuffering: Boolean = false,
    val repeat: AudioRepeat = AudioRepeat.NONE,
    val repeatTimes: Int = 3,
    /** رقم مرة التشغيل الحالية للآية في وضع تكرار الآية (1..repeatTimes). */
    val currentRepeat: Int = 1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** رسالة خطأ للمستخدم (مثل انقطاع الشبكة) أو null. */
    val error: String? = null,
) {
    val isActive: Boolean get() = currentAyahId != null
}

/**
 * عدّاد تكرار الآية: يُستخدم مع وضع التكرار الواحد في المشغّل.
 * [onNewItem] عند الانتقال إلى آية جديدة (يرجع هل يُفعَّل تكرار الآية)،
 * [onLooped] عند إعادة الآية نفسها (يرجع هل يستمر التكرار بعد هذه المرة).
 */
class AyahRepeatCounter(var times: Int) {
    var played: Int = 1
        private set

    fun onNewItem(): Boolean { played = 1; return times > 1 }

    fun onLooped(): Boolean { played++; return played < times }
}

/** قوائم التشغيل (دوال خالصة). */
object AudioPlaylists {
    /** سورة كاملة، يبدأ التشغيل من الآية [fromAyah]. ترجع (معرّفات الآيات، فهرس البداية). */
    fun surah(q: QuranData, surah: Int, fromAyah: Int): Pair<List<Int>, Int> {
        val s = q.surah(surah.coerceIn(1, 114))
        val ids = (s.firstAyahId until s.firstAyahId + s.ayahCount).toList()
        return ids to (fromAyah.coerceIn(1, s.ayahCount) - 1)
    }

    fun range(totalAyahs: Int, from: Int, to: Int): List<Int> {
        val a = from.coerceIn(1, totalAyahs)
        val b = to.coerceIn(1, totalAyahs)
        return if (a <= b) (a..b).toList() else (a downTo b).toList().reversed()
    }
}

/** ملفات الصوت المحلية: filesDir/audio/<reciter>/<ayahId>.mp3 (الملف الكامل فقط؛ الجزئي بامتداد .part). */
object AudioFiles {
    fun reciterDir(root: File, reciterId: String) = File(File(root, "audio"), reciterId)
    fun file(root: File, reciterId: String, ayahId: Int) = File(reciterDir(root, reciterId), "$ayahId.mp3")
    fun partFile(root: File, reciterId: String, ayahId: Int) = File(reciterDir(root, reciterId), "$ayahId.mp3.part")

    /** عدد الآيات المحمّلة لكل سورة (فهرس 1..114) من أسماء الملفات. */
    fun countBySurah(fileNames: Collection<String>, q: QuranData): IntArray {
        val out = IntArray(115)
        for (n in fileNames) {
            if (!n.endsWith(".mp3")) continue
            val id = n.removeSuffix(".mp3").toIntOrNull() ?: continue
            if (id !in 1..q.totalAyahs) continue
            out[q.ayah(id).surah]++
        }
        return out
    }
}

/** تنسيق الأحجام بالعربية. */
object SizeFormat {
    fun bytes(n: Long): String {
        if (n <= 0) return "٠ ك.ب"
        val kb = n / 1024.0
        return when {
            kb < 1024 -> "${ArabicText.indic(String.format(Locale.US, "%d", kb.toLong().coerceAtLeast(1)))} ك.ب"
            kb < 1024 * 1024 -> "${num(kb / 1024.0)} م.ب"
            else -> "${num(kb / 1024.0 / 1024.0)} ج.ب"
        }
    }

    private fun num(v: Double): String {
        val s = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
        return ArabicText.indic(s.replace(".", "٫"))
    }
}
