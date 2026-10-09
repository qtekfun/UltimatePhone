package com.qtekfun.ultimatephone.feature.sync

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.sync.SyncPhase

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncRoute(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshPending() }
    var confirmForget by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.sync_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            InfoBanner(kind = BannerKind.Info, title = stringResource(R.string.sync_intro), modifier = GroupModifier)
            SettingsGroup(modifier = GroupModifier) {
                Column(Modifier.padding(Spacing.Medium)) { AccountFields(state, viewModel) }
            }
            FlowRow(modifier = GroupModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                OutlinedButton(
                    onClick = viewModel::test,
                    enabled = state.action != SyncAction.Testing,
                    modifier = Modifier.heightIn(min = Spacing.MinTarget)
                ) { Text(stringResource(R.string.sync_test)) }
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.form.serverUrl.isNotBlank() && state.form.username.isNotBlank(),
                    modifier = Modifier.heightIn(min = Spacing.MinTarget)
                ) { Text(stringResource(R.string.sync_save)) }
            }
            ActionMessage(state.action, GroupModifier)
            StatusSection(state, viewModel)
            if (state.sync.hasPassword || state.sync.serverUrl.isNotBlank()) {
                SettingsGroup(modifier = GroupModifier) {
                    SettingsRow(
                        title = stringResource(R.string.sync_forget),
                        icon = Icons.Filled.LinkOff,
                        onClick = { confirmForget = true },
                        trailing = {}
                    )
                }
            }
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            title = stringResource(R.string.sync_forget_title),
            body = stringResource(R.string.sync_forget_message),
            confirmLabel = stringResource(R.string.sync_forget_confirm),
            dismissLabel = stringResource(R.string.sync_cancel),
            onConfirm = viewModel::forget,
            onDismiss = { confirmForget = false },
            destructive = true
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
    Column(modifier = modifier.then(scrollModifier), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Text(stringResource(R.string.sync_setup_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sync_setup_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium)
        AccountFields(state, viewModel)
        ActionMessage(state.action, Modifier)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalArrangement = Arrangement.Center) {
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
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
private fun ActionMessage(action: SyncAction, modifier: Modifier) {
    // Progress, success and failure appear under the buttons: TalkBack announces them by itself.
    when (action) {
        SyncAction.None -> Unit
        SyncAction.Testing -> InfoBanner(kind = BannerKind.Info, title = stringResource(R.string.sync_testing), modifier = modifier, liveRegion = true)
        SyncAction.TestOk -> InfoBanner(kind = BannerKind.Success, title = stringResource(R.string.sync_test_ok), modifier = modifier, liveRegion = true)
        SyncAction.Saved -> InfoBanner(kind = BannerKind.Success, title = stringResource(R.string.sync_saved), modifier = modifier, liveRegion = true)
        is SyncAction.Done -> InfoBanner(
            kind = BannerKind.Success,
            title = stringResource(R.string.sync_result, action.counters.pulled, action.counters.pushed),
            body = if (action.counters.badLines > 0) {
                pluralStringResource(R.plurals.sync_bad_lines, action.counters.badLines, action.counters.badLines)
            } else {
                null
            },
            modifier = modifier,
            liveRegion = true
        )
        is SyncAction.Failed -> InfoBanner(
            kind = BannerKind.Error,
            title = stringResource(syncErrorText(action.error)),
            modifier = modifier,
            liveRegion = true
        )
    }
}

@Composable
private fun StatusSection(state: SyncScreenState, viewModel: SyncViewModel) {
    val sync = state.sync
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        SettingsGroup(modifier = GroupModifier) {
            SwitchRow(
                title = stringResource(R.string.sync_enable),
                summary = stringResource(R.string.sync_enable_summary),
                icon = Icons.Filled.Sync,
                checked = sync.enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = sync.isConfigured
            )
            val last = sync.lastSyncAt
            SettingsRow(
                title = if (last == null) {
                    stringResource(R.string.sync_never)
                } else {
                    stringResource(R.string.sync_last_sync, DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
                },
                icon = Icons.Filled.Schedule
            )
            if (sync.pendingChanges) SettingsRow(title = stringResource(R.string.sync_pending), icon = Icons.Filled.PendingActions)
            SettingsRow(
                title = stringResource(if (sync.phase == SyncPhase.SYNCING) R.string.sync_syncing else R.string.sync_now),
                icon = Icons.Filled.Refresh,
                enabled = sync.isConfigured && sync.phase == SyncPhase.IDLE,
                onClick = viewModel::syncNow,
                trailing = {}
            )
        }
        sync.lastError?.let { error ->
            InfoBanner(
                kind = BannerKind.Error,
                title = stringResource(R.string.sync_error_title),
                body = stringResource(syncErrorText(error)),
                modifier = GroupModifier
            )
        }
    }
}
