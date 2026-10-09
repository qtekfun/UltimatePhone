package com.qtekfun.ultimatephone.feature.spam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SegmentedChoice
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)

/** Settings > Spam: the screening role, what to do per level, and the way to the lists and data. */
@Composable
fun SpamHomeRoute(
    onBack: () -> Unit,
    onOpenOwnList: () -> Unit,
    onOpenAllowed: () -> Unit,
    onOpenData: () -> Unit,
    viewModel: SpamHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRole() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRole() }

    ScreenScaffold(title = stringResource(R.string.spam_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            RoleBanner(state, onRequest = { viewModel.roleIntent()?.let(launcher::launch) })
            SettingsGroup(title = stringResource(R.string.spam_actions_title), modifier = GroupModifier) {
                HelpText(R.string.spam_actions_help)
                TABLE_LEVELS.forEach { level ->
                    ActionPicker(
                        label = stringResource(levelLabelRes(level)),
                        selected = state.prefs.actionOf(level),
                        onSelect = { viewModel.setLevelAction(level, it) }
                    )
                }
            }
            SettingsGroup(title = stringResource(R.string.spam_commercial_title), modifier = GroupModifier) {
                HelpText(R.string.spam_commercial_help)
                ActionPicker(
                    label = stringResource(R.string.spam_rule_es_400_commercial),
                    selected = state.commercialRuleAction,
                    onSelect = viewModel::setCommercialRuleAction
                )
            }
            SettingsGroup(modifier = GroupModifier) {
                SwitchRow(
                    title = stringResource(R.string.spam_business_title),
                    summary = stringResource(R.string.spam_business_help),
                    icon = Icons.Filled.Storefront,
                    checked = state.prefs.identifiedBusinessIsNotSpam,
                    onCheckedChange = viewModel::setBusinessNotSpam
                )
            }
            SettingsGroup(modifier = GroupModifier) {
                SettingsRow(
                    title = stringResource(R.string.spam_own_list_title),
                    summary = stringResource(R.string.spam_own_list_summary),
                    icon = Icons.Filled.Block,
                    onClick = onOpenOwnList
                )
                SettingsRow(
                    title = stringResource(R.string.spam_allowed_title),
                    summary = stringResource(R.string.spam_allowed_summary),
                    icon = Icons.Filled.VerifiedUser,
                    onClick = onOpenAllowed
                )
                SettingsRow(
                    title = stringResource(R.string.spam_data_link_title),
                    summary = stringResource(R.string.spam_data_link_summary),
                    icon = Icons.Filled.CloudDownload,
                    onClick = onOpenData
                )
            }
            state.slowestScreeningMillis?.let {
                Text(
                    stringResource(R.string.spam_latency, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = GroupModifier
                )
            }
        }
    }
}

@Composable
private fun RoleBanner(state: SpamHomeState, onRequest: () -> Unit) {
    val body = stringResource(
        when {
            state.roleHeld -> R.string.spam_role_held
            state.roleAvailable -> R.string.spam_role_missing
            else -> R.string.spam_role_unavailable
        }
    )
    InfoBanner(
        kind = when {
            state.roleHeld -> BannerKind.Success
            state.roleAvailable -> BannerKind.Warning
            else -> BannerKind.Info
        },
        title = stringResource(R.string.spam_role_title),
        body = body,
        modifier = GroupModifier,
        action = if (state.roleAvailable && !state.roleHeld) UiAction(stringResource(R.string.spam_role_request), onRequest) else null
    )
}

/** An explanation at the top of a group, inside its container. */
@Composable
private fun HelpText(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
    )
}

/** Warn, silence or reject for one row of the table. */
@Composable
private fun ActionPicker(label: String, selected: SpamAction, onSelect: (SpamAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        SegmentedChoice(
            options = SELECTABLE_ACTIONS,
            selected = selected,
            onSelected = onSelect,
            label = { stringResource(actionLabelRes(it)) }
        )
    }
}
