package com.qtekfun.ultimatephone.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.data.NetworkState
import com.qtekfun.ultimatephone.data.PackCatalog
import com.qtekfun.ultimatephone.data.RegionGroup
import com.qtekfun.ultimatephone.data.SizeFormat
import com.qtekfun.ultimatephone.feature.data.errorText

// ---- Data regions ---------------------------------------------------------------------------------------------------

@Composable
internal fun DataStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(R.string.onboarding_data_title, R.string.onboarding_data_body)
    if (state.network == NetworkState.METERED) {
        Text(stringResource(R.string.onboarding_data_wifi_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    }
    when {
        !state.catalogLoaded || (state.catalogRefreshing && !state.hasManifest) -> Text(stringResource(R.string.onboarding_data_loading))
        !state.hasManifest -> {
            // Works offline: this step can be skipped and done later from Settings.
            Text(stringResource(R.string.onboarding_data_offline), style = MaterialTheme.typography.bodyMedium)
            state.catalogError?.let { Text(errorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = viewModel::retryCatalog, enabled = !state.catalogRefreshing) { Text(stringResource(R.string.data_retry)) }
        }
        else -> {
            if (state.catalogFromCache && state.catalogError != null) {
                Text(stringResource(R.string.onboarding_data_cached), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::retryCatalog, enabled = !state.catalogRefreshing) { Text(stringResource(R.string.data_retry)) }
            }
            state.regions.forEach { group -> RegionRow(group, group.region in state.selectedRegions) { group.region?.let(viewModel::toggleRegion) } }
            SelectionTotal(state)
        }
    }
}

@Composable
private fun RegionRow(group: RegionGroup, selected: Boolean, onToggle: () -> Unit) {
    val name = group.region?.let { PackCatalog.regionName(it) }.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = selected, role = Role.Checkbox, onValueChange = {
            onToggle()
        }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onboarding_data_size, SizeFormat.format(group.downloadBytes), SizeFormat.format(group.installedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectionTotal(state: OnboardingUiState) {
    Text(
        if (state.selectedRegions.isEmpty()) {
            stringResource(R.string.onboarding_data_none)
        } else {
            stringResource(
                R.string.onboarding_data_total,
                SizeFormat.format(state.selectedDownloadBytes),
                SizeFormat.format(state.selectedInstalledBytes)
            )
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

// ---- Nextcloud ------------------------------------------------------------------------------------------------------

@Composable
internal fun NextcloudStep(onSetUp: (() -> Unit)?, onLater: () -> Unit) {
    StepTitle(R.string.onboarding_nextcloud_title, R.string.onboarding_nextcloud_body)
    if (onSetUp != null) Button(onClick = onSetUp) { Text(stringResource(R.string.onboarding_nextcloud_now)) }
    OutlinedButton(onClick = onLater) { Text(stringResource(R.string.onboarding_nextcloud_later)) }
}

// ---- Battery, auto-start, pop-ups and full-screen calls -----------------------------------------------------------------

@Composable
internal fun BatteryStep(state: OnboardingUiState) {
    val context = LocalContext.current
    StepTitle(R.string.onboarding_battery_title, R.string.onboarding_battery_body)
    if (state.manufacturer.isNotBlank()) {
        Text(
            stringResource(R.string.onboarding_battery_detected, state.manufacturer, stringResource(guideName(state.guideId))),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
    state.topics.forEach { topic ->
        var failed by remember(topic.topic) { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(topicTitle(topic.topic)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(guideText(topic.textKey, topic.topic), style = MaterialTheme.typography.bodyMedium)
                if (topic.intents.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        failed = !SystemSettingsLauncher.open(context, topic)
                    }) { Text(stringResource(R.string.onboarding_open_settings)) }
                }
                if (failed) {
                    Text(
                        stringResource(R.string.onboarding_open_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** The guide text for [textKey] from the string resources; the generic text of the same topic when the key does not exist. */
@Composable
private fun guideText(textKey: String, topic: String): String {
    val context = LocalContext.current
    val id = remember(textKey) {
        context.resources.getIdentifier(textKey, "string", context.packageName).takeIf { it != 0 }
            ?: context.resources.getIdentifier("onboarding_oem_generic_$topic", "string", context.packageName)
    }
    return if (id != 0) stringResource(id) else ""
}

private fun topicTitle(topic: String): Int = when (topic) {
    GuideTopics.BATTERY -> R.string.onboarding_topic_battery
    GuideTopics.AUTOSTART -> R.string.onboarding_topic_autostart
    GuideTopics.POPUP -> R.string.onboarding_topic_popup
    else -> R.string.onboarding_topic_fullscreen
}

private fun guideName(id: String): Int = when (id) {
    "coloros" -> R.string.onboarding_guide_coloros
    "samsung" -> R.string.onboarding_guide_samsung
    "xiaomi" -> R.string.onboarding_guide_xiaomi
    "stock" -> R.string.onboarding_guide_stock
    else -> R.string.onboarding_guide_generic
}

// ---- Summary --------------------------------------------------------------------------------------------------------

@Composable
internal fun SummaryStep(state: OnboardingUiState, onFinish: (download: Boolean) -> Unit) {
    StepTitle(R.string.onboarding_summary_title, R.string.onboarding_summary_body)
    SummaryLine(R.string.onboarding_summary_phone, state.roles.dialerHeld)
    SummaryLine(R.string.onboarding_summary_screening, state.roles.screeningHeld)
    val regionNames = state.selectedRegions.sorted().joinToString { PackCatalog.regionName(it) }
    Text(
        if (state.selectedRegions.isEmpty()) {
            stringResource(R.string.onboarding_summary_no_regions)
        } else {
            stringResource(R.string.onboarding_summary_regions, regionNames, SizeFormat.format(state.selectedDownloadBytes))
        },
        style = MaterialTheme.typography.bodyLarge
    )
    val later = OnboardingFlow.pendingForLater(state.flow)
    if (later.isNotEmpty()) {
        Text(stringResource(R.string.onboarding_summary_later), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        later.forEach {
            val name = stringResource(stepName(it))
            Text("•  $name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = name })
        }
    }
    val downloading = state.selectedRegions.isNotEmpty()
    if (downloading) {
        Text(
            stringResource(
                if (state.canDownloadNow &&
                    state.hasManifest
                ) {
                    R.string.onboarding_summary_download_now
                } else {
                    R.string.onboarding_summary_download_later
                }
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Button(onClick = { onFinish(downloading) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (downloading) R.string.onboarding_summary_finish_download else R.string.onboarding_summary_finish))
    }
    if (downloading) {
        TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_summary_finish_no_download)) }
    }
}

@Composable
private fun SummaryLine(label: Int, done: Boolean) {
    Text(
        stringResource(label) + ": " + stringResource(if (done) R.string.onboarding_summary_yes else R.string.onboarding_summary_not_yet),
        style = MaterialTheme.typography.bodyLarge
    )
}

private fun stepName(step: OnboardingStep): Int = when (step) {
    OnboardingStep.ROLES -> R.string.onboarding_step_name_roles
    OnboardingStep.PERMISSIONS -> R.string.onboarding_step_name_permissions
    OnboardingStep.DATA -> R.string.onboarding_step_name_data
    OnboardingStep.NEXTCLOUD -> R.string.onboarding_step_name_nextcloud
    OnboardingStep.BATTERY -> R.string.onboarding_step_name_battery
    else -> R.string.onboarding_step_name_other
}
