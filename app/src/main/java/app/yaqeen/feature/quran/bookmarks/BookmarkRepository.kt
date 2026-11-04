package app.yaqeen.feature.quran.bookmarks

import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.BookmarkCategoryEntity
import app.yaqeen.data.local.BookmarkEntity
import kotlinx.coroutines.flow.first

/** ألوان العلامات والتصنيفات (اختيار المستخدم؛ ARGB). */
object BookmarkPalette {
    val colors: List<Int> = listOf(
        0xFFD9534F.toInt(), // أحمر
        0xFFE59A3B.toInt(), // برتقالي
        0xFFC9A227.toInt(), // ذهبي
        0xFF4C9F70.toInt(), // أخضر
        0xFF3AA6B9.toInt(), // فيروزي
        0xFF4A78A8.toInt(), // أزرق
        0xFF8E6BBF.toInt(), // بنفسجي
        0xFFD16B9A.toInt(), // وردي
    )
    val default: Int get() = colors[5]

    /** هل اللون فاتح (لاختيار لون علامة الاختيار فوقه). */
    fun isLight(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170
    }
}

/** منطق خالص لتصفية العلامات والبحث فيها. */
object BookmarkFilter {
    /** null = الكل، "" = بلا تصنيف، وإلا معرّف التصنيف. */
    const val UNCATEGORIZED = ""

    fun matches(
        b: BookmarkEntity,
        category: String?,
        query: String,
        surahName: String,
        categoryName: String?,
    ): Boolean {
        if (category != null) {
            if (category == UNCATEGORIZED) { if (b.categoryId != null) return false } else if (b.categoryId != category) return false
        }
        val terms = ArabicText.normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val hay = ArabicText.normalize("${b.title} ${b.note} $surahName ${categoryName ?: ""} ${b.ayah}")
        return terms.all { hay.contains(it) }
    }

    fun deterministicId(surah: Int, ayah: Int) = "bm_${surah}_$ayah"
}

/** كل كتابات العلامات والتصنيفات (عبر c.writer دائمًا). */
class BookmarkRepository(private val c: AppContainer) {
    private val dao get() = c.db.bookmarks()
    private val catDao get() = c.db.bookmarkCategories()

    suspend fun find(surah: Int, ayah: Int): BookmarkEntity? = dao.findAt(surah, ayah)

    /** ينشئ علامة جديدة أو يحدّث الموجودة على الآية نفسها. */
    suspend fun upsert(
        surah: Int, ayah: Int, page: Int, title: String, note: String, color: Int, categoryId: String?,
    ): BookmarkEntity {
        val existing = dao.findAt(surah, ayah)
        val row = existing?.copy(title = title.trim(), note = note.trim(), color = color, categoryId = categoryId, deleted = false)
            ?: BookmarkEntity(
                id = BookmarkFilter.deterministicId(surah, ayah),
                surah = surah, ayah = ayah, page = page,
                title = title.trim(), note = note.trim(), color = color, categoryId = categoryId,
                createdAt = c.clock.nowMillis(),
            )
        return c.writer.save(dao, row)
    }

    suspend fun delete(b: BookmarkEntity) = c.writer.delete(dao, b)

    suspend fun createCategory(name: String, color: Int): BookmarkCategoryEntity {
        val id = "cat_" + app.yaqeen.core.newId()
        return c.writer.save(catDao, BookmarkCategoryEntity(id = id, name = name.trim(), color = color, createdAt = c.clock.nowMillis()))
    }

    suspend fun updateCategory(cat: BookmarkCategoryEntity, name: String, color: Int) {
        c.writer.save(catDao, cat.copy(name = name.trim(), color = color, deleted = false))
    }

    /** حذف التصنيف؛ العلامات التي كانت فيه تصبح بلا تصنيف (لا تُحذف). */
    suspend fun deleteCategory(cat: BookmarkCategoryEntity) {
        val moved = dao.observeByCategory(cat.id).first()
        if (moved.isNotEmpty()) c.writer.saveAll(dao, moved.map { it.copy(categoryId = null) })
        c.writer.delete(catDao, cat)
    }
}
