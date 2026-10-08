package com.qtekfun.ultimatephone.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.qtekfun.ultimatephone.core.recording.AnnouncePolicy
import com.qtekfun.ultimatephone.core.recording.AutoRecordPolicy
import com.qtekfun.ultimatephone.core.recording.CaptureSource
import com.qtekfun.ultimatephone.core.recording.MicWarningPolicy
import com.qtekfun.ultimatephone.core.recording.NumberKey
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingCapability
import com.qtekfun.ultimatephone.core.recording.RecordingMode
import com.qtekfun.ultimatephone.core.recording.RecordingStorage
import com.qtekfun.ultimatephone.core.recording.SessionMachine
import com.qtekfun.ultimatephone.core.recording.SessionState
import com.qtekfun.ultimatephone.core.recording.SpeakerPolicy
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.CallController
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the call screen needs to show about recording. */
data class RecordingUi(
    val session: SessionState = SessionState.Idle,
    /** The Record button can be offered (the feature is set up and allowed). */
    val available: Boolean = false,
    /** "Microphone only" warning to show once. */
    val micWarning: Boolean = false
) {
    val isRecording: Boolean get() = session is SessionState.Recording
    val isBusy: Boolean get() = SessionMachine.isBusy(session)
}

/** What the call screen can ask of call recording. Implemented by [RecordingCoordinator]; tests use a fake. */
interface CallRecording {
    val ui: StateFlow<RecordingUi>

    fun refresh()

    fun startManual(call: CallInfo)

    fun stop()

    fun dismissFailure()

    fun dismissMicWarning()
}

/** What a recording of [source] can capture. */
fun capabilityOf(source: CaptureSource): RecordingCapability = if (source.isLine) RecordingCapability.LINE_CAPTURE else RecordingCapability.MICROPHONE_ONLY

/**
 * Connects calls to recording, independently of any screen: decides when a call is recorded automatically, ends the
 * recording with the call, and applies the speaker and announcement options when a recording starts.
 */
@Singleton
@Suppress("LongParameterList") // Hilt constructor: every collaborator is a separate singleton.
class RecordingCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: CallController,
    private val engine: RecordingEngine,
    private val settings: RecordingSettings,
    private val storage: RecordingStorage,
    private val announcer: RecordingAnnouncer,
    @ApplicationScope private val scope: CoroutineScope
) : CallRecording {
    private val started = AtomicBoolean(false)
    private val handled = HashSet<String>()
    private var hadCalls = false
    private val refreshTick = MutableStateFlow(0)

    override val ui: StateFlow<RecordingUi> = combine(engine.state, settings.config, refreshTick) { session, config, _ ->
        val folderUsable = withContext(Dispatchers.IO) { config.folderUri?.let { storage.isUsable(Uri.parse(it)) } == true }
        val readiness = Readiness.evaluate(
            config.enabled,
            config.legalAccepted,
            folderUsable,
            hasPermission(),
            engine.effectiveCapability(config.capability)
        )
        val warn = session is SessionState.Recording && MicWarningPolicy.shouldWarn(capabilityOf(session.source), config.micWarningShown)
        RecordingUi(session, available = readiness == Readiness.READY, micWarning = warn)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecordingUi())

    /** Starts following calls. Idempotent; called once when the app starts. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { controller.calls.collect(::onCalls) }
        scope.launch {
            var previous: SessionState = SessionState.Idle
            engine.state.collect { current ->
                if (current is SessionState.Recording && previous !is SessionState.Recording) onRecordingStarted(current)
                previous = current
            }
        }
    }

    /** Re-reads things the system can change behind our back, such as the microphone permission. */
    override fun refresh() = refreshTick.update { it + 1 }

    override fun startManual(call: CallInfo) {
        scope.launch { engine.requestStart(request(call, automatic = false)) }
    }

    override fun stop() = engine.stop()

    override fun dismissFailure() = engine.dismissFailure()

    override fun dismissMicWarning() {
        scope.launch { settings.setMicWarningShown() }
    }

    private fun request(call: CallInfo, automatic: Boolean) = RecordingRequest(call.id, call.number, call.contactName, call.incoming, automatic)

    private fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isEnding(call: CallInfo?) = call == null || call.status == CallStatus.DISCONNECTING || call.status == CallStatus.DISCONNECTED

    private fun onCalls(calls: List<CallInfo>) {
        handled.retainAll(calls.map { it.id }.toSet())
        SessionMachine.callIdOf(engine.state.value)?.let { id -> if (isEnding(calls.firstOrNull { it.id == id })) engine.stop() }
        val live = calls.any { !isEnding(it) }
        if (hadCalls && !live) engine.onCallsEnded()
        hadCalls = live
        calls.filter { it.status == CallStatus.ACTIVE && handled.add(it.id) }.forEach { decide(it.id) }
    }

    /** Applies the automatic-recording policy to a call that has just become active. */
    private fun decide(callId: String) {
        scope.launch {
            // The caller's name is looked up a moment after the call appears; "unknown numbers only" needs it.
            delay(LABEL_GRACE_MS)
            val call = controller.calls.value.firstOrNull { it.id == callId && it.status == CallStatus.ACTIVE } ?: return@launch
            val config = settings.current()
            if (!config.enabled || config.mode == RecordingMode.OFF) return@launch
            val known = call.contactName != null && !call.isBusiness
            if (AutoRecordPolicy.shouldRecord(config.mode, known, NumberKey.of(call.number), config.selectedNumbers)) {
                engine.requestStart(request(call, automatic = true))
            }
        }
    }

    private suspend fun onRecordingStarted(session: SessionState.Recording) {
        val config = settings.current()
        val capability = capabilityOf(session.source)
        val onEarpiece = controller.audio.value.route == AudioRoute.EARPIECE
        if (SpeakerPolicy.shouldEnableSpeaker(capability, config.autoSpeaker, config.announce, onEarpiece)) {
            withContext(Dispatchers.Main) { controller.setRoute(AudioRoute.SPEAKER) }
        }
        if (AnnouncePolicy.shouldAnnounce(config.announce, capability)) announcer.announce()
    }

    private companion object {
        const val LABEL_GRACE_MS = 1_200L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
