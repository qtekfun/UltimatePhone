package com.qtekfun.ultimatephone.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.qtekfun.ultimatephone.core.recording.CapabilityDetector
import com.qtekfun.ultimatephone.core.recording.CaptureSource
import com.qtekfun.ultimatephone.core.recording.FailureReason
import com.qtekfun.ultimatephone.core.recording.LineCaptureProbe
import com.qtekfun.ultimatephone.core.recording.MediaCapture
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingCapability
import com.qtekfun.ultimatephone.core.recording.RecordingFormat
import com.qtekfun.ultimatephone.core.recording.RecordingNames
import com.qtekfun.ultimatephone.core.recording.RecordingStorage
import com.qtekfun.ultimatephone.core.recording.SessionEvent
import com.qtekfun.ultimatephone.core.recording.SessionMachine
import com.qtekfun.ultimatephone.core.recording.SessionState
import com.qtekfun.ultimatephone.core.recording.SilenceWatch
import com.qtekfun.ultimatephone.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What a recording needs to know about its call. The number lives only in memory, to build a masked file name.
 *
 * @property automatic started by the auto-record policy rather than by the user.
 */
data class RecordingRequest(val callId: String, val number: String?, val contactName: String?, val incoming: Boolean, val automatic: Boolean)

/** Outcome of the 3 second microphone test in Settings. */
sealed interface MicTestResult {
    data object Busy : MicTestResult

    data object CouldNotStart : MicTestResult

    /** [peak] is the highest amplitude heard (0 to 32767); 0 means silence. */
    data class Done(val peak: Int) : MicTestResult
}

/**
 * Owns the recorder and the state of the one recording session. Every failure ends in [SessionState.Failed]; nothing here
 * throws to the caller. [RecordingService] keeps the process allowed to record in the background; this class does the work.
 */
@Singleton
class RecordingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: RecordingSettings,
    private val storage: RecordingStorage,
    private val notifications: RecordingNotifications,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = sessionState.asStateFlow()

    private val lock = Mutex()
    private val capture = MediaCapture(context).also { c ->
        c.onError =
            { scope.launch(Dispatchers.IO) { lock.withLock { finish(FailureReason.CAPTURE_ERROR) } } }
    }

    @Volatile
    private var pending: RecordingRequest? = null

    @Volatile
    private var testing = false
    private var targetUri: Uri? = null
    private var targetFd: ParcelFileDescriptor? = null
    private var watcher: Job? = null

    private fun dispatch(event: SessionEvent) = sessionState.update { SessionMachine.reduce(it, event) }

    /** Takes the single session slot for [callId]. False if a session is already active. */
    private fun claim(callId: String): Boolean {
        while (true) {
            val current = sessionState.value
            if (SessionMachine.isBusy(current) || testing) return false
            if (sessionState.compareAndSet(current, SessionMachine.reduce(current, SessionEvent.RequestStart(callId)))) return true
        }
    }

    private fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasMicrophone() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    /** What recording can do here: no microphone means nothing, otherwise what was detected (null if not yet). */
    fun effectiveCapability(detected: RecordingCapability?): RecordingCapability? = if (hasMicrophone()) detected else RecordingCapability.UNAVAILABLE

    /** Whether recording can be used right now, and if not the first thing the user has to fix. */
    suspend fun readiness(): Readiness {
        val config = settings.current()
        val folderUsable = withContext(Dispatchers.IO) { config.folderUri?.let { storage.isUsable(Uri.parse(it)) } == true }
        return Readiness.evaluate(config.enabled, config.legalAccepted, folderUsable, hasPermission(), effectiveCapability(config.capability))
    }

    /**
     * Starts a recording: checks that it is allowed, then asks the system for the microphone foreground service. When the
     * system refuses (an automatic start from the background), the session ends as [FailureReason.FOREGROUND_NOT_ALLOWED]
     * and a notification offers "Record". Returns whether the service was started.
     */
    suspend fun requestStart(request: RecordingRequest): Boolean {
        if (!claim(request.callId)) return false
        pending = request
        val failure = readiness().toFailure()
        if (failure != null) {
            fail(failure)
            return false
        }
        // ForegroundServiceStartNotAllowedException (an IllegalStateException) when the app is not visible and has no
        // exemption, SecurityException when the microphone type is refused: both mean "not from here".
        val started = runCatching { RecordingService.start(context) }.isSuccess
        if (!started) onForegroundDenied()
        return started
    }

    /** The system did not let the microphone service run from here. The request is kept so a tap on "Record" can retry. */
    fun onForegroundDenied() {
        val request = pending
        dispatch(SessionEvent.StartFailed(FailureReason.FOREGROUND_NOT_ALLOWED))
        if (request != null && sessionState.value is SessionState.Failed) {
            if (request.automatic) scope.launch { settings.setAutoStartBlocked(true) }
            notifications.showPrompt()
        }
    }

    /**
     * Called by the service once it is in the foreground. True when there is something to run or already running; a tap on
     * the "Record" notification arrives here with the session in the failed state and takes the slot again.
     */
    fun claimForService(): Boolean {
        val current = sessionState.value
        if (SessionMachine.isBusy(current)) return true
        val request = pending ?: return false
        return current is SessionState.Failed && claim(request.callId)
    }

    /** Opens the file and the recorder. Runs in the background; the outcome is the new state. */
    fun begin() {
        scope.launch(Dispatchers.IO) { lock.withLock { run() } }
    }

    @Suppress("ReturnCount") // Each early return is one way the start can fail, and each ends the session.
    private suspend fun run() {
        val request = pending
        if (request == null || sessionState.value !is SessionState.Starting) {
            // Stopped before it began, or nothing to record.
            dispatch(SessionEvent.StartFailed(FailureReason.CAPTURE_ERROR))
            return
        }
        val config = settings.current()
        val tree = config.folderUri?.let(Uri::parse) ?: return fail(FailureReason.NO_FOLDER)
        val source = resolveSource(config)
        if (sessionState.value !is SessionState.Starting) return cancelled()

        val startedAt = System.currentTimeMillis()
        val name = RecordingNames.build(startedAt, request.incoming, request.number, request.contactName, config.includeContactName, config.format)
        // A line source that worked in the probe can still fail now; the microphone is always the way out.
        val candidates = listOf(source, CaptureSource.MICROPHONE, CaptureSource.VOICE_RECOGNITION).distinct()
        var attempt: Attempt = Attempt.SourceFailed
        for (candidate in candidates) {
            attempt = tryStart(tree, name, config.format, candidate)
            if (attempt !is Attempt.SourceFailed) break
        }
        val started = attempt as? Attempt.Started
            ?: return fail(if (attempt is Attempt.StorageFailed) FailureReason.STORAGE_FAILED else FailureReason.AUDIO_SOURCE_FAILED)
        targetUri = started.uri
        targetFd = started.fd
        dispatch(SessionEvent.Started(started.source, System.currentTimeMillis()))
        // The user may have pressed Stop while the file was being prepared.
        if (sessionState.value is SessionState.Stopping) finish(null) else watchForSilence(tree, config.format, started.source)
    }

    /**
     * Some phones start a microphone source and then deliver only silence while a call is going on. Listens to the level
     * and moves to the next microphone source when nothing is heard, ending as [FailureReason.NO_AUDIO] if all stay silent.
     */
    private fun watchForSilence(tree: Uri, format: RecordingFormat, source: CaptureSource) {
        if (source.isLine) return
        val watch = SilenceWatch(SilenceWatch.MICROPHONES).also { it.startedOn(source, System.currentTimeMillis()) }
        watcher?.cancel()
        watcher = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(WATCH_STEP_MS)
                val done = lock.withLock {
                    if (sessionState.value !is SessionState.Recording) return@withLock true
                    when (val verdict = watch.onPeak(capture.peak(), System.currentTimeMillis())) {
                        SilenceWatch.Verdict.Keep -> false
                        is SilenceWatch.Verdict.Swap -> !swapTo(tree, format, verdict.next)
                        SilenceWatch.Verdict.GiveUp -> {
                            discardCurrent()
                            dispatch(SessionEvent.Error(FailureReason.NO_AUDIO))
                            true
                        }
                    }
                }
                if (done) break
            }
        }
    }

    /** Replaces the silent file with a new one recorded from [next]. False when that failed (the session then ends). */
    private fun swapTo(tree: Uri, format: RecordingFormat, next: CaptureSource): Boolean {
        val request = pending
        discardCurrent()
        val name = request?.let {
            RecordingNames.build(System.currentTimeMillis(), it.incoming, it.number, it.contactName, false, format)
        }
        val attempt = name?.let { tryStart(tree, it, format, next) }
        if (attempt is Attempt.Started) {
            targetUri = attempt.uri
            targetFd = attempt.fd
            return true
        }
        pending = null
        dispatch(SessionEvent.Error(FailureReason.AUDIO_SOURCE_FAILED))
        return false
    }

    /** Stops the recorder and removes its file. */
    private fun discardCurrent() {
        capture.stop()
        runCatching { targetFd?.close() }
        targetFd = null
        targetUri?.let(storage::delete)
        targetUri = null
    }

    private sealed interface Attempt {
        data class Started(val uri: Uri, val fd: ParcelFileDescriptor, val source: CaptureSource) : Attempt

        data object StorageFailed : Attempt

        data object SourceFailed : Attempt
    }

    /** Creates the file and starts [source] into it; whatever fails leaves no file behind. */
    private fun tryStart(tree: Uri, name: String, format: RecordingFormat, source: CaptureSource): Attempt {
        val uri = storage.create(tree, name) ?: return Attempt.StorageFailed
        val fd = storage.openForRecording(uri)
        if (fd == null) {
            storage.delete(uri)
            return Attempt.StorageFailed
        }
        if (capture.start(source, format, fd.fileDescriptor)) return Attempt.Started(uri, fd, source)
        runCatching { fd.close() }
        storage.delete(uri)
        return Attempt.SourceFailed
    }

    /** Uses the saved capability, or checks the call line now (once; the answer is saved) when it is not known. */
    private suspend fun resolveSource(config: RecordingConfig): CaptureSource {
        config.capability?.let { known ->
            return if (known == RecordingCapability.LINE_CAPTURE) config.lineSource ?: CaptureSource.VOICE_CALL else CaptureSource.MICROPHONE
        }
        val decision = CapabilityDetector.decide(LineCaptureProbe(context).run(), config.silentAttempts)
        if (decision.persist) {
            settings.saveCapability(decision.capability, decision.source.takeIf { it.isLine })
        } else {
            settings.saveSilentAttempts(decision.silentAttempts)
        }
        return decision.source
    }

    private fun cancelled() {
        dispatch(SessionEvent.StartFailed(FailureReason.CAPTURE_ERROR))
        pending = null
    }

    private fun fail(reason: FailureReason) {
        dispatch(SessionEvent.StartFailed(reason))
        pending = null
    }

    /** Ends the recording. Safe to call at any time and more than once. */
    fun stop() {
        if (!SessionMachine.isBusy(sessionState.value)) return
        dispatch(SessionEvent.RequestStop)
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                if (capture.isRecording || targetUri != null) finish(null)
            }
        }
    }

    /** Closes the recorder and the file; an empty file is deleted. [error] ends the session as failed instead of stopped. */
    private fun finish(error: FailureReason?) {
        val valid = capture.stop()
        runCatching { targetFd?.close() }
        targetFd = null
        val uri = targetUri
        targetUri = null
        if (!valid && uri != null) storage.delete(uri)
        pending = null
        dispatch(if (error != null) SessionEvent.Error(error) else SessionEvent.Stopped(valid))
    }

    /** The user saw the failure. */
    fun dismissFailure() {
        dispatch(SessionEvent.Dismiss)
    }

    /** No call is left: whatever is recording stops, and a pending "Record" offer goes away. */
    fun onCallsEnded() {
        stop()
        notifications.cancelPrompt()
        if (sessionState.value is SessionState.Failed) pending = null
    }

    /** Records the microphone for 3 seconds into a scratch file and reports how loud it was. */
    suspend fun runMicTest(): MicTestResult {
        if (SessionMachine.isBusy(sessionState.value) || testing) return MicTestResult.Busy
        testing = true
        try {
            return withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "mic-test.m4a")
                val test = MediaCapture(context)
                if (!test.start(CaptureSource.MICROPHONE, RecordingFormat.M4A_AAC, file)) {
                    file.delete()
                    return@withContext MicTestResult.CouldNotStart
                }
                var peak = 0
                try {
                    repeat(TEST_STEPS) {
                        delay(TEST_STEP_MS)
                        peak = maxOf(peak, test.peak())
                    }
                } finally {
                    test.stop()
                    file.delete()
                }
                MicTestResult.Done(peak)
            }
        } finally {
            testing = false
        }
    }

    private companion object {
        const val WATCH_STEP_MS = 500L
        const val TEST_STEPS = 12
        const val TEST_STEP_MS = 250L
    }
}
