package app.yaqeen.feature.prayer.times

import android.content.Context
import app.yaqeen.core.ArabicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** مدينة من القائمة المدمجة (assets/cities/cities.json). */
@Serializable
data class City(
    val name: String,
    val en: String = "",
    val country: String,
    val lat: Double,
    val lon: Double,
    val zone: String,
)

object CityRepository {
    private const val ASSET = "cities/cities.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cache: List<City>? = null

    fun parse(text: String): List<City> = json.decodeFromString<List<City>>(text)

    /** يحمّل القائمة من الأصول مرة واحدة (بلا إنترنت). */
    suspend fun load(context: Context): List<City> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val text = context.applicationContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(text).also { cache = it }
        }
    }

    private fun norm(s: String): String = ArabicText.normalize(s).lowercase()

    /**
     * بحث غير حساس للهمزات والتاء المربوطة والتشكيل، في الاسم العربي والدولة والاسم اللاتيني.
     * كل كلمة في الاستعلام يجب أن توجد. النتائج التي يبدأ اسمها بالاستعلام أولًا.
     */
    fun search(all: List<City>, query: String): List<City> {
        val q = norm(query)
        if (q.isEmpty()) return all
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        data class Hit(val city: City, val rank: Int)
        val hits = all.mapNotNull { c ->
            val name = norm(c.name)
            val hay = "$name ${norm(c.country)} ${c.en.lowercase()}"
            if (!tokens.all { hay.contains(it) }) return@mapNotNull null
            val rank = when {
                name == q -> 0
                name.startsWith(q) || c.en.lowercase().startsWith(q) -> 1
                tokens.all { name.contains(it) } -> 2
                else -> 3
            }
            Hit(c, rank)
        }
        return hits.sortedBy { it.rank }.map { it.city }
    }

    /** أقرب مدينة ضمن [maxKm] من الإحداثيات، أو null. */
    fun nearest(all: List<City>, lat: Double, lon: Double, maxKm: Double = 30.0): City? =
        all.minByOrNull { distanceKm(lat, lon, it.lat, it.lon) }
            ?.takeIf { distanceKm(lat, lon, it.lat, it.lon) <= maxKm }

    /** المسافة الدائرية الكبرى (هافرساين) بالكيلومتر. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }
}
