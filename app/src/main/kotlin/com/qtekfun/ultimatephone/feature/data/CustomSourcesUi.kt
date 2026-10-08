package com.qtekfun.ultimatephone.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.data_sources_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.settings.customSources.isEmpty()) Text(stringResource(R.string.data_sources_empty), style = MaterialTheme.typography.bodyMedium)
        state.settings.customSources.forEach { source ->
            SourceCard(
                source = source,
                onEnabled = { viewModel.setSourceEnabled(source.id, it) },
                onRefresh = { viewModel.refreshSource(source.id) },
                onRemove = { removing = source }
            )
        }
        OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.data_source_add)) }
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
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.data_source_remove_title)) },
            text = { Text(stringResource(R.string.data_source_remove_body, source.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSource(source.id)
                    removing = null
                }) { Text(stringResource(R.string.data_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.data_cancel)) } }
        )
    }
}

@Composable
private fun SourceCard(source: CustomSource, onEnabled: (Boolean) -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(source.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Switch(checked = source.enabled, onCheckedChange = onEnabled)
            }
            val updated = source.lastUpdateMillis
            Text(
                if (updated !=
                    null
                ) {
                    stringResource(R.string.data_source_updated, dateTimeText(updated), source.entries)
                } else {
                    stringResource(R.string.data_source_never)
                },
                style = MaterialTheme.typography.bodySmall
            )
            source.lastError?.let { Text(errorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row {
                TextButton(onClick = onRefresh, enabled = source.enabled) { Text(stringResource(R.string.data_source_refresh)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.data_remove)) }
            }
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
        title = { Text(stringResource(R.string.data_source_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.data_source_name)) }, singleLine = true)
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
                    singleLine = true
                )
                Text(stringResource(R.string.data_source_format), style = MaterialTheme.typography.labelLarge)
                FORMATS.forEach { f -> RadioLine(stringResource(formatLabel(f)), format == f) { format = f } }
                Text(stringResource(R.string.data_source_level), style = MaterialTheme.typography.labelLarge)
                LEVELS.forEach { l -> RadioLine(stringResource(levelLabel(l)), level == l) { level = l } }
                TestResultLine(test)
                OutlinedButton(onClick = { viewModel.testSource(url, format) }, enabled = valid && test !is SourceTestState.Running) {
                    Text(stringResource(if (test is SourceTestState.Running) R.string.data_source_testing else R.string.data_source_test))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    viewModel.addSource(name, url, format, level)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.data_source_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.data_cancel)) } }
    )
}

@Composable
private fun TestResultLine(test: SourceTestState) {
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

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
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
