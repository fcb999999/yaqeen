package app.yaqeen.core

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** مصدر الوقت (قابل للاستبدال في الاختبارات). */
interface Clock {
    fun nowMillis(): Long
    fun zone(): ZoneId
    fun today(): LocalDate = java.time.Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()
}

object SystemClockImpl : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun newId(): String = UUID.randomUUID().toString()

/** مفتاح اليوم المستخدم في كل السجلات: التاريخ الميلادي بصيغة ISO (yyyy-MM-dd). */
fun LocalDate.dayKey(): String = toString()

fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

/**
 * ساعة هجينة: تضمن أن كل تعديل على هذا الجهاز يحمل رقمًا أكبر تمامًا من سابقه
 * حتى لو تأخرت ساعة النظام أو تكررت الملّي ثانية. تُستعمل في حقل updatedAt للمزامنة.
 */
class HybridClock(private val clock: Clock = SystemClockImpl, initial: Long = 0L) {
    private val last = AtomicLong(initial)

    /** يرفع الحد الأدنى بعد رؤية قيمة من جهاز آخر (لتفادي أن يكتب هذا الجهاز قيمة أقدم مما رآه). */
    fun observe(remoteMillis: Long) {
        while (true) {
            val cur = last.get()
            if (remoteMillis <= cur || last.compareAndSet(cur, remoteMillis)) return
        }
    }

    fun next(): Long {
        while (true) {
            val cur = last.get()
            val candidate = maxOf(clock.nowMillis(), cur + 1)
            if (last.compareAndSet(cur, candidate)) return candidate
        }
    }
}
