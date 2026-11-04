package app.yaqeen.feature.prayer.tracking

import app.yaqeen.data.local.CustomSunnahEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.Sunnah

/** عنصر في قائمة السنن: راتبة افتراضية أو مخصصة (id = "custom:<id>"). */
data class SunnahItem(
    val id: String,
    val name: String,
    val rakaat: Int,
    val after: Prayer?,
    val isCustom: Boolean,
    val archived: Boolean = false,
) {
    /** معرّف كيان [CustomSunnahEntity] للمخصصة، وإلا null. */
    val customEntityId: String? get() = if (isCustom) id.removePrefix(Sunnah.CUSTOM_PREFIX) else null
}

/** سطر في قائمة اليوم: السنة وهل أُنجزت وعدد ركعاتها المسجّلة. */
data class ChecklistEntry(val item: SunnahItem, val done: Boolean, val rakaat: Int)

data class RawatibDay(val entries: List<ChecklistEntry>) {
    val doneCount: Int get() = entries.count { it.done }
    val totalCount: Int get() = entries.size
    val totalRakaat: Int get() = entries.filter { it.done }.sumOf { it.rakaat }
}

object SunnahModel {
    const val MAX_NAME_LENGTH = 40
    const val MIN_RAKAAT = 1
    const val MAX_RAKAAT = 30

    val DEFAULTS: List<SunnahItem> = Sunnah.entries.map { SunnahItem(it.name, it.ar, it.rakaat, it.after, isCustom = false) }

    fun customId(entityId: String) = "${Sunnah.CUSTOM_PREFIX}$entityId"

    fun fromEntity(e: CustomSunnahEntity, archived: Boolean) =
        SunnahItem(customId(e.id), e.name, e.rakaat, null, isCustom = true, archived = archived)

    /** تنظيف الاسم: إزالة الفراغات الزائدة. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * يتحقق من اسم وعدد ركعات سنة مخصصة. يعيد رسالة خطأ عربية أو null إن كانت صالحة.
     * [existingNames]: أسماء السنن الأخرى (للمنع من التكرار)، تُستثنى منها السنة المعدَّلة نفسها.
     */
    fun validate(rawName: String, rakaat: Int, existingNames: Collection<String>): String? {
        val name = cleanName(rawName)
        return when {
            name.isEmpty() -> "اكتب اسم السنة"
            name.length > MAX_NAME_LENGTH -> "الاسم طويل؛ الحد الأقصى ${app.yaqeen.core.ArabicText.indic(MAX_NAME_LENGTH)} حرفًا"
            rakaat !in MIN_RAKAAT..MAX_RAKAAT ->
                "عدد الركعات يجب أن يكون بين ${app.yaqeen.core.ArabicText.indic(MIN_RAKAAT)} و${app.yaqeen.core.ArabicText.indic(MAX_RAKAAT)}"
            existingNames.any { it.trim().equals(name, ignoreCase = true) } -> "هذه السنة موجودة بالفعل"
            else -> null
        }
    }

    /** ترميز/فك ترميز مجموعة معرّفات السنن المؤرشفة المخزّنة في إعداد نصي. */
    fun encodeIds(ids: Collection<String>): String = ids.filter { it.isNotBlank() }.distinct().sorted().joinToString(",")
    fun decodeIds(raw: String): Set<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * يبني قائمة اليوم: الخمس الافتراضية ثم المخصصة غير المؤرشفة؛ والمؤرشفة تظهر فقط إن كان لها إنجاز في ذلك اليوم
     * (حتى لا يضيع ما سُجّل). [customs] غير المحذوفة، [logs] سجلات ذلك اليوم.
     */
    fun buildChecklist(
        customs: List<CustomSunnahEntity>,
        archivedIds: Set<String>,
        logs: List<RawatibLogEntity>,
    ): RawatibDay {
        val doneById = logs.filter { !it.deleted && it.done }.associateBy { it.sunnahId }
        val entries = ArrayList<ChecklistEntry>()
        for (d in DEFAULTS) {
            val row = doneById[d.id]
            entries += ChecklistEntry(d, row != null, row?.let { rakaatOf(it) } ?: d.rakaat)
        }
        for (e in customs.filter { !it.deleted }.sortedBy { it.createdAt }) {
            val item = fromEntity(e, archived = e.id in archivedIds || customId(e.id) in archivedIds)
            val row = doneById[item.id]
            if (item.archived && row == null) continue
            entries += ChecklistEntry(item, row != null, row?.let { rakaatOf(it) } ?: item.rakaat)
        }
        return RawatibDay(entries)
    }
}
