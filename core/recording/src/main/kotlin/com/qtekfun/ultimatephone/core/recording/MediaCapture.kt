package com.qtekfun.ultimatephone.core.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.FileDescriptor

/**
 * One [MediaRecorder] with the platform encoders (AAC in MPEG-4, Opus in Ogg). It never throws: every failure of the
 * system (an unsupported source, a source the app may not use, a full disk) is reported as a result.
 */
class MediaCapture(private val context: Context) {
    private var recorder: MediaRecorder? = null

    /** Called, from a framework thread, when the recorder fails or reaches a limit while recording. */
    @Volatile
    var onError: (() -> Unit)? = null

    val isRecording: Boolean get() = recorder != null

    fun start(source: CaptureSource, format: RecordingFormat, file: File): Boolean = begin(source, format) { it.setOutputFile(file) }

    /** [output] must be opened for reading and writing: the MPEG-4 muxer goes back to the start of the file when it finishes. */
    fun start(source: CaptureSource, format: RecordingFormat, output: FileDescriptor): Boolean = begin(source, format) { it.setOutputFile(output) }

    // Each source fails with a different RuntimeException (or IOException); all of them mean "not usable".
    private fun begin(source: CaptureSource, format: RecordingFormat, output: (MediaRecorder) -> Unit): Boolean {
        if (recorder != null) return false
        val candidate = MediaRecorder(context)
        val started = runCatching {
            candidate.setAudioSource(source.androidSource)
            when (format) {
                RecordingFormat.M4A_AAC -> {
                    candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    candidate.setAudioSamplingRate(AAC_RATE)
                    candidate.setAudioEncodingBitRate(AAC_BITRATE)
                }
                RecordingFormat.OGG_OPUS -> {
                    candidate.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    candidate.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    candidate.setAudioSamplingRate(OPUS_RATE)
                    candidate.setAudioEncodingBitRate(OPUS_BITRATE)
                }
            }
            candidate.setAudioChannels(1)
            output(candidate)
            candidate.setOnErrorListener { _, _, _ -> onError?.invoke() }
            candidate.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onError?.invoke()
            }
            candidate.prepare()
            candidate.start()
        }.isSuccess
        if (started) recorder = candidate else runCatching { candidate.release() }
        return started
    }

    /** Highest amplitude since the last call (0 to 32767). 0 for silence, and when nothing is recording. */
    fun peak(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0) // Throws on a recorder in a bad state: "no sound".

    /** Stops and releases. False when the file holds no valid audio (stop() throws when nothing was captured). */
    fun stop(): Boolean {
        val active = recorder ?: return false
        recorder = null
        val valid = runCatching { active.stop() }.isSuccess // stop() throws a bare RuntimeException when no audio was captured.
        runCatching { active.release() }
        return valid
    }

    private companion object {
        const val AAC_RATE = 44_100
        const val AAC_BITRATE = 64_000
        const val OPUS_RATE = 48_000
        const val OPUS_BITRATE = 32_000
    }
}
