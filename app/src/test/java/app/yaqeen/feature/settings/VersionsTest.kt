package app.yaqeen.feature.settings

import app.yaqeen.feature.settings.updates.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test fun parsesLeadingV() {
        assertEquals(listOf(1, 2, 3), SemVer.parse("v1.2.3")?.numbers)
        assertEquals(listOf(1, 2), SemVer.parse("V1.2")?.numbers)
    }

    @Test fun rejectsGarbage() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("1.2.x"))
        assertNull(SemVer.parse("v"))
    }

    @Test fun numericOrderingNotLexical() {
        assertTrue(SemVer.isNewer("1.10.0", "1.9.9"))
        assertTrue(SemVer.isNewer("v0.2.0", "0.1.9"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("0.9.0", "1.0.0"))
    }

    @Test fun missingPatchEqualsZero() {
        assertFalse(SemVer.isNewer("1.2", "1.2.0"))
        assertTrue(SemVer.isNewer("1.2.1", "1.2"))
    }

    @Test fun debugSuffixOfInstalledIsIgnored() {
        assertFalse(SemVer.isNewer("v0.1.0", "0.1.0-debug"))
        assertTrue(SemVer.isNewer("v0.1.1", "0.1.0-debug"))
        assertNotNull(SemVer.parseInstalled("0.1.0-debug"))
        assertEquals(SemVer.parse("0.1.0"), SemVer.parseInstalled("0.1.0-debug"))
    }

    @Test fun preReleaseIsLowerThanRelease() {
        assertTrue(SemVer.isNewer("1.0.0", "1.0.0-beta.2"))
        assertFalse(SemVer.isNewer("1.0.0-beta.1", "1.0.0"))
        assertTrue(SemVer.isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
        assertTrue(SemVer.isNewer("1.0.0-rc.1", "1.0.0-beta.9"))
    }

    @Test fun buildMetadataIgnored() {
        assertFalse(SemVer.isNewer("1.0.0+build7", "1.0.0"))
    }

    @Test fun invalidRemoteIsNeverNewer() {
        assertFalse(SemVer.isNewer("nightly", "1.0.0"))
        assertFalse(SemVer.isNewer("2.0.0", "garbage"))
    }
}
