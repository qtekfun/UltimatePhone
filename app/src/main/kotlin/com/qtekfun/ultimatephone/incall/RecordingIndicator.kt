package com.qtekfun.ultimatephone.incall

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.recording.RecordingFormatters
import com.qtekfun.ultimatephone.core.recording.SessionState
import com.qtekfun.ultimatephone.feature.recording.failureText
import com.qtekfun.ultimatephone.recording.RecordingUi
import kotlinx.coroutines.delay

private val MIN_TARGET = 48.dp
private const val TICK_MS = 1000L

/**
 * What the call screen shows about recording: a red dot with the elapsed time and a Stop button while recording, the
 * reason when a recording failed, and (once) the warning that only the microphone is recorded on this phone.
 */
@Composable
internal fun RecordingStatus(ui: RecordingUi, onStop: () -> Unit, onDismissWarning: () -> Unit, onDismissError: () -> Unit) {
    val session = ui.session
    if (session !is SessionState.Recording && session !is SessionState.Failed && !ui.micWarning) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (session) {
            is SessionState.Recording -> RecordingBar(session.startedAtMillis, onStop)
            is SessionState.Failed -> NoticeCard(
                text = stringResource(failureText(session.reason)),
                action = stringResource(R.string.recording_dismiss),
                onAction = onDismissError,
                error = true
            )
            else -> Unit
        }
        if (ui.micWarning) {
            NoticeCard(
                title = stringResource(R.string.recording_mic_warning_title),
                text = stringResource(R.string.recording_mic_warning_text),
                action = stringResource(R.string.recording_mic_warning_ok),
                onAction = onDismissWarning
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
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The state is never colour alone: a dot, the word "Recording" and the elapsed time. The word is a polite live region so
        // TalkBack announces it when recording starts; the ticking time is not, or it would be read every second.
        Spacer(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape).clearAndSetSemantics {})
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.recording_indicator_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.width(8.dp))
        Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        val stopDescription = stringResource(R.string.recording_stop_description)
        TextButton(onClick = onStop, modifier = Modifier.heightIn(min = MIN_TARGET).semantics { contentDescription = stopDescription }) {
            Text(stringResource(R.string.recording_stop))
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onAction: () -> Unit, title: String? = null, error: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End).heightIn(min = MIN_TARGET)) { Text(action) }
        }
    }
}

/** The Record / Stop button of the action grid. */
@Composable
internal fun RecordAction(ui: RecordingUi, onClick: () -> Unit, size: Dp, modifier: Modifier) {
    val recording = ui.isRecording
    ToggleAction(
        icon = Icons.Filled.FiberManualRecord,
        description = stringResource(if (recording) R.string.recording_stop_description else R.string.recording_record),
        checked = recording,
        onClick = onClick,
        size = size,
        modifier = modifier,
        enabled = recording || !ui.isBusy,
        // The label already says what the button does (Record / Stop recording); it is not an on/off switch.
        toggle = false
    )
}
