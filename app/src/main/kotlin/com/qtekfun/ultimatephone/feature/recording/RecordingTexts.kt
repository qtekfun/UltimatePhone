package com.qtekfun.ultimatephone.feature.recording

import androidx.annotation.StringRes
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.recording.FailureReason
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingCapability

@StringRes
fun failureText(reason: FailureReason): Int = when (reason) {
    FailureReason.NOT_ENABLED, FailureReason.LEGAL_NOT_ACCEPTED -> R.string.recording_error_setup
    FailureReason.NO_FOLDER -> R.string.recording_error_no_folder
    FailureReason.NO_PERMISSION -> R.string.recording_error_permission
    FailureReason.NO_MICROPHONE -> R.string.recording_error_no_microphone
    FailureReason.FOREGROUND_NOT_ALLOWED -> R.string.recording_error_foreground
    FailureReason.AUDIO_SOURCE_FAILED -> R.string.recording_error_source
    FailureReason.STORAGE_FAILED -> R.string.recording_error_storage
    FailureReason.NO_AUDIO -> R.string.recording_error_no_audio
    FailureReason.CAPTURE_ERROR -> R.string.recording_error_capture
}

/** Why recording is not available, or null when it is ready. */
@StringRes
fun readinessText(readiness: Readiness): Int? = when (readiness) {
    Readiness.READY -> null
    Readiness.DISABLED -> R.string.recording_why_disabled
    Readiness.LEGAL_NOT_ACCEPTED -> R.string.recording_why_legal
    Readiness.NO_FOLDER -> R.string.recording_why_folder
    Readiness.NO_PERMISSION -> R.string.recording_why_permission
    Readiness.NO_MICROPHONE -> R.string.recording_why_no_microphone
}

/** The honest statement of what this phone can record. A null capability has not been detected yet. */
@StringRes
fun capabilityText(capability: RecordingCapability?): Int = when (capability) {
    null -> R.string.recording_capability_unknown
    RecordingCapability.LINE_CAPTURE -> R.string.recording_capability_line
    RecordingCapability.MICROPHONE_ONLY -> R.string.recording_capability_mic
    RecordingCapability.UNAVAILABLE -> R.string.recording_capability_none
}
