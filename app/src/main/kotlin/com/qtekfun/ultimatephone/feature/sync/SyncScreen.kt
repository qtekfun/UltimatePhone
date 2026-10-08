package com.qtekfun.ultimatephone.feature.sync

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.sync.SyncPhase

@StringRes
internal fun syncErrorText(error: SyncError): Int = when (error) {
    SyncError.NOT_CONFIGURED -> R.string.sync_error_not_configured
    SyncError.INVALID_URL -> R.string.sync_error_invalid_url
    SyncError.INSECURE_URL -> R.string.sync_error_insecure_url
    SyncError.AUTH -> R.string.sync_error_auth
    SyncError.NETWORK -> R.string.sync_error_network
    SyncError.TLS -> R.string.sync_error_tls
    SyncError.CONFLICT_EXHAUSTED -> R.string.sync_error_conflict
    SyncError.SERVER_TOO_OLD -> R.string.sync_error_server_old
    SyncError.REMOTE_CORRUPT -> R.string.sync_error_remote_corrupt
    SyncError.CREDENTIALS_UNAVAILABLE -> R.string.sync_error_credentials
    SyncError.UNKNOWN -> R.string.sync_error_unknown
}

/** Settings > Nextcloud sync. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncRoute(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshPending() }
    var confirmForget by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sync_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium)
            AccountFields(state, viewModel)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::test, enabled = state.action != SyncAction.Testing) { Text(stringResource(R.string.sync_test)) }
                Button(onClick = { viewModel.save() }, enabled = state.form.serverUrl.isNotBlank() && state.form.username.isNotBlank()) {
                    Text(stringResource(R.string.sync_save))
                }
            }
            ActionMessage(state.action)
            HorizontalDivider()
            StatusSection(state, viewModel)
            if (state.sync.hasPassword || state.sync.serverUrl.isNotBlank()) {
                HorizontalDivider()
                TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.sync_forget)) }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.sync_forget_title)) },
            text = { Text(stringResource(R.string.sync_forget_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forget()
                }) { Text(stringResource(R.string.sync_forget_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.sync_cancel)) } }
        )
    }
}

/**
 * The Nextcloud account form with a Connect button, for the onboarding step. It tests the connection, saves the account,
 * turns sync on and calls [onDone]; "Not now" calls [onSkip] (by default [onDone]) without saving anything.
 * [scrollable] is false when a parent already scrolls (a nested scroll would crash on unbounded height).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NextcloudSetupContent(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit = onDone,
    scrollable: Boolean = true,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(modifier = modifier.then(scrollModifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sync_setup_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sync_setup_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium)
        AccountFields(state, viewModel)
        ActionMessage(state.action)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Button(
                onClick = { viewModel.connect(onDone) },
                enabled = state.action != SyncAction.Testing && state.form.serverUrl.isNotBlank() && state.form.username.isNotBlank()
            ) { Text(stringResource(R.string.sync_connect)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.sync_skip)) }
        }
    }
}

@Composable
private fun AccountFields(state: SyncScreenState, viewModel: SyncViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.form.serverUrl,
            onValueChange = viewModel::setServer,
            label = { Text(stringResource(R.string.sync_server_label)) },
            placeholder = { Text(stringResource(R.string.sync_server_placeholder)) },
            supportingText = { Text(stringResource(R.string.sync_server_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.form.username,
            onValueChange = viewModel::setUser,
            label = { Text(stringResource(R.string.sync_user_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.form.password,
            onValueChange = viewModel::setPassword,
            label = { Text(stringResource(R.string.sync_password_label)) },
            supportingText = {
                Text(stringResource(if (state.sync.hasPassword && state.form.password.isEmpty()) R.string.sync_password_saved else R.string.sync_password_help))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (state.sync.hasPassword && state.form.password.isEmpty()) {
            Text(stringResource(R.string.sync_password_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionMessage(action: SyncAction) {
    // Progress, success and failure appear under the buttons: TalkBack announces them by itself.
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { ActionMessageText(action) }
}

@Composable
private fun ActionMessageText(action: SyncAction) {
    when (action) {
        SyncAction.None -> Unit
        SyncAction.Testing -> Text(stringResource(R.string.sync_testing), style = MaterialTheme.typography.bodyMedium)
        SyncAction.TestOk -> Text(stringResource(R.string.sync_test_ok), style = MaterialTheme.typography.bodyMedium)
        SyncAction.Saved -> Text(stringResource(R.string.sync_saved), style = MaterialTheme.typography.bodyMedium)
        is SyncAction.Done -> {
            Text(stringResource(R.string.sync_result, action.counters.pulled, action.counters.pushed), style = MaterialTheme.typography.bodyMedium)
            if (action.counters.badLines >
                0
            ) {
                Text(
                    pluralStringResource(R.plurals.sync_bad_lines, action.counters.badLines, action.counters.badLines),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        is SyncAction.Failed -> Text(
            stringResource(syncErrorText(action.error)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun StatusSection(state: SyncScreenState, viewModel: SyncViewModel) {
    val sync = state.sync
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = sync.enabled, enabled = sync.isConfigured, role = Role.Switch, onValueChange = viewModel::setEnabled),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sync_enable), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.sync_enable_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = sync.enabled, onCheckedChange = null, enabled = sync.isConfigured)
        }
        val last = sync.lastSyncAt
        Text(
            if (last == null) {
                stringResource(R.string.sync_never)
            } else {
                stringResource(R.string.sync_last_sync, DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (sync.pendingChanges) Text(stringResource(R.string.sync_pending), style = MaterialTheme.typography.bodyMedium)
        sync.lastError?.let { error ->
            Text(stringResource(R.string.sync_error_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            Text(stringResource(syncErrorText(error)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = viewModel::syncNow, enabled = sync.isConfigured && sync.phase == SyncPhase.IDLE) {
            Text(stringResource(if (sync.phase == SyncPhase.SYNCING) R.string.sync_syncing else R.string.sync_now))
        }
    }
}
