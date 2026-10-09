package com.qtekfun.ultimatephone.feature.recents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import com.qtekfun.ultimatephone.core.designsystem.AvatarStyled
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SectionHeader
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.feature.data.BusinessCategoryLine
import com.qtekfun.ultimatephone.feature.data.businessIcon

private val AvatarSize = 96.dp

/** Detail of one number: who it is, what can be done with it, how often it called, and every call. */
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

    ScreenScaffold(
        title = stringResource(R.string.recents_detail_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        CallDetailContent(state = state, handlers = handlers, padding = padding)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.recents_delete_number_title),
            body = stringResource(R.string.recents_delete_number_body),
            confirmLabel = stringResource(R.string.recents_delete),
            dismissLabel = stringResource(R.string.recents_cancel),
            onConfirm = viewModel::deleteHistory,
            onDismiss = { confirmDelete = false },
            destructive = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallDetailContent(state: CallDetailUiState, handlers: DetailHandlers, padding: PaddingValues) {
    val caller = state.caller
    val title = caller?.title ?: stringResource(R.string.recents_unknown_caller)
    val actions = detailActions(caller, state.isPrivate, state.entries.isNotEmpty(), handlers, state.spam)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "header") { HeaderCard(caller = caller, title = title) }
        if (actions.isNotEmpty()) {
            item(key = "actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    actions.forEach { action -> ActionButton(action) }
                }
            }
        }
        item(key = "stats") { StatsGroup(state.stats) }
        item(key = "calls-header") { SectionHeader(stringResource(R.string.recents_calls_heading)) }
        items(state.entries.size, key = { "call-${state.entries[it].id}" }) { index ->
            GroupedItem(index = index, count = state.entries.size) { CallEntryRow(state.entries[index]) }
        }
        item(key = "end") { Spacer(Modifier.size(Spacing.Medium)) }
    }
}

@Composable
private fun HeaderCard(caller: CallerInfo?, title: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            val business = caller?.business
            AvatarStyled(
                name = caller?.title,
                size = AvatarSize,
                business = business != null,
                businessIcon = businessIcon(business?.iconName)
            ) {
                caller?.contact?.photoThumbUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            business?.let { BusinessCategoryLine(it.category, it.iconName) }
            if (caller != null && caller.displayName != null && caller.formattedNumber.isNotBlank()) {
                Text(text = caller.formattedNumber, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionButton(action: DetailAction) {
    val modifier = Modifier.heightIn(min = Spacing.MinTarget)
    val content: @Composable RowScope.() -> Unit = {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(action.label), modifier = Modifier.padding(start = Spacing.Small))
    }
    when (action.id) {
        DetailActionId.CALL -> Button(onClick = action.onClick, modifier = modifier, content = content)
        DetailActionId.DELETE -> FilledTonalButton(
            onClick = action.onClick,
            modifier = modifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            content = content
        )
        else -> FilledTonalButton(onClick = action.onClick, modifier = modifier, content = content)
    }
}

@Composable
private fun StatsGroup(stats: CallStats) {
    SettingsGroup(
        title = stringResource(R.string.recents_stats_heading),
        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
    ) {
        StatLine(R.string.recents_stats_total, stats.total.toString())
        StatLine(R.string.recents_stats_missed, stats.missed.toString())
        StatLine(R.string.recents_stats_incoming, stats.incoming.toString())
        StatLine(R.string.recents_stats_outgoing, stats.outgoing.toString())
        StatLine(R.string.recents_stats_duration, formatDurationClock(stats.totalDurationSeconds))
    }
}

@Composable
private fun StatLine(label: Int, value: String) {
    SettingsRow(
        title = stringResource(label),
        modifier = Modifier.heightIn(min = Spacing.MinTarget),
        trailing = { Text(text = value, style = MaterialTheme.typography.titleMedium) }
    )
}

@Composable
private fun CallEntryRow(entry: CallLogEntry) {
    Row(
        // One item for TalkBack: type, date, SIM and duration.
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.RowHeight).semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        CallTypeIcon(entry.type, Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(entry.type.label()), style = MaterialTheme.typography.titleMedium, color = entry.type.tint())
            Text(dateTimeText(entry.dateMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sim = entry.sim
            if (sim != null) StatusChip(text = simText(sim), modifier = Modifier.padding(top = Spacing.XSmall))
        }
        if (entry.durationSeconds > 0) Text(formatDurationClock(entry.durationSeconds), style = MaterialTheme.typography.labelLarge)
    }
}
