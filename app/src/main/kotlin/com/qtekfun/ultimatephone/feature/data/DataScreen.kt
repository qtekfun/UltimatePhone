package com.qtekfun.ultimatephone.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.data.PackCatalog
import com.qtekfun.ultimatephone.data.PackItem
import com.qtekfun.ultimatephone.data.PackProgress
import com.qtekfun.ultimatephone.data.RegionGroup
import com.qtekfun.ultimatephone.data.SizeFormat

internal const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
internal const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** Settings > Data: packs by region, updates, storage, custom sources and the OpenStreetMap attribution. */
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
    ScreenScaffold(title = stringResource(R.string.data_title), onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        DataContent(state = state, viewModel = viewModel, padding = padding)
    }
    state.confirmMetered?.let {
        ConfirmDialog(
            title = stringResource(R.string.data_metered_title),
            body = stringResource(R.string.data_metered_body, SizeFormat.format(state.confirmMeteredBytes)),
            confirmLabel = stringResource(R.string.data_metered_confirm),
            dismissLabel = stringResource(R.string.data_cancel),
            onConfirm = viewModel::confirmMetered,
            onDismiss = viewModel::dismissMetered
        )
    }
}

@Composable
private fun DataContent(state: DataUiState, viewModel: DataViewModel, padding: PaddingValues) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val layoutDirectionPadding = PaddingValues(
        top = padding.calculateTopPadding(),
        bottom = padding.calculateBottomPadding() + Spacing.Medium,
        start = Spacing.Medium,
        end = Spacing.Medium
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = layoutDirectionPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        item { StatusBanner(state, onUpdate = viewModel::updateNow) }
        item { CatalogNotice(state, onRetry = viewModel::retryCatalog) }
        items(state.groups, key = { it.region ?: "global" }) { group ->
            RegionGroupCard(group, state, viewModel)
        }
        item {
            SettingsGroup(title = stringResource(R.string.data_updates_title)) {
                SwitchRow(
                    title = stringResource(R.string.data_wifi_only),
                    summary = stringResource(R.string.data_wifi_only_summary),
                    icon = Icons.Filled.Wifi,
                    checked = state.settings.wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly
                )
                SwitchRow(
                    title = stringResource(R.string.data_auto_update),
                    summary = stringResource(R.string.data_auto_update_summary),
                    icon = Icons.Filled.Autorenew,
                    checked = state.settings.autoUpdate,
                    onCheckedChange = viewModel::setAutoUpdate
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.data_storage_title)) {
                SettingsRow(
                    title = stringResource(R.string.data_space_used, SizeFormat.format(state.spaceUsedBytes)),
                    icon = Icons.Filled.Storage
                )
                SettingsRow(
                    title = stringResource(R.string.data_delete_all),
                    icon = Icons.Filled.DeleteForever,
                    enabled = state.installedCount > 0,
                    onClick = { confirmDeleteAll = true },
                    trailing = {}
                )
            }
        }
        item { CustomSourcesSection(state, viewModel) }
        item { AttributionSection() }
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.data_delete_all_title),
            body = stringResource(R.string.data_delete_all_body),
            confirmLabel = stringResource(R.string.data_delete_confirm),
            dismissLabel = stringResource(R.string.data_cancel),
            onConfirm = viewModel::deleteAll,
            onDismiss = { confirmDeleteAll = false },
            destructive = true
        )
    }
}

@Composable
private fun StatusBanner(state: DataUiState, onUpdate: () -> Unit) {
    val last = state.settings.lastSuccessfulUpdateMillis
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        InfoBanner(
            kind = if (last != null) BannerKind.Success else BannerKind.Info,
            title = if (last != null) stringResource(R.string.data_last_update, dateTimeText(last)) else stringResource(R.string.data_never_updated),
            body = if (state.updating) stringResource(R.string.data_updating) else null,
            icon = Icons.Filled.Update,
            action = if (state.updating) null else UiAction(stringResource(R.string.data_update_now), onUpdate)
        )
        state.settings.lastError?.let {
            InfoBanner(kind = BannerKind.Error, title = stringResource(R.string.data_last_error, errorText(it)))
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
        InfoBanner(
            kind = if (message == R.string.data_no_manifest) BannerKind.Warning else BannerKind.Info,
            title = stringResource(message),
            body = state.refreshError?.let { errorText(it) },
            action = if (state.loaded && !state.refreshing) UiAction(stringResource(R.string.data_retry), onRetry) else null
        )
    }
}

@Composable
private fun RegionGroupCard(group: RegionGroup, state: DataUiState, viewModel: DataViewModel) {
    val name = group.region?.let { PackCatalog.regionName(it) } ?: stringResource(R.string.data_region_global)
    SettingsGroup(title = name) {
        Text(
            stringResource(R.string.data_pack_sizes, SizeFormat.format(group.downloadBytes), SizeFormat.format(group.installedBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
        )
        group.packs.forEach { pack -> PackRow(pack, state.progress[pack.entry.id], viewModel) }
        RegionActions(group, state, viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionActions(group: RegionGroup, state: DataUiState, viewModel: DataViewModel) {
    val missing = group.packs.filter { !it.isInstalled || it.updateAvailable }.map { it.entry.id }
    val installed = group.packs.filter { it.isInstalled }.map { it.entry.id }
    val busy = group.packs.any { state.progress[it.entry.id] != null }
    if (missing.size > 1 || installed.size > 1) {
        FlowRow(modifier = Modifier.padding(horizontal = Spacing.Small), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            if (missing.size > 1) {
                TextButton(onClick = { viewModel.install(missing) }, enabled = !busy, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                    Text(stringResource(R.string.data_region_install_all, SizeFormat.format(group.pendingDownloadBytes)))
                }
            }
            if (installed.size > 1) {
                TextButton(onClick = { viewModel.remove(installed) }, enabled = !busy, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                    Text(stringResource(R.string.data_region_remove_all))
                }
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.data_pack_detail,
                        pack.entry.version,
                        SizeFormat.format(pack.downloadBytes),
                        SizeFormat.format(pack.installedBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pack.isInstalled) {
                    // Icon and word, so the state is not colour alone.
                    if (pack.updateAvailable) {
                        StatusChip(stringResource(R.string.data_update_available), icon = Icons.Filled.Update, kind = BannerKind.Warning)
                    } else {
                        StatusChip(stringResource(R.string.data_installed), icon = Icons.Filled.CheckCircle, kind = BannerKind.Success)
                    }
                }
                pack.entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (!stacked) PackButtons(pack, title, progress, viewModel)
        }
        if (stacked) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) { PackButtons(pack, title, progress, viewModel) }
        when (progress) {
            PackProgress.Queued -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is PackProgress.Downloading -> {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.data_progress, SizeFormat.format(progress.doneBytes), SizeFormat.format(progress.totalBytes)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is PackProgress.Failed -> Text(errorText(progress.error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }
}

private const val STACK_FONT_SCALE = 1.3f

/** A pack button says which pack it acts on ("Install: Businesses (Spain)"), since TalkBack reads buttons one by one. */
@Composable
private fun PackButton(label: Int, packTitle: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.data_pack_action, stringResource(label), packTitle)
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Spacing.MinTarget).semantics { contentDescription = spoken }) {
        Text(stringResource(label))
    }
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
private fun AttributionSection() {
    val uriHandler = LocalUriHandler.current
    SettingsGroup(title = stringResource(R.string.data_attribution_title)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text(stringResource(R.string.data_attribution_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.data_coverage_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsRow(
            title = stringResource(R.string.data_attribution_osm_link),
            icon = Icons.Filled.OpenInNew,
            onClick = { runCatching { uriHandler.openUri(OSM_COPYRIGHT_URL) } },
            trailing = {}
        )
        SettingsRow(
            title = stringResource(R.string.data_attribution_licence_link),
            icon = Icons.Filled.OpenInNew,
            onClick = { runCatching { uriHandler.openUri(ODBL_URL) } },
            trailing = {}
        )
    }
}
