package app.yaqeen.data.adhkar

import android.content.Context
import app.yaqeen.core.ArabicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AdhkarItem(
    val id: String,
    val text: String,
    val count: Int = 1,
    /** العزو (كتاب الحديث). */
    val source: String = "",
    /** الفضل: يُعرض فقط إذا كان نص حديث ثابت في الباب. */
    val fadl: String = "",
    /** تعليمات إضافية (مثل: يقال بعد الفجر). */
    val note: String = "",
    /** تجميع داخل القسم (للأذكار المتنوعة). */
    val group: String = "",
    /** إن كان الذكر آيات قرآنية: مرجعها (مثل 2:255). */
    val quranRef: String = "",
)

@Serializable
data class AdhkarCategory(val id: String, val title: String, val items: List<AdhkarItem>)

@Serializable
private data class AdhkarFile(val format: Int, val notice: String = "", val categories: List<AdhkarCategory>)

class AdhkarCatalog(val notice: String, val categories: List<AdhkarCategory>) {
    private val byItem: Map<String, Pair<AdhkarCategory, AdhkarItem>> =
        categories.flatMap { c -> c.items.map { it.id to (c to it) } }.toMap()

    fun category(id: String): AdhkarCategory? = categories.firstOrNull { it.id == id }
    fun item(itemId: String): AdhkarItem? = byItem[itemId]?.second
    fun categoryOf(itemId: String): AdhkarCategory? = byItem[itemId]?.first

    /** بحث نصي في كل الأذكار (بعد تطبيع التشكيل). */
    fun search(query: String, limit: Int = 100): List<Pair<AdhkarCategory, AdhkarItem>> {
        val terms = ArabicText.normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return categories.flatMap { c -> c.items.map { c to it } }
            .filter { (_, it) ->
                val hay = ArabicText.normalize(it.text + " " + it.fadl + " " + it.group + " " + it.note)
                terms.all { t -> hay.contains(t) }
            }
            .take(limit)
    }
}

object AdhkarCatalogLoader {
    const val ASSET_PATH = "adhkar/adhkar.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: AdhkarCatalog? = null

    suspend fun load(context: Context): AdhkarCatalog {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                val text = context.applicationContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(text)
            }.also { cached = it }
        }
    }

    fun parse(jsonText: String): AdhkarCatalog {
        val f = json.decodeFromString<AdhkarFile>(jsonText)
        return AdhkarCatalog(f.notice, f.categories)
    }
}
