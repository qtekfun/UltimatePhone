package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.feature.recents.dateTimeText

/** "Why was this flagged": the level, the source or rule, the date and the number, with a way to overrule it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyFlaggedRoute(onBack: () -> Unit, viewModel: WhyFlaggedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spam_why_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.spam_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Line(R.string.spam_why_number, state.displayNumber)
            val verdict = state.verdict
            if (verdict == null) {
                if (!state.isLoading) Text(stringResource(R.string.spam_why_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                verdict.decision.level?.let { Line(R.string.spam_why_level, stringResource(levelLabelRes(it))) }
                Line(R.string.spam_why_reason, reasonText(verdict.decision))
                Line(R.string.spam_why_date, dateTimeText(verdict.decidedAt))
                Line(R.string.spam_why_action, stringResource(actionLabelRes(verdict.decision.action)))
            }
            if (state.inAllowed) {
                Text(stringResource(R.string.spam_why_allowed), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::removeFromAllowed) { Text(stringResource(R.string.spam_action_remove_allowed)) }
            } else {
                Button(onClick = viewModel::notSpam, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.spam_not_spam)) }
            }
        }
    }
}

@Composable
private fun Line(label: Int, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f))
    }
}
