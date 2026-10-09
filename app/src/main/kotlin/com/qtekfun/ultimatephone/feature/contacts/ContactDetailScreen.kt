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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactDetail
import com.qtekfun.ultimatephone.core.contacts.ContactGroupState
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing

private val GroupModifier = Modifier.padding(horizontal = Spacing.Medium)
private val DetailAvatarSize = 112.dp

@Composable
fun ContactDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onDialFallback: (String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button),
        icon = Icons.Filled.AccountCircle
    ) {
        ContactDetailContentScreen(onBack, onEdit, onDialFallback)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    ScreenScaffold(
        title = contact?.displayName?.ifBlank { null } ?: if (contact != null) stringResource(R.string.contact_unnamed) else "",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (contact != null) {
                val starLabel = stringResource(if (contact.starred) R.string.contact_unstar else R.string.contact_star)
                IconButton(onClick = { viewModel.toggleStar(contact.starred) }) {
                    Icon(if (contact.starred) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = starLabel)
                }
                IconButton(onClick = { onEdit(contact.lookupKey) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit))
                }
                DetailMenu(
                    onExport = { exportLauncher.launch(exportFileName(single = true)) },
                    onShare = { share(context, contact.lookupKey) },
                    onDelete = { confirmDelete = true }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ContactDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                ContactDetailState.NotFound -> EmptyState(
                    icon = Icons.Filled.PersonOff,
                    title = stringResource(R.string.contact_not_found),
                    body = stringResource(R.string.design2_contact_missing_body),
                    modifier = Modifier.align(Alignment.Center)
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
        ConfirmDialog(
            title = stringResource(R.string.contact_delete_title),
            body = stringResource(R.string.contact_delete_message, shownName),
            confirmLabel = stringResource(R.string.contact_delete),
            dismissLabel = stringResource(R.string.contact_cancel),
            onConfirm = { viewModel.delete(onDeleted = onBack) },
            onDismiss = { confirmDelete = false },
            destructive = true
        )
    }
}

@Composable
private fun DetailMenu(onExport: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.contactsadv_menu_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contactsadv_export_one)) },
                onClick = {
                    open = false
                    onExport()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_share)) },
                onClick = {
                    open = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDelete()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailBody(contact: ContactDetail, groups: List<ContactGroupState>, onEditGroups: () -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val name = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // The name is the screen title above; the header card shows who it is.
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = GroupModifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                ContactAvatar(name = name, photoUri = contact.photoUri, size = DetailAvatarSize)
                if (contact.organization.isNotBlank()) {
                    Text(
                        contact.organization,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (contact.phones.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.contact_section_phones), modifier = GroupModifier) {
                contact.phones.forEach { stored ->
                    val number = stored.value.value
                    SettingsRow(
                        title = number,
                        summary = stored.typeLabel,
                        icon = Icons.Filled.Call,
                        onClick = { onCall(number) },
                        onClickLabel = stringResource(R.string.contact_call, number),
                        trailing = {}
                    )
                }
            }
        }
        if (contact.emails.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.contact_section_emails), modifier = GroupModifier) {
                contact.emails.forEach { stored ->
                    val address = stored.value.value
                    SettingsRow(
                        title = address,
                        summary = stored.typeLabel,
                        icon = Icons.Filled.Email,
                        onClick = { sendEmail(context, address) },
                        onClickLabel = stringResource(R.string.contact_email_to, address),
                        trailing = {}
                    )
                }
            }
        }
        contact.birthday?.let { birthday ->
            SettingsGroup(title = stringResource(R.string.contact_section_birthday), modifier = GroupModifier) {
                SettingsRow(title = birthdayText(context, birthday), icon = Icons.Filled.Cake)
            }
        }
        if (contact.notes.isNotBlank()) {
            SettingsGroup(title = stringResource(R.string.contact_section_notes), modifier = GroupModifier) {
                SettingsRow(title = contact.notes, icon = Icons.Filled.Notes)
            }
        }
        contact.account?.let { account ->
            SettingsGroup(title = stringResource(R.string.contact_section_account), modifier = GroupModifier) {
                SettingsRow(title = accountText(context, account), icon = Icons.Filled.AccountCircle)
            }
        }
        if (groups.isNotEmpty()) {
            val joined = groups.filter { it.member }.joinToString(", ") { it.group.title }
            SettingsGroup(title = stringResource(R.string.contactsadv_detail_groups), modifier = GroupModifier) {
                SettingsRow(
                    title = joined.ifEmpty { stringResource(R.string.contactsadv_detail_groups_none) },
                    icon = Icons.Filled.Groups,
                    onClick = onEditGroups,
                    onClickLabel = stringResource(R.string.contactsadv_detail_edit_groups)
                )
            }
        }
    }
}

/** Tick the groups the contact belongs to; a change is written at once. A group of an account this contact is not in cannot be ticked. */
@Composable
private fun GroupsDialog(groups: List<ContactGroupState>, onToggle: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.contactsadv_detail_groups_dialog)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { entry ->
                    val enabled = entry.canChange || entry.member
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget).toggleable(
                            value = entry.member,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(entry.group.id, it) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = entry.member, onCheckedChange = null, enabled = enabled)
                        Column(Modifier.padding(start = Spacing.Medium)) {
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
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.contactsadv_done)) }
        }
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
