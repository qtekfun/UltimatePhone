package com.qtekfun.ultimatephone.core.recording

/**
 * Some phones start a source without error and then deliver only silence during a call. This decides, from the peak
 * amplitudes read while recording, when the source must be swapped for the next one in [order]; it is pure, so it is tested
 * without a device.
 */
class SilenceWatch(private val order: List<CaptureSource>, private val silenceMillis: Long = SILENCE_MS) {
    private var index = 0
    private var silentSince = -1L

    /** What to do after reading [peak] at [nowMillis]. */
    sealed interface Verdict {
        data object Keep : Verdict

        /** Stop this source and record from [next] instead. */
        data class Swap(val next: CaptureSource) : Verdict

        /** Every source stayed silent. */
        data object GiveUp : Verdict
    }

    val current: CaptureSource get() = order[index]

    /** Declares that the recording runs on [source], restarting the silence clock (it may not be first in [order]). */
    fun startedOn(source: CaptureSource, nowMillis: Long) {
        index = order.indexOf(source).coerceAtLeast(0)
        silentSince = nowMillis
    }

    fun onPeak(peak: Int, nowMillis: Long): Verdict {
        if (peak > 0) {
            // Sound heard once: this source works, silence later is just a quiet moment.
            silentSince = Long.MAX_VALUE
            return Verdict.Keep
        }
        if (silentSince == Long.MAX_VALUE) return Verdict.Keep
        if (nowMillis - silentSince < silenceMillis) return Verdict.Keep
        if (index + 1 >= order.size) return Verdict.GiveUp
        index++
        silentSince = nowMillis
        return Verdict.Swap(order[index])
    }

    companion object {
        const val SILENCE_MS = 4_000L

        /** Microphone sources, in the order they are tried when the first one is silent. */
        val MICROPHONES = listOf(CaptureSource.MICROPHONE, CaptureSource.VOICE_RECOGNITION, CaptureSource.UNPROCESSED, CaptureSource.CAMCORDER)
    }
}
