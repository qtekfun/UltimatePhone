package com.qtekfun.ultimatephone.feature.recents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.calllog.CallLogEntry
import com.qtekfun.ultimatephone.core.calllog.CallStats
import com.qtekfun.ultimatephone.core.calllog.formatDurationClock
import com.qtekfun.ultimatephone.core.designsystem.Avatar

/** Detail of one number: who it is, what can be done with it, how often it called, and every call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailRoute(
    onBack: () -> Unit,
    onCreateContact: (number: String) -> Unit,
    onAddToContact: (lookupKey: String, number: String) -> Unit,
    onOpenContact: (lookupKey: String) -> Unit,
    onEditContact: (lookupKey: String) -> Unit,
    onWhyFlagged: (number: String) -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val callFailed = stringResource(R.string.recents_call_failed)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CallDetailEvent.CallFailed -> snackbar.showSnackbar(callFailed)
                CallDetailEvent.Deleted -> onBack()
            }
        }
    }
    // Coming back from creating or editing a contact: the match may have changed.
    LifecycleStartEffect(viewModel) {
        viewModel.refreshContacts()
        onStopOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        val key = uri?.let { lookupKeyFromContactUri(it.toString()) }?.let(Uri::decode)
        if (key != null) onAddToContact(key, state.number)
    }
    val handlers = DetailHandlers(
        onCall = viewModel::call,
        onCreateContact = { onCreateContact(state.number) },
        onAddToExistingContact = { picker.launch(null) },
        onOpenContact = onOpenContact,
        onEditContact = onEditContact,
        onDelete = { confirmDelete = true },
        onMarkSpam = viewModel::markSpam,
        onRemoveSpam = viewModel::removeFromSpam,
        onAllow = viewModel::allow,
        onWhyFlagged = { onWhyFlagged(state.number) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recents_detail_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recents_back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        CallDetailContent(state = state, handlers = handlers, modifier = Modifier.padding(padding))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recents_delete_number_title)) },
            text = { Text(stringResource(R.string.recents_delete_number_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteHistory()
                }) { Text(stringResource(R.string.recents_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.recents_cancel)) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CallDetailContent(state: CallDetailUiState, handlers: DetailHandlers, modifier: Modifier = Modifier) {
    val caller = state.caller
    val title = caller?.title ?: stringResource(R.string.recents_unknown_caller)
    val actions = detailActions(caller, state.isPrivate, state.entries.isNotEmpty(), handlers, state.spam)
    LazyColumn(modifier.fillMaxSize()) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Avatar(name = title, size = 96.dp) {
                    caller?.contact?.photoThumbUri?.let { uri ->
                        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (caller != null && caller.displayName != null && caller.formattedNumber.isNotBlank()) {
                    Text(text = caller.formattedNumber, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (actions.isNotEmpty()) {
            item(key = "actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.forEach { action ->
                        FilledTonalButton(onClick = action.onClick) {
                            Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(action.label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        item(key = "stats") { StatsCard(state.stats) }
        item(key = "calls-header") {
            Text(
                text = stringResource(R.string.recents_calls_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }
            )
        }
        items(state.entries, key = { "call-${it.id}" }) { CallEntryRow(it) }
    }
}

@Composable
private fun StatsCard(stats: CallStats) {
    ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.recents_stats_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            StatLine(R.string.recents_stats_total, stats.total.toString())
            StatLine(R.string.recents_stats_missed, stats.missed.toString())
            StatLine(R.string.recents_stats_incoming, stats.incoming.toString())
            StatLine(R.string.recents_stats_outgoing, stats.outgoing.toString())
            StatLine(R.string.recents_stats_duration, formatDurationClock(stats.totalDurationSeconds))
        }
    }
}

@Composable
private fun StatLine(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = stringResource(label), modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CallEntryRow(entry: CallLogEntry) {
    ListItem(
        leadingContent = { CallTypeIcon(entry.type, Modifier.size(24.dp)) },
        supportingContent = {
            Column {
                Text(dateTimeText(entry.dateMillis))
                val sim = entry.sim
                if (sim != null) Text(simText(sim))
            }
        },
        trailingContent = {
            if (entry.durationSeconds > 0) Text(formatDurationClock(entry.durationSeconds), style = MaterialTheme.typography.labelLarge)
        }
    ) {
        Text(stringResource(entry.type.label()), color = entry.type.tint())
    }
}
