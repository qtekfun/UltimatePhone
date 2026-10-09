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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.designsystem.AvatarStyled
import com.qtekfun.ultimatephone.core.designsystem.ChoiceChip
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SectionHeader
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.feature.data.BusinessCategoryLine
import com.qtekfun.ultimatephone.feature.data.businessIcon
import com.qtekfun.ultimatephone.feature.spam.SpamBadge

private enum class Confirm { DELETE_SELECTED, CLEAR_ALL }

private val RowAvatarSize = Spacing.MinTarget

/** The call history tab. Nothing is read until the call log permissions are granted. */
@Composable
fun RecentsRoute(onOpenDetail: (number: String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        rationale = stringResource(R.string.recents_permission_rationale),
        buttonLabel = stringResource(R.string.recents_permission_button),
        icon = Icons.Filled.History
    ) {
        // Created only after the grant: the call log would otherwise be read once, empty, and never again.
        RecentsContent(viewModel = hiltViewModel(), onOpenDetail = onOpenDetail)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    ScreenScaffold(
        title = if (state.isSelecting) {
            pluralStringResource(R.plurals.recents_selected_count, state.selected.size, state.selected.size)
        } else {
            stringResource(R.string.nav_recents)
        },
        actions = {
            if (state.isSelecting) {
                SelectionActions(onClose = viewModel::clearSelection, onDelete = { confirm = Confirm.DELETE_SELECTED })
            } else {
                MoreMenu(canClear = state.sections.isNotEmpty(), onClear = { confirm = Confirm.CLEAR_ALL })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            state.isEmpty -> Box(Modifier.padding(padding).fillMaxSize()) {
                Column {
                    FilterRow(state = state, onSelect = viewModel::setFilter)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.design2_recents_empty_title),
                            body = stringResource(R.string.recents_empty)
                        )
                    }
                }
            }
            else -> HistoryList(state = state, padding = padding, viewModel = viewModel, onOpenDetail = onOpenDetail)
        }
    }

    when (confirm) {
        Confirm.DELETE_SELECTED -> ConfirmDialog(
            title = stringResource(R.string.recents_delete_selected_title),
            body = stringResource(R.string.recents_delete_selected_body),
            confirmLabel = stringResource(R.string.recents_delete),
            dismissLabel = stringResource(R.string.recents_cancel),
            onConfirm = viewModel::deleteSelected,
            onDismiss = { confirm = null },
            destructive = true
        )
        Confirm.CLEAR_ALL -> ConfirmDialog(
            title = stringResource(R.string.recents_clear_title),
            body = stringResource(R.string.recents_clear_body),
            confirmLabel = stringResource(R.string.recents_clear_confirm),
            dismissLabel = stringResource(R.string.recents_cancel),
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirm = null },
            destructive = true
        )
        null -> Unit
    }
}

@Composable
private fun MoreMenu(canClear: Boolean, onClear: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.recents_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = MaterialTheme.shapes.medium) {
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

@Composable
private fun SelectionActions(onClose: () -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recents_delete_selected)) }
    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recents_cancel_selection)) }
}

@Composable
private fun FilterRow(state: RecentsUiState, onSelect: (CallLogFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        val basics = listOf(
            CallLogFilter.All to R.string.recents_filter_all,
            CallLogFilter.Missed to R.string.recents_filter_missed,
            CallLogFilter.Incoming to R.string.recents_filter_incoming,
            CallLogFilter.Outgoing to R.string.recents_filter_outgoing,
            CallLogFilter.Spam to R.string.spam_filter_chip
        )
        items(basics) { (filter, label) ->
            ChoiceChip(label = stringResource(label), selected = state.filter == filter, onClick = { onSelect(filter) })
        }
        if (state.isDualSim) {
            items(state.sims, key = { it.key }) { sim ->
                val filter = sim.toFilter()
                ChoiceChip(label = simText(sim), selected = state.filter == filter, onClick = { onSelect(filter) })
            }
        }
    }
}

private fun SimAccount.toFilter() = CallLogFilter.BySim(handle.componentName.flattenToString(), handle.id)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(state: RecentsUiState, padding: PaddingValues, viewModel: RecentsViewModel, onOpenDetail: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "filters") { FilterRow(state = state, onSelect = viewModel::setFilter) }
        state.sections.forEach { section ->
            stickyHeader(key = "header-${section.section}") {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { SectionHeader(section.section.text()) }
            }
            items(section.rows.size, key = { section.rows[it].key }) { index ->
                val row = section.rows[index]
                val isSelected = row.key in state.selected
                RecentsRowItem(
                    row = row,
                    index = index,
                    count = section.rows.size,
                    showSim = state.isDualSim,
                    selected = isSelected,
                    onClick = { if (state.isSelecting) viewModel.toggleSelection(row.key) else viewModel.callBack(row) },
                    onLongClick = { viewModel.toggleSelection(row.key) },
                    onInfo = { onOpenDetail(row.number) }
                )
            }
        }
        item(key = "end") { Box(Modifier.size(Spacing.Medium)) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun RecentsRowItem(
    row: RecentsRow,
    index: Int,
    count: Int,
    showSim: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit
) {
    val title = row.caller.title ?: stringResource(R.string.recents_unknown_caller)
    val typeLabel = stringResource(row.type.label())
    val supporting = if (row.count > 1) "$typeLabel · ${stringResource(R.string.recents_count_calls, row.count)}" else typeLabel
    val newState = stringResource(R.string.recents_new)
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    GroupedItem(index = index, count = count, color = containerColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.RowHeight)
                .semantics {
                    this.selected = selected
                    if (row.isNew && !selected) stateDescription = newState
                }
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (row.isPrivate) R.string.recents_select else R.string.recents_call_back),
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.recents_select),
                    onLongClick = onLongClick
                )
                .padding(start = Spacing.Medium, top = Spacing.Small, bottom = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            RowAvatar(row = row, selected = selected)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (row.isNew) FontWeight.Bold else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                        // The icon and the word say what kind of call it was; the error colour only adds to a missed call.
                        CallTypeIcon(row.type, Modifier.size(16.dp))
                        Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = row.type.tint())
                    }
                    row.caller.business?.let { BusinessCategoryLine(it.category, it.iconName) }
                    val sim = row.sim
                    if (showSim && sim != null) SimBadge(sim)
                    row.spam?.let { SpamBadge(it) }
                }
            }
            Text(text = timeText(row.dateMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!row.isPrivate) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.recents_details_for, title))
                }
            } else {
                Box(Modifier.size(Spacing.Small))
            }
        }
    }
}

@Composable
private fun RowAvatar(row: RecentsRow, selected: Boolean) {
    if (selected) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RowAvatarSize)) {
            Box(contentAlignment = Alignment.Center) {
                // Decorative: the row itself carries the "selected" state.
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    } else {
        val business = row.caller.business
        AvatarStyled(
            name = row.caller.title,
            size = RowAvatarSize,
            business = business != null,
            businessIcon = businessIcon(business?.iconName)
        ) {
            row.caller.contact?.photoThumbUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
