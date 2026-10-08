package com.qtekfun.ultimatephone.spike

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File

/** Audio sources worth trying for call recording; the call-specific ones normally need a privileged permission. */
object AudioSources {
    val all: List<Pair<String, Int>> = listOf(
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
        "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
        "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK
    )
}

/**
 * Test for the recording decision (doc 05). It records to the app cache and reports, per source, whether it started,
 * how many bytes were written and the peak amplitude. A source that "works" but has a peak of 0 captured silence.
 */
class CallRecorder(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var name = ""
    private var peak = 0
    private var samples = 0
    private var nonZero = 0

    private val sampler = object : Runnable {
        override fun run() {
            val active = recorder ?: return
            val amplitude = active.maxAmplitude
            samples++
            if (amplitude > 0) nonZero++
            if (amplitude > peak) peak = amplitude
            handler.postDelayed(this, SAMPLE_MS)
        }
    }

    val isRecording: Boolean get() = recorder != null

    @Suppress("TooGenericExceptionCaught") // Any failure of an unsupported source is the result being measured.
    fun start(sourceName: String, source: Int): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            EventLog.add("rec", "$sourceName: RECORD_AUDIO not granted")
            return false
        }
        if (recorder != null) stop()
        val target = File(context.cacheDir, "probe-$sourceName.m4a")
        val candidate = MediaRecorder(context)
        return try {
            candidate.setAudioSource(source)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setOutputFile(target.absolutePath)
            candidate.prepare()
            candidate.start()
            recorder = candidate
            file = target
            name = sourceName
            peak = 0
            samples = 0
            nonZero = 0
            handler.postDelayed(sampler, SAMPLE_MS)
            EventLog.add("rec", "$sourceName: started")
            true
        } catch (e: Exception) {
            candidate.release()
            EventLog.add("rec", "$sourceName: FAILED ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    @Suppress("TooGenericExceptionCaught") // stop() throws a bare RuntimeException when no audio was captured.
    fun stop() {
        val active = recorder ?: return
        recorder = null
        handler.removeCallbacks(sampler)
        try {
            active.stop()
        } catch (e: RuntimeException) {
            EventLog.add("rec", "$name: stop failed, no valid audio (${e.message})")
        }
        active.release()
        EventLog.add("rec", "$name: stopped bytes=${file?.length() ?: 0} peak=$peak nonzero_samples=$nonZero/$samples")
    }

    /** Starts and stops every source in turn, outside of a call, to see which ones the system even lets us open. */
    fun probeAll(onDone: () -> Unit) {
        val queue = ArrayDeque(AudioSources.all)
        fun next() {
            val item = queue.removeFirstOrNull()
            if (item == null) {
                onDone()
                return
            }
            val started = start(item.first, item.second)
            handler.postDelayed({
                if (started) stop()
                next()
            }, PROBE_MS)
        }
        next()
    }

    private companion object {
        const val SAMPLE_MS = 250L
        const val PROBE_MS = 1500L
    }
}
