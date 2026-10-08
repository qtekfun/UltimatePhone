package com.qtekfun.ultimatephone.recording

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.qtekfun.ultimatephone.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says "this call is being recorded" with the phone's own text-to-speech, on the call's audio stream. With the speaker on
 * the other person hears it through the microphone; without a speech engine on the phone nothing is said.
 */
@Singleton
class RecordingAnnouncer @Inject constructor(@ApplicationContext private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun announce() {
        main.post(::speak)
    }

    private fun speak() {
        var speech: TextToSpeech? = null
        speech = TextToSpeech(context) { status ->
            val engine = speech
            if (status != TextToSpeech.SUCCESS || engine == null) {
                engine?.shutdown()
                return@TextToSpeech
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.language = Locale.getDefault()
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        engine.shutdown()
                    }

                    @Deprecated("Deprecated in the framework, but still the abstract member.")
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        engine.shutdown()
                    }
                }
            )
            val queued = engine.speak(context.getString(R.string.recording_announcement), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
            if (queued == TextToSpeech.ERROR) engine.shutdown()
        }
    }

    private companion object {
        const val UTTERANCE = "recording-announcement"
    }
}
