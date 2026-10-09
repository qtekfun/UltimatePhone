package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.feature.recents.dateTimeText

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)

/** "Why was this flagged": the level, the source or rule, the date and the number, with a way to overrule it. */
@Composable
fun WhyFlaggedRoute(onBack: () -> Unit, viewModel: WhyFlaggedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.spam_why_title), onBack = onBack) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            val verdict = state.verdict
            if (verdict == null) {
                if (!state.isLoading) {
                    InfoBanner(kind = BannerKind.Info, title = stringResource(R.string.spam_why_none), modifier = GroupModifier)
                }
                SettingsGroup(modifier = GroupModifier) {
                    SettingsRow(title = stringResource(R.string.spam_why_number), summary = state.displayNumber, icon = Icons.Filled.Phone)
                }
            } else {
                SettingsGroup(modifier = GroupModifier) {
                    SettingsRow(title = stringResource(R.string.spam_why_number), summary = state.displayNumber, icon = Icons.Filled.Phone)
                    verdict.decision.level?.let {
                        SettingsRow(title = stringResource(R.string.spam_why_level), summary = stringResource(levelLabelRes(it)), icon = Icons.Filled.Tune)
                    }
                    SettingsRow(title = stringResource(R.string.spam_why_reason), summary = reasonText(verdict.decision), icon = Icons.Filled.Info)
                    SettingsRow(title = stringResource(R.string.spam_why_date), summary = dateTimeText(verdict.decidedAt), icon = Icons.Filled.Schedule)
                    SettingsRow(
                        title = stringResource(R.string.spam_why_action),
                        summary = stringResource(actionLabelRes(verdict.decision.action)),
                        icon = Icons.Filled.Report
                    )
                }
            }
            if (state.inAllowed) {
                InfoBanner(
                    kind = BannerKind.Success,
                    title = stringResource(R.string.spam_why_allowed),
                    modifier = GroupModifier,
                    action = UiAction(stringResource(R.string.spam_action_remove_allowed), viewModel::removeFromAllowed)
                )
            } else {
                Button(
                    onClick = viewModel::notSpam,
                    modifier = GroupModifier.fillMaxWidth().heightIn(min = Spacing.MinTarget)
                ) { Text(stringResource(R.string.spam_not_spam)) }
            }
        }
    }
}
