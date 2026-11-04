package app.yaqeen.feature.settings

import app.yaqeen.feature.settings.updates.HttpResult
import app.yaqeen.feature.settings.updates.ReleaseParser
import app.yaqeen.feature.settings.updates.SimpleHttp
import app.yaqeen.feature.settings.updates.UpdateCheckResult
import app.yaqeen.feature.settings.updates.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReleaseParserTest {
    private val hex = "a".repeat(64)
    private val hex2 = "b1".repeat(32)

    private fun release(
        tag: String = "v1.2.0",
        body: String = "## الجديد\\n- ميزة",
        assets: String = """[
            {"name":"app-release.apk","browser_download_url":"https://github.com/o/r/releases/download/v1.2.0/app-release.apk","size":1234},
            {"name":"app-release.apk.sha256","browser_download_url":"https://github.com/o/r/releases/download/v1.2.0/app-release.apk.sha256","size":100}
        ]""",
    ) = """{"tag_name":"$tag","name":"يقين $tag","body":"$body","published_at":"2026-11-04T10:00:00Z",
        "html_url":"https://github.com/o/r/releases/tag/$tag","assets":$assets}"""

    @Test fun parsesBasicRelease() {
        val r = ReleaseParser.parse(release())!!
        assertEquals("v1.2.0", r.tag)
        assertEquals("2026-11-04", r.publishedDate)
        assertEquals("app-release.apk", r.apk?.name)
        assertEquals(1234L, r.apk?.size)
        assertTrue(r.checksumAssetUrl!!.endsWith("app-release.apk.sha256"))
        assertNull(r.sha256)
    }

    @Test fun apkPreferenceAndNoApk() {
        val r = ReleaseParser.parse(release(assets = """[{"name":"notes.txt","browser_download_url":"https://x/y"}]"""))!!
        assertNull(r.apk)
        assertNull(r.checksumAssetUrl)
    }

    @Test fun digestFieldIsUsed() {
        val assets = """[{"name":"app.apk","browser_download_url":"https://x/app.apk","size":5,"digest":"sha256:$hex2"}]"""
        assertEquals(hex2, ReleaseParser.parse(release(assets = assets))!!.sha256)
    }

    @Test fun checksumFromBody() {
        val r = ReleaseParser.parse(release(body = "SHA-256: $hex", assets = "[]"))!!
        assertEquals(hex, r.sha256)
        assertEquals(hex2, ReleaseParser.sha256FromBody("\\n$hex2  app-release.apk"))
        assertNull(ReleaseParser.sha256FromBody("لا شيء هنا"))
    }

    @Test fun sha256TextFileParsing() {
        assertEquals(hex, ReleaseParser.parseSha256Text("$hex  app-release.apk\n"))
        assertEquals(hex2, ReleaseParser.parseSha256Text("$hex  other.apk\n$hex2  app-release.apk", "app-release.apk"))
        assertEquals(hex, ReleaseParser.parseSha256Text(hex.uppercase()))
        assertNull(ReleaseParser.parseSha256Text("not a hash"))
        assertNull(ReleaseParser.parseSha256Text("abc123"))
    }

    @Test fun invalidJsonOrMissingTag() {
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("""{"name":"x"}"""))
        assertNull(ReleaseParser.parse("[]"))
    }

    // ---- UpdateChecker مع HTTP مزيّف

    private class FakeHttp(private val res: () -> HttpResult) : SimpleHttp {
        var lastUrl: String? = null
        override fun get(url: String, headers: Map<String, String>): HttpResult { lastUrl = url; return res() }
    }

    @Test fun checkerDisabledWithoutRepo() {
        assertEquals(UpdateCheckResult.Disabled, UpdateChecker(FakeHttp { error("no call") }, "", "0.1.0").check())
        assertEquals(UpdateCheckResult.Disabled, UpdateChecker(FakeHttp { error("no call") }, "bad repo", "0.1.0").check())
    }

    @Test fun checkerFindsNewerVersion() {
        val http = FakeHttp { HttpResult(200, release()) }
        val res = UpdateChecker(http, "o/r", "0.1.0-debug").check()
        assertTrue(res is UpdateCheckResult.Available)
        assertEquals("https://api.github.com/repos/o/r/releases/latest", http.lastUrl)
    }

    @Test fun checkerUpToDate() {
        val res = UpdateChecker(FakeHttp { HttpResult(200, release(tag = "v1.2.0")) }, "o/r", "1.2.0").check()
        assertTrue(res is UpdateCheckResult.UpToDate)
    }

    @Test fun checkerHandlesErrors() {
        assertEquals(UpdateCheckResult.NoRelease, UpdateChecker(FakeHttp { HttpResult(404, "{}") }, "o/r", "1.0.0").check())
        assertEquals(UpdateCheckResult.Offline, UpdateChecker(FakeHttp { throw IOException("no network") }, "o/r", "1.0.0").check())
        val limited = UpdateChecker(
            FakeHttp { HttpResult(403, "{}", mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1700000000")) }, "o/r", "1.0.0",
        ).check()
        assertEquals(UpdateCheckResult.RateLimited(1700000000L), limited)
        assertTrue(UpdateChecker(FakeHttp { HttpResult(403, "{}") }, "o/r", "1.0.0").check() is UpdateCheckResult.Failed)
        assertTrue(UpdateChecker(FakeHttp { HttpResult(500, "") }, "o/r", "1.0.0").check() is UpdateCheckResult.Failed)
        assertTrue(UpdateChecker(FakeHttp { HttpResult(200, "garbage") }, "o/r", "1.0.0").check() is UpdateCheckResult.Failed)
        assertTrue(UpdateChecker(FakeHttp { HttpResult(200, release(tag = "nightly")) }, "o/r", "1.0.0").check() is UpdateCheckResult.Failed)
    }

    @Test fun resolveSha256PrefersInlineThenFetchesAsset() {
        val inline = ReleaseParser.parse(release(body = "SHA-256: $hex"))!!
        assertEquals(hex, UpdateChecker(FakeHttp { error("no") }, "o/r", "1.0.0").resolveSha256(inline))
        val withAsset = ReleaseParser.parse(release())!!
        val http = FakeHttp { HttpResult(200, "$hex2  app-release.apk") }
        assertEquals(hex2, UpdateChecker(http, "o/r", "1.0.0").resolveSha256(withAsset))
        assertTrue(http.lastUrl!!.startsWith("https://"))
        val failing = FakeHttp { HttpResult(500, "") }
        assertNull(UpdateChecker(failing, "o/r", "1.0.0").resolveSha256(withAsset))
    }

    @Test fun repoValidation() {
        assertTrue(UpdateChecker.isValidRepo("owner/name"))
        assertTrue(UpdateChecker.isValidRepo("my-org/yaqeen.app"))
        assertFalse(UpdateChecker.isValidRepo(""))
        assertFalse(UpdateChecker.isValidRepo("owner"))
        assertFalse(UpdateChecker.isValidRepo("a/b/c"))
        assertFalse(UpdateChecker.isValidRepo("../x"))
        assertNotNull(UpdateChecker.latestUrl("o/r"))
    }
}
