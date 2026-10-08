package com.qtekfun.ultimatephone.feature.spam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction

/** Settings > Spam: the screening role, what to do per level, and the way to the lists and data. */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spam_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.spam_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoleCard(state, onRequest = { viewModel.roleIntent()?.let(launcher::launch) })
            ActionsSection(state, viewModel)
            CommercialSection(state.commercialRuleAction, viewModel::setCommercialRuleAction)
            BusinessSwitch(state.prefs.identifiedBusinessIsNotSpam, viewModel::setBusinessNotSpam)
            HorizontalDivider()
            LinkRow(R.string.spam_own_list_title, R.string.spam_own_list_summary, onOpenOwnList)
            LinkRow(R.string.spam_allowed_title, R.string.spam_allowed_summary, onOpenAllowed)
            LinkRow(R.string.spam_data_link_title, R.string.spam_data_link_summary, onOpenData)
            state.slowestScreeningMillis?.let {
                Text(
                    stringResource(R.string.spam_latency, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoleCard(state: SpamHomeState, onRequest: () -> Unit) {
    val container = if (state.roleHeld) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.spam_role_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val body = when {
                state.roleHeld -> R.string.spam_role_held
                state.roleAvailable -> R.string.spam_role_missing
                else -> R.string.spam_role_unavailable
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            if (state.roleAvailable && !state.roleHeld) {
                TextButton(onClick = onRequest) { Text(stringResource(R.string.spam_role_request)) }
            }
        }
    }
}

@Composable
private fun ActionsSection(state: SpamHomeState, viewModel: SpamHomeViewModel) {
    SectionTitle(R.string.spam_actions_title)
    Text(stringResource(R.string.spam_actions_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TABLE_LEVELS.forEach { level ->
        ActionPicker(
            label = stringResource(levelLabelRes(level)),
            selected = state.prefs.actionOf(level),
            onSelect = { viewModel.setLevelAction(level, it) }
        )
    }
}

@Composable
private fun CommercialSection(selected: SpamAction, onSelect: (SpamAction) -> Unit) {
    HorizontalDivider()
    SectionTitle(R.string.spam_commercial_title)
    Text(stringResource(R.string.spam_commercial_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ActionPicker(label = stringResource(R.string.spam_rule_es_400_commercial), selected = selected, onSelect = onSelect)
}

@Composable
private fun BusinessSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.spam_business_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.spam_business_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics {
            heading()
        }
    )
}

/** Warn, silence or reject for one row of the table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPicker(label: String, selected: SpamAction, onSelect: (SpamAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SELECTABLE_ACTIONS.forEachIndexed { index, action ->
                SegmentedButton(
                    selected = selected == action,
                    onClick = { onSelect(action) },
                    shape = SegmentedButtonDefaults.itemShape(index, SELECTABLE_ACTIONS.size),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    label = { Text(stringResource(actionLabelRes(action))) }
                )
            }
        }
    }
}

@Composable
private fun LinkRow(title: Int, summary: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
