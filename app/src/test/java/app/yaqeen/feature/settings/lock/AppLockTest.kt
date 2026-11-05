package app.yaqeen.feature.settings.lock

import app.yaqeen.feature.settings.updates.UpdatePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun pinValidation() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin("١٢٣٤")) // يُحوَّل إلى لاتيني قبل الحفظ
    }

    @Test
    fun hashVerifiesOnlyTheRightPin() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("2580", salt)
        assertTrue(PinHasher.verify("2580", salt, h))
        assertFalse(PinHasher.verify("2581", salt, h))
        assertFalse(h.contains("2580"))
        // ملح مختلف = تجزئة مختلفة للرمز نفسه
        assertNotEquals(h, PinHasher.hash("2580", PinHasher.newSalt()))
        assertEquals(h, PinHasher.hash("2580", salt))
    }

    @Test
    fun locksOnlyAfterGraceAndDelay() {
        val off = LockConfig(enabled = false)
        assertFalse(AppLock.shouldLock(off, 10 * 60_000L))

        val now = LockConfig(enabled = true, delaySeconds = 0)
        assertFalse(AppLock.shouldLock(now, 1_000L)) // نافذة مشاركة سريعة
        assertTrue(AppLock.shouldLock(now, AppLock.MIN_GRACE_MS))

        val minute = LockConfig(enabled = true, delaySeconds = 60)
        assertFalse(AppLock.shouldLock(minute, 59_000L))
        assertTrue(AppLock.shouldLock(minute, 60_000L))
    }

    @Test
    fun updatePromptThrottleAndSnooze() {
        val h = 60 * 60 * 1000L
        assertTrue(UpdatePrompt.isDue(nowMs = 10 * h, lastCheckMs = 0))
        assertFalse(UpdatePrompt.isDue(nowMs = 10 * h, lastCheckMs = 9 * h))
        assertTrue(UpdatePrompt.isDue(nowMs = 10 * h, lastCheckMs = 7 * h))
        assertTrue(UpdatePrompt.isDue(nowMs = 5 * h, lastCheckMs = 9 * h)) // ساعة الجهاز رجعت للوراء

        assertTrue(UpdatePrompt.isSnoozed("v0.1.6", "v0.1.6", snoozeUntilMs = 20 * h, nowMs = 10 * h))
        assertTrue(UpdatePrompt.isSnoozed("0.1.6", "v0.1.6", snoozeUntilMs = 20 * h, nowMs = 10 * h))
        assertFalse(UpdatePrompt.isSnoozed("v0.1.6", "v0.1.6", snoozeUntilMs = 20 * h, nowMs = 21 * h))
        // إصدار أحدث من المؤجَّل يظهر فورًا
        assertFalse(UpdatePrompt.isSnoozed("v0.1.7", "v0.1.6", snoozeUntilMs = 20 * h, nowMs = 10 * h))
        assertFalse(UpdatePrompt.isSnoozed("v0.1.6", null, snoozeUntilMs = 0, nowMs = 10 * h))
    }
}
