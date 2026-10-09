package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SegmentedChoice
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.feature.recents.dateTimeText

/** Keeps the last rows clear of the add button. */
private val FabClearance = 88.dp

/** The user's spam list or the allowed numbers, depending on the route argument. */
@Composable
fun SpamListRoute(onBack: () -> Unit, viewModel: SpamListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val isOwn = state.list == ListType.OWN

    ScreenScaffold(
        title = stringResource(if (isOwn) R.string.spam_own_list_title else R.string.spam_allowed_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.clearError()
                    adding = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.spam_add)) }
        }
    ) { padding ->
        if (state.entries.isEmpty() && !state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = if (isOwn) Icons.Filled.Block else Icons.Filled.VerifiedUser,
                    title = stringResource(if (isOwn) R.string.spam_own_list_title else R.string.spam_allowed_title),
                    body = stringResource(if (isOwn) R.string.spam_own_list_empty else R.string.spam_allowed_empty)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = FabClearance)) {
                items(state.entries.size, key = { state.entries[it].id }) { index ->
                    val entry = state.entries[index]
                    GroupedItem(index = index, count = state.entries.size) { EntryRow(entry, onRemove = { viewModel.remove(entry) }) }
                }
            }
        }
    }

    if (adding) {
        AddEntryDialog(
            error = state.error,
            onDismiss = { adding = false },
            onSave = { kind, value, label, note -> if (viewModel.add(kind, value, label, note)) adding = false }
        )
    }
}

@Composable
private fun EntryRow(entry: ListEntry, onRemove: () -> Unit) {
    val title = if (entry.kind == EntryKind.PREFIX) stringResource(R.string.spam_entry_prefix, entry.value) else entry.value
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.RowHeight).padding(start = Spacing.Medium, top = Spacing.Small, bottom = Spacing.Small, end = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            entry.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            entry.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                stringResource(R.string.spam_entry_added, dateTimeText(entry.updatedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.spam_remove_entry, title)) }
    }
}

@Composable
private fun AddEntryDialog(error: ListInputError?, onDismiss: () -> Unit, onSave: (EntryKind, String, String, String) -> Unit) {
    var kind by remember { mutableStateOf(EntryKind.NUMBER) }
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.spam_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                SegmentedChoice(
                    options = listOf(EntryKind.NUMBER, EntryKind.PREFIX),
                    selected = kind,
                    onSelected = { kind = it },
                    label = { stringResource(if (it == EntryKind.NUMBER) R.string.spam_kind_number else R.string.spam_kind_prefix) }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(if (kind == EntryKind.NUMBER) R.string.spam_field_number else R.string.spam_field_prefix)) },
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(stringResource(if (it == ListInputError.INVALID_NUMBER) R.string.spam_error_number else R.string.spam_error_prefix)) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.spam_field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.spam_field_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kind, value, label, note) }, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                Text(stringResource(R.string.spam_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.spam_cancel)) }
        }
    )
}
