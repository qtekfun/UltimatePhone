package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate

@Composable
fun VCardImportScreen(onClose: () -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        VCardImportContentScreen(onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VCardImportContentScreen(onClose: () -> Unit, viewModel: VCardImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }
    // Open the file picker straight away the first time; the buttons below reopen it.
    LaunchedEffect(Unit) {
        if (!viewModel.pickerOpened) {
            viewModel.pickerOpened = true
            picker.launch(VCARD_PICKER_TYPES)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contactsadv_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contact_back)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ImportState.Idle -> Centered { PickButton { picker.launch(VCARD_PICKER_TYPES) } }
                ImportState.Reading -> Centered { Busy(R.string.contactsadv_import_reading) }
                ImportState.Importing -> Centered { Busy(R.string.contactsadv_import_importing) }
                is ImportState.Failed -> Centered {
                    Text(
                        stringResource(if (current.unreadable) R.string.contactsadv_import_read_failed else R.string.contactsadv_import_none),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    PickButton { picker.launch(VCARD_PICKER_TYPES) }
                }
                is ImportState.Preview -> PreviewBody(current.preview, viewModel)
                is ImportState.Done -> Centered {
                    SummaryBody(current.summary)
                    Button(onClick = onClose, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.contactsadv_import_close)) }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Busy(label: Int) {
    LoadingIndicator()
    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun PickButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.contactsadv_import_pick)) }
}

@Composable
private fun SummaryBody(summary: ImportSummary) {
    Text(
        stringResource(R.string.contactsadv_import_done_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }
    )
    Text(stringResource(R.string.contactsadv_import_done_imported, summary.imported))
    if (summary.skippedDuplicates > 0) Text(stringResource(R.string.contactsadv_import_done_dups, summary.skippedDuplicates))
    if (summary.invalid > 0) Text(stringResource(R.string.contactsadv_import_done_invalid, summary.invalid))
    if (summary.failed > 0) Text(stringResource(R.string.contactsadv_import_done_failed, summary.failed), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PreviewBody(preview: ImportPreview, viewModel: VCardImportViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.contactsadv_import_found, preview.contacts.size, preview.contacts.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
            }
        )
        if (preview.invalid > 0) {
            Text(pluralStringResource(R.plurals.contactsadv_import_invalid, preview.invalid, preview.invalid), style = MaterialTheme.typography.bodyMedium)
        }
        if (preview.duplicates.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.contactsadv_import_dups, preview.duplicates.size, preview.duplicates.size),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).toggleable(
                    value = preview.skipDuplicates,
                    role = Role.Switch,
                    onValueChange = viewModel::setSkipDuplicates
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.contactsadv_import_skip_dups), modifier = Modifier.weight(1f))
                Switch(checked = preview.skipDuplicates, onCheckedChange = null)
            }
        }
        if (preview.accounts.size > 1) {
            Text(
                stringResource(R.string.contactsadv_import_account),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            preview.accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).selectable(
                        selected = account == preview.account,
                        role = Role.RadioButton,
                        onClick = { viewModel.chooseAccount(account) }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = account == preview.account, onClick = null)
                    Text(accountText(context, account), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Button(
            onClick = viewModel::import,
            enabled = preview.toImport.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).defaultMinSize(minHeight = 48.dp)
        ) {
            Text(pluralStringResource(R.plurals.contactsadv_import_action, preview.toImport.size, preview.toImport.size))
        }
    }
}
