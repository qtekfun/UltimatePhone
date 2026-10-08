package com.qtekfun.ultimatephone.feature.backup

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_PASSWORD_LENGTH = 8

/** Settings > Backup: export the settings to an encrypted file, or import one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::exportTo)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::filePicked)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.backup_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.backup_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        stringResource(R.string.backup_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.backup_export_summary), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = viewModel::askExport, enabled = !state.busy) { Text(stringResource(R.string.backup_export_action)) }
            HorizontalDivider()
            Text(stringResource(R.string.backup_import_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.backup_import_summary), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text(stringResource(R.string.backup_import_action)) }
            if (state.busy) Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
            state.message?.let { MessageText(it) }
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
private fun MessageText(message: BackupMessage) {
    when (message) {
        BackupMessage.ExportDone -> Text(stringResource(R.string.backup_export_done), style = MaterialTheme.typography.bodyMedium)
        BackupMessage.ExportFailed -> Text(stringResource(R.string.backup_export_failed), color = MaterialTheme.colorScheme.error)
        is BackupMessage.ImportFailed -> Text(stringResource(message.text), color = MaterialTheme.colorScheme.error)
        is BackupMessage.ImportDone -> Text(stringResource(R.string.backup_import_done, titles(message.sections)), style = MaterialTheme.typography.bodyMedium)
        is BackupMessage.ImportPartial -> Text(
            stringResource(R.string.backup_import_partial, titles(message.sections)),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun titles(ids: List<Int>): String =
    androidx.compose.ui.platform.LocalContext.current.let { context -> ids.joinToString(", ") { context.getString(it) } }

@Composable
private fun PasswordDialog(title: Int, confirmLabel: Int, repeat: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val tooShort = repeat && password.length < MIN_PASSWORD_LENGTH
    val mismatch = repeat && password != again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty() && !tooShort && !mismatch) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } }
    )
}

@Composable
private fun ChoiceDialog(choice: ImportChoice, viewModel: BackupViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        title = { Text(stringResource(R.string.backup_import_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (choice.createdAt > 0) {
                    Text(
                        stringResource(
                            R.string.backup_import_created,
                            DateUtils.formatDateTime(
                                androidx.compose.ui.platform.LocalContext.current,
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
                        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                            viewModel.toggle(item.id)
                        }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringResource(item.title), modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Text(
                    stringResource(R.string.backup_import_merge_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = viewModel::restore, enabled = choice.selected.isNotEmpty()) { Text(stringResource(R.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.backup_cancel)) } }
    )
}
