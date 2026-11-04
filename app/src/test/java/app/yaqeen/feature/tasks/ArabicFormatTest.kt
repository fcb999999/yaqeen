package app.yaqeen.feature.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArabicFormatTest {
    @Test fun time12() {
        assertEquals("٥:٣٠ م", ArabicFormat.time12("17:30"))
        assertEquals("١٢:٠٠ ص", ArabicFormat.time12("00:00"))
        assertEquals("١٢:٠٥ م", ArabicFormat.time12("12:05"))
        assertEquals("٦:٠٠ ص", ArabicFormat.time12("6:00"))
        assertNull(ArabicFormat.time12("25:00"))
        assertNull(ArabicFormat.time12(null))
        assertNull(ArabicFormat.time12("abc"))
    }

    @Test fun parseAndFormat() {
        assertEquals("07:05", ArabicFormat.formatHHmm(7, 5))
        assertNull(ArabicFormat.parseTime("12:60"))
        assertNull(ArabicFormat.parseDate("2026-13-01"))
    }

    @Test fun countNouns() {
        assertEquals("صفحة واحدة", ArabicFormat.pages(1))
        assertEquals("صفحتان", ArabicFormat.pages(2))
        assertEquals("٥ صفحات", ArabicFormat.pages(5))
        assertEquals("١١ صفحة", ArabicFormat.pages(11))
        assertEquals("٣ أيام", ArabicFormat.days(3))
        assertEquals("١٥ يومًا", ArabicFormat.days(15))
        assertEquals("جزء واحد", ArabicFormat.juz(1))
    }

    @Test fun relativeDays() {
        assertEquals("اليوم", ArabicFormat.relativeDays(0))
        assertEquals("غدًا", ArabicFormat.relativeDays(1))
        assertEquals("بعد يومين", ArabicFormat.relativeDays(2))
        assertEquals("بعد ٣ أيام", ArabicFormat.relativeDays(3))
        assertEquals("بعد ١٠ أيام", ArabicFormat.relativeDays(10))
        assertEquals("بعد ١١ يومًا", ArabicFormat.relativeDays(11))
        assertEquals("أمس", ArabicFormat.relativeDays(-1))
        assertEquals("قبل ٥ أيام", ArabicFormat.relativeDays(-5))
        assertEquals("قبل ٢٠ يومًا", ArabicFormat.relativeDays(-20))
    }
}
