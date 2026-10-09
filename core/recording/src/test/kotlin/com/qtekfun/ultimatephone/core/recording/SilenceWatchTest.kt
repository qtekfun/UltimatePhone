package com.qtekfun.ultimatephone.core.recording

import com.qtekfun.ultimatephone.core.recording.SilenceWatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceWatchTest {
    private fun watch() = SilenceWatch(SilenceWatch.MICROPHONES, silenceMillis = 1_000).also { it.startedOn(CaptureSource.MICROPHONE, 0) }

    @Test
    fun soundKeepsTheSourceForever() {
        val w = watch()
        assertEquals(Verdict.Keep, w.onPeak(120, 500))
        assertEquals(Verdict.Keep, w.onPeak(0, 60_000))
    }

    @Test
    fun briefSilenceIsKept() {
        assertEquals(Verdict.Keep, watch().onPeak(0, 900))
    }

    @Test
    fun silenceSwapsToTheNextSourceThenGivesUp() {
        val w = watch()
        assertEquals(Verdict.Swap(CaptureSource.VOICE_RECOGNITION), w.onPeak(0, 1_000))
        assertEquals(Verdict.Keep, w.onPeak(0, 1_500))
        assertEquals(Verdict.Swap(CaptureSource.UNPROCESSED), w.onPeak(0, 2_000))
        assertEquals(Verdict.Swap(CaptureSource.CAMCORDER), w.onPeak(0, 3_000))
        assertEquals(Verdict.GiveUp, w.onPeak(0, 4_000))
    }

    @Test
    fun startingOnALaterSourceContinuesFromThere() {
        val w = SilenceWatch(SilenceWatch.MICROPHONES, 1_000)
        w.startedOn(CaptureSource.UNPROCESSED, 0)
        assertEquals(Verdict.Swap(CaptureSource.CAMCORDER), w.onPeak(0, 1_000))
    }
}
