package com.qtekfun.ultimatephone.feature.recording

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingFormat
import com.qtekfun.ultimatephone.core.recording.RecordingMode
import com.qtekfun.ultimatephone.recording.MicTestResult

private val MIN_ROW = 56.dp

/** Settings > Call recording. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsRoute(onBack: () -> Unit, onOpenRecordings: () -> Unit, viewModel: RecordingSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var legalDialog by remember { mutableStateOf(false) }
    var numbersDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.folderChosen(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recording_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recording_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SwitchRow(
                title = R.string.recording_enable,
                summary = null,
                checked = state.config.enabled,
                onChange = { on ->
                    if (!on) {
                        viewModel.disable()
                    } else if (!state.config.legalAccepted) {
                        legalDialog = true
                    } else {
                        viewModel.enable()
                        if (!state.hasPermission) permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            StatusCard(state.readiness, state.config.mode != RecordingMode.OFF)
            if (state.config.enabled && !state.hasPermission) {
                Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.recording_allow_microphone))
                }
            }

            Section(R.string.recording_capability_title)
            Text(stringResource(capabilityText(state.capability)), style = MaterialTheme.typography.bodyMedium)
            if (state.config.capability != null) {
                TextButton(onClick = viewModel::recheckCapability, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.recording_capability_recheck))
                }
            }

            Section(R.string.recording_folder_title)
            val folderText = when {
                state.folderLost -> stringResource(R.string.recording_folder_lost)
                state.config.folderUri == null -> stringResource(R.string.recording_folder_none)
                else -> state.folderName ?: stringResource(R.string.recording_folder_none)
            }
            Text(folderText, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (state.config.folderUri == null) R.string.recording_folder_choose else R.string.recording_folder_change))
            }

            Section(R.string.recording_mode_title)
            RecordingMode.entries.forEach { mode ->
                RadioRow(stringResource(modeLabel(mode)), state.config.mode == mode) { viewModel.setMode(mode) }
            }
            if (state.config.mode == RecordingMode.SELECTED_CONTACTS) {
                TextButton(onClick = { numbersDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.recording_selected_manage, state.config.selectedNumbers.size))
                }
            }
            if (state.config.mode != RecordingMode.OFF) {
                Text(
                    stringResource(R.string.recording_auto_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.config.autoStartBlocked) {
                    Text(stringResource(R.string.recording_auto_blocked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Section(R.string.recording_format_title)
            RecordingFormat.entries.forEach { format ->
                RadioRow(stringResource(formatLabel(format)), state.config.format == format) { viewModel.setFormat(format) }
            }

            Section(R.string.recording_options_title)
            SwitchRow(
                R.string.recording_contact_name,
                R.string.recording_contact_name_summary,
                state.config.includeContactName,
                viewModel::setIncludeContactName
            )
            SwitchRow(R.string.recording_auto_speaker, R.string.recording_auto_speaker_summary, state.config.autoSpeaker, viewModel::setAutoSpeaker)
            SwitchRow(R.string.recording_announce, R.string.recording_announce_summary, state.config.announce, viewModel::setAnnounce)

            Section(R.string.recording_test_title)
            OutlinedButton(
                onClick = { if (state.hasPermission) viewModel.runTest() else permission.launch(Manifest.permission.RECORD_AUDIO) },
                enabled = state.test != MicTestState.Running,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.recording_test)) }
            TestResultText(state.test)

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_ROW).clickable(role = Role.Button, onClick = onOpenRecordings).padding(vertical = 8.dp)
            ) {
                Text(stringResource(R.string.recording_list_row), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.recording_list_row_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (legalDialog) {
        LegalDialog(
            onAccept = {
                legalDialog = false
                viewModel.enable()
                if (!state.hasPermission) permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onCancel = { legalDialog = false }
        )
    }
    if (numbersDialog) {
        NumbersDialog(state.config.selectedNumbers, onAdd = viewModel::addNumber, onRemove = viewModel::removeNumber, onDone = { numbersDialog = false })
    }
}

@Composable
private fun StatusCard(readiness: Readiness, auto: Boolean) {
    val reason = readinessText(readiness)
    val text = stringResource(reason ?: if (auto) R.string.recording_ready_auto else R.string.recording_ready)
    val container = if (reason == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun TestResultText(test: MicTestState) {
    val text = when (test) {
        MicTestState.None -> return
        MicTestState.Running -> stringResource(R.string.recording_testing)
        is MicTestState.Finished -> when (val result = test.result) {
            MicTestResult.Busy -> stringResource(R.string.recording_test_busy)
            MicTestResult.CouldNotStart -> stringResource(R.string.recording_test_failed)
            is MicTestResult.Done ->
                if (result.peak > 0) stringResource(R.string.recording_test_ok, result.peak) else stringResource(R.string.recording_test_silent)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun LegalDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.recording_legal_title)) },
        text = { Text(stringResource(R.string.recording_legal_text), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.recording_legal_accept)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.recording_legal_cancel)) } }
    )
}

@Composable
private fun NumbersDialog(numbers: Set<String>, onAdd: (String) -> Boolean, onRemove: (String) -> Unit, onDone: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.recording_selected_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(R.string.recording_selected_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { if (onAdd(typed)) typed = "" },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.recording_selected_add)) }
                }
                if (numbers.isEmpty()) Text(stringResource(R.string.recording_selected_empty), style = MaterialTheme.typography.bodyMedium)
                numbers.sorted().forEach { key ->
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(key) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recording_selected_remove, key))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.recording_selected_done)) } }
    )
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun SwitchRow(title: Int, summary: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(
            min = MIN_ROW
        ).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_ROW).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun modeLabel(mode: RecordingMode): Int = when (mode) {
    RecordingMode.OFF -> R.string.recording_mode_off
    RecordingMode.ALL_CALLS -> R.string.recording_mode_all
    RecordingMode.UNKNOWN_NUMBERS -> R.string.recording_mode_unknown
    RecordingMode.SELECTED_CONTACTS -> R.string.recording_mode_selected
}

private fun formatLabel(format: RecordingFormat): Int = when (format) {
    RecordingFormat.M4A_AAC -> R.string.recording_format_m4a
    RecordingFormat.OGG_OPUS -> R.string.recording_format_ogg
}
