package app.yaqeen.feature.quran.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.yaqeen.R
import app.yaqeen.ui.theme.YaqeenColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** مشاركة آية كنص أو كصورة بطاقة بهوية التطبيق. */
object AyahShare {
    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "مشاركة الآية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** يرسم بطاقة الآية في cacheDir/share ويشاركها عبر FileProvider (ACTION_SEND image/png). */
    suspend fun shareImage(context: Context, ayahText: String, surahNameAr: String, surahNumber: Int, ayahNumber: Int) {
        val file = withContext(Dispatchers.Default) {
            AyahCardRenderer.render(context, ayahText, surahNameAr, surahNumber, ayahNumber)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("ayah", uri)
        }
        context.startActivity(
            Intent.createChooser(send, "مشاركة الآية كصورة")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

/**
 * يرسم بطاقة الآية بـ Canvas: خلفية عاجية، إطار مزدوج ذهبي/أزرق ملكي، نص الآية بخط الأميري القرآني
 * (كما هو دون تعديل)، ثم المرجع واسم التطبيق. لا تعتمد على Compose فتعمل خارج الواجهة.
 */
object AyahCardRenderer {
    private const val WIDTH = 1080
    private const val PAD = 110f
    private const val MAX_TEXT_HEIGHT = 1500

    fun render(context: Context, ayahText: String, surahNameAr: String, surahNumber: Int, ayahNumber: Int): File {
        val quranFont = ResourcesCompat.getFont(context, R.font.kfgqpc_hafs) ?: Typeface.DEFAULT
        val uiFont = ResourcesCompat.getFont(context, R.font.plex_arabic_medium) ?: Typeface.DEFAULT_BOLD

        val royal = YaqeenColors.RoyalBlue.toArgb()
        val gold = YaqeenColors.Gold.toArgb()
        val ivory = YaqeenColors.Ivory.toArgb()
        val innerW = (WIDTH - 2 * PAD).toInt()

        // اختيار أكبر حجم خط يتسع فيه النص
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = royal; typeface = quranFont }
        var layout: StaticLayout? = null
        var size = 64f
        while (size >= 30f) {
            textPaint.textSize = size
            layout = buildLayout(ayahText, textPaint, innerW)
            if (layout.height <= MAX_TEXT_HEIGHT) break
            size -= 2f
        }
        val textLayout = layout ?: buildLayout(ayahText, textPaint.apply { textSize = 30f }, innerW)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold; typeface = quranFont; textSize = 50f; textAlign = Paint.Align.CENTER
        }
        val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = royal; typeface = uiFont; textSize = 42f; textAlign = Paint.Align.CENTER
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold; typeface = uiFont; textSize = 36f; textAlign = Paint.Align.CENTER
        }

        val headerH = 230f
        val gap = 70f
        val footerH = 250f
        val height = maxOf(WIDTH.toFloat(), headerH + textLayout.height + gap + footerH).toInt()

        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            canvas.drawColor(ivory)

            // الإطار المزدوج
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            frame.color = gold; frame.strokeWidth = 7f
            canvas.drawRoundRect(36f, 36f, WIDTH - 36f, height - 36f, 28f, 28f, frame)
            frame.color = royal; frame.strokeWidth = 2.5f
            canvas.drawRoundRect(60f, 60f, WIDTH - 60f, height - 60f, 18f, 18f, frame)

            // زخارف الزوايا (معينات ذهبية)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = gold }
            for ((px, py) in listOf(60f to 60f, WIDTH - 60f to 60f, 60f to height - 60f, WIDTH - 60f to height - 60f)) {
                diamond(canvas, px, py, 15f, fill)
            }

            // العنوان: اسم السورة
            val cx = WIDTH / 2f
            canvas.drawText("سُورَةُ $surahNameAr", cx, 150f, titlePaint)
            divider(canvas, cx, 190f, 220f, gold)

            // نص الآية
            canvas.save()
            canvas.translate(PAD, headerH)
            textLayout.draw(canvas)
            canvas.restore()

            // المرجع واسم التطبيق
            val refY = headerH + textLayout.height + gap
            divider(canvas, cx, refY - 20f, 220f, gold)
            canvas.drawText(QuranText.reference(surahNameAr, ayahNumber), cx, refY + 55f, refPaint)
            canvas.drawText("يقين", cx, refY + 140f, brandPaint)

            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "ayah_${surahNumber}_$ayahNumber.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return out
        } finally {
            bmp.recycle()
        }
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.55f)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .setIncludePad(false)
            .build()

    private fun diamond(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val p = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
        }
        canvas.drawPath(p, paint)
    }

    private fun divider(canvas: Canvas, cx: Float, y: Float, half: Float, color: Int) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 3f }
        canvas.drawLine(cx - half, y, cx - 24f, y, line)
        canvas.drawLine(cx + 24f, y, cx + half, y, line)
        diamond(canvas, cx, y, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color })
    }
}
