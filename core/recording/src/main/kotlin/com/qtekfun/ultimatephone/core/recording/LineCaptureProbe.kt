package com.qtekfun.ultimatephone.core.recording

import android.content.Context
import java.io.File
import kotlinx.coroutines.delay

/**
 * Tries the call-line sources once, during a live call, writing to a scratch file that is deleted again. A source counts
 * as working only when it starts AND returns audio that is not silent. Nothing here can crash the app.
 */
class LineCaptureProbe(private val context: Context) {
    /** Results in the order of [CapabilityDetector.LINE_SOURCES]. Stops at the first working source. */
    suspend fun run(maxMillisPerSource: Long = DEFAULT_WINDOW_MS): List<ProbeResult> {
        val results = ArrayList<ProbeResult>()
        for (source in CapabilityDetector.LINE_SOURCES) {
            val result = probe(source, maxMillisPerSource)
            results += result
            if (result.started && result.peak > 0) break
        }
        return results
    }

    private suspend fun probe(source: CaptureSource, maxMillis: Long): ProbeResult {
        val file = File(context.cacheDir, "line-probe.m4a")
        val capture = MediaCapture(context)
        if (!capture.start(source, RecordingFormat.M4A_AAC, file)) {
            file.delete()
            return ProbeResult(source, started = false, peak = 0)
        }
        var peak = 0
        try {
            var waited = 0L
            while (waited < maxMillis && peak == 0) {
                delay(STEP_MS)
                waited += STEP_MS
                peak = capture.peak()
            }
        } finally {
            capture.stop()
            file.delete()
        }
        return ProbeResult(source, started = true, peak = peak)
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 2_500L
        const val STEP_MS = 250L
    }
}
