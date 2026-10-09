package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.RadioRow
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.SwitchRow
import com.qtekfun.ultimatephone.core.designsystem.UiAction

@Composable
fun VCardImportScreen(onClose: () -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button),
        icon = Icons.Filled.UploadFile
    ) {
        VCardImportContentScreen(onClose)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VCardImportContentScreen(onClose: () -> Unit, viewModel: VCardImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }
    val pick = UiAction(stringResource(R.string.contactsadv_import_pick)) { picker.launch(VCARD_PICKER_TYPES) }
    // Open the file picker straight away the first time; the buttons below reopen it.
    LaunchedEffect(Unit) {
        if (!viewModel.pickerOpened) {
            viewModel.pickerOpened = true
            picker.launch(VCARD_PICKER_TYPES)
        }
    }

    ScreenScaffold(title = stringResource(R.string.contactsadv_import_title), onBack = onClose) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ImportState.Idle -> Centered {
                    EmptyState(
                        icon = Icons.Filled.UploadFile,
                        title = stringResource(R.string.contactsadv_import_title),
                        body = stringResource(R.string.design2_import_idle_body),
                        action = pick
                    )
                }
                ImportState.Reading -> Centered { Busy(R.string.contactsadv_import_reading) }
                ImportState.Importing -> Centered { Busy(R.string.contactsadv_import_importing) }
                is ImportState.Failed -> Centered {
                    EmptyState(
                        icon = Icons.Filled.ErrorOutline,
                        title = stringResource(if (current.unreadable) R.string.contactsadv_import_read_failed else R.string.contactsadv_import_none),
                        body = stringResource(R.string.design2_import_failed_body),
                        action = pick
                    )
                }
                is ImportState.Preview -> PreviewBody(current.preview, viewModel)
                is ImportState.Done -> Centered {
                    EmptyState(
                        icon = Icons.Filled.CheckCircle,
                        title = stringResource(R.string.contactsadv_import_done_title),
                        body = summaryText(current.summary),
                        action = UiAction(stringResource(R.string.contactsadv_import_close), onClose),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium, Alignment.CenterVertically),
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

/** One line per figure, as before; the ones that are zero are left out. */
@Composable
private fun summaryText(summary: ImportSummary): String = buildList {
    add(stringResource(R.string.contactsadv_import_done_imported, summary.imported))
    if (summary.skippedDuplicates > 0) add(stringResource(R.string.contactsadv_import_done_dups, summary.skippedDuplicates))
    if (summary.invalid > 0) add(stringResource(R.string.contactsadv_import_done_invalid, summary.invalid))
    if (summary.failed > 0) add(stringResource(R.string.contactsadv_import_done_failed, summary.failed))
}.joinToString("\n")

@Composable
private fun PreviewBody(preview: ImportPreview, viewModel: VCardImportViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        val notes = buildList {
            if (preview.invalid > 0) add(pluralStringResource(R.plurals.contactsadv_import_invalid, preview.invalid, preview.invalid))
            if (preview.duplicates.isNotEmpty()) {
                add(pluralStringResource(R.plurals.contactsadv_import_dups, preview.duplicates.size, preview.duplicates.size))
            }
        }
        InfoBanner(
            kind = BannerKind.Info,
            title = pluralStringResource(R.plurals.contactsadv_import_found, preview.contacts.size, preview.contacts.size),
            body = notes.joinToString("\n").ifEmpty { null },
            modifier = Modifier.padding(horizontal = Spacing.Medium)
        )
        if (preview.duplicates.isNotEmpty()) {
            SettingsGroup(modifier = Modifier.padding(horizontal = Spacing.Medium)) {
                SwitchRow(
                    title = stringResource(R.string.contactsadv_import_skip_dups),
                    checked = preview.skipDuplicates,
                    onCheckedChange = viewModel::setSkipDuplicates
                )
            }
        }
        if (preview.accounts.size > 1) {
            SettingsGroup(title = stringResource(R.string.contactsadv_import_account), modifier = Modifier.padding(horizontal = Spacing.Medium)) {
                preview.accounts.forEach { account ->
                    RadioRow(
                        title = accountText(context, account),
                        selected = account == preview.account,
                        onClick = { viewModel.chooseAccount(account) }
                    )
                }
            }
        }
        Button(
            onClick = viewModel::import,
            enabled = preview.toImport.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium).heightIn(min = Spacing.MinTarget)
        ) {
            Text(pluralStringResource(R.plurals.contactsadv_import_action, preview.toImport.size, preview.toImport.size))
        }
    }
}
