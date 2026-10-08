package com.qtekfun.ultimatephone.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.AudioState
import com.qtekfun.ultimatephone.core.telecom.CallController
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.feature.spam.CallSpamUi
import com.qtekfun.ultimatephone.feature.spam.SpamNumberActions
import com.qtekfun.ultimatephone.recording.CallRecording
import com.qtekfun.ultimatephone.recording.RecordingUi
import com.qtekfun.ultimatephone.screening.DecisionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class InCallViewModel @Inject constructor(
    private val controller: CallController,
    private val engine: DecisionEngine,
    private val spamActions: SpamNumberActions,
    private val recording: CallRecording
) : ViewModel() {
    val calls: StateFlow<List<CallInfo>> = controller.calls
    val audio: StateFlow<AudioState> = controller.audio

    /** Call recording: the indicator, the Record button and the one-time warning. */
    val recordingUi: StateFlow<RecordingUi> = recording.ui

    private val spamState = MutableStateFlow<Map<String, CallSpamUi>>(emptyMap())

    /** The spam side of each call, by call id. */
    val spam: StateFlow<Map<String, CallSpamUi>> = spamState

    private val seen = HashSet<String>()

    init {
        recording.refresh()
        viewModelScope.launch {
            controller.calls.collect { current ->
                val ids = current.map { it.id }.toSet()
                seen.retainAll(ids)
                spamState.update { it.filterKeys { id -> id in ids } }
                current.filter { it.number != null && seen.add(it.id) }.forEach(::decide)
            }
        }
    }

    /**
     * Decides once per call, reusing what the screening service decided when it ran (live decision). Without the
     * screening role this is the only place the decision is taken, and it is what shows the warning.
     */
    private fun decide(call: CallInfo) {
        val number = call.number ?: return
        viewModelScope.launch {
            val verdict = if (call.incoming) engine.verdictForCall(number) else null
            spamState.update { it + (call.id to (it[call.id] ?: CallSpamUi()).copy(verdict = verdict, hasNumber = isListable(number))) }
        }
    }

    private fun isListable(number: String) = number.any { it.isDigit() }

    fun markSpam(call: CallInfo) {
        val number = call.number ?: return
        viewModelScope.launch {
            if (spamActions.markSpam(number)) spamState.update { it + (call.id to (it[call.id] ?: CallSpamUi()).copy(markedSpam = true, allowed = false)) }
        }
    }

    fun notSpam(call: CallInfo) {
        val number = call.number ?: return
        viewModelScope.launch {
            if (spamActions.allow(number)) spamState.update { it + (call.id to (it[call.id] ?: CallSpamUi()).copy(allowed = true, markedSpam = false)) }
        }
    }

    fun answerRinging() {
        calls.value.firstOrNull { it.status == CallStatus.RINGING }?.let { controller.answer(it.id) }
    }

    fun answer(call: CallInfo) = controller.answer(call.id)

    fun reject(call: CallInfo) = controller.reject(call.id)

    fun hangup(call: CallInfo) = controller.hangup(call.id)

    fun toggleHold(call: CallInfo) = controller.setHold(call.id, call.status != CallStatus.HOLDING)

    fun toggleMute() = controller.setMuted(!audio.value.muted)

    fun setRoute(route: AudioRoute) = controller.setRoute(route)

    fun dtmfDown(call: CallInfo, digit: Char) = controller.playDtmf(call.id, digit)

    fun dtmfUp(call: CallInfo) = controller.stopDtmf(call.id)

    fun toggleRecording(call: CallInfo) = if (recordingUi.value.isBusy) recording.stop() else recording.startManual(call)

    fun stopRecording() = recording.stop()

    fun dismissRecordingFailure() = recording.dismissFailure()

    fun dismissMicWarning() = recording.dismissMicWarning()

    fun merge() = controller.mergeCalls()

    fun swap() = controller.swapCalls()
}
