package com.qtekfun.ultimatephone.incall

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.CallActionButton
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.LocalReduceMotion
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.core.designsystem.tabularFigures
import com.qtekfun.ultimatephone.core.recording.RecordingFormatters
import com.qtekfun.ultimatephone.core.recording.SessionState
import com.qtekfun.ultimatephone.feature.recording.failureText
import com.qtekfun.ultimatephone.recording.RecordingUi
import kotlinx.coroutines.delay

private val MIN_TARGET = 48.dp
private const val TICK_MS = 1000L
private const val BLINK_MS = 900
private const val BLINK_LOW_ALPHA = 0.35f

/**
 * What the call screen shows about recording: a red pill with a blinking dot, the elapsed time and a Stop button while
 * recording, the reason (an error banner) when a recording failed, and (once) the warning that only the microphone is
 * recorded on this phone.
 */
@Composable
internal fun RecordingStatus(ui: RecordingUi, onStop: () -> Unit, onDismissWarning: () -> Unit, onDismissError: () -> Unit) {
    val session = ui.session
    if (session !is SessionState.Recording && session !is SessionState.Failed && !ui.micWarning) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        when (session) {
            is SessionState.Recording -> RecordingBar(session.startedAtMillis, onStop)
            is SessionState.Failed -> InfoBanner(
                kind = BannerKind.Error,
                title = stringResource(failureText(session.reason)),
                action = UiAction(stringResource(R.string.recording_dismiss), onDismissError),
                liveRegion = true
            )
            else -> Unit
        }
        if (ui.micWarning) {
            InfoBanner(
                kind = BannerKind.Info,
                title = stringResource(R.string.recording_mic_warning_title),
                body = stringResource(R.string.recording_mic_warning_text),
                action = UiAction(stringResource(R.string.recording_mic_warning_ok), onDismissWarning),
                liveRegion = true
            )
        }
    }
}

@Composable
private fun RecordingBar(startedAtMillis: Long, onStop: () -> Unit) {
    val elapsed by produceState(0L, startedAtMillis) {
        while (true) {
            value = System.currentTimeMillis() - startedAtMillis
            delay(TICK_MS)
        }
    }
    val time = RecordingFormatters.duration(elapsed)
    val blink: State<Float>? = if (LocalReduceMotion.current) {
        null
    } else {
        rememberInfiniteTransition(label = "recordingDot").animateFloat(
            initialValue = 1f,
            targetValue = BLINK_LOW_ALPHA,
            animationSpec = infiniteRepeatable(tween(BLINK_MS), RepeatMode.Reverse),
            label = "recordingDotAlpha"
        )
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET).padding(start = Spacing.Medium, end = Spacing.XSmall),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The state is never colour alone: a dot, the word "Recording" and the elapsed time. The word is a polite live region so
            // TalkBack announces it when recording starts; the ticking time is not, or it would be read every second.
            Spacer(
                Modifier.size(12.dp).alpha(blink?.value ?: 1f).background(MaterialTheme.colorScheme.error, CircleShape).clearAndSetSemantics {}
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(
                stringResource(R.string.recording_indicator_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(time, style = MaterialTheme.typography.titleMedium.tabularFigures())
            Spacer(Modifier.weight(1f, fill = false).width(Spacing.Small))
            val stopDescription = stringResource(R.string.recording_stop_description)
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = MIN_TARGET).semantics { contentDescription = stopDescription }) {
                Text(stringResource(R.string.recording_stop))
            }
        }
    }
}

/** The Record / Stop button of the action grid. */
@Composable
internal fun RecordAction(ui: RecordingUi, onClick: () -> Unit, size: Dp, modifier: Modifier) {
    val recording = ui.isRecording
    CallActionButton(
        icon = Icons.Filled.FiberManualRecord,
        label = stringResource(if (recording) R.string.recording_stop_description else R.string.recording_record),
        onClick = onClick,
        // The label already says what the button does (Record / Stop recording); it is not an on/off switch for TalkBack, but
        // it looks "on" while recording.
        active = recording,
        enabled = recording || !ui.isBusy,
        size = size,
        modifier = modifier
    )
}
