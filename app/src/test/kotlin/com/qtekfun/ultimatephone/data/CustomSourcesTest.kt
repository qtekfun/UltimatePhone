package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberPackFileTest {
    @Test
    fun keysAreTheDigitsOfAnE164Number() {
        assertEquals(34600123456L, NumberPackFile.toKey("+34600123456"))
        assertNull(NumberPackFile.toKey("+34 600"))
        assertNull(NumberPackFile.toKey(""))
        assertNull(NumberPackFile.toKey("+1234567890123456"))
    }

    @Test
    fun writtenNumbersAreFoundAndOthersAreNot() {
        val file = File(testDir("pack-lookup"), "p.nums")
        val writer = NumberPackFile.Writer()
        listOf("+34900000003", "+34900000001", "+4915112345678", "+34900000002", "+34900000001", "junk").forEach(writer::add)
        assertEquals(4, writer.writeTo(file))

        val pack = NumberPackFile.open(file, "My list")!!
        assertEquals(4, pack.count)
        assertEquals("My list", pack.lookup("+34900000002")?.label)
        assertNotNull(pack.lookup("+4915112345678"))
        assertNull(pack.lookup("+34900000004"))
        assertNull(pack.lookup("not a number"))
        assertTrue(pack.prefixRules().isEmpty())
    }

    @Test
    fun manyNumbersStaySortedAndSearchable() {
        val file = File(testDir("pack-many"), "p.nums")
        val writer = NumberPackFile.Writer()
        val numbers = (0 until 20_000).map { "+346${(it * 7919 % 100_000_000).toString().padStart(8, '0')}" }
        numbers.forEach(writer::add)
        writer.writeTo(file)
        val pack = NumberPackFile.open(file, null)!!
        assertTrue(numbers.all { pack.lookup(it) != null })
    }

    @Test
    fun anEmptyPackIsValid() {
        val file = File(testDir("pack-empty"), "p.nums")
        assertEquals(0, NumberPackFile.Writer().writeTo(file))
        assertNull(NumberPackFile.open(file, null)!!.lookup("+34900000001"))
    }

    @Test
    fun missingTruncatedOrForeignFilesAreRejected() {
        val dir = testDir("pack-bad")
        assertNull(NumberPackFile.open(File(dir, "missing.nums"), null))
        File(dir, "text.nums").writeText("this is not a pack at all")
        assertNull(NumberPackFile.open(File(dir, "text.nums"), null))
        val good = File(dir, "good.nums")
        NumberPackFile.Writer().apply { add("+34900000001") }.writeTo(good)
        File(dir, "cut.nums").writeBytes(good.readBytes().copyOf(good.length().toInt() - 3))
        assertNull(NumberPackFile.open(File(dir, "cut.nums"), null))
    }

    @Test
    fun rewritingReplacesThePackAtomically() {
        val file = File(testDir("pack-rewrite"), "p.nums")
        NumberPackFile.Writer().apply { add("+34900000001") }.writeTo(file)
        NumberPackFile.Writer().apply { add("+34900000002") }.writeTo(file)
        val pack = NumberPackFile.open(file, null)!!
        assertNull(pack.lookup("+34900000001"))
        assertNotNull(pack.lookup("+34900000002"))
        assertEquals("no leftovers", listOf("p.nums"), file.parentFile.list()!!.toList())
    }
}

class CustomSourceUpdaterTest {
    private val fetcher = FakeSourceFetcher()
    private val pauses = mutableListOf<Long>()
    private var now = 1_000L

    private fun updater(name: String, scope: TestScope, maxBytes: Long = 1_000_000): CustomSourceUpdater = CustomSourceUpdater(
        fetcher = fetcher,
        normalizer = LibPhoneNormalizer(),
        region = { "ES" },
        packsDir = testDir(name),
        clock = { now },
        pause = { pauses += it },
        io = StandardTestDispatcher(scope.testScheduler),
        maxBytes = maxBytes
    )

    private val source = CustomSource(id = "abc", name = "Mine", url = "https://example.org/list.txt", format = SourceFormat.TXT, level = SpamLevel.RULE)
    private val list = "# my list\n912345678\n+34 911 22 33 44\nnot a number\n912345678\n"

    @Test
    fun aFirstDownloadBecomesALocalPackWithTheSourceBookkeeping() = runTest {
        val updater = updater("src-first", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list, etag = "\"v1\"", lastModified = "Mon, 05 Oct 2026 10:00:00 GMT"))
        val result = updater.update(source)

        assertNull(result.lastError)
        assertEquals(2, result.entries)
        assertEquals("\"v1\"", result.etag)
        assertEquals("Mon, 05 Oct 2026 10:00:00 GMT", result.lastModified)
        assertEquals(1_000L, result.lastUpdateMillis)
        assertNotNull(result.contentSha256)
        val pack = NumberPackFile.open(updater.packFile(source), "Mine")!!
        assertNotNull(pack.lookup("+34912345678"))
        assertNotNull(pack.lookup("+34911223344"))
        assertNull(fetcher.requests.single().etag)
        assertEquals("no partial files are left", listOf("custom-abc.nums"), updater.packFile(source).parentFile.list()!!.toList())
    }

    @Test
    fun theNextRequestIsConditionalAndNotModifiedKeepsThePack() = runTest {
        val updater = updater("src-304", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list, etag = "\"v1\"", lastModified = "Mon, 05 Oct 2026 10:00:00 GMT"), FakeSourceFetcher.Reply.NotModified)
        val first = updater.update(source)
        now = 2_000L
        val second = updater.update(first)

        val request = fetcher.requests.last()
        assertEquals("\"v1\"", request.etag)
        assertEquals("Mon, 05 Oct 2026 10:00:00 GMT", request.lastModified)
        assertNull(second.lastError)
        assertEquals(2_000L, second.lastUpdateMillis)
        assertEquals(first.entries, second.entries)
        assertEquals(first.contentSha256, second.contentSha256)
        assertNotNull(NumberPackFile.open(updater.packFile(source), null)!!.lookup("+34912345678"))
    }

    @Test
    fun validatorsAreNotSentWhenThePackFileIsGone() = runTest {
        val updater = updater("src-gone", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list))
        updater.update(source.copy(etag = "\"stale\"", lastModified = "yesterday"))
        assertNull("without the pack a 304 would leave us with nothing", fetcher.requests.single().etag)
        assertNull(fetcher.requests.single().lastModified)
    }

    @Test
    fun theSameContentWithNewValidatorsDoesNotRewriteThePack() = runTest {
        val updater = updater("src-same", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list, etag = "\"v1\""), FakeSourceFetcher.Reply.Body(list, etag = "\"v2\""))
        val first = updater.update(source)
        val packFile = updater.packFile(source)
        val before = packFile.lastModified()
        packFile.setLastModified(before - 60_000)
        val marker = packFile.lastModified()
        val second = updater.update(first)

        assertEquals("\"v2\"", second.etag)
        assertEquals(first.contentSha256, second.contentSha256)
        assertEquals("pack untouched", marker, packFile.lastModified())
    }

    @Test
    fun changedContentReplacesThePack() = runTest {
        val updater = updater("src-changed", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list, etag = "\"v1\""), FakeSourceFetcher.Reply.Body("933333333\n", etag = "\"v2\""))
        val first = updater.update(source)
        val second = updater.update(first)
        val pack = NumberPackFile.open(updater.packFile(source), null)!!
        assertEquals(1, second.entries)
        assertNull(pack.lookup("+34912345678"))
        assertNotNull(pack.lookup("+34933333333"))
    }

    @Test
    fun aFileWithoutValidNumbersKeepsTheOldPack() = runTest {
        val updater = updater("src-empty", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list, etag = "\"v1\""), FakeSourceFetcher.Reply.Body("<html>login</html>\n"))
        val first = updater.update(source)
        val second = updater.update(first)
        assertEquals(UpdateErrors.NO_NUMBERS, second.lastError)
        assertEquals("\"v1\"", second.etag)
        assertNotNull(NumberPackFile.open(updater.packFile(source), null)!!.lookup("+34912345678"))
    }

    @Test
    fun permanentHttpErrorsAreNotRetried() = runTest {
        val updater = updater("src-404", this)
        fetcher.reply(FakeSourceFetcher.Reply.Http(404))
        val result = updater.update(source)
        assertEquals(UpdateErrors.http(404), result.lastError)
        assertEquals(1, fetcher.requests.size)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun serverErrorsAreRetriedWithGrowingPausesThenReported() = runTest {
        val updater = updater("src-503", this)
        fetcher.reply(FakeSourceFetcher.Reply.Http(503))
        val result = updater.update(source)
        assertEquals(UpdateErrors.http(503), result.lastError)
        assertEquals(3, fetcher.requests.size)
        assertEquals(listOf(2_000L, 4_000L), pauses)
    }

    @Test
    fun aTransientFailureThatClearsStillSucceeds() = runTest {
        val updater = updater("src-flaky", this)
        fetcher.reply(FakeSourceFetcher.Reply.Network, FakeSourceFetcher.Reply.Body(list))
        val result = updater.update(source)
        assertNull(result.lastError)
        assertEquals(2, result.entries)
        assertEquals(2, fetcher.requests.size)
    }

    @Test
    fun anOversizedSourceIsRejectedWithoutRetrying() = runTest {
        val updater = updater("src-big", this)
        fetcher.reply(FakeSourceFetcher.Reply.TooLarge)
        assertEquals(UpdateErrors.TOO_LARGE, updater.update(source).lastError)
        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun anInvalidUrlNeverTouchesTheNetwork() = runTest {
        val updater = updater("src-url", this)
        val result = updater.update(source.copy(url = "http://example.org/list.txt"))
        assertEquals(UpdateErrors.INVALID_URL, result.lastError)
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun csvAndJsonLinesSourcesAreParsedWithTheirFormat() = runTest {
        val updater = updater("src-formats", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body("number,label\n912345678,Spam A\n911223344,Spam B\n"))
        assertEquals(2, updater.update(source.copy(format = SourceFormat.CSV)).entries)
        fetcher.reply(FakeSourceFetcher.Reply.Body("{\"number\":\"+34933333333\"}\n{\"e164\":\"+34944444444\"}\n"))
        assertEquals(2, updater.update(source.copy(id = "j", format = SourceFormat.JSONL)).entries)
    }

    @Test
    fun testingADownloadReportsCountsAndKeepsNothing() = runTest {
        val updater = updater("src-test", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list))
        val result = updater.test("https://example.org/list.txt", SourceFormat.TXT) as SourceTestResult.Ok
        assertEquals(2, result.accepted)
        assertEquals(1, result.invalid)
        assertEquals(1, result.duplicates)
        assertTrue(File(System.getProperty("user.dir"), "build/test-work/src-test").list()!!.isEmpty())
    }

    @Test
    fun testingReportsFailuresInsteadOfThrowing() = runTest {
        val updater = updater("src-test-fail", this)
        fetcher.reply(FakeSourceFetcher.Reply.Http(404))
        assertEquals(SourceTestResult.Failed(UpdateErrors.http(404)), updater.test("https://example.org/x", SourceFormat.TXT))
        assertEquals(SourceTestResult.Failed(UpdateErrors.INVALID_URL), updater.test("ftp://example.org/x", SourceFormat.TXT))
        fetcher.reply(FakeSourceFetcher.Reply.Body("nothing here\n"))
        assertEquals(SourceTestResult.Failed(UpdateErrors.NO_NUMBERS), updater.test("https://example.org/x", SourceFormat.TXT))
    }

    @Test
    fun deletingASourceRemovesItsPackFile() = runTest {
        val updater = updater("src-delete", this)
        fetcher.reply(FakeSourceFetcher.Reply.Body(list))
        updater.update(source)
        assertTrue(updater.packFile(source).exists())
        updater.deletePack(source)
        assertFalse(updater.packFile(source).exists())
    }
}
