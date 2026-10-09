package com.qtekfun.ultimatephone.crash

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test
    fun phoneNumbersAreMaskedInTheReport() {
        val text = CrashReporter.sanitize("Failed for +34 612 34 56 78 and (91) 123-45-67, also 600123456")
        assertFalse(text.contains("612"))
        assertFalse(text.contains("123-45"))
        assertFalse(text.contains("600123456"))
        assertTrue(text.contains("[number]"))
    }

    @Test
    fun shortNumbersLikeLineNumbersAndVersionsSurvive() {
        val text = CrashReporter.sanitize("at Foo.kt:123 in 0.1.0 sdk 36 code 10099")
        assertTrue(text.contains("Foo.kt:123"))
        assertTrue(text.contains("0.1.0"))
        assertTrue(text.contains("10099"))
    }

    @Test
    fun theReportHasTheDeviceAndTheTrace() {
        val run = CrashReporter.Run("0.1.0", 10099, "16", 36, "realme RMX5210", "main")
        val report = CrashReporter.format(
            run,
            "java.lang.SecurityException: Permission Denial\n\tat Foo.bar(Foo.kt:12)",
            Instant.parse("2026-10-09T06:43:07Z")
        )
        assertTrue("report was: $report", report.startsWith("UltimatePhone 0.1.0 (10099)"))
        assertTrue(report.contains("Android 16 (SDK 36)"))
        assertTrue(report.contains("Device: realme RMX5210"))
        assertTrue(report.contains("SecurityException"))
        assertTrue(report.contains("2026-10-09T06:43:07Z"))
        val masked = CrashReporter.format(run, "boom for +34 612 34 56 78", Instant.EPOCH)
        assertFalse(masked.contains("612"))
    }
}
