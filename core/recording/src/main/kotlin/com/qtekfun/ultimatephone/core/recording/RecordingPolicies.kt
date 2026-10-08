package com.qtekfun.ultimatephone.core.recording

/** Compares phone numbers without a normaliser: the last digits are what identifies a line. */
object NumberKey {
    private const val SIGNIFICANT_DIGITS = 9

    /** Null when [number] has no digits (hidden caller id). */
    fun of(number: String?): String? = number?.filter { it.isDigit() }?.takeLast(SIGNIFICANT_DIGITS)?.ifEmpty { null }
}

/** Decides whether a call is recorded without the user asking. */
object AutoRecordPolicy {
    /**
     * @param isKnownContact the caller is a saved contact (a business from a data pack does not count).
     * @param numberKey [NumberKey.of] of the caller, null when hidden.
     * @param selected keys chosen by the user for [RecordingMode.SELECTED_CONTACTS].
     */
    fun shouldRecord(mode: RecordingMode, isKnownContact: Boolean, numberKey: String?, selected: Set<String>): Boolean = when (mode) {
        RecordingMode.OFF -> false
        RecordingMode.ALL_CALLS -> true
        RecordingMode.UNKNOWN_NUMBERS -> !isKnownContact
        RecordingMode.SELECTED_CONTACTS -> numberKey != null && numberKey in selected
    }
}

/** Why recording can or cannot be used right now. The first problem found, in the order the user has to fix it. */
enum class Readiness {
    READY,
    DISABLED,
    LEGAL_NOT_ACCEPTED,
    NO_FOLDER,
    NO_PERMISSION,
    NO_MICROPHONE;

    fun toFailure(): FailureReason? = when (this) {
        READY -> null
        DISABLED -> FailureReason.NOT_ENABLED
        LEGAL_NOT_ACCEPTED -> FailureReason.LEGAL_NOT_ACCEPTED
        NO_FOLDER -> FailureReason.NO_FOLDER
        NO_PERMISSION -> FailureReason.NO_PERMISSION
        NO_MICROPHONE -> FailureReason.NO_MICROPHONE
    }

    companion object {
        fun evaluate(enabled: Boolean, legalAccepted: Boolean, folderUsable: Boolean, hasPermission: Boolean, capability: RecordingCapability?): Readiness =
            when {
                !enabled -> DISABLED
                !legalAccepted -> LEGAL_NOT_ACCEPTED
                !folderUsable -> NO_FOLDER
                capability == RecordingCapability.UNAVAILABLE -> NO_MICROPHONE
                !hasPermission -> NO_PERMISSION
                else -> READY
            }
    }
}

object SpeakerPolicy {
    /**
     * With a microphone-only recording the other person is heard only through the speaker, so the speaker is switched on
     * when the user chose that (or when the recording is announced aloud). A headset or Bluetooth choice is never overridden.
     */
    fun shouldEnableSpeaker(capability: RecordingCapability?, autoSpeaker: Boolean, announce: Boolean, onEarpiece: Boolean): Boolean {
        if (!onEarpiece || capability == RecordingCapability.UNAVAILABLE) return false
        val micOnly = capability != RecordingCapability.LINE_CAPTURE
        return (micOnly && autoSpeaker) || announce
    }
}

object AnnouncePolicy {
    fun shouldAnnounce(announce: Boolean, capability: RecordingCapability?): Boolean = announce && capability != RecordingCapability.UNAVAILABLE
}

object MicWarningPolicy {
    /** The "microphone only" warning appears the first time a microphone-only recording starts. */
    fun shouldWarn(capability: RecordingCapability?, alreadyShown: Boolean): Boolean = capability == RecordingCapability.MICROPHONE_ONLY && !alreadyShown
}

/** What a probe of one call-line source found. [peak] is the highest amplitude seen; 0 means silence. */
data class ProbeResult(val source: CaptureSource, val started: Boolean, val peak: Int)

/**
 * @property persist whether [capability] is final and should be saved. A line source that started but only gave silence
 * may just have caught a quiet moment of the call, so the decision is repeated on the next recordings.
 * @property silentAttempts value to save for the next decision.
 */
data class CapabilityDecision(val capability: RecordingCapability, val source: CaptureSource, val persist: Boolean, val silentAttempts: Int)

object CapabilityDetector {
    /** After this many recordings whose line source started but stayed silent, the line is considered unusable. */
    const val MAX_SILENT_ATTEMPTS = 3

    /** Line sources, in the order they are tried. */
    val LINE_SOURCES = listOf(CaptureSource.VOICE_CALL, CaptureSource.VOICE_DOWNLINK)

    fun decide(results: List<ProbeResult>, previousSilentAttempts: Int): CapabilityDecision {
        val working = results.firstOrNull { it.source.isLine && it.started && it.peak > 0 }
        if (working != null) return CapabilityDecision(RecordingCapability.LINE_CAPTURE, working.source, persist = true, silentAttempts = 0)
        val silent = results.any { it.source.isLine && it.started }
        if (!silent) return CapabilityDecision(RecordingCapability.MICROPHONE_ONLY, CaptureSource.MICROPHONE, persist = true, silentAttempts = 0)
        val attempts = previousSilentAttempts + 1
        return CapabilityDecision(
            RecordingCapability.MICROPHONE_ONLY,
            CaptureSource.MICROPHONE,
            persist = attempts >= MAX_SILENT_ATTEMPTS,
            silentAttempts = attempts
        )
    }
}
