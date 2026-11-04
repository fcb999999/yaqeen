package app.yaqeen.feature.adhkar

import app.yaqeen.data.adhkar.AdhkarItem
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.settings.IntKey
import app.yaqeen.data.settings.StringKey
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.domain.model.SettingScope

/** مفاتيح الإعدادات الخاصة بالأذكار (تُخزَّن في user_settings وتتزامن بين الأجهزة). */
object AdhkarKeys {
    const val TARGET_PREFIX = "adhkar_target_"
    const val POSITION_PREFIX = "adhkar_pos_"

    /** عدد التكرار المخصَّص لذكر؛ الافتراضي هو عدد الكتالوج. */
    fun target(itemId: String, default: Int) = IntKey(SettingScope.USER, TARGET_PREFIX + itemId, default)

    /** آخر ذكر وصل إليه المستخدم في قسم (بصيغة [AdhkarPosition]). */
    fun position(categoryId: String) = StringKey(SettingScope.USER, POSITION_PREFIX + categoryId, "")

    private const val ID_PREFIX = SettingScope.USER + ":"

    /** يستخرج التكرارات المخصَّصة (itemId → عدد) من خريطة الإعدادات الخام (id → value). */
    fun targetsFrom(raw: Map<String, String>): Map<String, Int> {
        val p = ID_PREFIX + TARGET_PREFIX
        val out = HashMap<String, Int>()
        for ((k, v) in raw) {
            if (!k.startsWith(p)) continue
            val n = v.toIntOrNull() ?: continue
            out[k.removePrefix(p)] = AdhkarCounter.clampTarget(n)
        }
        return out
    }

    /** يستخرج آخر المواضع المحفوظة (categoryId → موضع) من خريطة الإعدادات الخام. */
    fun positionsFrom(raw: Map<String, String>): Map<String, AdhkarPosition> {
        val p = ID_PREFIX + POSITION_PREFIX
        val out = HashMap<String, AdhkarPosition>()
        for ((k, v) in raw) {
            if (!k.startsWith(p)) continue
            val pos = AdhkarPosition.decode(v) ?: continue
            out[k.removePrefix(p)] = pos
        }
        return out
    }
}

/** موضع محفوظ في قسم: التاريخ (ليُعاد ضبطه طبيعيًا في اليوم التالي) + معرّف الذكر + وقت الحفظ (لترتيب "تابع"). */
data class AdhkarPosition(val date: String, val itemId: String, val savedAt: Long) {
    fun encode(): String = "$date|$itemId|$savedAt"

    companion object {
        fun decode(raw: String?): AdhkarPosition? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split('|')
            if (parts.size < 2) return null
            val date = parts[0]
            val itemId = parts[1]
            if (date.isBlank() || itemId.isBlank()) return null
            val at = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            return AdhkarPosition(date, itemId, at)
        }
    }
}

/** منطق العدّاد الخالص. */
object AdhkarCounter {
    const val MIN_TARGET = 1
    const val MAX_TARGET = 1000

    fun clampTarget(n: Int): Int = n.coerceIn(MIN_TARGET, MAX_TARGET)

    /** التكرار الفعلي: المخصَّص إن وُجد وإلا عدد الكتالوج. */
    fun effectiveTarget(catalogCount: Int, custom: Int?): Int = clampTarget(custom ?: catalogCount)

    /** العدد الجديد بعد نقرة واحدة (لا يتجاوز الهدف). */
    fun afterTap(count: Int, target: Int): Int = (count + 1).coerceAtMost(target)

    /** المتبقي من العدّ. */
    fun remaining(count: Int, target: Int): Int = (target - count).coerceAtLeast(0)

    fun isCompleted(count: Int, target: Int): Boolean = count >= target

    /** يحوّل نص إدخال المستخدم (يقبل الأرقام الهندية والفارسية) إلى تكرار صالح أو null. */
    fun parseTarget(input: String): Int? {
        val latin = buildString {
            for (ch in input.trim()) {
                when (ch) {
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    else -> append(ch)
                }
            }
        }
        if (latin.isEmpty() || !latin.all { it in '0'..'9' }) return null
        val n = latin.toLongOrNull() ?: return null
        if (n < MIN_TARGET || n > MAX_TARGET) return null
        return n.toInt()
    }
}

/** تقدّم ذكر في واجهة الاستخدام. */
data class ItemProgress(val count: Int, val target: Int) {
    val remaining: Int get() = AdhkarCounter.remaining(count, target)
    val completed: Boolean get() = AdhkarCounter.isCompleted(count, target)
    val ratio: Float get() = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)
}

/**
 * قيمة متفائلة (لم تُحفَظ بعد أو لم تصل إلى Room بعد) تغطي صف قاعدة البيانات لتستجيب الواجهة فورًا
 * حتى مع النقرات السريعة المتتالية. [savedUpdatedAt] = null ما دامت الكتابة لم تنتهِ.
 */
data class ProgressOverlay(val row: AdhkarProgressEntity, val seq: Long, val savedUpdatedAt: Long? = null)

object AdhkarMerge {
    /** الصف الفعّال: القيمة المتفائلة ما لم تلحق بها قاعدة البيانات (أو تتجاوزها بتعديل أحدث من جهاز آخر). */
    fun effective(db: AdhkarProgressEntity?, overlay: ProgressOverlay?, date: String): AdhkarProgressEntity? {
        if (overlay == null || overlay.row.date != date) return db
        val saved = overlay.savedUpdatedAt ?: return overlay.row
        return if (db != null && db.updatedAt >= saved) db else overlay.row
    }
}

/** استئناف الموضع. */
object AdhkarResume {
    /**
     * رقم الصفحة الابتدائية في قسم عدد أذكاره n (الصفحة n = صفحة الخلاصة/الإتمام):
     *  - [startArg] ضمن النطاق ← هو.
     *  - القسم مكتمل اليوم ← صفحة الإتمام.
     *  - موضع محفوظ بتاريخ اليوم ← ذلك الذكر.
     *  - وإلا ← الأول.
     */
    fun startPage(startArg: Int, itemIds: List<String>, saved: AdhkarPosition?, today: String, allCompleted: Boolean): Int {
        val n = itemIds.size
        if (startArg in 0 until n) return startArg
        if (allCompleted && n > 0) return n
        if (saved != null && saved.date == today) {
            val idx = itemIds.indexOf(saved.itemId)
            if (idx >= 0) return idx
        }
        return 0
    }
}

/** اقتراح قسم حسب ساعة اليوم (للبطاقة "ابدأ" في الرئيسية عند عدم وجود موضع محفوظ). */
object AdhkarSuggestion {
    fun categoryForHour(hour: Int): String = when {
        hour in 4..12 -> AdhkarCategoryId.MORNING
        hour in 13..20 -> AdhkarCategoryId.EVENING
        else -> AdhkarCategoryId.SLEEP
    }
}

/** نص المشاركة/النسخ: نص الذكر كما في الكتالوج ثم مصدره (دون أي تعديل في النص). */
object AdhkarShareText {
    fun of(item: AdhkarItem): String = buildString {
        append(item.text.trim())
        if (item.source.isNotBlank()) {
            append("\n\nالمصدر: ")
            append(item.source.trim())
        }
    }
}

/** عبارة العدد بالعربية: ذكر واحد، ذكران، ٣ أذكار ... ١١ ذكرًا. */
object AdhkarLabels {
    fun adhkarCount(n: Int, indic: (Int) -> String): String = when {
        n == 1 -> "ذكر واحد"
        n == 2 -> "ذكران"
        n in 3..10 -> "${indic(n)} أذكار"
        else -> "${indic(n)} ذكرًا"
    }

    /** عدد مرات التكرار: مرة واحدة، مرتان، ٣ مرات ... ٣٣ مرة. */
    fun times(n: Int, indic: (Int) -> String): String = when {
        n == 1 -> "مرة واحدة"
        n == 2 -> "مرتان"
        n in 3..10 -> "${indic(n)} مرات"
        else -> "${indic(n)} مرة"
    }
}

/** تجميع الأذكار داخل القسم (item.group) لأغراض التنقل السريع. */
object AdhkarGroups {
    /** المجموعات بترتيب ظهورها مع رقم أول ذكر في كل منها؛ فارغة إن لم يكن هناك أكثر من مجموعة مسمّاة. */
    fun starts(items: List<AdhkarItem>): List<Pair<String, Int>> {
        val out = LinkedHashMap<String, Int>()
        items.forEachIndexed { i, it -> if (it.group.isNotBlank() && it.group !in out) out[it.group] = i }
        return if (out.size >= 2) out.map { it.key to it.value } else emptyList()
    }
}
