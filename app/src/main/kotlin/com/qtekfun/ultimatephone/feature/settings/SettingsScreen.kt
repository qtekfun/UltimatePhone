package com.qtekfun.ultimatephone.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.BuildConfig
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.RadioRow
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SegmentedChoice
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.navigation.ONBOARDING_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_BACKUP_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_DATA_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_RECORDING_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_SPAM_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_SYNC_ROUTE
import java.util.Locale

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)
private val CapabilityValueMaxWidth = 180.dp

@Composable
fun SettingsScreen(onRequestPhoneRole: () -> Unit, onOpen: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var regionDialog by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.nav_settings)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            PhoneAppBanner(state, onRequestPhoneRole)
            NavigationGroup(onOpen)
            AppearanceGroup(state, viewModel)
            if (state.sims.size > 1) SimGroup(state, viewModel)
            SettingsGroup(title = stringResource(R.string.settings_region), modifier = GroupModifier) {
                val auto = state.settings.regionOverride == null
                SettingsRow(
                    title = regionName(state.effectiveRegion) + if (auto) " " + stringResource(R.string.settings_region_auto) else "",
                    summary = stringResource(R.string.settings_region_help),
                    icon = Icons.Filled.Public,
                    onClick = { regionDialog = true }
                )
            }
            AboutGroup(state, viewModel)
        }
    }

    if (regionDialog) {
        RegionDialog(
            current = state.settings.regionOverride,
            onDismiss = { regionDialog = false },
            onSelect = {
                viewModel.setRegion(it)
                regionDialog = false
            }
        )
    }
}

@Composable
private fun PhoneAppBanner(state: SettingsUiState, onRequestPhoneRole: () -> Unit) {
    when {
        !state.phoneRoleAvailable -> InfoBanner(
            kind = BannerKind.Warning,
            title = stringResource(R.string.settings_phone_app),
            body = stringResource(R.string.settings_phone_app_unavailable),
            modifier = GroupModifier
        )
        state.isDefaultPhoneApp -> InfoBanner(
            kind = BannerKind.Success,
            title = stringResource(R.string.settings_phone_app_active),
            modifier = GroupModifier
        )
        else -> InfoBanner(
            kind = BannerKind.Info,
            title = stringResource(R.string.settings_phone_app_inactive),
            modifier = GroupModifier,
            action = UiAction(stringResource(R.string.phone_role_banner_action), onRequestPhoneRole)
        )
    }
}

@Composable
private fun NavigationGroup(onOpen: (String) -> Unit) {
    SettingsGroup(title = stringResource(R.string.settings_spam), modifier = GroupModifier) {
        SettingsRow(
            title = stringResource(R.string.settings_spam_title),
            summary = stringResource(R.string.settings_spam_summary),
            icon = Icons.Filled.Shield,
            onClick = { onOpen(SETTINGS_SPAM_ROUTE) }
        )
        SettingsRow(
            title = stringResource(R.string.settings_data_title),
            summary = stringResource(R.string.settings_data_summary),
            icon = Icons.Filled.CloudDownload,
            onClick = { onOpen(SETTINGS_DATA_ROUTE) }
        )
        SettingsRow(
            title = stringResource(R.string.onboarding_settings_title),
            summary = stringResource(R.string.onboarding_settings_summary),
            icon = Icons.Filled.Checklist,
            onClick = { onOpen(ONBOARDING_ROUTE) }
        )
        SettingsRow(
            title = stringResource(R.string.sync_row_title),
            summary = stringResource(R.string.sync_row_summary),
            icon = Icons.Filled.Sync,
            onClick = { onOpen(SETTINGS_SYNC_ROUTE) }
        )
        SettingsRow(
            title = stringResource(R.string.backup_row_title),
            summary = stringResource(R.string.backup_row_summary),
            icon = Icons.Filled.Backup,
            onClick = { onOpen(SETTINGS_BACKUP_ROUTE) }
        )
        SettingsRow(
            title = stringResource(R.string.recording_row_title),
            summary = stringResource(R.string.recording_row_summary),
            icon = Icons.Filled.Mic,
            onClick = { onOpen(SETTINGS_RECORDING_ROUTE) }
        )
    }
}

@Composable
private fun AppearanceGroup(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup(title = stringResource(R.string.settings_appearance), modifier = GroupModifier) {
        SegmentedChoice(
            options = ThemeMode.entries.toList(),
            selected = state.settings.themeMode,
            onSelected = viewModel::setTheme,
            label = { stringResource(themeLabel(it)) },
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
        )
        SwitchRow(
            title = stringResource(R.string.design_system_colors_title),
            summary = stringResource(R.string.design_system_colors_summary),
            icon = Icons.Filled.Palette,
            checked = state.settings.useSystemColors,
            onCheckedChange = viewModel::setUseSystemColors
        )
    }
}

@Composable
private fun SimGroup(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup(title = stringResource(R.string.settings_default_sim), modifier = GroupModifier) {
        RadioRow(
            title = stringResource(R.string.settings_sim_ask),
            selected = state.settings.defaultSimKey == null,
            onClick = { viewModel.setDefaultSim(null) }
        )
        state.sims.forEach { sim ->
            val label = if (sim.slot >= 0) stringResource(R.string.dialer_sim_slot, sim.slot + 1) + " · " + sim.label else sim.label
            RadioRow(title = label, selected = state.settings.defaultSimKey == sim.key, onClick = { viewModel.setDefaultSim(sim.key) })
        }
    }
}

@Composable
private fun AboutGroup(state: SettingsUiState, viewModel: SettingsViewModel) {
    var diagnosticsOpen by remember { mutableStateOf(false) }
    SettingsGroup(title = stringResource(R.string.settings_about), modifier = GroupModifier) {
        SettingsRow(title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), icon = Icons.Filled.Info)
        val stateText = stringResource(if (diagnosticsOpen) R.string.design2_expanded else R.string.design2_collapsed)
        SettingsRow(
            title = stringResource(R.string.settings_diagnostics),
            icon = Icons.Filled.PhoneAndroid,
            onClick = {
                if (state.capabilities == null) viewModel.runDiagnostics()
                diagnosticsOpen = !diagnosticsOpen
            },
            modifier = Modifier.semantics { stateDescription = stateText },
            trailing = {
                Icon(
                    if (diagnosticsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        if (diagnosticsOpen) {
            state.capabilities?.lines()?.forEach { (name, value) ->
                SettingsRow(
                    title = name,
                    trailing = {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = CapabilityValueMaxWidth)
                        )
                    }
                )
            }
        }
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun countryName(region: String): String = Locale.Builder().setRegion(region).build().displayCountry

private fun regionName(region: String): String = if (region.isBlank()) "" else "${countryName(region)} ($region)"

@Composable
private fun RegionDialog(current: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val regions = remember { Locale.getISOCountries().sortedBy { countryName(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.settings_region)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RadioRow(title = stringResource(R.string.settings_region_auto_option), selected = current == null, onClick = { onSelect(null) })
                regions.forEach { code ->
                    Text(
                        text = regionName(code),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget).clickable(role = Role.Button) { onSelect(code) }
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}
