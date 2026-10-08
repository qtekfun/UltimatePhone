package com.qtekfun.ultimatephone.core.recording

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesAndFormattersTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val at = LocalDateTime.of(2026, 10, 8, 14, 32, 10).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun name(
        incoming: Boolean = true,
        number: String? = "+34 612 345 678",
        contact: String? = null,
        include: Boolean = false,
        format: RecordingFormat = RecordingFormat.M4A_AAC
    ) = RecordingNames.build(at, incoming, number, contact, include, format, utc)

    @Test
    fun `name has date direction and masked id only`() {
        assertEquals("call_20261008-143210_in_xxx678.m4a", name())
        assertEquals("call_20261008-143210_out_xxx678.ogg", name(incoming = false, format = RecordingFormat.OGG_OPUS))
    }

    @Test
    fun `a full number never appears in the name`() {
        val result = name(number = "+34 612 345 678")
        assertFalse(result.contains("612345"))
        assertFalse(result.contains("34612"))
    }

    @Test
    fun `hidden number gives unknown`() {
        assertEquals("call_20261008-143210_in_unknown.m4a", name(number = null))
        assertEquals("call_20261008-143210_in_unknown.m4a", name(number = "private"))
    }

    @Test
    fun `contact name is used only when the user allows it`() {
        assertEquals("call_20261008-143210_in_xxx678.m4a", name(contact = "Ana Pérez", include = false))
        assertEquals("call_20261008-143210_in_Ana-Pérez.m4a", name(contact = "Ana Pérez", include = true))
    }

    @Test
    fun `contact name falls back to the masked id when nothing usable is left`() {
        assertEquals("call_20261008-143210_in_xxx678.m4a", name(contact = "///", include = true))
        assertEquals("call_20261008-143210_in_xxx678.m4a", name(contact = null, include = true))
    }

    @Test
    fun `sanitize removes path and reserved characters`() {
        assertEquals("a-b-c", RecordingNames.sanitize("a/b\\c"))
        assertEquals("x", RecordingNames.sanitize("  x  "))
        assertEquals("", RecordingNames.sanitize("../.."))
        assertEquals("Mom-Dad", RecordingNames.sanitize("Mom & Dad"))
        assertEquals(40, RecordingNames.sanitize("a".repeat(100)).length)
    }

    @Test
    fun `parse reads back what build wrote`() {
        val parsed = RecordingNames.parse(name(incoming = false))!!
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 32, 10), parsed.dateTime)
        assertFalse(parsed.incoming)
        assertEquals("xxx678", parsed.label)
    }

    @Test
    fun `parse accepts the suffix a provider adds for duplicates`() {
        val parsed = RecordingNames.parse("call_20261008-143210_in_Ana-Pérez (1).ogg")!!
        assertEquals("Ana-Pérez", parsed.label)
        assertTrue(parsed.incoming)
    }

    @Test
    fun `parse rejects names that are not ours`() {
        assertNull(RecordingNames.parse("holiday.m4a"))
        assertNull(RecordingNames.parse("call_20261308-143210_in_x.m4a"))
        assertNull(RecordingNames.parse("call_20261008-143210_in_x.mp3"))
    }

    @Test
    fun `renamed keeps the extension and strips reserved characters`() {
        assertEquals("Interview.m4a", RecordingNames.renamed("Interview", "call_x.m4a"))
        assertEquals("Interview.m4a", RecordingNames.renamed("Interview.m4a", "call_x.m4a"))
        assertEquals("ab.ogg", RecordingNames.renamed("a/b", "x.ogg"))
        assertNull(RecordingNames.renamed("  ", "x.ogg"))
        assertNull(RecordingNames.renamed("///", "x.ogg"))
    }

    @Test
    fun `extension of`() {
        assertEquals(".m4a", RecordingNames.extensionOf("a.m4a"))
        assertEquals("", RecordingNames.extensionOf("noext"))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0:00", RecordingFormatters.duration(0))
        assertEquals("0:00", RecordingFormatters.duration(999))
        assertEquals("0:59", RecordingFormatters.duration(59_999))
        assertEquals("1:05", RecordingFormatters.duration(65_000))
        assertEquals("59:59", RecordingFormatters.duration(3_599_000))
        assertEquals("1:00:00", RecordingFormatters.duration(3_600_000))
        assertEquals("2:03:04", RecordingFormatters.duration(7_384_000))
        assertEquals("0:00", RecordingFormatters.duration(-5))
    }

    @Test
    fun `size formatting`() {
        assertEquals("0 B", RecordingFormatters.size(0, Locale.US))
        assertEquals("1023 B", RecordingFormatters.size(1023, Locale.US))
        assertEquals("1.0 KB", RecordingFormatters.size(1024, Locale.US))
        assertEquals("1.5 KB", RecordingFormatters.size(1536, Locale.US))
        assertEquals("1.0 MB", RecordingFormatters.size(1024L * 1024, Locale.US))
        assertEquals("2.5 MB", RecordingFormatters.size(2_621_440, Locale.US))
        assertEquals("1.0 GB", RecordingFormatters.size(1024L * 1024 * 1024, Locale.US))
        assertEquals("2048.0 GB", RecordingFormatters.size(2048L * 1024 * 1024 * 1024, Locale.US))
        assertEquals("0 B", RecordingFormatters.size(-1, Locale.US))
    }

    @Test
    fun `size uses the locale decimal separator`() {
        assertEquals("1,5 KB", RecordingFormatters.size(1536, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `storage recognises recording names`() {
        assertTrue(RecordingStorage.isRecordingName("a.M4A"))
        assertTrue(RecordingStorage.isRecordingName("a.ogg"))
        assertFalse(RecordingStorage.isRecordingName("a.txt"))
        assertEquals("audio/ogg", RecordingStorage.mimeOf("a.ogg"))
        assertEquals("audio/mp4", RecordingStorage.mimeOf("a.m4a"))
    }
}
