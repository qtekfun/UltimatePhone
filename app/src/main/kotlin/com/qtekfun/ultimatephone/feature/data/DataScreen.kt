package com.qtekfun.ultimatephone.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.data.PackCatalog
import com.qtekfun.ultimatephone.data.PackItem
import com.qtekfun.ultimatephone.data.PackProgress
import com.qtekfun.ultimatephone.data.RegionGroup
import com.qtekfun.ultimatephone.data.SizeFormat

internal const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
internal const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** Settings > Data: packs by region, updates, storage, custom sources and the OpenStreetMap attribution. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRoute(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val upToDate = stringResource(R.string.data_update_done)
    val noNetwork = stringResource(R.string.data_no_network)
    val wifiBlocked = stringResource(R.string.data_blocked_wifi)
    val failedPrefix = stringResource(R.string.data_update_failed)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    DataMessage.UpToDate -> upToDate
                    DataMessage.NoNetwork -> noNetwork
                    DataMessage.WifiOnlyBlocked -> wifiBlocked
                    is DataMessage.UpdateFailed -> failedPrefix
                }
            )
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.data_back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        DataContent(state = state, viewModel = viewModel, modifier = Modifier.padding(padding))
    }
    state.confirmMetered?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMetered,
            title = { Text(stringResource(R.string.data_metered_title)) },
            text = { Text(stringResource(R.string.data_metered_body, SizeFormat.format(state.confirmMeteredBytes))) },
            confirmButton = { TextButton(onClick = viewModel::confirmMetered) { Text(stringResource(R.string.data_metered_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissMetered) { Text(stringResource(R.string.data_cancel)) } }
        )
    }
}

@Composable
private fun DataContent(state: DataUiState, viewModel: DataViewModel, modifier: Modifier) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusCard(state, onUpdate = viewModel::updateNow) }
        item { SectionTitle(R.string.data_packs_title) }
        item { CatalogNotice(state, onRetry = viewModel::retryCatalog) }
        items(state.groups, key = { it.region ?: "global" }) { group ->
            RegionCard(group, state, viewModel)
        }
        item { SectionTitle(R.string.data_updates_title) }
        item {
            SwitchRow(R.string.data_wifi_only, R.string.data_wifi_only_summary, state.settings.wifiOnly, viewModel::setWifiOnly)
            SwitchRow(R.string.data_auto_update, R.string.data_auto_update_summary, state.settings.autoUpdate, viewModel::setAutoUpdate)
        }
        item { SectionTitle(R.string.data_storage_title) }
        item {
            Text(stringResource(R.string.data_space_used, SizeFormat.format(state.spaceUsedBytes)), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { confirmDeleteAll = true }, enabled = state.installedCount > 0, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.data_delete_all))
            }
        }
        item { SectionTitle(R.string.data_sources_title) }
        item { CustomSourcesSection(state, viewModel) }
        item { SectionTitle(R.string.data_attribution_title) }
        item { AttributionSection() }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.data_delete_all_title)) },
            text = { Text(stringResource(R.string.data_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAll()
                }) { Text(stringResource(R.string.data_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.data_cancel)) } }
        )
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() }
        )
    }
}

@Composable
private fun StatusCard(state: DataUiState, onUpdate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val last = state.settings.lastSuccessfulUpdateMillis
            Text(
                if (last != null) stringResource(R.string.data_last_update, dateTimeText(last)) else stringResource(R.string.data_never_updated),
                style = MaterialTheme.typography.titleMedium
            )
            state.settings.lastError?.let {
                Text(
                    stringResource(R.string.data_last_error, errorText(it)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onUpdate, enabled = !state.updating, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(if (state.updating) R.string.data_updating else R.string.data_update_now))
            }
        }
    }
}

@Composable
private fun CatalogNotice(state: DataUiState, onRetry: () -> Unit) {
    val message = when {
        !state.loaded || (state.refreshing && !state.hasManifest) -> R.string.data_loading_list
        !state.hasManifest -> R.string.data_no_manifest
        state.fromCache && state.refreshError != null -> R.string.data_cached_notice
        else -> null
    }
    if (message != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            state.refreshError?.let { Text(errorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.loaded && !state.refreshing) TextButton(onClick = onRetry) { Text(stringResource(R.string.data_retry)) }
        }
    }
}

@Composable
private fun RegionCard(group: RegionGroup, state: DataUiState, viewModel: DataViewModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val name = group.region?.let { PackCatalog.regionName(it) } ?: stringResource(R.string.data_region_global)
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.data_pack_sizes, SizeFormat.format(group.downloadBytes), SizeFormat.format(group.installedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            group.packs.forEach { pack -> PackRow(pack, state.progress[pack.entry.id], viewModel) }
            RegionActions(group, state, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionActions(group: RegionGroup, state: DataUiState, viewModel: DataViewModel) {
    val missing = group.packs.filter { !it.isInstalled || it.updateAvailable }.map { it.entry.id }
    val installed = group.packs.filter { it.isInstalled }.map { it.entry.id }
    val busy = group.packs.any { state.progress[it.entry.id] != null }
    if (missing.size > 1 || installed.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (missing.size > 1) {
                TextButton(onClick = { viewModel.install(missing) }, enabled = !busy) {
                    Text(stringResource(R.string.data_region_install_all, SizeFormat.format(group.pendingDownloadBytes)))
                }
            }
            if (installed.size > 1) {
                TextButton(onClick = { viewModel.remove(installed) }, enabled = !busy) { Text(stringResource(R.string.data_region_remove_all)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackRow(pack: PackItem, progress: PackProgress?, viewModel: DataViewModel) {
    // With large text the buttons go under the description instead of squeezing it.
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val title = packTitle(pack.entry)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        R.string.data_pack_detail,
                        pack.entry.version,
                        SizeFormat.format(pack.downloadBytes),
                        SizeFormat.format(pack.installedBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pack.isInstalled) {
                    Text(
                        stringResource(if (pack.updateAvailable) R.string.data_update_available else R.string.data_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                pack.entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (!stacked) PackButtons(pack, title, progress, viewModel)
        }
        if (stacked) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PackButtons(pack, title, progress, viewModel) }
        when (progress) {
            PackProgress.Queued -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is PackProgress.Downloading -> {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.data_progress, SizeFormat.format(progress.doneBytes), SizeFormat.format(progress.totalBytes)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is PackProgress.Failed -> Text(errorText(progress.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }
}

private const val STACK_FONT_SCALE = 1.3f

/** A pack button says which pack it acts on ("Install: Businesses (Spain)"), since TalkBack reads buttons one by one. */
@Composable
private fun PackButton(label: Int, packTitle: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.data_pack_action, stringResource(label), packTitle)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = spoken }) { Text(stringResource(label)) }
}

@Composable
private fun PackButtons(pack: PackItem, title: String, progress: PackProgress?, viewModel: DataViewModel) {
    val id = pack.entry.id
    when (progress) {
        PackProgress.Queued, is PackProgress.Downloading -> PackButton(R.string.data_cancel, title) { viewModel.cancel(id) }
        is PackProgress.Failed -> PackButton(R.string.data_retry, title) {
            viewModel.dismissFailure(id)
            viewModel.install(listOf(id))
        }
        null -> if (!pack.isInstalled) {
            PackButton(R.string.data_install, title) { viewModel.install(listOf(id)) }
        } else {
            if (pack.updateAvailable) PackButton(R.string.data_update, title) { viewModel.install(listOf(id)) }
            PackButton(R.string.data_remove, title) { viewModel.remove(listOf(id)) }
        }
    }
}

@Composable
private fun SwitchRow(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(
            min = 56.dp
        ).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AttributionSection() {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.data_attribution_text), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.data_coverage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { runCatching { uriHandler.openUri(OSM_COPYRIGHT_URL) } }) { Text(stringResource(R.string.data_attribution_osm_link)) }
        TextButton(onClick = { runCatching { uriHandler.openUri(ODBL_URL) } }) { Text(stringResource(R.string.data_attribution_licence_link)) }
    }
}
