package com.qtekfun.ultimatephone.spike

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.qtekfun.ultimatephone.R

/** Minimal in-call screen: answer, reject, hang up, speaker, mute, plus the recording probes of test 6/Phase 0. */
class InCallActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var answer: Button
    private lateinit var reject: Button
    private lateinit var hangup: Button
    private val handler = Handler(Looper.getMainLooper())
    private val recorder by lazy { CallRecorder(this) }
    private val listener: () -> Unit = { refresh() }
    private val finishWhenIdle = Runnable { if (CallStore.isEmpty()) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = UiKit.column(this)
        status = UiKit.body(this).apply { textSize = 20f }
        column.addView(status)
        answer = UiKit.button(this, getString(R.string.btn_answer)) { answerCall() }
        reject = UiKit.button(this, getString(R.string.btn_reject)) { CallStore.primary()?.reject(false, null) }
        hangup = UiKit.button(this, getString(R.string.btn_hangup)) { CallStore.primary()?.disconnect() }
        column.addView(answer)
        column.addView(reject)
        column.addView(hangup)
        column.addView(UiKit.button(this, getString(R.string.btn_speaker)) { toggleSpeaker() })
        column.addView(UiKit.button(this, getString(R.string.btn_mute)) { toggleMute() })
        column.addView(UiKit.heading(this, getString(R.string.section_recording)))
        column.addView(UiKit.body(this, getString(R.string.recording_hint)))
        AudioSources.all.filter { it.first in IN_CALL_SOURCES }.forEach { (name, source) ->
            column.addView(UiKit.button(this, getString(R.string.btn_record, name)) { recorder.start(name, source) })
        }
        column.addView(UiKit.button(this, getString(R.string.btn_record_stop)) { recorder.stop() })
        val scroll = ScrollView(this).apply { addView(column) }
        UiKit.fitSystemBars(scroll)
        setContentView(scroll)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        CallStore.addListener(listener)
        refresh()
    }

    override fun onStop() {
        CallStore.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        recorder.stop()
        handler.removeCallbacks(finishWhenIdle)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_ANSWER) answerCall()
    }

    private fun answerCall() {
        CallStore.primary()?.takeIf { it.details.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    @Suppress("DEPRECATION") // setAudioRoute is the only route API that works on every Android 12+ build.
    private fun toggleSpeaker() {
        val speaker = CallStore.audioState?.route == CallAudioState.ROUTE_SPEAKER
        SpikeInCallService.instance?.setAudioRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    private fun toggleMute() {
        SpikeInCallService.instance?.setMuted(CallStore.audioState?.isMuted != true)
    }

    private fun refresh() {
        val call = CallStore.primary()
        if (call == null) {
            status.text = getString(R.string.no_call)
            answer.isEnabled = false
            reject.isEnabled = false
            hangup.isEnabled = false
            handler.postDelayed(finishWhenIdle, FINISH_DELAY_MS)
            return
        }
        handler.removeCallbacks(finishWhenIdle)
        val number = call.details.handle?.schemeSpecificPart
        val rule = TestRules.match(RuleStore.load(this), number)
        val ringing = call.details.state == Call.STATE_RINGING
        answer.isEnabled = ringing
        reject.isEnabled = ringing
        hangup.isEnabled = !ringing
        val route = CallStore.audioState?.route
        status.text = buildString {
            appendLine(CallNames.state(call.details.state))
            appendLine(PhoneMask.mask(number))
            appendLine(SimInfo.describe(this@InCallActivity, call.details.accountHandle))
            appendLine("audio route=$route muted=${CallStore.audioState?.isMuted}")
            if (rule != null) append(getString(R.string.spam_warning, rule.action.name))
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_ANSWER = "answer"
        private const val FINISH_DELAY_MS = 2000L
        private val IN_CALL_SOURCES = setOf("MIC", "VOICE_COMMUNICATION", "VOICE_CALL")
    }
}
