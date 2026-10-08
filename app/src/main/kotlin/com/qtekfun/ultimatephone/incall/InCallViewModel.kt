package com.qtekfun.ultimatephone.incall

import androidx.lifecycle.ViewModel
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.AudioState
import com.qtekfun.ultimatephone.core.telecom.CallController
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class InCallViewModel @Inject constructor(private val controller: CallController) : ViewModel() {
    val calls: StateFlow<List<CallInfo>> = controller.calls
    val audio: StateFlow<AudioState> = controller.audio

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

    fun merge() = controller.mergeCalls()

    fun swap() = controller.swapCalls()
}
