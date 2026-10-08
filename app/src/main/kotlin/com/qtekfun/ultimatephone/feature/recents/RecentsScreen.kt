package com.qtekfun.ultimatephone.feature.recents

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.designsystem.Avatar
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.telecom.SimAccount

private enum class Confirm { DELETE_SELECTED, CLEAR_ALL }

/** The call history tab. Nothing is read until the call log permissions are granted. */
@Composable
fun RecentsRoute(onOpenDetail: (number: String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        rationale = stringResource(R.string.recents_permission_rationale),
        buttonLabel = stringResource(R.string.recents_permission_button)
    ) {
        // Created only after the grant: the call log would otherwise be read once, empty, and never again.
        RecentsContent(viewModel = hiltViewModel(), onOpenDetail = onOpenDetail)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentsContent(viewModel: RecentsViewModel, onOpenDetail: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val callFailed = stringResource(R.string.recents_call_failed)
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(callFailed) }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.refreshContacts()
        // New entries stay highlighted while the tab is open and are marked as seen when the user leaves it.
        onStopOrDispose { viewModel.markAllRead() }
    }
    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (state.isSelecting) {
                SelectionBar(count = state.selected.size, onClose = viewModel::clearSelection, onDelete = { confirm = Confirm.DELETE_SELECTED })
            } else {
                RecentsBar(canClear = state.sections.isNotEmpty(), onClear = { confirm = Confirm.CLEAR_ALL })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // The surrounding scaffold already pads for the system bars.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(state = state, onSelect = viewModel::setFilter)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.isEmpty -> EmptyState()
                else -> HistoryList(state = state, viewModel = viewModel, onOpenDetail = onOpenDetail)
            }
        }
    }

    when (confirm) {
        Confirm.DELETE_SELECTED -> ConfirmDialog(
            title = R.string.recents_delete_selected_title,
            body = R.string.recents_delete_selected_body,
            confirmLabel = R.string.recents_delete,
            onConfirm = viewModel::deleteSelected,
            onDismiss = { confirm = null }
        )
        Confirm.CLEAR_ALL -> ConfirmDialog(
            title = R.string.recents_clear_title,
            body = R.string.recents_clear_body,
            confirmLabel = R.string.recents_clear_confirm,
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirm = null }
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, confirmLabel: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recents_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentsBar(canClear: Boolean, onClear: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.nav_recents)) },
        windowInsets = WindowInsets(0),
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.recents_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recents_clear_history)) },
                        enabled = canClear,
                        onClick = {
                            menuOpen = false
                            onClear()
                        }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.recents_selected_count, count)) },
        windowInsets = WindowInsets(0),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recents_cancel_selection)) }
        },
        actions = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recents_delete_selected)) }
        }
    )
}

@Composable
private fun FilterRow(state: RecentsUiState, onSelect: (CallLogFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val basics = listOf(
            CallLogFilter.All to R.string.recents_filter_all,
            CallLogFilter.Missed to R.string.recents_filter_missed,
            CallLogFilter.Incoming to R.string.recents_filter_incoming,
            CallLogFilter.Outgoing to R.string.recents_filter_outgoing
        )
        items(basics) { (filter, label) ->
            FilterChip(selected = state.filter == filter, onClick = { onSelect(filter) }, label = { Text(stringResource(label)) })
        }
        if (state.isDualSim) {
            items(state.sims, key = { it.key }) { sim ->
                val filter = sim.toFilter()
                FilterChip(selected = state.filter == filter, onClick = { onSelect(filter) }, label = { Text(simText(sim)) })
            }
        }
    }
}

private fun SimAccount.toFilter() = CallLogFilter.BySim(handle.componentName.flattenToString(), handle.id)

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.recents_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(state: RecentsUiState, viewModel: RecentsViewModel, onOpenDetail: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        state.sections.forEach { section ->
            stickyHeader(key = "header-${section.section}") { SectionHeader(section.section.text()) }
            items(section.rows, key = { it.key }) { row ->
                val isSelected = row.key in state.selected
                RecentsRowItem(
                    row = row,
                    showSim = state.isDualSim,
                    selected = isSelected,
                    onClick = { if (state.isSelecting) viewModel.toggleSelection(row.key) else viewModel.callBack(row) },
                    onLongClick = { viewModel.toggleSelection(row.key) },
                    onInfo = { onOpenDetail(row.number) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentsRowItem(row: RecentsRow, showSim: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onInfo: () -> Unit) {
    val title = row.caller.title ?: stringResource(R.string.recents_unknown_caller)
    val typeLabel = stringResource(row.type.label())
    val supporting = if (row.count > 1) "$typeLabel · ${stringResource(R.string.recents_count_calls, row.count)}" else typeLabel
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ListItem(
        modifier = Modifier
            .semantics { this.selected = selected }
            .combinedClickable(
                onClickLabel = stringResource(if (row.isPrivate) R.string.recents_select else R.string.recents_call_back),
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.recents_select),
                onLongClick = onLongClick
            ),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = { RowAvatar(row = row, title = title, selected = selected) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CallTypeIcon(row.type, Modifier.size(16.dp))
                    Text(text = supporting, color = row.type.tint())
                }
                val sim = row.sim
                if (showSim && sim != null) SimBadge(sim)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = timeText(row.dateMillis), style = MaterialTheme.typography.labelMedium)
                if (!row.isPrivate) {
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.recents_details_for, title))
                    }
                }
            }
        }
    ) {
        Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (row.isNew) FontWeight.Bold else null)
    }
}

@Composable
private fun RowAvatar(row: RecentsRow, title: String, selected: Boolean) {
    if (selected) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.recents_selected),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    } else {
        Avatar(name = title) {
            row.caller.contact?.photoThumbUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
