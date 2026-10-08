package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.feature.recents.dateTimeText

/** The user's spam list or the allowed numbers, depending on the route argument. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamListRoute(onBack: () -> Unit, viewModel: SpamListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val isOwn = state.list == ListType.OWN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isOwn) R.string.spam_own_list_title else R.string.spam_allowed_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.spam_back)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.clearError()
                adding = true
            }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.spam_add)) }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        if (state.entries.isEmpty() && !state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (isOwn) R.string.spam_own_list_empty else R.string.spam_allowed_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(state.entries, key = { it.id }) { entry -> EntryRow(entry, onRemove = { viewModel.remove(entry) }) }
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
    ListItem(
        supportingContent = {
            Column {
                entry.label?.let { Text(it) }
                entry.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(stringResource(R.string.spam_entry_added, dateTimeText(entry.updatedAt)), style = MaterialTheme.typography.labelMedium)
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.spam_remove_entry, title)) }
        }
    ) { Text(title) }
}

@Composable
private fun AddEntryDialog(error: ListInputError?, onDismiss: () -> Unit, onSave: (EntryKind, String, String, String) -> Unit) {
    var kind by remember { mutableStateOf(EntryKind.NUMBER) }
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spam_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == EntryKind.NUMBER, onClick = {
                        kind = EntryKind.NUMBER
                    }, label = { Text(stringResource(R.string.spam_kind_number)) })
                    FilterChip(selected = kind == EntryKind.PREFIX, onClick = {
                        kind = EntryKind.PREFIX
                    }, label = { Text(stringResource(R.string.spam_kind_prefix)) })
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(if (kind == EntryKind.NUMBER) R.string.spam_field_number else R.string.spam_field_prefix)) },
                    isError = error != null,
                    supportingText = error?.let {
                        {
                            Text(
                                stringResource(
                                    if (it ==
                                        ListInputError.INVALID_NUMBER
                                    ) {
                                        R.string.spam_error_number
                                    } else {
                                        R.string.spam_error_prefix
                                    }
                                )
                            )
                        }
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
        confirmButton = { TextButton(onClick = { onSave(kind, value, label, note) }) { Text(stringResource(R.string.spam_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.spam_cancel)) } }
    )
}
