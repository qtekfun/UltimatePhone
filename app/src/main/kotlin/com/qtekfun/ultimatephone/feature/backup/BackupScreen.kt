package com.qtekfun.ultimatephone.feature.backup

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_PASSWORD_LENGTH = 8

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)

/** Settings > Backup: export the settings to an encrypted file, or import one. */
@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::exportTo)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::filePicked)

    ScreenScaffold(title = stringResource(R.string.backup_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            InfoBanner(
                kind = BannerKind.Warning,
                title = stringResource(R.string.backup_warning_title),
                body = stringResource(R.string.backup_warning),
                modifier = GroupModifier
            )
            SettingsGroup(title = stringResource(R.string.backup_export_title), modifier = GroupModifier) {
                SettingsRow(
                    title = stringResource(R.string.backup_export_action),
                    summary = stringResource(R.string.backup_export_summary),
                    icon = Icons.Filled.FileUpload,
                    enabled = !state.busy,
                    onClick = viewModel::askExport
                )
            }
            SettingsGroup(title = stringResource(R.string.backup_import_title), modifier = GroupModifier) {
                SettingsRow(
                    title = stringResource(R.string.backup_import_action),
                    summary = stringResource(R.string.backup_import_summary),
                    icon = Icons.Filled.FileDownload,
                    enabled = !state.busy,
                    onClick = { openFile.launch(arrayOf("*/*")) }
                )
            }
            // Progress and the result are announced by TalkBack as they appear.
            if (state.busy) {
                InfoBanner(kind = BannerKind.Info, title = stringResource(R.string.backup_working), modifier = GroupModifier, liveRegion = true)
            }
            state.message?.let { MessageBanner(it) }
        }
    }

    if (state.askExportPassword) {
        PasswordDialog(
            title = R.string.backup_export_password_title,
            confirmLabel = R.string.backup_continue,
            repeat = true,
            onDismiss = viewModel::cancelExport,
            onConfirm = {
                viewModel.exportPasswordChosen(it)
                createFile.launch("ultimatephone-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".upbk")
            }
        )
    }
    if (state.askImportPassword) {
        PasswordDialog(
            title = R.string.backup_import_password_title,
            confirmLabel = R.string.backup_open,
            repeat = false,
            onDismiss = viewModel::cancelImport,
            onConfirm = viewModel::openWithPassword
        )
    }
    state.choice?.let { choice -> ChoiceDialog(choice, viewModel) }
}

@Composable
private fun MessageBanner(message: BackupMessage) {
    val (kind, text) = when (message) {
        BackupMessage.ExportDone -> BannerKind.Success to stringResource(R.string.backup_export_done)
        BackupMessage.ExportFailed -> BannerKind.Error to stringResource(R.string.backup_export_failed)
        is BackupMessage.ImportFailed -> BannerKind.Error to stringResource(message.text)
        is BackupMessage.ImportDone -> BannerKind.Success to stringResource(R.string.backup_import_done, titles(message.sections))
        is BackupMessage.ImportPartial -> BannerKind.Warning to stringResource(R.string.backup_import_partial, titles(message.sections))
    }
    InfoBanner(kind = kind, title = text, modifier = GroupModifier, liveRegion = true)
}

@Composable
private fun titles(ids: List<Int>): String = LocalContext.current.let { context -> ids.joinToString(", ") { context.getString(it) } }

@Composable
private fun PasswordDialog(title: Int, confirmLabel: Int, repeat: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val tooShort = repeat && password.length < MIN_PASSWORD_LENGTH
    val mismatch = repeat && password != again
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (repeat) ({ Text(stringResource(R.string.backup_password_short, MIN_PASSWORD_LENGTH)) }) else null,
                    isError = repeat && password.isNotEmpty() && tooShort
                )
                if (repeat) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it },
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = again.isNotEmpty() && mismatch,
                        supportingText = if (again.isNotEmpty() && mismatch) ({ Text(stringResource(R.string.backup_password_mismatch)) }) else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && !tooShort && !mismatch,
                modifier = Modifier.heightIn(min = Spacing.MinTarget)
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.backup_cancel)) }
        }
    )
}

@Composable
private fun ChoiceDialog(choice: ImportChoice, viewModel: BackupViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.backup_import_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (choice.createdAt > 0) {
                    Text(
                        stringResource(
                            R.string.backup_import_created,
                            DateUtils.formatDateTime(
                                LocalContext.current,
                                choice.createdAt,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                choice.available.forEach { item ->
                    val checked = item.id in choice.selected
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget).toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                            viewModel.toggle(item.id)
                        }).padding(vertical = Spacing.XSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringResource(item.title), modifier = Modifier.padding(start = Spacing.Medium))
                    }
                }
                Text(
                    stringResource(R.string.backup_import_merge_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::restore, enabled = choice.selected.isNotEmpty(), modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                Text(stringResource(R.string.backup_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelImport, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}
