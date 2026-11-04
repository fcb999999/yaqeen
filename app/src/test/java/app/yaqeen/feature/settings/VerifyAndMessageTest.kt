package app.yaqeen.feature.settings

import app.yaqeen.feature.settings.updates.Hashing
import app.yaqeen.feature.settings.updates.MarkdownLight
import app.yaqeen.feature.settings.updates.MdBlock
import app.yaqeen.feature.settings.updates.UpdateMessage
import app.yaqeen.feature.settings.updates.VerifyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifyAndMessageTest {
    @Test fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashing.sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256Hex("abc".toByteArray()))
    }

    @Test fun sha256OfFile() {
        val f = File.createTempFile("yaqeen", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256Hex(f))
        } finally { f.delete() }
    }

    @Test fun hexEqualsIsCaseInsensitiveAndStrict() {
        val h = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(Hashing.hexEquals(h, h.uppercase()))
        assertTrue(Hashing.hexEquals(" $h\n", h))
        assertFalse(Hashing.hexEquals(h, h.dropLast(1) + "0"))
        assertFalse(Hashing.hexEquals(h, h.dropLast(1)))
    }

    @Test fun checksumDecision() {
        assertNull(VerifyRules.checksumMatches(null, "abc"))
        assertEquals(true, VerifyRules.checksumMatches("ABC", "abc"))
        assertEquals(false, VerifyRules.checksumMatches("abd", "abc"))
    }

    @Test fun signerMatching() {
        assertTrue(VerifyRules.signersMatch(setOf("AA", "bb"), setOf("aa", "BB")))
        assertFalse(VerifyRules.signersMatch(setOf("aa"), setOf("aa", "bb")))
        assertFalse(VerifyRules.signersMatch(setOf("aa"), setOf("cc")))
        assertFalse(VerifyRules.signersMatch(emptySet(), emptySet()))
        assertFalse(VerifyRules.signersMatch(setOf("aa"), emptySet()))
    }

    @Test fun fcmMessageParsing() {
        val m = UpdateMessage.parse(mapOf("type" to "update", "version" to "v1.4.0", "url" to "https://github.com/o/r/releases/tag/v1.4.0"))!!
        assertEquals("1.4.0", m.version)
        assertEquals("https://github.com/o/r/releases/tag/v1.4.0", m.url)
        assertNull(UpdateMessage.parse(mapOf("type" to "update", "version" to "1.4.0", "url" to "http://evil"))!!.url)
        assertNull(UpdateMessage.parse(mapOf("type" to "other", "version" to "1.4.0")))
        assertNull(UpdateMessage.parse(mapOf("type" to "update", "version" to "soon")))
        assertNull(UpdateMessage.parse(emptyMap()))
    }

    @Test fun markdownBlocks() {
        val md = "## الجديد\n- **ميزة** جديدة\n- تحسين [الأداء](https://x.y)\n\nفقرة عادية\nتكملة\n\n### إصلاحات\n1. خطأ `الذاكرة`\n---\n<b>html</b> نص"
        val blocks = MarkdownLight.parse(md)
        assertTrue(blocks[0] is MdBlock.Heading && (blocks[0] as MdBlock.Heading).level == 2)
        val b1 = blocks[1] as MdBlock.Bullet
        assertEquals(listOf("ميزة", " جديدة"), b1.spans.map { it.text })
        assertTrue(b1.spans[0].bold)
        assertEquals("تحسين الأداء", (blocks[2] as MdBlock.Bullet).spans.joinToString("") { it.text })
        assertEquals("فقرة عادية تكملة", (blocks[3] as MdBlock.Paragraph).spans.joinToString("") { it.text })
        assertTrue(blocks[4] is MdBlock.Heading)
        assertEquals("خطأ الذاكرة", (blocks[5] as MdBlock.Bullet).spans.joinToString("") { it.text })
        assertEquals("html نص", (blocks[6] as MdBlock.Paragraph).spans.joinToString("") { it.text })
        assertEquals(7, blocks.size)
    }

    @Test fun markdownEmptyInput() {
        assertTrue(MarkdownLight.parse("").isEmpty())
        assertTrue(MarkdownLight.parse("\n\n  \n").isEmpty())
    }
}
