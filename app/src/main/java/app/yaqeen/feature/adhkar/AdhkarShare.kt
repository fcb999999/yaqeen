package app.yaqeen.feature.adhkar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.yaqeen.R
import app.yaqeen.data.adhkar.AdhkarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** ألوان بطاقة الصورة (تُؤخذ من MaterialTheme الحالي فتطابق مظهر التطبيق). كلها ARGB. */
class SharePalette(
    val background: Int,
    val card: Int,
    val border: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
)

/** نسخ الذكر ومشاركته نصًّا أو صورة. لا تعديل على النص إطلاقًا. */
object AdhkarShare {

    fun copy(context: Context, item: AdhkarItem): Boolean = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dhikr", AdhkarShareText.of(item)))
    }.isSuccess

    fun shareText(context: Context, item: AdhkarItem): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, AdhkarShareText.of(item))
        }
        return launchChooser(context, send)
    }

    /** يرسم بطاقة صورة للذكر في cacheDir/share ويفتح قائمة المشاركة. يعيد false عند الفشل. */
    suspend fun shareImage(context: Context, item: AdhkarItem, categoryTitle: String, palette: SharePalette): Boolean {
        val uri = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = render(context, item, categoryTitle, palette)
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "adhkar_${item.id}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        } ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launchChooser(context, send)
    }

    private fun launchChooser(context: Context, send: Intent): Boolean = runCatching {
        val chooser = Intent.createChooser(send, null)
        if (!context.isActivityContext()) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.isSuccess

    private fun Context.isActivityContext(): Boolean {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is android.app.Activity) return true
            c = c.baseContext
        }
        return false
    }

    private const val WIDTH = 1080
    private const val OUTER = 48
    private const val INNER = 64
    private const val MAX_HEIGHT = 3600

    private fun render(context: Context, item: AdhkarItem, categoryTitle: String, p: SharePalette): Bitmap {
        val contentWidth = WIDTH - 2 * OUTER - 2 * INNER
        val bodyFace = ResourcesCompat.getFont(context, if (item.quranRef.isNotBlank()) R.font.amiri_quran else R.font.amiri_regular)
        val uiFace = ResourcesCompat.getFont(context, R.font.plex_arabic_regular)

        fun layout(text: String, size: Float, color: Int, face: Typeface?, spacing: Float): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = face
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build()
        }

        val header = layout(categoryTitle, 40f, p.accent, uiFace, 1.2f)
        val source = item.source.trim().takeIf { it.isNotEmpty() }?.let { layout("المصدر: $it", 28f, p.muted, uiFace, 1.35f) }
        val footer = layout("يقين", 36f, p.accent, uiFace, 1.2f)
        val gap = 44

        var body: StaticLayout? = null
        var totalHeight = 0
        for (size in floatArrayOf(60f, 54f, 48f, 44f, 40f, 36f, 32f, 28f)) {
            val b = layout(item.text.trim(), size, p.text, bodyFace, 1.55f)
            val h = 2 * OUTER + 2 * INNER + header.height + gap + 4 + gap + b.height + gap +
                (source?.let { it.height + gap } ?: 0) + footer.height
            body = b
            totalHeight = h
            if (h <= MAX_HEIGHT) break
        }
        val bodyLayout = body!!

        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(p.background)
        val cardRect = RectF(OUTER.toFloat(), OUTER.toFloat(), (WIDTH - OUTER).toFloat(), (totalHeight - OUTER).toFloat())
        canvas.drawRoundRect(cardRect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.card })
        canvas.drawRoundRect(
            cardRect, 48f, 48f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.border; style = Paint.Style.STROKE; strokeWidth = 3f },
        )

        val x = (OUTER + INNER).toFloat()
        var y = (OUTER + INNER).toFloat()
        fun draw(l: StaticLayout) {
            canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
            y += l.height
        }
        draw(header)
        y += gap
        canvas.drawRoundRect(
            RectF(WIDTH / 2f - 60f, y, WIDTH / 2f + 60f, y + 4f), 2f, 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent },
        )
        y += 4 + gap
        draw(bodyLayout)
        y += gap
        if (source != null) { draw(source); y += gap }
        draw(footer)
        return bitmap
    }
}
