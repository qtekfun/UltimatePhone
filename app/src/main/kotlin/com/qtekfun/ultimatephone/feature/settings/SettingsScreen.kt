package com.qtekfun.ultimatephone.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.BuildConfig
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.navigation.ONBOARDING_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_BACKUP_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_DATA_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_SPAM_ROUTE
import com.qtekfun.ultimatephone.navigation.SETTINGS_SYNC_ROUTE
import java.util.Locale

@Composable
fun SettingsScreen(onRequestPhoneRole: () -> Unit, onOpen: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var regionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

        Section(R.string.settings_phone_app)
        if (state.phoneRoleAvailable) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(if (state.isDefaultPhoneApp) R.string.settings_phone_app_active else R.string.settings_phone_app_inactive),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!state.isDefaultPhoneApp) TextButton(onClick = onRequestPhoneRole) { Text(stringResource(R.string.phone_role_banner_action)) }
                }
            }
        } else {
            Text(stringResource(R.string.settings_phone_app_unavailable))
        }

        Section(R.string.settings_spam)
        NavRow(R.string.settings_spam_title, R.string.settings_spam_summary) { onOpen(SETTINGS_SPAM_ROUTE) }
        NavRow(R.string.settings_data_title, R.string.settings_data_summary) { onOpen(SETTINGS_DATA_ROUTE) }
        NavRow(R.string.onboarding_settings_title, R.string.onboarding_settings_summary) { onOpen(ONBOARDING_ROUTE) }
        NavRow(R.string.sync_row_title, R.string.sync_row_summary) { onOpen(SETTINGS_SYNC_ROUTE) }
        NavRow(R.string.backup_row_title, R.string.backup_row_summary) { onOpen(SETTINGS_BACKUP_ROUTE) }

        Section(R.string.settings_appearance)
        ThemeMode.entries.forEach { mode ->
            RadioRow(stringResource(themeLabel(mode)), state.settings.themeMode == mode) { viewModel.setTheme(mode) }
        }

        if (state.sims.size > 1) {
            Section(R.string.settings_default_sim)
            RadioRow(stringResource(R.string.settings_sim_ask), state.settings.defaultSimKey == null) { viewModel.setDefaultSim(null) }
            state.sims.forEach { sim ->
                val label = if (sim.slot >= 0) stringResource(R.string.dialer_sim_slot, sim.slot + 1) + " · " + sim.label else sim.label
                RadioRow(label, state.settings.defaultSimKey == sim.key) { viewModel.setDefaultSim(sim.key) }
            }
        }

        Section(R.string.settings_region)
        Text(stringResource(R.string.settings_region_help), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { regionDialog = true }) {
            val auto = state.settings.regionOverride == null
            Text(regionName(state.effectiveRegion) + if (auto) " " + stringResource(R.string.settings_region_auto) else "")
        }

        Section(R.string.settings_about)
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = viewModel::runDiagnostics) { Text(stringResource(R.string.settings_diagnostics)) }
        state.capabilities?.lines()?.forEach { (name, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.bodySmall)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun NavRow(title: Int, summary: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
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
        title = { Text(stringResource(R.string.settings_region)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RadioRow(stringResource(R.string.settings_region_auto_option), current == null) { onSelect(null) }
                regions.forEach { code ->
                    Text(
                        text = regionName(code),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(code) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}
