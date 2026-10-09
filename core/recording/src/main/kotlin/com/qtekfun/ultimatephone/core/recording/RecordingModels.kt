package com.qtekfun.ultimatephone.core.recording

import android.media.MediaRecorder

/** What the phone lets this app record during a call. Detected at run time, never assumed. */
enum class RecordingCapability {
    /** A call-line source (the other person's voice, straight from the modem) produced real audio. */
    LINE_CAPTURE,

    /** Only the microphone can be recorded: the other person is captured only through the speaker. */
    MICROPHONE_ONLY,

    /** This phone has no usable microphone. */
    UNAVAILABLE
}

/** Container and platform encoder of a recording. Only encoders that ship with Android are used. */
enum class RecordingFormat(val extension: String, val mimeType: String) {
    M4A_AAC("m4a", "audio/mp4"),
    OGG_OPUS("ogg", "audio/ogg")
}

/** Which calls are recorded without the user asking. */
enum class RecordingMode {
    /** Only when the user taps Record. */
    OFF,
    ALL_CALLS,
    UNKNOWN_NUMBERS,
    SELECTED_CONTACTS
}

/** An Android audio source the recorder can be asked to use. */
enum class CaptureSource(val androidSource: Int, val isLine: Boolean) {
    VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL, true),
    VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK, true),
    MICROPHONE(MediaRecorder.AudioSource.MIC, false),
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, false),
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED, false),
    CAMCORDER(MediaRecorder.AudioSource.CAMCORDER, false)
}

/** Why a recording did not start or had to end. */
enum class FailureReason {
    NOT_ENABLED,
    LEGAL_NOT_ACCEPTED,
    NO_FOLDER,
    NO_PERMISSION,
    NO_MICROPHONE,

    /** The system refused to start the microphone service from the background. */
    FOREGROUND_NOT_ALLOWED,
    AUDIO_SOURCE_FAILED,
    STORAGE_FAILED,

    /** Stopped, but nothing usable had been captured. */
    NO_AUDIO,
    CAPTURE_ERROR
}
