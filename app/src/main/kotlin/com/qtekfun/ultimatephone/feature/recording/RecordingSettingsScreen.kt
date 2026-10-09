package com.qtekfun.ultimatephone.feature.recording

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.RadioRow
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingFormat
import com.qtekfun.ultimatephone.core.recording.RecordingMode
import com.qtekfun.ultimatephone.recording.MicTestResult

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)

/** Settings > Call recording. */
@Composable
fun RecordingSettingsRoute(onBack: () -> Unit, onOpenRecordings: () -> Unit, viewModel: RecordingSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var legalDialog by remember { mutableStateOf(false) }
    var numbersDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.folderChosen(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    ScreenScaffold(title = stringResource(R.string.recording_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            SettingsGroup(modifier = GroupModifier) {
                SwitchRow(
                    title = stringResource(R.string.recording_enable),
                    icon = Icons.Filled.Mic,
                    checked = state.config.enabled,
                    onCheckedChange = { on ->
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
            }
            ReadinessBanner(state.readiness, state.config.mode != RecordingMode.OFF)
            if (state.config.enabled && !state.hasPermission) {
                Button(
                    onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = GroupModifier.heightIn(min = Spacing.MinTarget)
                ) { Text(stringResource(R.string.recording_allow_microphone)) }
            }

            InfoBanner(
                kind = BannerKind.Info,
                title = stringResource(R.string.recording_capability_title),
                body = stringResource(capabilityText(state.capability)),
                modifier = GroupModifier,
                action = if (state.config.capability != null) {
                    UiAction(stringResource(R.string.recording_capability_recheck), viewModel::recheckCapability)
                } else {
                    null
                }
            )

            SettingsGroup(title = stringResource(R.string.recording_folder_title), modifier = GroupModifier) {
                val folderText = when {
                    state.folderLost -> stringResource(R.string.recording_folder_lost)
                    state.config.folderUri == null -> stringResource(R.string.recording_folder_none)
                    else -> state.folderName ?: stringResource(R.string.recording_folder_none)
                }
                SettingsRow(
                    title = folderText,
                    summary = stringResource(if (state.config.folderUri == null) R.string.recording_folder_choose else R.string.recording_folder_change),
                    icon = Icons.Filled.Folder,
                    onClick = { folderPicker.launch(null) }
                )
            }

            ModeGroup(state, viewModel, onManageNumbers = { numbersDialog = true })

            SettingsGroup(title = stringResource(R.string.recording_format_title), modifier = GroupModifier) {
                RecordingFormat.entries.forEach { format ->
                    RadioRow(
                        title = stringResource(formatLabel(format)),
                        selected = state.config.format == format,
                        onClick = { viewModel.setFormat(format) }
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.recording_options_title), modifier = GroupModifier) {
                SwitchRow(
                    title = stringResource(R.string.recording_contact_name),
                    summary = stringResource(R.string.recording_contact_name_summary),
                    icon = Icons.Filled.Subtitles,
                    checked = state.config.includeContactName,
                    onCheckedChange = viewModel::setIncludeContactName
                )
                SwitchRow(
                    title = stringResource(R.string.recording_auto_speaker),
                    summary = stringResource(R.string.recording_auto_speaker_summary),
                    icon = Icons.Filled.VolumeUp,
                    checked = state.config.autoSpeaker,
                    onCheckedChange = viewModel::setAutoSpeaker
                )
                SwitchRow(
                    title = stringResource(R.string.recording_announce),
                    summary = stringResource(R.string.recording_announce_summary),
                    icon = Icons.Filled.RecordVoiceOver,
                    checked = state.config.announce,
                    onCheckedChange = viewModel::setAnnounce
                )
            }

            SettingsGroup(title = stringResource(R.string.recording_test_title), modifier = GroupModifier) {
                SettingsRow(
                    title = stringResource(R.string.recording_test),
                    icon = Icons.Filled.GraphicEq,
                    enabled = state.test != MicTestState.Running,
                    onClick = { if (state.hasPermission) viewModel.runTest() else permission.launch(Manifest.permission.RECORD_AUDIO) },
                    trailing = {}
                )
            }
            TestResultBanner(state.test)

            SettingsGroup(modifier = GroupModifier) {
                SettingsRow(
                    title = stringResource(R.string.recording_list_row),
                    summary = stringResource(R.string.recording_list_row_summary),
                    icon = Icons.Filled.PlaylistPlay,
                    onClick = onOpenRecordings
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
private fun ModeGroup(state: RecordingSettingsState, viewModel: RecordingSettingsViewModel, onManageNumbers: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SettingsGroup(title = stringResource(R.string.recording_mode_title), modifier = GroupModifier) {
            RecordingMode.entries.forEach { mode ->
                RadioRow(title = stringResource(modeLabel(mode)), selected = state.config.mode == mode, onClick = { viewModel.setMode(mode) })
            }
            if (state.config.mode == RecordingMode.SELECTED_CONTACTS) {
                SettingsRow(
                    title = stringResource(R.string.recording_selected_manage, state.config.selectedNumbers.size),
                    icon = Icons.Filled.Group,
                    onClick = onManageNumbers
                )
            }
        }
        if (state.config.mode != RecordingMode.OFF) {
            Text(
                stringResource(R.string.recording_auto_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = GroupModifier
            )
            if (state.config.autoStartBlocked) {
                InfoBanner(kind = BannerKind.Warning, title = stringResource(R.string.recording_auto_blocked), modifier = GroupModifier)
            }
        }
    }
}

@Composable
private fun ReadinessBanner(readiness: Readiness, auto: Boolean) {
    val reason = readinessText(readiness)
    val text = stringResource(reason ?: if (auto) R.string.recording_ready_auto else R.string.recording_ready)
    InfoBanner(
        kind = if (reason == null) BannerKind.Success else BannerKind.Warning,
        title = text,
        modifier = GroupModifier,
        liveRegion = true
    )
}

@Composable
private fun TestResultBanner(test: MicTestState) {
    val (kind, text) = when (test) {
        MicTestState.None -> return
        MicTestState.Running -> BannerKind.Info to stringResource(R.string.recording_testing)
        is MicTestState.Finished -> when (val result = test.result) {
            MicTestResult.Busy -> BannerKind.Warning to stringResource(R.string.recording_test_busy)
            MicTestResult.CouldNotStart -> BannerKind.Error to stringResource(R.string.recording_test_failed)
            is MicTestResult.Done ->
                if (result.peak > 0) {
                    BannerKind.Success to stringResource(R.string.recording_test_ok, result.peak)
                } else {
                    BannerKind.Warning to stringResource(R.string.recording_test_silent)
                }
        }
    }
    InfoBanner(kind = kind, title = text, modifier = GroupModifier, liveRegion = true)
}

@Composable
private fun LegalDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.recording_legal_title)) },
        text = { Text(stringResource(R.string.recording_legal_text), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onAccept, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.recording_legal_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.recording_legal_cancel)) }
        }
    )
}

@Composable
private fun NumbersDialog(numbers: Set<String>, onAdd: (String) -> Boolean, onRemove: (String) -> Unit, onDone: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.recording_selected_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
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
                        modifier = Modifier.heightIn(min = Spacing.MinTarget)
                    ) { Text(stringResource(R.string.recording_selected_add)) }
                }
                if (numbers.isEmpty()) Text(stringResource(R.string.recording_selected_empty), style = MaterialTheme.typography.bodyMedium)
                numbers.sorted().forEach { key ->
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget), verticalAlignment = Alignment.CenterVertically) {
                        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(key) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recording_selected_remove, key))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.recording_selected_done)) }
        }
    )
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
