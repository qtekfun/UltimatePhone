package com.qtekfun.ultimatephone.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.SectionHeader
import com.qtekfun.ultimatephone.core.designsystem.SegmentedChoice
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import com.qtekfun.ultimatephone.data.CustomSource
import com.qtekfun.ultimatephone.data.SourceTestResult
import com.qtekfun.ultimatephone.data.SourceUrls

private val FORMATS = listOf(SourceFormat.TXT, SourceFormat.CSV, SourceFormat.JSONL)
private val LEVELS = listOf(SpamLevel.COMMUNITY, SpamLevel.RULE)

@Composable
internal fun CustomSourcesSection(state: DataUiState, viewModel: DataViewModel) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CustomSource?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Column {
            SectionHeader(stringResource(R.string.data_sources_title))
            Text(
                stringResource(R.string.data_sources_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.Medium)
            )
        }
        if (state.settings.customSources.isEmpty()) {
            Text(
                stringResource(R.string.data_sources_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.Medium)
            )
        }
        state.settings.customSources.forEach { source ->
            SourceGroup(
                source = source,
                onEnabled = { viewModel.setSourceEnabled(source.id, it) },
                onRefresh = { viewModel.refreshSource(source.id) },
                onRemove = { removing = source }
            )
        }
        SettingsGroup {
            SettingsRow(title = stringResource(R.string.data_source_add), icon = Icons.Filled.Add, onClick = { adding = true }, trailing = {})
        }
    }
    if (adding) {
        AddSourceDialog(
            viewModel = viewModel,
            onDismiss = {
                adding = false
                viewModel.resetSourceTest()
            }
        )
    }
    removing?.let { source ->
        ConfirmDialog(
            title = stringResource(R.string.data_source_remove_title),
            body = stringResource(R.string.data_source_remove_body, source.name),
            confirmLabel = stringResource(R.string.data_remove),
            dismissLabel = stringResource(R.string.data_cancel),
            onConfirm = { viewModel.removeSource(source.id) },
            onDismiss = { removing = null },
            destructive = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceGroup(source: CustomSource, onEnabled: (Boolean) -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    SettingsGroup {
        SwitchRow(title = source.name, summary = source.url, checked = source.enabled, onCheckedChange = onEnabled)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)
        ) {
            val updated = source.lastUpdateMillis
            Text(
                if (updated != null) {
                    pluralStringResource(R.plurals.data_source_updated, source.entries, dateTimeText(updated), source.entries)
                } else {
                    stringResource(R.string.data_source_never)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            source.lastError?.let { Text(errorText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        FlowRow(modifier = Modifier.padding(horizontal = Spacing.Small)) {
            // The name is part of what TalkBack says, so several sources can be told apart.
            val refreshLabel = stringResource(R.string.data_pack_action, stringResource(R.string.data_source_refresh), source.name)
            val removeLabel = stringResource(R.string.data_pack_action, stringResource(R.string.data_remove), source.name)
            TextButton(
                onClick = onRefresh,
                enabled = source.enabled,
                modifier = Modifier.heightIn(min = Spacing.MinTarget).semantics { contentDescription = refreshLabel }
            ) { Text(stringResource(R.string.data_source_refresh)) }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = Spacing.MinTarget).semantics { contentDescription = removeLabel }
            ) { Text(stringResource(R.string.data_remove)) }
        }
    }
}

@Composable
private fun AddSourceDialog(viewModel: DataViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(SourceFormat.TXT) }
    var level by remember { mutableStateOf(SpamLevel.COMMUNITY) }
    val test by viewModel.sourceTest.collectAsStateWithLifecycle()
    val valid = SourceUrls.isValid(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.data_source_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.data_source_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        viewModel.resetSourceTest()
                    },
                    label = { Text(stringResource(R.string.data_source_url)) },
                    isError = url.isNotEmpty() && !valid,
                    supportingText = { if (url.isNotEmpty() && !valid) Text(stringResource(R.string.data_error_invalid_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.data_source_format), style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(options = FORMATS, selected = format, onSelected = { format = it }, label = { stringResource(formatLabel(it)) })
                Text(stringResource(R.string.data_source_level), style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(options = LEVELS, selected = level, onSelected = { level = it }, label = { stringResource(levelLabel(it)) })
                TestResultLine(test)
                OutlinedButton(
                    onClick = { viewModel.testSource(url, format) },
                    enabled = valid && test !is SourceTestState.Running,
                    modifier = Modifier.heightIn(min = Spacing.MinTarget)
                ) {
                    Text(stringResource(if (test is SourceTestState.Running) R.string.data_source_testing else R.string.data_source_test))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                modifier = Modifier.heightIn(min = Spacing.MinTarget),
                onClick = {
                    viewModel.addSource(name, url, format, level)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.data_source_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.data_cancel)) }
        }
    )
}

@Composable
private fun TestResultLine(test: SourceTestState) {
    // The result appears below the button: TalkBack announces it by itself.
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { TestResultText(test) }
}

@Composable
private fun TestResultText(test: SourceTestState) {
    when (test) {
        is SourceTestState.Done -> when (val result = test.result) {
            is SourceTestResult.Ok -> Text(
                stringResource(R.string.data_source_test_ok, result.accepted, result.invalid, result.duplicates),
                style = MaterialTheme.typography.bodyMedium
            )
            is SourceTestResult.Failed -> Text(errorText(result.error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        else -> Unit
    }
}

private fun formatLabel(format: SourceFormat): Int = when (format) {
    SourceFormat.CSV -> R.string.data_format_csv
    SourceFormat.JSONL -> R.string.data_format_jsonl
    else -> R.string.data_format_txt
}

private fun levelLabel(level: SpamLevel): Int = when (level) {
    SpamLevel.RULE -> R.string.data_level_rule
    else -> R.string.data_level_community
}
