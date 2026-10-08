package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactDetail
import com.qtekfun.ultimatephone.core.contacts.ContactGroupState
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate

@Composable
fun ContactDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onDialFallback: (String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        ContactDetailContentScreen(onBack, onEdit, onDialFallback)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactDetailContentScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDialFallback: (String) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val contact = (state as? ContactDetailState.Ready)?.contact
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var editingGroups by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(VCARD_MIME)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val exported = stringResource(R.string.contactsadv_export_one_done)
    val exportFailed = stringResource(R.string.contactsadv_export_failed)
    val groupFailed = stringResource(R.string.contactsadv_detail_group_failed)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    DetailMessage.Exported -> exported
                    DetailMessage.ExportFailed -> exportFailed
                    DetailMessage.GroupFailed -> groupFailed
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contact_back)) }
                },
                actions = {
                    if (contact != null) {
                        val starLabel = stringResource(if (contact.starred) R.string.contact_unstar else R.string.contact_star)
                        IconButton(onClick = { viewModel.toggleStar(contact.starred) }) {
                            Icon(if (contact.starred) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = starLabel)
                        }
                        IconButton(onClick = {
                            onEdit(contact.lookupKey)
                        }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit)) }
                        IconButton(onClick = { exportLauncher.launch(exportFileName(single = true)) }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.contactsadv_export_one))
                        }
                        IconButton(onClick = { share(context, contact.lookupKey) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.contact_share))
                        }
                        IconButton(onClick = {
                            confirmDelete = true
                        }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.contact_delete)) }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ContactDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                ContactDetailState.NotFound -> Text(
                    stringResource(R.string.contact_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is ContactDetailState.Ready -> DetailBody(
                    contact = current.contact,
                    groups = groups,
                    onEditGroups = { editingGroups = true },
                    onCall = { number -> if (!viewModel.call(number)) onDialFallback(number) }
                )
            }
        }
    }

    if (editingGroups) {
        GroupsDialog(groups, onToggle = viewModel::setGroupMembership, onDismiss = { editingGroups = false })
    }

    if (confirmDelete && contact != null) {
        val shownName = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = { Text(stringResource(R.string.contact_delete_message, shownName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text(stringResource(R.string.contact_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.contact_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailBody(contact: ContactDetail, groups: List<ContactGroupState>, onEditGroups: () -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val name = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactAvatar(name = name, photoUri = contact.photoUri, size = 112.dp)
            Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            if (contact.organization.isNotBlank()) {
                Text(contact.organization, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (contact.phones.isNotEmpty()) {
            Section(R.string.contact_section_phones)
            contact.phones.forEach { stored ->
                val number = stored.value.value
                ListItem(
                    headlineContent = { Text(number) },
                    supportingContent = { Text(stored.typeLabel) },
                    trailingContent = {
                        // The whole row calls; the button is the same action for sighted users, so TalkBack skips it.
                        FilledTonalIconButton(onClick = { onCall(number) }, modifier = Modifier.clearAndSetSemantics {}) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.contact_call, number)) { onCall(number) }
                )
            }
        }
        if (contact.emails.isNotEmpty()) {
            Section(R.string.contact_section_emails)
            contact.emails.forEach { stored ->
                val address = stored.value.value
                ListItem(
                    headlineContent = { Text(address) },
                    supportingContent = { Text(stored.typeLabel) },
                    trailingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.contact_email_to, address)) {
                        sendEmail(context, address)
                    }
                )
            }
        }
        contact.birthday?.let { birthday ->
            Section(R.string.contact_section_birthday)
            ListItem(headlineContent = { Text(birthdayText(context, birthday)) })
        }
        if (contact.notes.isNotBlank()) {
            Section(R.string.contact_section_notes)
            ListItem(headlineContent = { Text(contact.notes) })
        }
        contact.account?.let { account ->
            Section(R.string.contact_section_account)
            ListItem(headlineContent = { Text(accountText(context, account)) })
        }
        if (groups.isNotEmpty()) GroupsSection(groups, onEditGroups)
    }
}

@Composable
private fun GroupsSection(groups: List<ContactGroupState>, onEdit: () -> Unit) {
    Section(R.string.contactsadv_detail_groups)
    val joined = groups.filter { it.member }.joinToString(", ") { it.group.title }
    ListItem(
        headlineContent = { Text(joined.ifEmpty { stringResource(R.string.contactsadv_detail_groups_none) }) },
        trailingContent = {
            TextButton(onClick = onEdit, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.contactsadv_detail_edit_groups))
            }
        }
    )
}

/** Tick the groups the contact belongs to; a change is written at once. A group of an account this contact is not in cannot be ticked. */
@Composable
private fun GroupsDialog(groups: List<ContactGroupState>, onToggle: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contactsadv_detail_groups_dialog)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { entry ->
                    val enabled = entry.canChange || entry.member
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).toggleable(
                            value = entry.member,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(entry.group.id, it) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = entry.member, onCheckedChange = null, enabled = enabled)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(entry.group.title, style = MaterialTheme.typography.bodyLarge)
                            if (!entry.canChange && !entry.member) {
                                Text(
                                    stringResource(R.string.contactsadv_detail_group_other_account),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.contactsadv_done)) } }
    )
}

@Composable
private fun Section(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() }
    )
}

private fun sendEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_share_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Shares the contact as a vCard straight from the contacts provider, so every field and the photo go along. */
private fun share(context: Context, lookupKey: String) {
    val vcard = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, Uri.encode(lookupKey))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
        putExtra(Intent.EXTRA_STREAM, vcard)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_share_failed, Toast.LENGTH_SHORT).show()
    }
}
